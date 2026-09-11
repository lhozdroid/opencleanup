package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Simplifies an if/else that returns opposite boolean literals.
 *
 * <p>The supported shape is an if statement with an else branch where both
 * branches consist solely of a return of {@code true} or {@code false}.
 * Branches may be direct return statements or blocks containing exactly one
 * return statement. Other control-flow shapes remain unchanged.</p>
 */
public final class SimplifyBooleanIfElseRule implements CleanupRule {

    /** The stable identifier used to select this cleanup rule. */
    public static final String ID = "control-statements.simplify-boolean-if-else";

    /**
     * Returns the stable identifier for boolean if/else simplification.
     *
     * @return the boolean if/else rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for if/else statements with opposite boolean returns.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one if/else statement is simplified
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Simplifies one eligible if/else statement.
             *
             * @param node the visited if statement
             * @return {@code false} after replacement, otherwise {@code true}
             */
            @Override
            public boolean visit(IfStatement node) {
                ReturnStatement thenReturn = singleReturn(node.getThenStatement());
                ReturnStatement elseReturn = singleReturn(node.getElseStatement());
                if (thenReturn == null || elseReturn == null) {
                    return true;
                }

                BooleanLiteral thenLiteral = booleanLiteral(thenReturn);
                BooleanLiteral elseLiteral = booleanLiteral(elseReturn);
                if (thenLiteral == null || elseLiteral == null
                        || thenLiteral.booleanValue() == elseLiteral.booleanValue()) {
                    return true;
                }

                rewrite.replace(node, replacementFor(node, thenLiteral.booleanValue()), null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Finds a direct return statement or the sole return in a block.
     *
     * @param statement the branch statement to inspect
     * @return the branch return statement, or {@code null} when the branch has
     *         another shape
     */
    private ReturnStatement singleReturn(Statement statement) {
        if (statement instanceof ReturnStatement returnStatement) {
            return returnStatement;
        }
        if (!(statement instanceof Block block) || block.statements().size() != 1) {
            return null;
        }
        Object onlyStatement = block.statements().get(0);
        return onlyStatement instanceof ReturnStatement returnStatement ? returnStatement : null;
    }

    /**
     * Finds a boolean literal returned by a branch.
     *
     * @param returnStatement the return statement to inspect
     * @return the returned boolean literal, or {@code null} for another return
     *         expression
     */
    private BooleanLiteral booleanLiteral(ReturnStatement returnStatement) {
        return returnStatement.getExpression() instanceof BooleanLiteral literal ? literal : null;
    }

    /**
     * Creates the single return equivalent of an eligible if/else statement.
     *
     * @param ifStatement the if/else statement being replaced
     * @param thenValue the boolean literal returned by the then branch
     * @return a return statement containing the condition or its negation
     */
    private ReturnStatement replacementFor(IfStatement ifStatement, boolean thenValue) {
        AST ast = ifStatement.getAST();
        Expression condition = (Expression) ASTNode.copySubtree(ast, ifStatement.getExpression());
        if (!thenValue) {
            PrefixExpression negated = ast.newPrefixExpression();
            negated.setOperator(PrefixExpression.Operator.NOT);
            negated.setOperand(condition);
            condition = negated;
        }

        ReturnStatement replacement = ast.newReturnStatement();
        replacement.setExpression(condition);
        return replacement;
    }
}
