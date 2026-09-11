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
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Removes explicit primitive conversion calls from literal wrapper factories in primitive declarations.
 */
public final class UnboxingRule implements CleanupRule {

    /** The stable identifier used to select this cleanup rule. */
    public static final String ID = "boxing.unboxing";

    /**
     * Returns the stable identifier for unboxing cleanup.
     *
     * @return the unboxing rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records removals for exact literal wrapper factory and primitive accessor chains.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one accessor chain is replaced
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Replaces an eligible wrapper accessor initializer.
             *
             * @param node the visited primitive declaration
             * @return {@code true} to continue visiting nested expressions
             */
            @Override
            public boolean visit(VariableDeclarationStatement node) {
                String primitive = primitiveName(node.getType());
                if (primitive == null) {
                    return true;
                }
                replaceAccessors(node.fragments(), node.getAST(), primitive, rewrite, changed);
                return true;
            }

            /**
             * Replaces eligible wrapper accessors in a primitive field declaration.
             *
             * @param node the visited field declaration
             * @return {@code true} to continue visiting nested expressions
             */
            @Override
            public boolean visit(FieldDeclaration node) {
                String primitive = primitiveName(node.getType());
                if (primitive != null) {
                    replaceAccessors(node.fragments(), node.getAST(), primitive, rewrite, changed);
                }
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Returns the primitive type for a declaration when it is supported.
     *
     * @param declaration the declaration to inspect
     * @return the primitive name, or {@code null} when unsupported
     */
    private String primitiveName(org.eclipse.jdt.core.dom.Type type) {
        if (!(type instanceof PrimitiveType primitiveType)) {
            return null;
        }
        return primitiveType.getPrimitiveTypeCode().toString();
    }

    /**
     * Replaces eligible accessor chains in a declaration fragment list.
     *
     * @param fragments the declaration fragments to inspect
     * @param ast the AST owning the declarations
     * @param primitive the declared primitive type
     * @param rewrite the AST rewrite collecting source edits
     * @param changed mutable change flag
     */
    private void replaceAccessors(
            java.util.List<?> fragments,
            org.eclipse.jdt.core.dom.AST ast,
            String primitive,
            ASTRewrite rewrite,
            boolean[] changed) {
        for (Object object : fragments) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment) object;
            if (fragment.getInitializer() instanceof MethodInvocation accessor) {
                org.eclipse.jdt.core.dom.Expression argument = replacementArgument(primitive, accessor);
                if (argument != null) {
                    rewrite.replace(accessor, ASTNode.copySubtree(ast, argument), null);
                    changed[0] = true;
                }
            }
        }
    }

    /**
     * Finds the literal argument of an exact wrapper accessor chain.
     *
     * @param primitive the declared primitive type
     * @param accessor the initializer accessor invocation
     * @return the primitive literal, or {@code null} when the shape is unsupported
     */
    private org.eclipse.jdt.core.dom.Expression replacementArgument(
            String primitive,
            MethodInvocation accessor) {
        String wrapper = wrapperFor(primitive);
        if (wrapper == null
                || !(accessor.getExpression() instanceof MethodInvocation valueOf)
                || !(valueOf.getExpression() instanceof SimpleName receiver)
                || !wrapper.equals(receiver.getIdentifier())
                || !"valueOf".equals(valueOf.getName().getIdentifier())
                || valueOf.arguments().size() != 1
                || !accessor.arguments().isEmpty()
                || !(primitive + "Value").equals(accessor.getName().getIdentifier())) {
            return null;
        }
        org.eclipse.jdt.core.dom.Expression argument =
                (org.eclipse.jdt.core.dom.Expression) valueOf.arguments().get(0);
        return literalMatches(primitive, argument) ? argument : null;
    }

    /**
     * Maps a supported primitive type to its wrapper type.
     *
     * @param primitive the primitive type name
     * @return the wrapper name, or {@code null} when not supported
     */
    private String wrapperFor(String primitive) {
        return switch (primitive) {
            case "boolean" -> "Boolean";
            case "char" -> "Character";
            case "byte" -> "Byte";
            case "short" -> "Short";
            case "int" -> "Integer";
            case "long" -> "Long";
            case "float" -> "Float";
            case "double" -> "Double";
            default -> null;
        };
    }

    /**
     * Checks whether a literal has the exact primitive shape required by the accessor.
     *
     * @param primitive the primitive type name
     * @param argument the value-factory argument
     * @return {@code true} when the literal can replace the full chain
     */
    private boolean literalMatches(String primitive, org.eclipse.jdt.core.dom.Expression argument) {
        if ("boolean".equals(primitive)) {
            return argument instanceof BooleanLiteral;
        }
        if ("char".equals(primitive)) {
            return argument instanceof CharacterLiteral;
        }
        if (!(argument instanceof NumberLiteral literal)) {
            return false;
        }
        String token = literal.getToken().toLowerCase(java.util.Locale.ROOT);
        return switch (primitive) {
            case "byte", "short", "int" -> !token.endsWith("l")
                    && !token.endsWith("f")
                    && !token.endsWith("d");
            case "long" -> token.endsWith("l");
            case "float" -> token.endsWith("f");
            case "double" -> token.endsWith("d") || token.contains(".") || token.contains("e");
            default -> false;
        };
    }
}
