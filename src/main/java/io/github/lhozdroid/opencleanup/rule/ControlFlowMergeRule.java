package io.github.lhozdroid.opencleanup.rule;

import java.util.List;

import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Pulls one identical trailing statement out of two braced {@code if} branches.
 *
 * <p>The rule deliberately requires braced branches, an identical syntax tree,
 * no local declarations, and no comments in the candidate. These restrictions
 * avoid changing variable scope or comment attachment without resolved bindings.</p>
 */
public final class ControlFlowMergeRule implements CleanupRule {

    /** The stable identifier for this rule. */
    public static final String ID = "control-flow.merge";

    /**
     * Returns the stable identifier for control-flow merging.
     *
     * @return the control-flow merge rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records edits that move an identical trailing statement after an if statement.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one trailing statement is merged
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Tries to merge the trailing statements of one if statement.
             *
             * @param node the visited if statement
             * @return {@code false} after a rewrite, otherwise {@code true}
             */
            @Override
            public boolean visit(IfStatement node) {
                if (mergeTrailingStatement(compilationUnit, rewrite, node)) {
                    changed[0] = true;
                    return false;
                }
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Schedules a merge when both branches have the same final statement.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param ifStatement the candidate if statement
     * @return {@code true} when a merge is scheduled
     */
    private boolean mergeTrailingStatement(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            IfStatement ifStatement) {
        if (!(ifStatement.getThenStatement() instanceof Block thenBlock)
                || !(ifStatement.getElseStatement() instanceof Block elseBlock)
                || !(ifStatement.getParent() instanceof Block parentBlock)
                || containsComment(compilationUnit, ifStatement)
                || containsVariableDeclaration(thenBlock)
                || containsVariableDeclaration(elseBlock)) {
            return false;
        }

        List<?> thenStatements = thenBlock.statements();
        List<?> elseStatements = elseBlock.statements();
        if (thenStatements.isEmpty() || elseStatements.isEmpty()) {
            return false;
        }

        Statement thenLast = (Statement) thenStatements.get(thenStatements.size() - 1);
        Statement elseLast = (Statement) elseStatements.get(elseStatements.size() - 1);
        if (!thenLast.subtreeMatch(new ASTMatcher(), elseLast)) {
            return false;
        }

        ListRewrite elseRewrite = rewrite.getListRewrite(elseBlock, Block.STATEMENTS_PROPERTY);
        elseRewrite.remove(elseLast, null);
        ListRewrite parentRewrite = rewrite.getListRewrite(parentBlock, Block.STATEMENTS_PROPERTY);
        parentRewrite.insertAfter(rewrite.createMoveTarget(thenLast), ifStatement, null);
        return true;
    }

    /**
     * Checks whether a node overlaps any source comment.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param node the node whose source range is checked
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

    /**
     * Checks whether a branch contains a local variable declaration.
     *
     * @param block the branch block to inspect
     * @return {@code true} when a variable declaration occurs in the block
     */
    private boolean containsVariableDeclaration(Block block) {
        boolean[] found = {false};
        block.accept(new ASTVisitor() {
            /**
             * Records a variable declaration in the branch.
             *
             * @param node the visited variable declaration fragment
             * @return {@code false} because no nested traversal is needed
             */
            @Override
            public boolean visit(VariableDeclarationFragment node) {
                found[0] = true;
                return false;
            }
        });
        return found[0];
    }
}
