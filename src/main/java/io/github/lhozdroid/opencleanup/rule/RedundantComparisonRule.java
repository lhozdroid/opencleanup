package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Removes passive bad-value checks that select the same value either way.
 *
 * <p>For example, {@code if (value == null) return null; else return value;} becomes
 * {@code return value;}. The excluded value must be a literal and both branches must return or
 * assign either the passive expression or that literal.</p>
 */
public final class RedundantComparisonRule implements CleanupRule {

    /** The stable identifier for redundant comparison statement cleanup. */
    public static final String ID = "statements.redundant-comparison";

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
     * Records replacements for redundant bad-value checks.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source changes
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
             * Replaces one redundant comparison statement.
             *
             * @param node the visited expression statement
             * @return {@code false} after removal, otherwise {@code true}
             */
            @Override
            public boolean visit(IfStatement node) {
                Statement replacement = replacement(node);
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
     * Finds the branch statement that makes a comparison redundant.
     *
     * @param statement the candidate if statement
     * @return a copied replacement statement, or {@code null} when unsupported
     */
    private Statement replacement(IfStatement statement) {
        if (statement.getElseStatement() == null
                || !(statement.getExpression() instanceof InfixExpression comparison)
                || !comparison.extendedOperands().isEmpty()
                || (comparison.getOperator() != InfixExpression.Operator.EQUALS
                && comparison.getOperator() != InfixExpression.Operator.NOT_EQUALS)) {
            return null;
        }
        Expression value;
        Expression excluded;
        if (isLiteral(comparison.getLeftOperand())) {
            excluded = comparison.getLeftOperand();
            value = comparison.getRightOperand();
        } else if (isLiteral(comparison.getRightOperand())) {
            value = comparison.getLeftOperand();
            excluded = comparison.getRightOperand();
        } else {
            return null;
        }
        if (!isPassive(value)) {
            return null;
        }
        Statement thenStatement = singleStatement(statement.getThenStatement());
        Statement elseStatement = singleStatement(statement.getElseStatement());
        Statement selected = comparison.getOperator() == InfixExpression.Operator.EQUALS
                ? matchingStatement(elseStatement, value, excluded)
                : matchingStatement(thenStatement, value, excluded);
        Statement other = comparison.getOperator() == InfixExpression.Operator.EQUALS
                ? matchingStatement(thenStatement, excluded, value)
                : matchingStatement(elseStatement, excluded, value);
        return selected != null && other != null
                ? (Statement) ASTNode.copySubtree(statement.getAST(), selected) : null;
    }

    /**
     * Extracts the only statement from a direct or braced branch.
     *
     * @param statement the branch statement
     * @return the single branch statement, or {@code null} when the branch has multiple statements
     */
    private Statement singleStatement(Statement statement) {
        if (!(statement instanceof Block block)) {
            return statement;
        }
        return block.statements().size() == 1 ? (Statement) block.statements().get(0) : null;
    }

    /**
     * Checks whether a branch returns or assigns a requested value.
     *
     * @param statement the branch statement
     * @param expected the requested expression
     * @param other the alternate expression
     * @return the statement when it matches, otherwise {@code null}
     */
    private Statement matchingStatement(Statement statement, Expression expected, Expression other) {
        if (statement instanceof ReturnStatement returnStatement
                && returnStatement.getExpression() != null
                && returnStatement.getExpression().subtreeMatch(new ASTMatcher(), expected)) {
            return statement;
        }
        if (statement instanceof ExpressionStatement expressionStatement
                && expressionStatement.getExpression() instanceof Assignment assignment
                && assignment.getOperator() == Assignment.Operator.ASSIGN
                && assignment.getRightHandSide().subtreeMatch(new ASTMatcher(), expected)
                && !assignment.getLeftHandSide().subtreeMatch(new ASTMatcher(), other)) {
            return statement;
        }
        return null;
    }

    /**
     * Checks whether an expression can be evaluated without side effects.
     *
     * @param expression the expression to inspect
     * @return {@code true} for names, literals, and simple qualified names
     */
    private boolean isPassive(Expression expression) {
        return expression instanceof org.eclipse.jdt.core.dom.Name
                || isLiteral(expression);
    }

    /**
     * Checks whether an expression is a hard-coded Java literal.
     *
     * @param expression the expression to inspect
     * @return {@code true} when the expression is a supported literal
     */
    private boolean isLiteral(Expression expression) {
        return expression instanceof BooleanLiteral
                || expression instanceof CharacterLiteral
                || expression instanceof NumberLiteral
                || expression instanceof StringLiteral
                || expression instanceof NullLiteral;
    }
}
