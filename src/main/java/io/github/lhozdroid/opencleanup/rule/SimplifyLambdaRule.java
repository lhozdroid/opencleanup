package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Simplifies lambda parameter parentheses and single-statement lambda bodies.
 */
public final class SimplifyLambdaRule implements CleanupRule {

    /** The stable identifier for lambda simplification. */
    public static final String ID = "functional-interfaces.simplify-lambda";

    /**
     * Returns the stable identifier for lambda simplification.
     *
     * @return the lambda simplification rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records syntax-only simplifications for lambda expressions.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one lambda is simplified
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Simplifies one lambda's parentheses or body when legal from syntax alone.
             *
             * @param node the visited lambda expression
             * @return {@code true} to continue visiting nested lambdas
             */
            @Override
            public boolean visit(LambdaExpression node) {
                if (simplifyParentheses(node)) {
                    rewrite.set(node, LambdaExpression.PARENTHESES_PROPERTY, false, null);
                    changed[0] = true;
                }
                if (node.getBody() instanceof Block block && block.statements().size() == 1) {
                    Statement statement = (Statement) block.statements().get(0);
                    Expression expression = expressionBody(statement);
                    if (expression != null) {
                        rewrite.replace(block, ASTNode.copySubtree(node.getAST(), expression), null);
                        changed[0] = true;
                        return false;
                    }
                }
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Checks whether a lambda has one untyped parameter whose parentheses can be removed.
     *
     * @param lambda the lambda to inspect
     * @return {@code true} when parentheses are optional for this lambda
     */
    private boolean simplifyParentheses(LambdaExpression lambda) {
        if (!lambda.hasParentheses() || lambda.parameters().size() != 1) {
            return false;
        }
        Object parameter = lambda.parameters().get(0);
        if (parameter instanceof VariableDeclarationFragment variable) {
            return variable.extraDimensions().isEmpty() && variable.getInitializer() == null;
        }
        return parameter instanceof SingleVariableDeclaration variable
                && variable.getType() == null
                && variable.modifiers().isEmpty()
                && !variable.isVarargs()
                && variable.extraDimensions().isEmpty();
    }

    /**
     * Extracts an expression body from a one-statement lambda block.
     *
     * @param statement the sole statement in the lambda block
     * @return the equivalent expression, or {@code null} when the block must remain
     */
    private Expression expressionBody(Statement statement) {
        if (statement instanceof ReturnStatement returnStatement
                && returnStatement.getExpression() != null) {
            return returnStatement.getExpression();
        }
        if (statement instanceof ExpressionStatement expressionStatement
                && isStatementExpression(expressionStatement.getExpression())) {
            return expressionStatement.getExpression();
        }
        return null;
    }

    /**
     * Checks whether an expression is permitted as a lambda expression body.
     *
     * @param expression the candidate lambda body expression
     * @return {@code true} when the expression is a statement expression
     */
    private boolean isStatementExpression(Expression expression) {
        return expression instanceof org.eclipse.jdt.core.dom.Assignment
                || expression instanceof org.eclipse.jdt.core.dom.ClassInstanceCreation
                || expression instanceof org.eclipse.jdt.core.dom.MethodInvocation
                || expression instanceof org.eclipse.jdt.core.dom.PostfixExpression
                || expression instanceof org.eclipse.jdt.core.dom.PrefixExpression
                || expression instanceof org.eclipse.jdt.core.dom.SuperMethodInvocation;
    }
}
