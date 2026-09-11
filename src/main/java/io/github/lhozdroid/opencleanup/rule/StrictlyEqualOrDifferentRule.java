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
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Replaces passive boolean combinations with direct equality or exclusive-or.
 *
 * <p>The supported shapes are {@code (X && Y) || (!X && !Y)} and
 * {@code (X && !Y) || (!X && Y)}, including operand and branch reordering.</p>
 */
public final class StrictlyEqualOrDifferentRule implements CleanupRule {

    /** The stable identifier for strict equality-or-difference cleanup. */
    public static final String ID = "comparisons.strictly-equal-or-different";

    /**
     * Returns the stable identifier for strict equality-or-difference cleanup.
     *
     * @return the strict equality-or-difference rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records equality and exclusive-or replacements for passive boolean shapes.
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
             * Replaces one eligible disjunction of boolean conjunctions.
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

                Combination combination = combination(left, right);
                if (combination == null) {
                    combination = combination(right, left);
                }
                if (combination == null) {
                    return true;
                }

                rewrite.replace(node, replacement(node.getAST(), combination), null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Matches two conjunctions whose corresponding operands have opposite signs.
     *
     * @param first the first conjunction
     * @param second the second conjunction
     * @return the matched combination, or {@code null} when unsupported
     */
    private Combination combination(InfixExpression first, InfixExpression second) {
        SignedOperand[] firstOperands = signedOperands(first);
        SignedOperand[] secondOperands = signedOperands(second);
        if (firstOperands == null || secondOperands == null) {
            return null;
        }

        if (sameTree(firstOperands[0].operand(), secondOperands[0].operand())
                && sameTree(firstOperands[1].operand(), secondOperands[1].operand())
                && firstOperands[0].negated() != secondOperands[0].negated()
                && firstOperands[1].negated() != secondOperands[1].negated()) {
            return new Combination(firstOperands[0].operand(), firstOperands[1].operand(),
                    firstOperands[0].negated() == firstOperands[1].negated());
        }
        if (sameTree(firstOperands[0].operand(), secondOperands[1].operand())
                && sameTree(firstOperands[1].operand(), secondOperands[0].operand())
                && firstOperands[0].negated() != secondOperands[1].negated()
                && firstOperands[1].negated() != secondOperands[0].negated()) {
            return new Combination(firstOperands[0].operand(), firstOperands[1].operand(),
                    firstOperands[0].negated() == firstOperands[1].negated());
        }
        return null;
    }

    /**
     * Decomposes a binary conjunction into signed operands.
     *
     * @param expression the conjunction to decompose
     * @return the signed operands, or {@code null} when the shape is unsupported
     */
    private SignedOperand[] signedOperands(InfixExpression expression) {
        SignedOperand left = signedOperand(expression.getLeftOperand());
        SignedOperand right = signedOperand(expression.getRightOperand());
        return left == null || right == null ? null : new SignedOperand[] {left, right};
    }

    /**
     * Decomposes one expression into a passive operand and its negation state.
     *
     * @param expression the expression to decompose
     * @return the signed operand, or {@code null} when unsupported
     */
    private SignedOperand signedOperand(Expression expression) {
        Expression unparenthesized = stripParentheses(expression);
        if (unparenthesized instanceof PrefixExpression prefix
                && prefix.getOperator() == PrefixExpression.Operator.NOT) {
            Expression operand = stripParentheses(prefix.getOperand());
            return isPassive(operand) ? new SignedOperand(operand, true) : null;
        }
        return isPassive(unparenthesized) ? new SignedOperand(unparenthesized, false) : null;
    }

    /**
     * Extracts a binary conditional conjunction.
     *
     * @param expression the candidate expression
     * @return the conjunction, or {@code null} when unsupported
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
     * Creates the direct equality or exclusive-or replacement.
     *
     * @param ast the AST receiving the replacement
     * @param combination the matched boolean combination
     * @return the replacement expression
     */
    private Expression replacement(AST ast, Combination combination) {
        InfixExpression replacement = ast.newInfixExpression();
        replacement.setOperator(combination.equal()
                ? InfixExpression.Operator.EQUALS
                : InfixExpression.Operator.XOR);
        replacement.setLeftOperand(copy(ast, combination.first()));
        replacement.setRightOperand(copy(ast, combination.second()));
        return replacement;
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
     * Holds one signed boolean operand.
     *
     * @param operand the passive operand
     * @param negated whether the source used logical negation
     */
    private record SignedOperand(Expression operand, boolean negated) {
    }

    /**
     * Holds the two operands and relation selected for a replacement.
     *
     * @param first the first passive operand
     * @param second the second passive operand
     * @param equal whether equality rather than exclusive-or is required
     */
    private record Combination(Expression first, Expression second, boolean equal) {
    }
}
