package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Converts a leading conditional break in an infinite while loop into a loop condition.
 */
public final class LoopsBreakRule implements CleanupRule {

    /** The stable identifier for loop-break cleanup. */
    public static final String ID = "loops.break";

    /**
     * Returns the stable identifier for this cleanup rule.
     *
     * @return the loop-break rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for supported {@code while (true)} loops.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one loop is simplified
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Converts one supported infinite loop.
             *
             * @param node the visited while statement
             * @return {@code false} after replacement, otherwise {@code true}
             */
            @Override
            public boolean visit(WhileStatement node) {
                if (!(node.getExpression() instanceof BooleanLiteral literal)
                        || !literal.booleanValue()) {
                    return true;
                }
                IfStatement guard = leadingGuard(node.getBody());
                if (guard == null || guard.getExpression() == null) {
                    return true;
                }
                Expression condition = guard.getExpression();
                boolean breaksWhenCondition = guard.getElseStatement() == null
                        && isBreak(guard.getThenStatement());
                if (!breaksWhenCondition) {
                    return true;
                }
                Expression loopCondition = condition instanceof PrefixExpression prefix
                        && prefix.getOperator() == PrefixExpression.Operator.NOT
                        ? (Expression) ASTNode.copySubtree(node.getAST(), prefix.getOperand())
                        : negate(condition, node.getAST());
                WhileStatement replacement = node.getAST().newWhileStatement();
                replacement.setExpression(loopCondition);
                replacement.setBody(remainingBody(node.getBody(), node.getAST()));
                rewrite.replace(node, replacement, null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Returns the first statement when a loop body is a block beginning with an if statement.
     *
     * @param body the loop body
     * @return the leading guard, or {@code null} when unsupported
     */
    private IfStatement leadingGuard(Statement body) {
        if (!(body instanceof Block block) || block.statements().isEmpty()) {
            return null;
        }
        Object first = block.statements().get(0);
        return first instanceof IfStatement ifStatement ? ifStatement : null;
    }

    /**
     * Checks whether a statement is an unlabeled break.
     *
     * @param statement the statement to inspect
     * @return {@code true} when it is an unlabeled break statement
     */
    private boolean isBreak(Statement statement) {
        return statement instanceof BreakStatement breakStatement
                && breakStatement.getLabel() == null;
    }

    /**
     * Negates an expression by creating a prefix-not expression.
     *
     * @param expression the expression to negate
     * @param ast the owning AST
     * @return the negated expression
     */
    private Expression negate(Expression expression, AST ast) {
        PrefixExpression result = ast.newPrefixExpression();
        result.setOperator(PrefixExpression.Operator.NOT);
        result.setOperand((Expression) ASTNode.copySubtree(ast, expression));
        return result;
    }

    /**
     * Copies all statements after the leading guard into a new block.
     *
     * @param body the original loop body
     * @param ast the owning AST
     * @return the replacement loop body
     */
    private Block remainingBody(Statement body, AST ast) {
        Block result = ast.newBlock();
        Block original = (Block) body;
        for (int index = 1; index < original.statements().size(); index++) {
            result.statements().add(ASTNode.copySubtree(ast, (ASTNode) original.statements().get(index)));
        }
        return result;
    }
}
