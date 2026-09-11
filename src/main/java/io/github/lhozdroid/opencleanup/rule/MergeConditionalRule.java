package io.github.lhozdroid.opencleanup.rule;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Merges an {@code if}/ {@code else if} chain whose consecutive branches have
 * identical bodies.
 *
 * <p>For example, {@code if (a) { work(); } else if (b) { work(); }} becomes
 * {@code if (a || b) { work(); }}. Comments in the candidate chain are left
 * untouched by skipping the rewrite.</p>
 */
public final class MergeConditionalRule implements CleanupRule {

    /** The stable identifier for conditional-block merging cleanup. */
    public static final String ID = "blocks.merge-conditional";

    /**
     * Returns the stable identifier for conditional-block merging cleanup.
     *
     * @return the conditional-block merging rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records edits that merge consecutive equal conditional branches.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one conditional chain is merged
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Merges one eligible conditional chain.
             *
             * @param node the visited outer if statement
             * @return {@code false} after replacement, otherwise {@code true}
             */
            @Override
            public boolean visit(IfStatement node) {
                MergeCandidate candidate = candidate(compilationUnit, node);
                if (candidate == null) {
                    return true;
                }

                AST ast = node.getAST();
                rewrite.replace(node.getExpression(), mergedCondition(ast, candidate.conditions()),
                        null);
                if (candidate.finalElse() == null) {
                    rewrite.remove(node.getElseStatement(), null);
                } else {
                    rewrite.replace(node.getElseStatement(),
                            ASTNode.copySubtree(ast, candidate.finalElse()), null);
                }
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Finds a consecutive else-if chain with equal block bodies.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param outerIf the candidate outer if statement
     * @return the merge candidate, or {@code null} when no eligible pair exists
     */
    private MergeCandidate candidate(CompilationUnit compilationUnit, IfStatement outerIf) {
        if (!(outerIf.getThenStatement() instanceof Block expectedBody)
                || outerIf.getElseStatement() == null
                || containsComment(compilationUnit, outerIf)) {
            return null;
        }

        List<Expression> conditions = new ArrayList<>();
        conditions.add(outerIf.getExpression());
        IfStatement current = outerIf;
        IfStatement next = asIf(current.getElseStatement());
        Statement finalElse = null;
        while (next != null && sameBody(expectedBody, next.getThenStatement())) {
            if (containsComment(compilationUnit, next)) {
                return null;
            }
            conditions.add(next.getExpression());
            current = next;
            if (current.getElseStatement() instanceof IfStatement following) {
                next = following;
            } else {
                finalElse = current.getElseStatement();
                next = null;
            }
        }

        if (next != null) {
            finalElse = current.getElseStatement();
        }

        return conditions.size() > 1 ? new MergeCandidate(conditions, finalElse) : null;
    }

    /**
     * Converts a statement to an else-if node when possible.
     *
     * @param statement the candidate else statement
     * @return the else-if node, or {@code null} for a regular else branch
     */
    private IfStatement asIf(Statement statement) {
        return statement instanceof IfStatement ifStatement ? ifStatement : null;
    }

    /**
     * Checks whether two branch statements have identical syntax trees.
     *
     * @param expectedBody the body retained by the outer if
     * @param candidateBody the body considered for merging
     * @return {@code true} when both branches are equivalent blocks
     */
    private boolean sameBody(Block expectedBody, Statement candidateBody) {
        return candidateBody instanceof Block block
                && expectedBody.subtreeMatch(new ASTMatcher(), block);
    }

    /**
     * Creates a left-associated short-circuit disjunction of conditions.
     *
     * @param ast the AST receiving the replacement
     * @param conditions the conditions to merge
     * @return the merged condition
     */
    private Expression mergedCondition(AST ast, List<Expression> conditions) {
        Expression merged = (Expression) ASTNode.copySubtree(ast, conditions.get(0));
        for (int index = 1; index < conditions.size(); index++) {
            InfixExpression disjunction = ast.newInfixExpression();
            disjunction.setOperator(InfixExpression.Operator.CONDITIONAL_OR);
            disjunction.setLeftOperand(merged);
            disjunction.setRightOperand((Expression) ASTNode.copySubtree(
                    ast, conditions.get(index)));
            merged = disjunction;
        }
        return merged;
    }

    /**
     * Checks whether a candidate source range contains a parsed comment.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param node the source range to inspect
     * @return {@code true} when a comment overlaps the node's source range
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
     * Holds the conditions to merge and the final else branch to retain.
     *
     * @param conditions the conditions from equal branches
     * @param finalElse the else branch after the equal chain, if any
     */
    private record MergeCandidate(List<Expression> conditions, Statement finalElse) {
    }
}
