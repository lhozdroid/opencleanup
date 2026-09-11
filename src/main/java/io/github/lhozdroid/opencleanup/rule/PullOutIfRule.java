package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Pulls a shared passive inner if condition outside both branches of an outer if.
 *
 * <p>The supported shape is {@code if (outer) if (common) then else if (common)
 * elseBranch}, with both inner if statements lacking else branches. The result
 * evaluates the shared condition once and retains the outer branch selection
 * inside it.</p>
 */
public final class PullOutIfRule implements CleanupRule {

    /** The stable identifier for this rule. */
    public static final String ID = "conditions.pull-out-if";

    /**
     * Returns the stable identifier for pull-out-if cleanup.
     *
     * @return the pull-out-if rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records edits that pull common inner conditions out of if/else branches.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one common condition is pulled out
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Pulls a shared condition out of one eligible if statement.
             *
             * @param node the visited outer if statement
             * @return {@code false} after a replacement, otherwise {@code true}
             */
            @Override
            public boolean visit(IfStatement node) {
                IfStatement replacement = replacementFor(compilationUnit, node);
                if (replacement == null) {
                    return true;
                }

                rewrite.replace(node, replacement, null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Builds a replacement for an outer if with matching inner conditions.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param outerIf the candidate outer if statement
     * @return the replacement if statement, or {@code null} when unsupported
     */
    private IfStatement replacementFor(CompilationUnit compilationUnit, IfStatement outerIf) {
        IfStatement thenInner = soleIf(outerIf.getThenStatement());
        IfStatement elseInner = soleIf(outerIf.getElseStatement());
        if (thenInner == null
                || elseInner == null
                || thenInner.getElseStatement() != null
                || elseInner.getElseStatement() != null
                || !isPassive(outerIf.getExpression())
                || !isPassive(thenInner.getExpression())
                || !isPassive(elseInner.getExpression())
                || !thenInner.getExpression().subtreeMatch(
                        new ASTMatcher(), elseInner.getExpression())
                || containsComment(compilationUnit, outerIf)) {
            return null;
        }

        AST ast = outerIf.getAST();
        IfStatement replacement = ast.newIfStatement();
        replacement.setExpression(copyExpression(ast, thenInner.getExpression()));

        Block replacementBlock = ast.newBlock();
        IfStatement retainedIf = ast.newIfStatement();
        retainedIf.setExpression(copyExpression(ast, outerIf.getExpression()));
        retainedIf.setThenStatement((org.eclipse.jdt.core.dom.Statement) ASTNode.copySubtree(
                ast, thenInner.getThenStatement()));
        retainedIf.setElseStatement((org.eclipse.jdt.core.dom.Statement) ASTNode.copySubtree(
                ast, elseInner.getThenStatement()));
        replacementBlock.statements().add(retainedIf);
        replacement.setThenStatement(replacementBlock);
        return replacement;
    }

    /**
     * Extracts an if statement when a branch is either that statement itself or
     * a block containing exactly that statement.
     *
     * @param statement the branch statement to inspect
     * @return the sole nested if statement, or {@code null} when the shape differs
     */
    private IfStatement soleIf(Statement statement) {
        if (statement instanceof IfStatement ifStatement) {
            return ifStatement;
        }
        if (statement instanceof Block block && block.statements().size() == 1
                && block.statements().get(0) instanceof IfStatement ifStatement) {
            return ifStatement;
        }
        return null;
    }

    /**
     * Copies an expression into the same AST.
     *
     * @param ast the target AST
     * @param expression the expression to copy
     * @return the copied expression
     */
    private Expression copyExpression(AST ast, Expression expression) {
        return (Expression) ASTNode.copySubtree(ast, expression);
    }

    /**
     * Checks whether an expression is a name with no executable side effects.
     *
     * @param expression the expression to inspect
     * @return {@code true} when the expression is a simple or qualified name
     */
    private boolean isPassive(Expression expression) {
        return expression instanceof Name;
    }

    /**
     * Checks whether a source range contains a parsed comment.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param node the source range to inspect
     * @return {@code true} when a comment overlaps the node
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
