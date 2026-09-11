package io.github.lhozdroid.opencleanup.rule;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.PatternInstanceofExpression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Merges consecutive if statements with identical bodies that terminate with jumps.
 *
 * <p>The conditions are joined with short-circuit {@code ||}. Only sibling if
 * statements without else branches are considered, and pattern conditions and
 * comments are skipped because syntax-only analysis cannot prove their safety.</p>
 */
public final class OneIfForFallThroughRule implements CleanupRule {

    /** The stable identifier for this rule. */
    public static final String ID = "blocks.one-if-for-fall-through";

    /**
     * Returns the stable identifier for one-if fall-through cleanup.
     *
     * @return the one-if fall-through rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records edits that merge eligible consecutive if statements.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least two if statements are merged
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Merges successive if statements in one block.
             *
             * @param node the visited block
             * @return {@code true} to inspect nested blocks
             */
            @Override
            public boolean visit(Block node) {
                if (mergeSuccessiveIfs(compilationUnit, rewrite, node)) {
                    changed[0] = true;
                }
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Finds maximal consecutive groups of matching terminating if statements.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param block the block whose direct statements are inspected
     * @return {@code true} when at least one group is scheduled for merging
     */
    private boolean mergeSuccessiveIfs(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            Block block) {
        List<?> rawStatements = block.statements();
        boolean changed = false;
        int index = 0;
        while (index < rawStatements.size()) {
            if (!(rawStatements.get(index) instanceof IfStatement first)
                    || !eligible(first)
                    || containsComment(compilationUnit, first)) {
                index++;
                continue;
            }

            List<IfStatement> group = new ArrayList<>();
            group.add(first);
            int nextIndex = index + 1;
            while (nextIndex < rawStatements.size()
                    && rawStatements.get(nextIndex) instanceof IfStatement next
                    && eligible(next)
                    && !containsComment(compilationUnit, next)
                    && first.getThenStatement().subtreeMatch(
                            new ASTMatcher(), next.getThenStatement())) {
                group.add(next);
                nextIndex++;
            }

            if (group.size() > 1
                    && !containsComment(compilationUnit, group.get(0), group.get(group.size() - 1))) {
                mergeGroup(rewrite, block, group);
                changed = true;
                index = nextIndex;
            } else {
                index++;
            }
        }
        return changed;
    }

    /**
     * Checks whether an if statement has a supported terminating body.
     *
     * @param ifStatement the if statement to inspect
     * @return {@code true} when the statement has no else and ends with a jump
     */
    private boolean eligible(IfStatement ifStatement) {
        return ifStatement.getElseStatement() == null
                && endsWithJump(ifStatement.getThenStatement())
                && !containsPattern(ifStatement.getExpression());
    }

    /**
     * Replaces the final condition with an OR of all group conditions.
     *
     * @param rewrite the rewrite collecting source edits
     * @param block the containing block
     * @param group the consecutive if statements to merge
     */
    private void mergeGroup(ASTRewrite rewrite, Block block, List<IfStatement> group) {
        AST ast = block.getAST();
        InfixExpression mergedCondition = ast.newInfixExpression();
        mergedCondition.setOperator(InfixExpression.Operator.CONDITIONAL_OR);
        mergedCondition.setLeftOperand((org.eclipse.jdt.core.dom.Expression) ASTNode.copySubtree(
                ast, group.get(0).getExpression()));
        mergedCondition.setRightOperand((org.eclipse.jdt.core.dom.Expression) ASTNode.copySubtree(
                ast, group.get(1).getExpression()));
        for (int index = 2; index < group.size(); index++) {
            mergedCondition.extendedOperands().add(ASTNode.copySubtree(
                    ast, group.get(index).getExpression()));
        }

        ListRewrite statementRewrite = rewrite.getListRewrite(block, Block.STATEMENTS_PROPERTY);
        for (int index = 0; index < group.size() - 1; index++) {
            statementRewrite.remove(group.get(index), null);
        }
        rewrite.replace(group.get(group.size() - 1).getExpression(), mergedCondition, null);
    }

    /**
     * Checks whether a statement ends with a direct jump statement.
     *
     * @param statement the statement to inspect
     * @return {@code true} when the final statement is a return, throw, break, or continue
     */
    private boolean endsWithJump(Statement statement) {
        Statement last = statement;
        if (statement instanceof Block block) {
            List<?> statements = block.statements();
            if (statements.isEmpty()) {
                return false;
            }
            last = (Statement) statements.get(statements.size() - 1);
        }
        return last instanceof ReturnStatement
                || last instanceof ThrowStatement
                || last instanceof BreakStatement
                || last instanceof ContinueStatement;
    }

    /**
     * Checks whether an expression contains an instanceof pattern.
     *
     * @param expression the expression to inspect
     * @return {@code true} when an instanceof pattern occurs
     */
    private boolean containsPattern(org.eclipse.jdt.core.dom.Expression expression) {
        boolean[] found = {false};
        expression.accept(new ASTVisitor() {
            /**
             * Records an instanceof pattern in a condition.
             *
             * @param node the visited pattern expression
             * @return {@code false} because the candidate is already rejected
             */
            @Override
            public boolean visit(PatternInstanceofExpression node) {
                found[0] = true;
                return false;
            }
        });
        return found[0];
    }

    /**
     * Checks whether a source range contains a parsed comment.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param first the first node in the range
     * @param last the final node in the range
     * @return {@code true} when a comment overlaps the range
     */
    private boolean containsComment(CompilationUnit compilationUnit, ASTNode first, ASTNode last) {
        int start = first.getStartPosition();
        int end = last.getStartPosition() + last.getLength();
        for (Object value : compilationUnit.getCommentList()) {
            Comment comment = (Comment) value;
            int commentStart = comment.getStartPosition();
            int commentEnd = commentStart + comment.getLength();
            if (commentStart < end && start < commentEnd) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether a single node contains a parsed comment.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param node the node whose source range is checked
     * @return {@code true} when a comment overlaps the node
     */
    private boolean containsComment(CompilationUnit compilationUnit, ASTNode node) {
        return containsComment(compilationUnit, node, node);
    }
}
