package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Removes a passive negated condition from an {@code else if} branch.
 *
 * <p>The supported form is {@code if (condition) ... else if (!condition) ...}
 * with no second else branch. Conditions are restricted to names so that the
 * rewrite does not need binding or side-effect analysis.</p>
 */
public final class RedundantIfConditionRule implements CleanupRule {

    /** The stable identifier for this rule. */
    public static final String ID = "conditions.redundant-if";

    /**
     * Returns the stable identifier for redundant-if cleanup.
     *
     * @return the redundant-if rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records edits that remove conditions redundant after a preceding if.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one redundant condition is removed
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Removes one redundant else-if condition.
             *
             * @param node the visited outer if statement
             * @return {@code false} after a replacement, otherwise {@code true}
             */
            @Override
            public boolean visit(IfStatement node) {
                IfStatement secondIf = redundantElseIf(compilationUnit, node);
                if (secondIf == null) {
                    return true;
                }

                rewrite.replace(secondIf, rewrite.createMoveTarget(secondIf.getThenStatement()), null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Finds a negated else-if condition that is redundant.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param outerIf the candidate outer if statement
     * @return the redundant nested if, or {@code null} when unsupported
     */
    private IfStatement redundantElseIf(CompilationUnit compilationUnit, IfStatement outerIf) {
        if (!(outerIf.getElseStatement() instanceof IfStatement secondIf)
                || secondIf.getElseStatement() != null
                || !isPassive(outerIf.getExpression())
                || !isPassive(secondIf.getExpression())
                || !isNegationOf(secondIf.getExpression(), outerIf.getExpression())
                || containsComment(compilationUnit, outerIf)) {
            return null;
        }
        return secondIf;
    }

    /**
     * Checks whether an expression is a name with no executable side effects.
     *
     * @param expression the expression to inspect
     * @return {@code true} when the expression is a simple or qualified name
     */
    private boolean isPassive(Expression expression) {
        if (expression instanceof Name) {
            return true;
        }
        return expression instanceof PrefixExpression prefix
                && prefix.getOperator() == PrefixExpression.Operator.NOT
                && prefix.getOperand() instanceof Name;
    }

    /**
     * Checks whether one expression is the direct logical negation of another.
     *
     * @param candidate the possible negated expression
     * @param original the preceding if condition
     * @return {@code true} when the candidate is {@code !original}
     */
    private boolean isNegationOf(Expression candidate, Expression original) {
        if (!(candidate instanceof PrefixExpression prefix)
                || prefix.getOperator() != PrefixExpression.Operator.NOT) {
            return false;
        }
        return prefix.getOperand().subtreeMatch(new ASTMatcher(), original);
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
