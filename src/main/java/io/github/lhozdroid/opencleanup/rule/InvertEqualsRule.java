package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Places the non-null operand first in null equality and inequality comparisons.
 *
 * <p>The rule only reverses comparisons whose left operand is the null literal.
 * This avoids changing evaluation order for expressions that may have side
 * effects while still making the supported null comparison form consistent.</p>
 */
public final class InvertEqualsRule implements CleanupRule {

    public static final String ID = "comparisons.invert-equals";

    /**
     * Returns the stable identifier for null comparison inversion cleanup.
     *
     * @return the null comparison inversion rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records swaps for eligible null equality and inequality comparisons.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one comparison is scheduled for replacement
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Swaps operands for one eligible null comparison and skips its children.
             *
             * @param node the visited infix-expression node
             * @return {@code false} after replacement, otherwise {@code true}
             */
            @Override
            public boolean visit(InfixExpression node) {
                InfixExpression replacement = replacementFor(node);
                if (replacement == null) {
                    return true;
                }
                rewrite.replace(node, replacement, null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Creates a comparison with its non-null operand on the left when eligible.
     *
     * @param expression the comparison to inspect
     * @return the swapped comparison, or {@code null} when it is not eligible
     */
    private InfixExpression replacementFor(InfixExpression expression) {
        if (!isEqualityOrInequality(expression)
                || !expression.extendedOperands().isEmpty()
                || !(expression.getLeftOperand() instanceof NullLiteral)
                || expression.getRightOperand() instanceof NullLiteral) {
            return null;
        }

        AST ast = expression.getAST();
        InfixExpression replacement = ast.newInfixExpression();
        replacement.setOperator(expression.getOperator());
        replacement.setLeftOperand(copyExpression(ast, expression.getRightOperand()));
        replacement.setRightOperand(copyExpression(ast, expression.getLeftOperand()));
        return replacement;
    }

    /**
     * Checks whether an infix expression uses equality or inequality.
     *
     * @param expression the infix expression to inspect
     * @return {@code true} for {@code ==} and {@code !=}
     */
    private boolean isEqualityOrInequality(InfixExpression expression) {
        InfixExpression.Operator operator = expression.getOperator();
        return operator == InfixExpression.Operator.EQUALS
                || operator == InfixExpression.Operator.NOT_EQUALS;
    }

    /**
     * Copies an expression into the AST that owns the replacement node.
     *
     * @param ast the destination AST
     * @param expression the expression to copy
     * @return the copied expression
     */
    private Expression copyExpression(AST ast, Expression expression) {
        return (Expression) ASTNode.copySubtree(ast, expression);
    }
}
