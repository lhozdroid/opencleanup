package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Simplifies boolean expressions whose value is determined entirely by boolean literals.
 *
 * <p>The rule intentionally does not simplify expressions containing variables, method calls,
 * fields, or other non-literal expressions. Without resolved bindings, those expressions could
 * involve a boxed {@link Boolean} value and therefore require unboxing or have different static
 * types after a rewrite.</p>
 */
public final class BooleanLiteralRule implements CleanupRule {

    /** The stable identifier for this rule. */
    public static final String ID = "booleans.literal";

    /**
     * Returns the stable identifier for boolean literal cleanup.
     *
     * @return the boolean literal rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for fully literal boolean expressions.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one literal expression is scheduled for replacement
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Replaces a fully literal boolean expression with its calculated value.
             *
             * @param node the visited parenthesized expression
             * @return {@code false} after replacement, otherwise {@code true}
             */
            @Override
            public boolean visit(ParenthesizedExpression node) {
                return replaceWithLiteral(node, rewrite, changed);
            }

            /**
             * Replaces a fully literal boolean expression with its calculated value.
             *
             * @param node the visited prefix expression
             * @return {@code false} after replacement, otherwise {@code true}
             */
            @Override
            public boolean visit(PrefixExpression node) {
                return replaceWithLiteral(node, rewrite, changed);
            }

            /**
             * Replaces a fully literal boolean expression with its calculated value.
             *
             * @param node the visited infix expression
             * @return {@code false} after replacement, otherwise {@code true}
             */
            @Override
            public boolean visit(InfixExpression node) {
                return replaceWithLiteral(node, rewrite, changed);
            }
        });
        return changed[0];
    }

    /**
     * Replaces an expression when its value can be evaluated from boolean literals alone.
     *
     * @param expression the expression being visited
     * @param rewrite the rewrite collecting source edits
     * @param changed mutable flag tracking whether a replacement was recorded
     * @return {@code false} when the expression was replaced, otherwise {@code true}
     */
    private boolean replaceWithLiteral(
            Expression expression,
            ASTRewrite rewrite,
            boolean[] changed) {
        Boolean value = constantValue(expression);
        if (value == null) {
            return true;
        }

        BooleanLiteral replacement = expression.getAST().newBooleanLiteral(value);
        rewrite.replace(expression, replacement, null);
        changed[0] = true;
        return false;
    }

    /**
     * Evaluates an expression only when every participating expression is a boolean literal.
     *
     * @param expression the expression to evaluate
     * @return the constant boolean value, or {@code null} when the expression is not fully literal
     */
    private Boolean constantValue(Expression expression) {
        if (expression instanceof BooleanLiteral literal) {
            return literal.booleanValue();
        }
        if (expression instanceof ParenthesizedExpression parenthesized) {
            return constantValue(parenthesized.getExpression());
        }
        if (expression instanceof PrefixExpression prefix
                && PrefixExpression.Operator.NOT.equals(prefix.getOperator())) {
            Boolean operand = constantValue(prefix.getOperand());
            return operand == null ? null : !operand;
        }
        if (!(expression instanceof InfixExpression infix)) {
            return null;
        }

        boolean isAnd = InfixExpression.Operator.CONDITIONAL_AND.equals(infix.getOperator());
        boolean isOr = InfixExpression.Operator.CONDITIONAL_OR.equals(infix.getOperator());
        if (!isAnd && !isOr) {
            return null;
        }

        Boolean left = constantValue(infix.getLeftOperand());
        Boolean right = constantValue(infix.getRightOperand());
        if (left == null || right == null) {
            return null;
        }
        boolean value = isAnd ? left && right : left || right;
        for (Object extendedOperand : infix.extendedOperands()) {
            Boolean extendedValue = constantValue((Expression) extendedOperand);
            if (extendedValue == null) {
                return null;
            }
            value = isAnd ? value && extendedValue : value || extendedValue;
        }
        return value;
    }
}
