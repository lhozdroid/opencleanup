package io.github.lhozdroid.opencleanup.rule;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Pushes one logical negation through a parenthesized conjunction or disjunction.
 */
public final class NegationPushDownRule implements CleanupRule {

    /** The stable identifier for this cleanup rule. */
    public static final String ID = "negation.push-down";

    /**
     * Returns the stable identifier for negation push-down cleanup.
     *
     * @return the negation push-down rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records De Morgan replacements for eligible logical-not expressions.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one negation is pushed down
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Replaces one negated parenthesized conjunction or disjunction.
             *
             * @param node the visited prefix expression
             * @return {@code false} after replacement to avoid nested rewrites
             */
            @Override
            public boolean visit(PrefixExpression node) {
                if (node.getOperator() != PrefixExpression.Operator.NOT
                        || hasNegatingPrefixAncestor(node)
                        || !(node.getOperand() instanceof ParenthesizedExpression parenthesized)
                        || !(parenthesized.getExpression() instanceof InfixExpression logicalExpression)
                        || !isConditionalOperator(logicalExpression.getOperator())) {
                    return true;
                }

                rewrite.replace(node, createReplacement(node.getAST(), logicalExpression), null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Checks whether the candidate is already nested below another logical-not expression.
     *
     * @param node the candidate logical-not expression
     * @return {@code true} when a logical-not ancestor encloses the candidate
     */
    private boolean hasNegatingPrefixAncestor(PrefixExpression node) {
        ASTNode ancestor = node.getParent();
        while (ancestor != null) {
            if (ancestor instanceof PrefixExpression prefix
                    && prefix.getOperator() == PrefixExpression.Operator.NOT) {
                return true;
            }
            ancestor = ancestor.getParent();
        }
        return false;
    }

    /**
     * Checks whether an infix operator is a short-circuit boolean operator.
     *
     * @param operator the infix operator to check
     * @return {@code true} for conditional conjunction and disjunction
     */
    private boolean isConditionalOperator(InfixExpression.Operator operator) {
        return operator == InfixExpression.Operator.CONDITIONAL_AND
                || operator == InfixExpression.Operator.CONDITIONAL_OR;
    }

    /**
     * Creates the De Morgan equivalent of a logical conjunction or disjunction.
     *
     * @param ast the AST receiving the copied nodes
     * @param logicalExpression the source logical expression
     * @return the replacement expression
     */
    private Expression createReplacement(AST ast, InfixExpression logicalExpression) {
        InfixExpression replacement = ast.newInfixExpression();
        replacement.setOperator(logicalExpression.getOperator()
                == InfixExpression.Operator.CONDITIONAL_AND
                        ? InfixExpression.Operator.CONDITIONAL_OR
                        : InfixExpression.Operator.CONDITIONAL_AND);

        List<Expression> operands = new ArrayList<>();
        operands.add(logicalExpression.getLeftOperand());
        operands.add(logicalExpression.getRightOperand());
        operands.addAll(logicalExpression.extendedOperands());

        replacement.setLeftOperand(negatedCopy(ast, operands.get(0)));
        replacement.setRightOperand(negatedCopy(ast, operands.get(1)));
        for (int index = 2; index < operands.size(); index++) {
            replacement.extendedOperands().add(negatedCopy(ast, operands.get(index)));
        }
        return replacement;
    }

    /**
     * Creates a logical-not expression around a copied operand.
     *
     * @param ast the AST receiving the copied node
     * @param operand the source operand
     * @return the negated copied operand
     */
    private Expression negatedCopy(AST ast, Expression operand) {
        Expression copiedOperand = (Expression) ASTNode.copySubtree(ast, operand);
        if (requiresParentheses(copiedOperand)) {
            ParenthesizedExpression parenthesized = ast.newParenthesizedExpression();
            parenthesized.setExpression(copiedOperand);
            copiedOperand = parenthesized;
        }

        PrefixExpression negatedOperand = ast.newPrefixExpression();
        negatedOperand.setOperator(PrefixExpression.Operator.NOT);
        negatedOperand.setOperand(copiedOperand);
        return negatedOperand;
    }

    /**
     * Checks whether a copied operand needs parentheses below a logical-not operator.
     *
     * @param operand the copied operand to check
     * @return {@code true} when omitting parentheses could change its meaning
     */
    private boolean requiresParentheses(Expression operand) {
        return operand instanceof Assignment
                || operand instanceof ConditionalExpression
                || operand instanceof InfixExpression
                || operand instanceof LambdaExpression;
    }
}
