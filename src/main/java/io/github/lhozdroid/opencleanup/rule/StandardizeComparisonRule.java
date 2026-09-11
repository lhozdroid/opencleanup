package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Standardizes selected relational comparisons by placing a literal on the right.
 *
 * <p>The supported shape is a non-extended comparison whose left operand is a numeric or
 * character literal, whose right operand is not a literal, and whose operator is one of
 * {@code <}, {@code <=}, {@code >}, or {@code >=}. The rule swaps the operands and reverses the
 * operator, for example {@code 10 < value} becomes {@code value > 10}. This is a syntax-only
 * transformation with no binding resolution: swapping a literal with the other operand and
 * reversing a relational operator is equivalent for Java's relational comparison operators.
 * Equality comparisons, literal-to-literal comparisons, and all other shapes are preserved.
 */
public final class StandardizeComparisonRule implements CleanupRule {

    /** The stable identifier for this rule. */
    public static final String ID = "comparisons.standard";

    /**
     * Returns the stable identifier for relational comparison standardization.
     *
     * @return the comparison standardization rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for eligible literal-left relational comparisons.
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
             * Replaces one eligible relational comparison and skips its children.
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
     * Creates the standardized comparison when the expression matches the supported shape.
     *
     * @param expression the comparison to inspect
     * @return the swapped comparison, or {@code null} when it is not eligible
     */
    private InfixExpression replacementFor(InfixExpression expression) {
        if (!isRelational(expression)
                || !expression.extendedOperands().isEmpty()
                || !isSupportedLiteral(expression.getLeftOperand())
                || isSupportedLiteral(expression.getRightOperand())) {
            return null;
        }

        AST ast = expression.getAST();
        InfixExpression replacement = ast.newInfixExpression();
        replacement.setOperator(reversedOperator(expression.getOperator()));
        replacement.setLeftOperand(copyExpression(ast, expression.getRightOperand()));
        replacement.setRightOperand(copyExpression(ast, expression.getLeftOperand()));
        return replacement;
    }

    /**
     * Checks whether an infix expression is a supported binary relational comparison.
     *
     * @param expression the expression to inspect
     * @return {@code true} for {@code <}, {@code <=}, {@code >}, and {@code >=}
     */
    private boolean isRelational(InfixExpression expression) {
        InfixExpression.Operator operator = expression.getOperator();
        return operator == InfixExpression.Operator.LESS
                || operator == InfixExpression.Operator.LESS_EQUALS
                || operator == InfixExpression.Operator.GREATER
                || operator == InfixExpression.Operator.GREATER_EQUALS;
    }

    /**
     * Checks whether an expression is a literal supported on the left side of this rule.
     *
     * @param expression the expression to inspect
     * @return {@code true} for numeric and character literals
     */
    private boolean isSupportedLiteral(Expression expression) {
        return expression instanceof NumberLiteral || expression instanceof CharacterLiteral;
    }

    /**
     * Returns the relational operator that preserves the comparison after swapping operands.
     *
     * @param operator the original relational operator
     * @return the reversed relational operator
     */
    private InfixExpression.Operator reversedOperator(InfixExpression.Operator operator) {
        if (operator == InfixExpression.Operator.LESS) {
            return InfixExpression.Operator.GREATER;
        }
        if (operator == InfixExpression.Operator.LESS_EQUALS) {
            return InfixExpression.Operator.GREATER_EQUALS;
        }
        if (operator == InfixExpression.Operator.GREATER) {
            return InfixExpression.Operator.LESS;
        }
        if (operator == InfixExpression.Operator.GREATER_EQUALS) {
            return InfixExpression.Operator.LESS_EQUALS;
        }
        throw new IllegalArgumentException("Unsupported relational operator: " + operator);
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
