package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Replaces sign-sensitive bitwise comparisons with explicit non-zero checks.
 */
public final class BitwiseCheckSignRule implements CleanupRule {

    /** The stable identifier used to select this cleanup rule. */
    public static final String ID = "bitwise.check-sign";

    /**
     * Returns the stable identifier for bitwise sign-check cleanup.
     *
     * @return the bitwise sign-check rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for bitwise expressions compared greater than zero.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one comparison is changed
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Changes one bitwise sign comparison to an explicit non-zero comparison.
             *
             * @param node the visited infix expression
             * @return {@code true} to continue visiting nested expressions
             */
            @Override
            public boolean visit(InfixExpression node) {
                if (isSignCheck(node)) {
                    rewrite.set(node, InfixExpression.OPERATOR_PROPERTY,
                            InfixExpression.Operator.NOT_EQUALS, null);
                    changed[0] = true;
                }
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Checks for the Eclipse cleanup shape {@code bitwiseExpression > 0}.
     *
     * @param expression the comparison to inspect
     * @return {@code true} when the comparison is a supported sign check
     */
    private boolean isSignCheck(InfixExpression expression) {
        return expression.getOperator() == InfixExpression.Operator.GREATER
                && expression.extendedOperands().isEmpty()
                && isBitwise(expression.getLeftOperand())
                && isZero(expression.getRightOperand());
    }

    /**
     * Checks whether an expression is a non-extended bitwise operation.
     *
     * @param expression the expression to inspect
     * @return {@code true} for {@code &}, {@code |}, or {@code ^}
     */
    private boolean isBitwise(Expression expression) {
        expression = unwrap(expression);
        if (!(expression instanceof InfixExpression infix)
                || !infix.extendedOperands().isEmpty()) {
            return false;
        }
        return infix.getOperator() == InfixExpression.Operator.AND
                || infix.getOperator() == InfixExpression.Operator.OR
                || infix.getOperator() == InfixExpression.Operator.XOR;
    }

    /**
     * Checks whether an expression is the decimal integer literal zero.
     *
     * @param expression the expression to inspect
     * @return {@code true} for the exact literal {@code 0}
     */
    private boolean isZero(Expression expression) {
        expression = unwrap(expression);
        return expression instanceof NumberLiteral literal && "0".equals(literal.getToken());
    }

    /**
     * Removes source parentheses that do not change the inspected expression shape.
     *
     * @param expression the expression to unwrap
     * @return the innermost non-parenthesized expression
     */
    private Expression unwrap(Expression expression) {
        while (expression instanceof ParenthesizedExpression parenthesized) {
            expression = parenthesized.getExpression();
        }
        return expression;
    }
}
