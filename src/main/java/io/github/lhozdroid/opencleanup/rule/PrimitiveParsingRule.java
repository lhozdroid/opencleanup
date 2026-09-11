package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Uses primitive parsing methods when a wrapper value factory initializes a primitive declaration.
 */
public final class PrimitiveParsingRule implements CleanupRule {

    /** The stable identifier used to select this cleanup rule. */
    public static final String ID = "parsing.primitive";

    /**
     * Returns the stable identifier for primitive parsing cleanup.
     *
     * @return the primitive parsing rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for wrapper value factories in primitive declarations.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one factory is replaced
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Replaces factories in a local primitive declaration.
             *
             * @param node the visited variable declaration statement
             * @return {@code true} to continue visiting nested expressions
             */
            @Override
            public boolean visit(VariableDeclarationStatement node) {
                replace(node.getType(), node.fragments(), node.getAST(), rewrite, changed);
                return true;
            }

            /**
             * Replaces factories in a primitive field declaration.
             *
             * @param node the visited field declaration
             * @return {@code true} to continue visiting nested expressions
             */
            @Override
            public boolean visit(FieldDeclaration node) {
                replace(node.getType(), node.fragments(), node.getAST(), rewrite, changed);
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Replaces eligible value factories in one declaration.
     *
     * @param type the declaration type
     * @param fragments the declaration fragments
     * @param ast the owning AST
     * @param rewrite the AST rewrite collecting source edits
     * @param changed mutable change flag
     */
    private void replace(
            Type type,
            java.util.List<?> fragments,
            AST ast,
            ASTRewrite rewrite,
            boolean[] changed) {
        if (!(type instanceof PrimitiveType primitiveType)) {
            return;
        }
        String method = parserMethod(primitiveType.getPrimitiveTypeCode().toString());
        if (method == null) {
            return;
        }
        for (Object value : fragments) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment) value;
            if (fragment.getInitializer() instanceof MethodInvocation invocation) {
                MethodInvocation replacement = replacement(invocation, method, ast);
                if (replacement != null) {
                    rewrite.replace(invocation, replacement, null);
                    changed[0] = true;
                }
            }
        }
    }

    /**
     * Creates a primitive parser invocation for an exact wrapper value factory.
     *
     * @param invocation the candidate value factory
     * @param parserMethodName the expected primitive parser name
     * @param ast the owning AST
     * @return the parser invocation, or {@code null} when unsupported
     */
    private MethodInvocation replacement(MethodInvocation invocation, String parserMethodName, AST ast) {
        if (!(invocation.getExpression() instanceof SimpleName receiver)
                || !"valueOf".equals(invocation.getName().getIdentifier())
                || invocation.arguments().size() != 1
                || !parserMethodName.equals(parserMethodForWrapper(receiver.getIdentifier()))) {
            return null;
        }
        MethodInvocation replacement = ast.newMethodInvocation();
        replacement.setExpression(ast.newSimpleName(receiver.getIdentifier()));
        replacement.setName(ast.newSimpleName(parserMethodName));
        replacement.arguments().add(ASTNode.copySubtree(ast, (ASTNode) invocation.arguments().get(0)));
        return replacement;
    }

    /**
     * Maps a primitive type to its wrapper parsing method.
     *
     * @param primitive the primitive type name
     * @return the parser method name, or {@code null} when no direct parser exists
     */
    private String parserMethod(String primitive) {
        return switch (primitive) {
            case "boolean" -> "parseBoolean";
            case "byte" -> "parseByte";
            case "short" -> "parseShort";
            case "int" -> "parseInt";
            case "long" -> "parseLong";
            case "float" -> "parseFloat";
            case "double" -> "parseDouble";
            default -> null;
        };
    }

    /**
     * Maps a primitive wrapper name to its parsing method.
     *
     * @param wrapper the wrapper type name
     * @return the parser method name, or {@code null} when unsupported
     */
    private String parserMethodForWrapper(String wrapper) {
        return switch (wrapper) {
            case "Boolean" -> "parseBoolean";
            case "Byte" -> "parseByte";
            case "Short" -> "parseShort";
            case "Integer" -> "parseInt";
            case "Long" -> "parseLong";
            case "Float" -> "parseFloat";
            case "Double" -> "parseDouble";
            default -> null;
        };
    }
}
