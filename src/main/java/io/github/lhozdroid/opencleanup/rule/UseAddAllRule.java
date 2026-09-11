package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Replaces a simple loop that adds every source element with {@code addAll}.
 *
 * <p>Only simple-name receiver and source expressions are transformed. This
 * avoids changing evaluation order or relying on unresolved method bindings.</p>
 */
public final class UseAddAllRule implements CleanupRule {

    /** The stable identifier used to select this cleanup rule. */
    public static final String ID = "control-statements.use-add-all";

    /**
     * Returns the stable identifier for bulk-add cleanup.
     *
     * @return the bulk-add rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for eligible enhanced-for add loops.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one loop is replaced
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Replaces one eligible enhanced-for add loop.
             *
             * @param node the visited enhanced-for statement
             * @return {@code false} after replacement, otherwise {@code true}
             */
            @Override
            public boolean visit(EnhancedForStatement node) {
                MethodInvocation addCall = addCall(node);
                if (addCall == null || containsComment(compilationUnit, node)) {
                    return true;
                }

                MethodInvocation replacement = replacementFor(node, addCall);
                if (replacement == null) {
                    return true;
                }

                AST ast = node.getAST();
                ExpressionStatement replacementStatement = ast.newExpressionStatement(replacement);
                rewrite.replace(node, replacementStatement, null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Finds the loop body's sole add invocation.
     *
     * @param loop the enhanced-for statement to inspect
     * @return the add invocation, or {@code null} when the body has another shape
     */
    private MethodInvocation addCall(EnhancedForStatement loop) {
        Statement body = loop.getBody();
        if (body instanceof ExpressionStatement expressionStatement) {
            return methodInvocation(expressionStatement);
        }
        if (!(body instanceof Block block) || block.statements().size() != 1) {
            return null;
        }
        return methodInvocation((Statement) block.statements().get(0));
    }

    /**
     * Extracts a method invocation from an expression statement.
     *
     * @param statement the statement to inspect
     * @return the invocation, or {@code null} when the statement is not a call
     */
    private MethodInvocation methodInvocation(Statement statement) {
        if (!(statement instanceof ExpressionStatement expressionStatement)
                || !(expressionStatement.getExpression() instanceof MethodInvocation invocation)) {
            return null;
        }
        if (!"add".equals(invocation.getName().getIdentifier())
                || invocation.arguments().size() != 1
                || !(invocation.getExpression() instanceof SimpleName)) {
            return null;
        }
        return invocation;
    }

    /**
     * Builds an {@code addAll} invocation when the loop variable is the only argument.
     *
     * @param loop the enhanced-for statement to rewrite
     * @param addCall the loop body's add invocation
     * @return the replacement invocation, or {@code null} when the shape is unsafe
     */
    private MethodInvocation replacementFor(
            EnhancedForStatement loop,
            MethodInvocation addCall) {
        SingleVariableDeclaration parameter = loop.getParameter();
        if (parameter == null
                || !(loop.getExpression() instanceof SimpleName source)
                || !(addCall.getExpression() instanceof SimpleName target)
                || target.getIdentifier().equals(source.getIdentifier())
                || !(addCall.arguments().get(0) instanceof SimpleName argument)
                || !parameter.getName().getIdentifier().equals(argument.getIdentifier())) {
            return null;
        }

        AST ast = loop.getAST();
        MethodInvocation replacement = ast.newMethodInvocation();
        replacement.setExpression((Expression) ASTNode.copySubtree(ast, target));
        replacement.setName(ast.newSimpleName("addAll"));
        replacement.arguments().add(ASTNode.copySubtree(ast, source));
        return replacement;
    }

    /**
     * Checks whether a parsed comment overlaps the loop's source range.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param node the source range to inspect
     * @return {@code true} when a comment overlaps the source range
     */
    private boolean containsComment(CompilationUnit compilationUnit, ASTNode node) {
        int nodeStart = node.getStartPosition();
        int nodeEnd = nodeStart + node.getLength();
        for (Object value : compilationUnit.getCommentList()) {
            Comment comment = (Comment) value;
            int commentStart = comment.getStartPosition();
            int commentEnd = commentStart + comment.getLength();
            if (commentStart < nodeEnd && nodeStart < commentEnd) {
                return true;
            }
        }
        return false;
    }
}
