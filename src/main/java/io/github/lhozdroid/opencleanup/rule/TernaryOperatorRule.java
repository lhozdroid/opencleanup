package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Replaces a passive boolean selection implemented with two conjunctions by a
 * conditional expression.
 *
 * <p>The supported shape is {@code (X && Y) || (!X && Z)}. The two branches
 * may occur in either order, but all operands must be passive syntax-only
 * expressions.</p>
 */
public final class TernaryOperatorRule implements CleanupRule {

    /** The stable identifier for ternary-operator cleanup. */
    public static final String ID = "expressions.ternary-operator";

    /**
     * Returns the stable identifier for ternary-operator cleanup.
     *
     * @return the ternary-operator rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for passive boolean conjunction selections.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one expression is scheduled for replacement
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Converts one eligible disjunction into a conditional expression.
             *
             * @param node the visited infix expression
             * @return {@code false} after replacement, otherwise {@code true}
             */
            @Override
            public boolean visit(InfixExpression node) {
                if (node.getOperator() != InfixExpression.Operator.CONDITIONAL_OR
                        || !node.extendedOperands().isEmpty()) {
                    return true;
                }

                InfixExpression first = conjunction(node.getLeftOperand());
                InfixExpression second = conjunction(node.getRightOperand());
                if (first == null || second == null) {
                    return true;
                }

                ConditionalParts parts = parts(first, second);
                if (parts == null) {
                    parts = parts(second, first);
                }
                if (parts == null) {
                    return true;
                }

                rewrite.replace(node, replacement(node.getAST(), parts), null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Finds conditional-expression parts when the first branch contains the
     * positive condition and the second branch contains its negation.
     *
     * @param positiveBranch the conjunction containing the positive condition
     * @param negativeBranch the conjunction containing the negated condition
     * @return the conditional parts, or {@code null} when the shape is unsupported
     */
    private ConditionalParts parts(
            InfixExpression positiveBranch,
            InfixExpression negativeBranch) {
        Expression[] positiveOperands = operands(positiveBranch);
        Expression[] negativeOperands = operands(negativeBranch);
        for (int positiveIndex = 0; positiveIndex < positiveOperands.length; positiveIndex++) {
            Expression condition = stripParentheses(positiveOperands[positiveIndex]);
            if (!isPassive(condition) || condition instanceof PrefixExpression) {
                continue;
            }
            for (int negativeIndex = 0; negativeIndex < negativeOperands.length; negativeIndex++) {
                Expression negated = negatedOperand(negativeOperands[negativeIndex]);
                if (negated == null || !sameTree(condition, negated)) {
                    continue;
                }

                Expression thenExpression = positiveOperands[1 - positiveIndex];
                Expression elseExpression = negativeOperands[1 - negativeIndex];
                if (isPassive(thenExpression) && isPassive(elseExpression)) {
                    return new ConditionalParts(condition, thenExpression, elseExpression);
                }
            }
        }
        return null;
    }

    /**
     * Extracts a binary short-circuit conjunction after removing parentheses.
     *
     * @param expression the candidate expression
     * @return the conjunction, or {@code null} when the shape is unsupported
     */
    private InfixExpression conjunction(Expression expression) {
        Expression unparenthesized = stripParentheses(expression);
        if (!(unparenthesized instanceof InfixExpression infix)
                || infix.getOperator() != InfixExpression.Operator.CONDITIONAL_AND
                || !infix.extendedOperands().isEmpty()) {
            return null;
        }
        return infix;
    }

    /**
     * Returns the two operands of a binary conjunction.
     *
     * @param expression the conjunction
     * @return its left and right operands
     */
    private Expression[] operands(InfixExpression expression) {
        return new Expression[] {expression.getLeftOperand(), expression.getRightOperand()};
    }

    /**
     * Extracts the operand below one logical-not expression.
     *
     * @param expression the candidate negated expression
     * @return the negated operand, or {@code null} when the expression is unsupported
     */
    private Expression negatedOperand(Expression expression) {
        Expression unparenthesized = stripParentheses(expression);
        if (!(unparenthesized instanceof PrefixExpression prefix)
                || prefix.getOperator() != PrefixExpression.Operator.NOT) {
            return null;
        }
        Expression operand = stripParentheses(prefix.getOperand());
        return isPassive(operand) ? operand : null;
    }

    /**
     * Creates the conditional-expression equivalent of matched branches.
     *
     * @param ast the AST receiving the replacement
     * @param parts the condition and branch expressions
     * @return the replacement conditional expression
     */
    private Expression replacement(AST ast, ConditionalParts parts) {
        ConditionalExpression conditional = ast.newConditionalExpression();
        conditional.setExpression(copy(ast, parts.condition()));
        conditional.setThenExpression(copy(ast, parts.thenExpression()));
        conditional.setElseExpression(copy(ast, parts.elseExpression()));
        return conditional;
    }

    /**
     * Copies an expression into the AST receiving a rewrite.
     *
     * @param ast the destination AST
     * @param expression the expression to copy
     * @return the copied expression
     */
    private Expression copy(AST ast, Expression expression) {
        return (Expression) ASTNode.copySubtree(ast, stripParentheses(expression));
    }

    /**
     * Removes all parenthesized-expression wrappers from an expression.
     *
     * @param expression the expression to unwrap
     * @return the innermost expression
     */
    private Expression stripParentheses(Expression expression) {
        Expression current = expression;
        while (current instanceof ParenthesizedExpression parenthesized) {
            current = parenthesized.getExpression();
        }
        return current;
    }

    /**
     * Checks whether two expressions have the same syntax tree.
     *
     * @param first the first expression
     * @param second the second expression
     * @return {@code true} when the expressions are structurally equivalent
     */
    private boolean sameTree(Expression first, Expression second) {
        return stripParentheses(first).subtreeMatch(new ASTMatcher(), stripParentheses(second));
    }

    /**
     * Checks whether an expression is passive using syntax-only restrictions.
     *
     * @param expression the expression to inspect
     * @return {@code true} for a supported passive expression
     */
    private boolean isPassive(Expression expression) {
        Expression unparenthesized = stripParentheses(expression);
        if (unparenthesized instanceof SimpleName
                || unparenthesized instanceof BooleanLiteral) {
            return true;
        }
        if (unparenthesized instanceof PrefixExpression prefix
                && prefix.getOperator() == PrefixExpression.Operator.NOT) {
            return isPassive(prefix.getOperand());
        }
        return false;
    }

    /**
     * Holds the condition and result expressions for a ternary replacement.
     *
     * @param condition the positive condition
     * @param thenExpression the expression selected when the condition is true
     * @param elseExpression the expression selected when the condition is false
     */
    private record ConditionalParts(
            Expression condition,
            Expression thenExpression,
            Expression elseExpression) {
    }
}
