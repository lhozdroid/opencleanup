package io.github.lhozdroid.opencleanup.rule;

import java.util.List;

import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Removes a terminating jump duplicated immediately after an if statement.
 *
 * <p>For example, {@code if (ready) { return value; } return value;} becomes
 * an if whose branch falls through to the shared return. Only braced branches,
 * direct sibling statements, and syntactically identical jump statements are
 * considered.</p>
 */
public final class RedundantFallThroughEndRule implements CleanupRule {

    /** The stable identifier for this rule. */
    public static final String ID = "blocks.redundant-fall-through-end";

    /**
     * Returns the stable identifier for redundant fall-through cleanup.
     *
     * @return the redundant fall-through-end rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records edits that remove redundant branch-ending jump statements.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one redundant jump is removed
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Removes duplicated jumps from one eligible if statement.
             *
             * @param node the visited if statement
             * @return {@code false} after a rewrite, otherwise {@code true}
             */
            @Override
            public boolean visit(IfStatement node) {
                if (removeRedundantJumps(compilationUnit, rewrite, node)) {
                    changed[0] = true;
                    return false;
                }
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Removes matching terminating jumps from one or both braced branches.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param ifStatement the candidate if statement
     * @return {@code true} when at least one jump is scheduled for removal
     */
    private boolean removeRedundantJumps(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            IfStatement ifStatement) {
        if (!(ifStatement.getParent() instanceof Block parentBlock)
                || !(ifStatement.getThenStatement() instanceof Block thenBlock)
                || containsComment(compilationUnit, ifStatement)
                || !(followingStatement(parentBlock, ifStatement) instanceof Statement following)) {
            return false;
        }

        Statement thenLast = lastStatement(thenBlock);
        Statement elseLast = ifStatement.getElseStatement() instanceof Block elseBlock
                ? lastStatement(elseBlock) : null;
        if (thenLast == null && elseLast == null) {
            return false;
        }

        boolean removeThen = matchesJump(thenLast, following);
        boolean removeElse = matchesJump(elseLast, following);
        if (!removeThen && !removeElse) {
            return false;
        }

        if (removeThen) {
            rewrite.getListRewrite(thenBlock, Block.STATEMENTS_PROPERTY).remove(thenLast, null);
        }
        if (removeElse) {
            Block elseBlock = (Block) ifStatement.getElseStatement();
            rewrite.getListRewrite(elseBlock, Block.STATEMENTS_PROPERTY).remove(elseLast, null);
        }
        return true;
    }

    /**
     * Returns the statement immediately following an if statement.
     *
     * @param parentBlock the block containing the if statement
     * @param ifStatement the if statement whose sibling is requested
     * @return the following statement, or {@code null} when there is none
     */
    private Statement followingStatement(Block parentBlock, IfStatement ifStatement) {
        List<?> statements = parentBlock.statements();
        int index = statements.indexOf(ifStatement);
        if (index < 0 || index + 1 >= statements.size()) {
            return null;
        }
        return (Statement) statements.get(index + 1);
    }

    /**
     * Returns the final statement in a block.
     *
     * @param block the block to inspect
     * @return the final statement, or {@code null} when the block is empty
     */
    private Statement lastStatement(Block block) {
        List<?> statements = block.statements();
        return statements.isEmpty() ? null : (Statement) statements.get(statements.size() - 1);
    }

    /**
     * Checks whether two statements are identical terminating jumps.
     *
     * @param candidate the branch-ending statement
     * @param following the statement after the if
     * @return {@code true} when both statements are supported jumps with equal syntax
     */
    private boolean matchesJump(Statement candidate, Statement following) {
        return candidate != null
                && isJump(candidate)
                && candidate.subtreeMatch(new ASTMatcher(), following);
    }

    /**
     * Checks whether a statement is one of the supported jump statements.
     *
     * @param statement the statement to inspect
     * @return {@code true} for return, throw, break, or continue statements
     */
    private boolean isJump(Statement statement) {
        return statement instanceof ReturnStatement
                || statement instanceof ThrowStatement
                || statement instanceof BreakStatement
                || statement instanceof ContinueStatement;
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
