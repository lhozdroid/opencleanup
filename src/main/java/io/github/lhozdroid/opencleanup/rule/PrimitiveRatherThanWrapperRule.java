package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Replaces safely initialized local wrapper variables with primitive variables.
 */
public final class PrimitiveRatherThanWrapperRule implements CleanupRule {

    /** The stable identifier used to select this cleanup rule. */
    public static final String ID = "boxing.primitive-rather-than-wrapper";

    /**
     * Returns the stable identifier for primitive-over-wrapper cleanup.
     *
     * @return the primitive-over-wrapper rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for local wrapper declarations with non-null literal values.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one local declaration is converted
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Converts one safely initialized local wrapper declaration.
             *
             * @param node the visited local declaration statement
             * @return {@code true} to continue visiting nested expressions
             */
            @Override
            public boolean visit(VariableDeclarationStatement node) {
                String primitive = primitiveName(node);
                if (primitive == null || node.fragments().isEmpty()) {
                    return true;
                }
                for (Object value : node.fragments()) {
                    VariableDeclarationFragment fragment = (VariableDeclarationFragment) value;
                    if (primitiveValue(primitive, fragment.getInitializer()) == null) {
                        return true;
                    }
                }
                rewrite.replace(node.getType(), node.getAST().newPrimitiveType(primitiveCode(primitive)), null);
                for (Object value : node.fragments()) {
                    VariableDeclarationFragment fragment = (VariableDeclarationFragment) value;
                    Expression primitiveValue = primitiveValue(primitive, fragment.getInitializer());
                    if (primitiveValue != fragment.getInitializer()) {
                        rewrite.replace(fragment.getInitializer(),
                                ASTNode.copySubtree(node.getAST(), primitiveValue), null);
                    }
                }
                changed[0] = true;
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Returns the primitive counterpart of a supported local wrapper declaration.
     *
     * @param declaration the declaration to inspect
     * @return the primitive name, or {@code null} when unsupported
     */
    private String primitiveName(VariableDeclarationStatement declaration) {
        if (!(declaration.getType() instanceof SimpleType type)
                || !declaration.modifiers().isEmpty()) {
            return null;
        }
        return switch (type.getName().getFullyQualifiedName()) {
            case "Boolean" -> "boolean";
            case "Byte" -> "byte";
            case "Short" -> "short";
            case "Integer" -> "int";
            case "Long" -> "long";
            case "Float" -> "float";
            case "Double" -> "double";
            case "Character" -> "char";
            default -> null;
        };
    }

    /**
     * Returns a primitive replacement expression for an exact non-null wrapper value.
     *
     * @param primitive the target primitive name
     * @param initializer the wrapper initializer
     * @return the primitive expression, or {@code null} when unsupported
     */
    private Expression primitiveValue(String primitive, Expression initializer) {
        if (initializer == null) {
            return null;
        }
        if (isLiteral(primitive, initializer)) {
            return initializer;
        }
        String wrapper = wrapperName(primitive);
        if (initializer instanceof MethodInvocation invocation
                && invocation.getExpression() instanceof SimpleName receiver
                && wrapper.equals(receiver.getIdentifier())
                && "valueOf".equals(invocation.getName().getIdentifier())
                && invocation.arguments().size() == 1) {
            return (Expression) invocation.arguments().get(0);
        }
        if (initializer instanceof ClassInstanceCreation creation
                && creation.getType() instanceof SimpleType type
                && wrapper.equals(type.getName().getFullyQualifiedName())
                && creation.arguments().size() == 1) {
            Expression argument = (Expression) creation.arguments().get(0);
            return isLiteral(primitive, argument) ? argument : null;
        }
        return null;
    }

    /**
     * Checks whether an expression is a literal of the requested primitive type.
     *
     * @param primitive the primitive name
     * @param expression the expression to inspect
     * @return {@code true} when the literal has the expected primitive shape
     */
    private boolean isLiteral(String primitive, Expression expression) {
        if ("boolean".equals(primitive)) {
            return expression instanceof BooleanLiteral;
        }
        if ("char".equals(primitive)) {
            return expression instanceof CharacterLiteral;
        }
        if (!(expression instanceof NumberLiteral literal)) {
            return false;
        }
        String token = literal.getToken().toLowerCase(java.util.Locale.ROOT);
        return switch (primitive) {
            case "byte", "short", "int" -> !token.endsWith("l")
                    && !token.endsWith("f") && !token.endsWith("d");
            case "long" -> token.endsWith("l");
            case "float" -> token.endsWith("f");
            case "double" -> token.endsWith("d") || token.contains(".") || token.contains("e");
            default -> false;
        };
    }

    /**
     * Maps a primitive name to its wrapper name.
     *
     * @param primitive the primitive name
     * @return the wrapper name
     */
    private String wrapperName(String primitive) {
        return switch (primitive) {
            case "boolean" -> "Boolean";
            case "byte" -> "Byte";
            case "short" -> "Short";
            case "int" -> "Integer";
            case "long" -> "Long";
            case "float" -> "Float";
            case "double" -> "Double";
            case "char" -> "Character";
            default -> "";
        };
    }

    /**
     * Maps a primitive name to the corresponding JDT primitive type code.
     *
     * @param primitive the primitive name
     * @return the JDT primitive type code
     */
    private PrimitiveType.Code primitiveCode(String primitive) {
        return switch (primitive) {
            case "boolean" -> PrimitiveType.BOOLEAN;
            case "byte" -> PrimitiveType.BYTE;
            case "short" -> PrimitiveType.SHORT;
            case "int" -> PrimitiveType.INT;
            case "long" -> PrimitiveType.LONG;
            case "float" -> PrimitiveType.FLOAT;
            case "double" -> PrimitiveType.DOUBLE;
            case "char" -> PrimitiveType.CHAR;
            default -> throw new IllegalArgumentException("Unsupported primitive: " + primitive);
        };
    }
}
