package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Pulls a direct assignment out of an {@code if} condition.
 *
 * <p>The syntax-only implementation handles assignments whose left-hand side is a simple name,
 * a {@code this} field access, or a {@code super} field access. More deeply nested conditions are
 * left unchanged because moving them could change short-circuit evaluation.</p>
 */
public final class PullUpAssignmentRule implements CleanupRule {

    /** The stable identifier for this cleanup rule. */
    public static final String ID = "expressions.pull-up-assignment";

    /**
     * Creates an assignment pull-up cleanup rule.
     */
    public PullUpAssignmentRule() {
    }

    /**
     * Returns the stable identifier for assignment pull-up cleanup.
     *
     * @return the assignment pull-up rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records edits that extract direct assignments from {@code if} conditions.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one assignment is moved before an {@code if}
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Pulls one direct assignment out of an if condition.
             *
             * @param node the visited if statement
             * @return {@code false} after a rewrite, otherwise {@code true}
             */
            @Override
            public boolean visit(IfStatement node) {
                Expression conditionTarget = withoutParentheses(node.getExpression());
                if (!(conditionTarget instanceof Assignment assignment)
                        || assignment.getOperator() != Assignment.Operator.ASSIGN
                        || !isStableLeftHandSide(assignment.getLeftHandSide())) {
                    return true;
                }

                Expression leftHandSide = withoutParentheses(assignment.getLeftHandSide());
                rewrite.replace(
                        conditionTarget,
                        rewrite.createCopyTarget(leftHandSide),
                        null);
                ExpressionStatement extracted = node.getAST().newExpressionStatement(
                        (Expression) rewrite.createMoveTarget(assignment));
                insertBefore(node, extracted, rewrite);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Removes surrounding parentheses from an expression for shape inspection.
     *
     * @param expression the expression to inspect
     * @return the expression after all surrounding parentheses are skipped
     */
    private Expression withoutParentheses(Expression expression) {
        Expression unwrapped = expression;
        while (unwrapped instanceof ParenthesizedExpression parenthesized) {
            unwrapped = parenthesized.getExpression();
        }
        return unwrapped;
    }

    /**
     * Checks whether replacing an assignment with its left-hand side does not re-evaluate a
     * receiver or index expression.
     *
     * @param leftHandSide the assignment target
     * @return {@code true} for stable syntax-only assignment targets
     */
    private boolean isStableLeftHandSide(Expression leftHandSide) {
        Expression unwrapped = withoutParentheses(leftHandSide);
        if (unwrapped instanceof SimpleName || unwrapped instanceof SuperFieldAccess) {
            return true;
        }
        return unwrapped instanceof FieldAccess fieldAccess
                && fieldAccess.getExpression() instanceof ThisExpression;
    }

    /**
     * Inserts an extracted assignment immediately before its if statement.
     *
     * @param ifStatement the if statement that formerly contained the assignment
     * @param extracted the new assignment statement
     * @param rewrite the AST rewrite collecting source edits
     */
    private void insertBefore(
            IfStatement ifStatement,
            ExpressionStatement extracted,
            ASTRewrite rewrite) {
        ASTNode parent = ifStatement.getParent();
        if (ifStatement.getLocationInParent() instanceof ChildListPropertyDescriptor property) {
            ListRewrite statements = rewrite.getListRewrite(parent, property);
            statements.insertBefore(extracted, ifStatement, null);
            return;
        }

        Block replacement = ifStatement.getAST().newBlock();
        ListRewrite statements = rewrite.getListRewrite(replacement, Block.STATEMENTS_PROPERTY);
        statements.insertLast(extracted, null);
        statements.insertLast(rewrite.createMoveTarget(ifStatement), null);
        rewrite.replace(ifStatement, replacement, null);
    }
}
