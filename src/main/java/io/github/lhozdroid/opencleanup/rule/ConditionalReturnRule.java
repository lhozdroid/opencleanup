package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Simplifies direct return expressions containing boolean conditional literals.
 *
 * <p>The supported forms are {@code return condition ? true : false;} and
 * {@code return condition ? false : true;}. The rule intentionally inspects only
 * syntax and only direct return expressions, so it does not require resolved bindings.</p>
 */
public final class ConditionalReturnRule implements CleanupRule {

    /** The stable identifier for this rule. */
    public static final String ID = "returns.expression";

    /**
     * Returns the stable identifier for conditional-return cleanup.
     *
     * @return the conditional-return rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for direct returns of boolean conditional literals.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one return is scheduled for replacement
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Simplifies one direct return of a boolean conditional expression.
             *
             * @param node the visited return statement
             * @return {@code false} after replacement, otherwise {@code true}
             */
            @Override
            public boolean visit(ReturnStatement node) {
                if (!(node.getExpression() instanceof ConditionalExpression conditional)) {
                    return true;
                }

                Boolean thenValue = literalValue(conditional.getThenExpression());
                Boolean elseValue = literalValue(conditional.getElseExpression());
                if (thenValue == null || elseValue == null || thenValue == elseValue) {
                    return true;
                }

                rewrite.replace(node.getExpression(), replacementFor(conditional, thenValue), null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Returns the value of a direct boolean literal expression.
     *
     * @param expression the expression to inspect
     * @return the literal value, or {@code null} when the expression is not a
     *         direct boolean literal
     */
    private Boolean literalValue(Expression expression) {
        return expression instanceof BooleanLiteral literal ? literal.booleanValue() : null;
    }

    /**
     * Creates the condition equivalent of a boolean conditional expression.
     *
     * @param conditional the conditional expression being replaced
     * @param thenValue the literal value in the then branch
     * @return a copy of the condition, negated when the then branch is false
     */
    private Expression replacementFor(ConditionalExpression conditional, boolean thenValue) {
        Expression condition = (Expression) org.eclipse.jdt.core.dom.ASTNode.copySubtree(
                conditional.getAST(), conditional.getExpression());
        if (thenValue) {
            return condition;
        }

        PrefixExpression negated = conditional.getAST().newPrefixExpression();
        negated.setOperator(PrefixExpression.Operator.NOT);
        negated.setOperand(condition);
        return negated;
    }
}
