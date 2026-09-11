package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Replaces comparisons of boolean expressions with boolean literals by the
 * equivalent boolean expression.
 */
public final class BooleanValueRatherThanComparisonRule implements CleanupRule {

    public static final String ID = "booleans.value-rather-than-comparison";

    /**
     * Returns the stable identifier for boolean comparison cleanup.
     *
     * @return the boolean comparison rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for equality and inequality comparisons with boolean literals.
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
             * Replaces one eligible boolean comparison and skips its children.
             *
             * @param node the visited infix-expression node
             * @return {@code false} after replacement, otherwise {@code true}
             */
            @Override
            public boolean visit(InfixExpression node) {
                Expression replacement = replacementFor(node);
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
     * Creates an equivalent expression for a comparison with one boolean literal.
     *
     * @param expression the comparison to simplify
     * @return the replacement expression, or {@code null} when it is not eligible
     */
    private Expression replacementFor(InfixExpression expression) {
        if (!expression.extendedOperands().isEmpty()) {
            return null;
        }

        Expression left = expression.getLeftOperand();
        Expression right = expression.getRightOperand();
        BooleanLiteral literal;
        Expression operand;
        if (left instanceof BooleanLiteral leftLiteral && !(right instanceof BooleanLiteral)) {
            literal = leftLiteral;
            operand = right;
        } else if (right instanceof BooleanLiteral rightLiteral && !(left instanceof BooleanLiteral)) {
            literal = rightLiteral;
            operand = left;
        } else {
            return null;
        }

        boolean negate = switch (expression.getOperator().toString()) {
            case "==" -> !literal.booleanValue();
            case "!=" -> literal.booleanValue();
            default -> false;
        };
        if (!"==".equals(expression.getOperator().toString())
                && !"!=".equals(expression.getOperator().toString())) {
            return null;
        }

        AST ast = expression.getAST();
        Expression replacement = (Expression) ASTNode.copySubtree(ast, operand);
        if (!negate) {
            return replacement;
        }

        PrefixExpression negated = ast.newPrefixExpression();
        negated.setOperator(PrefixExpression.Operator.NOT);
        negated.setOperand(replacement);
        return negated;
    }
}
