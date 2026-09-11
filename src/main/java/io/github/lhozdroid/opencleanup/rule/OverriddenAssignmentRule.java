package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Removes a passive assignment that is immediately overwritten before the variable can be read.
 */
public final class OverriddenAssignmentRule implements CleanupRule {

    /** The stable identifier for overridden assignment cleanup. */
    public static final String ID = "assignments.overridden";

    /**
     * Returns the stable identifier for this rule.
     *
     * @return the overridden assignment rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records removals for adjacent passive assignments to the same local name.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one overridden assignment is removed
     */
    @Override
    public boolean apply(CompilationUnit compilationUnit, ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Finds adjacent assignment statements in one lexical block.
             *
             * @param node the visited block
             * @return {@code true} to continue visiting nested blocks
             */
            @Override
            public boolean visit(Block node) {
                for (int index = 1; index < node.statements().size(); index++) {
                    Statement previous = (Statement) node.statements().get(index - 1);
                    Statement current = (Statement) node.statements().get(index);
                    if (overridden(previous, current, rewrite)) {
                        changed[0] = true;
                    }
                }
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Removes the first assignment when the following assignment overwrites the same name.
     *
     * @param previous the earlier statement
     * @param current the later statement
     * @param rewrite the AST rewrite collecting source edits
     * @return {@code true} when the earlier statement is safe to remove
     */
    private boolean overridden(Statement previous, Statement current, ASTRewrite rewrite) {
        Assignment first = assignment(previous);
        Assignment second = assignment(current);
        if (first == null || second == null
                || first.getOperator() != Assignment.Operator.ASSIGN
                || second.getOperator() != Assignment.Operator.ASSIGN
                || !(first.getLeftHandSide() instanceof SimpleName firstName)
                || !(second.getLeftHandSide() instanceof SimpleName secondName)
                || !firstName.getIdentifier().equals(secondName.getIdentifier())
                || !isPassive(first.getRightHandSide())) {
            return false;
        }
        rewrite.remove(previous, null);
        return true;
    }

    /**
     * Extracts a plain assignment from an expression statement.
     *
     * @param statement the statement to inspect
     * @return its assignment, or {@code null} when it is another statement form
     */
    private Assignment assignment(Statement statement) {
        return statement instanceof ExpressionStatement expressionStatement
                && expressionStatement.getExpression() instanceof Assignment value ? value : null;
    }

    /**
     * Checks whether an expression is side-effect free enough to discard with its assignment.
     *
     * @param expression the expression to inspect
     * @return {@code true} for literals, names, and null
     */
    private boolean isPassive(Expression expression) {
        return expression instanceof Name || expression instanceof NullLiteral
                || expression instanceof NumberLiteral || expression instanceof StringLiteral
                || expression instanceof BooleanLiteral || expression instanceof CharacterLiteral;
    }
}
