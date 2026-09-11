package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Evaluates null comparisons whose operand is provably non-null from Java syntax.
 */
public final class EvaluateNullableRule implements CleanupRule {

    /** The stable identifier for nullable null-check cleanup. */
    public static final String ID = "null-checks.evaluate-nullable";

    /**
     * Returns the stable identifier for this rule.
     *
     * @return the rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records boolean replacements for provably constant null comparisons.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source changes
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one comparison is evaluated
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Evaluates one null comparison with a known non-null operand.
             *
             * @param node the visited infix expression
             * @return {@code false} after replacement, otherwise {@code true}
             */
            @Override
            public boolean visit(InfixExpression node) {
                if (!node.extendedOperands().isEmpty()
                        || (node.getOperator() != InfixExpression.Operator.EQUALS
                        && node.getOperator() != InfixExpression.Operator.NOT_EQUALS)) {
                    return true;
                }
                if (!nullOperand(node.getLeftOperand()) && !nullOperand(node.getRightOperand())) {
                    return true;
                }
                Expression candidate = node.getLeftOperand() instanceof NullLiteral
                        ? node.getRightOperand() : node.getLeftOperand();
                if (!isProvablyNonNull(candidate)) {
                    return true;
                }
                boolean result = node.getOperator() == InfixExpression.Operator.NOT_EQUALS;
                rewrite.replace(node, node.getAST().newBooleanLiteral(result), null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Checks whether an expression is a null literal.
     *
     * @param expression the expression to inspect
     * @return {@code true} when the expression is null
     */
    private boolean nullOperand(Expression expression) {
        return expression instanceof NullLiteral;
    }

    /**
     * Checks whether an expression is guaranteed non-null without bindings.
     *
     * @param expression the expression to inspect
     * @return {@code true} for Java constructs that always produce a value
     */
    private boolean isProvablyNonNull(Expression expression) {
        return expression instanceof ClassInstanceCreation
                || expression instanceof ArrayCreation
                || expression instanceof LambdaExpression
                || expression instanceof ThisExpression
                || expression instanceof StringLiteral
                || expression instanceof NumberLiteral
                || expression instanceof BooleanLiteral
                || expression instanceof CharacterLiteral;
    }
}
