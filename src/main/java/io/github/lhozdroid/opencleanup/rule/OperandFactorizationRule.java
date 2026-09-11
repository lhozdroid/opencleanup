package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Factors a common passive operand from two short-circuit conjunctions.
 *
 * <p>The supported shape is {@code (X && Y) || (X && Z)}, including the
 * equivalent operand ordering. Only passive syntax-only expressions are
 * considered so the transformation does not need resolved bindings.</p>
 */
public final class OperandFactorizationRule implements CleanupRule {

    /** The stable identifier for operand factorization cleanup. */
    public static final String ID = "expressions.operand-factorization";

    /**
     * Returns the stable identifier for operand factorization cleanup.
     *
     * @return the operand factorization rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements that factor a common passive operand.
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
             * Factors one eligible disjunction of conjunctions.
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

                InfixExpression left = conjunction(node.getLeftOperand());
                InfixExpression right = conjunction(node.getRightOperand());
                if (left == null || right == null) {
                    return true;
                }

                Expression common = null;
                Expression leftFactor = null;
                Expression rightFactor = null;
                Expression[] leftOperands = operands(left);
                Expression[] rightOperands = operands(right);
                for (int leftIndex = 0; leftIndex < leftOperands.length && common == null;
                        leftIndex++) {
                    for (int rightIndex = 0; rightIndex < rightOperands.length; rightIndex++) {
                        if (sameTree(leftOperands[leftIndex], rightOperands[rightIndex])) {
                            common = leftOperands[leftIndex];
                            leftFactor = leftOperands[1 - leftIndex];
                            rightFactor = rightOperands[1 - rightIndex];
                            break;
                        }
                    }
                }

                if (common == null || !isPassive(common) || !isPassive(leftFactor)
                        || !isPassive(rightFactor)) {
                    return true;
                }

                rewrite.replace(node, replacement(node.getAST(), common, leftFactor, rightFactor),
                        null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
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
     * Returns the two operands of a binary infix expression.
     *
     * @param expression the binary expression
     * @return its left and right operands
     */
    private Expression[] operands(InfixExpression expression) {
        return new Expression[] {expression.getLeftOperand(), expression.getRightOperand()};
    }

    /**
     * Creates the factored equivalent of two conjunctions.
     *
     * @param ast the AST receiving the replacement
     * @param common the operand shared by both conjunctions
     * @param leftFactor the non-shared left conjunction operand
     * @param rightFactor the non-shared right conjunction operand
     * @return the factored expression
     */
    private Expression replacement(
            AST ast,
            Expression common,
            Expression leftFactor,
            Expression rightFactor) {
        InfixExpression disjunction = ast.newInfixExpression();
        disjunction.setOperator(InfixExpression.Operator.CONDITIONAL_OR);
        disjunction.setLeftOperand(copy(ast, leftFactor));
        disjunction.setRightOperand(copy(ast, rightFactor));

        InfixExpression conjunction = ast.newInfixExpression();
        conjunction.setOperator(InfixExpression.Operator.CONDITIONAL_AND);
        conjunction.setLeftOperand(copy(ast, common));
        ParenthesizedExpression parenthesized = ast.newParenthesizedExpression();
        parenthesized.setExpression(disjunction);
        conjunction.setRightOperand(parenthesized);
        return conjunction;
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
}
