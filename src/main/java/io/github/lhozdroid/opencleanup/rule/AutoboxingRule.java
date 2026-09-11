package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Replaces explicit wrapper {@code valueOf} calls with primitive literals in wrapper declarations.
 */
public final class AutoboxingRule implements CleanupRule {

    /** The stable identifier used to select this cleanup rule. */
    public static final String ID = "boxing.autoboxing";

    /**
     * Returns the stable identifier for autoboxing cleanup.
     *
     * @return the autoboxing rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records removals for literal wrapper value factories in typed declarations.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one value factory is replaced
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Replaces an eligible wrapper value factory initializer.
             *
             * @param node the visited variable declaration statement
             * @return {@code true} to continue visiting nested expressions
             */
            @Override
            public boolean visit(VariableDeclarationStatement node) {
                String wrapper = wrapperName(node.getType());
                if (wrapper == null) {
                    return true;
                }
                replaceFactories(node.fragments(), node.getAST(), wrapper, rewrite, changed);
                return true;
            }

            /**
             * Replaces eligible wrapper value factories in a field declaration.
             *
             * @param node the visited field declaration
             * @return {@code true} to continue visiting nested expressions
             */
            @Override
            public boolean visit(FieldDeclaration node) {
                String wrapper = wrapperName(node.getType());
                if (wrapper != null) {
                    replaceFactories(node.fragments(), node.getAST(), wrapper, rewrite, changed);
                }
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Returns the wrapper type for a declaration when it is a supported simple type.
     *
     * @param declaration the declaration to inspect
     * @return the wrapper name, or {@code null} when it is not supported
     */
    private String wrapperName(org.eclipse.jdt.core.dom.Type type) {
        if (!(type instanceof org.eclipse.jdt.core.dom.SimpleType simpleType)) {
            return null;
        }
        String name = simpleType.getName().getFullyQualifiedName();
        return isWrapper(name) ? name : null;
    }

    /**
     * Replaces eligible value factories in a declaration fragment list.
     *
     * @param fragments the declaration fragments to inspect
     * @param ast the AST owning the declarations
     * @param wrapper the declared wrapper name
     * @param rewrite the AST rewrite collecting source edits
     * @param changed mutable change flag
     */
    private void replaceFactories(
            java.util.List<?> fragments,
            org.eclipse.jdt.core.dom.AST ast,
            String wrapper,
            ASTRewrite rewrite,
            boolean[] changed) {
        for (Object object : fragments) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment) object;
            if (fragment.getInitializer() instanceof MethodInvocation invocation) {
                org.eclipse.jdt.core.dom.Expression argument = replacementArgument(wrapper, invocation);
                if (argument != null) {
                    rewrite.replace(invocation, ASTNode.copySubtree(ast, argument), null);
                    changed[0] = true;
                }
            }
        }
    }

    /**
     * Finds the primitive literal argument of an exact wrapper value factory.
     *
     * @param wrapper the declared wrapper name
     * @param invocation the initializer invocation
     * @return the replacement argument, or {@code null} when it is not safe
     */
    private org.eclipse.jdt.core.dom.Expression replacementArgument(
            String wrapper,
            MethodInvocation invocation) {
        if (!(invocation.getExpression() instanceof SimpleName receiver)
                || !wrapper.equals(receiver.getIdentifier())
                || !"valueOf".equals(invocation.getName().getIdentifier())
                || invocation.arguments().size() != 1) {
            return null;
        }
        org.eclipse.jdt.core.dom.Expression argument =
                (org.eclipse.jdt.core.dom.Expression) invocation.arguments().get(0);
        return literalMatches(wrapper, argument) ? argument : null;
    }

    /**
     * Checks whether an argument is a literal with the exact primitive shape for a wrapper.
     *
     * @param wrapper the wrapper name
     * @param argument the value-factory argument
     * @return {@code true} when autoboxing preserves the literal type
     */
    private boolean literalMatches(String wrapper, org.eclipse.jdt.core.dom.Expression argument) {
        if ("Boolean".equals(wrapper)) {
            return argument instanceof BooleanLiteral;
        }
        if ("Character".equals(wrapper)) {
            return argument instanceof CharacterLiteral;
        }
        if (!(argument instanceof NumberLiteral literal)) {
            return false;
        }
        String token = literal.getToken().toLowerCase(java.util.Locale.ROOT);
        return switch (wrapper) {
            case "Byte", "Short", "Integer" -> !token.endsWith("l")
                    && !token.endsWith("f")
                    && !token.endsWith("d");
            case "Long" -> token.endsWith("l");
            case "Float" -> token.endsWith("f");
            case "Double" -> token.endsWith("d") || token.contains(".") || token.contains("e");
            default -> false;
        };
    }

    /**
     * Checks whether a simple type name identifies a supported Java wrapper.
     *
     * @param name the type name to inspect
     * @return {@code true} for a supported wrapper type
     */
    private boolean isWrapper(String name) {
        return switch (name) {
            case "Boolean", "Character", "Byte", "Short", "Integer", "Long", "Float", "Double" -> true;
            default -> false;
        };
    }
}
