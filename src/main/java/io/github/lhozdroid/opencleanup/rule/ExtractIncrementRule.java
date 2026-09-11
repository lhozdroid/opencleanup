package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Extracts a direct increment or decrement from a local variable declaration.
 *
 * <p>The supported shape is one declaration fragment in a block whose initializer is exactly
 * {@code ++name}, {@code --name}, {@code name++}, or {@code name--}, where {@code name} is a
 * simple variable name. Prefix operations become a postfix statement before the declaration;
 * postfix operations become a postfix statement after it. This syntax-only scope makes the
 * evaluation order explicit without moving side-effecting receivers, loop expressions, or
 * multiple increments in one statement.</p>
 */
public final class ExtractIncrementRule implements CleanupRule {

    /** The stable identifier for this cleanup rule. */
    public static final String ID = "expressions.extract-increment";

    /**
     * Returns the stable identifier for increment extraction cleanup.
     *
     * @return the increment extraction rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records edits that extract eligible increments from local variable declarations.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one increment or decrement is extracted
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Extracts a direct increment or decrement from one local declaration statement.
             *
             * @param node the visited local variable declaration statement
             * @return {@code false} after an extraction, otherwise {@code true}
             */
            @Override
            public boolean visit(VariableDeclarationStatement node) {
                if (!(node.getParent() instanceof Block)
                        || node.getLocationInParent() != Block.STATEMENTS_PROPERTY
                        || node.fragments().size() != 1) {
                    return true;
                }

                VariableDeclarationFragment fragment =
                        (VariableDeclarationFragment) node.fragments().get(0);
                Expression initializer = fragment.getInitializer();
                if (initializer instanceof PrefixExpression prefix) {
                    if (isIncrementOrDecrement(prefix.getOperator())
                            && prefix.getOperand() instanceof SimpleName variable) {
                        extractPrefix(node, prefix, variable, rewrite);
                        changed[0] = true;
                        return false;
                    }
                }
                if (initializer instanceof PostfixExpression postfix
                        && isIncrementOrDecrement(postfix.getOperator())
                        && postfix.getOperand() instanceof SimpleName variable) {
                    extractPostfix(node, postfix, variable, rewrite);
                    changed[0] = true;
                    return false;
                }
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Checks whether a prefix operator changes its operand by one.
     *
     * @param operator the prefix operator to inspect
     * @return {@code true} for prefix increment and decrement operators
     */
    private boolean isIncrementOrDecrement(PrefixExpression.Operator operator) {
        return operator == PrefixExpression.Operator.INCREMENT
                || operator == PrefixExpression.Operator.DECREMENT;
    }

    /**
     * Checks whether a postfix operator changes its operand by one.
     *
     * @param operator the postfix operator to inspect
     * @return {@code true} for postfix increment and decrement operators
     */
    private boolean isIncrementOrDecrement(PostfixExpression.Operator operator) {
        return operator == PostfixExpression.Operator.INCREMENT
                || operator == PostfixExpression.Operator.DECREMENT;
    }

    /**
     * Extracts a prefix operation before its declaration and normalizes it to postfix syntax.
     *
     * @param declaration the declaration containing the prefix operation
     * @param prefix the prefix increment or decrement expression
     * @param variable the simple variable operand
     * @param rewrite the rewrite collecting source edits
     */
    private void extractPrefix(
            VariableDeclarationStatement declaration,
            PrefixExpression prefix,
            SimpleName variable,
            ASTRewrite rewrite) {
        rewrite.replace(prefix, rewrite.createCopyTarget(variable), null);
        PostfixExpression extracted = declaration.getAST().newPostfixExpression();
        extracted.setOperator(toPostfixOperator(prefix.getOperator()));
        extracted.setOperand((Expression) rewrite.createMoveTarget(variable));
        insertStatement(declaration, extracted, true, rewrite);
    }

    /**
     * Extracts a postfix operation after its declaration while retaining postfix syntax.
     *
     * @param declaration the declaration containing the postfix operation
     * @param postfix the postfix increment or decrement expression
     * @param variable the simple variable operand
     * @param rewrite the rewrite collecting source edits
     */
    private void extractPostfix(
            VariableDeclarationStatement declaration,
            PostfixExpression postfix,
            SimpleName variable,
            ASTRewrite rewrite) {
        rewrite.replace(postfix, rewrite.createCopyTarget(variable), null);
        insertStatement(
                declaration,
                (Expression) rewrite.createMoveTarget(postfix),
                false,
                rewrite);
    }

    /**
     * Converts a prefix increment or decrement operator to its postfix equivalent.
     *
     * @param operator the prefix operator to convert
     * @return the equivalent postfix operator
     */
    private PostfixExpression.Operator toPostfixOperator(PrefixExpression.Operator operator) {
        return operator == PrefixExpression.Operator.INCREMENT
                ? PostfixExpression.Operator.INCREMENT
                : PostfixExpression.Operator.DECREMENT;
    }

    /**
     * Inserts an extracted expression statement immediately before or after its declaration.
     *
     * @param declaration the declaration that anchors the insertion
     * @param expression the extracted increment or decrement expression
     * @param before {@code true} to insert before the declaration, otherwise after it
     * @param rewrite the rewrite collecting source edits
     */
    private void insertStatement(
            VariableDeclarationStatement declaration,
            Expression expression,
            boolean before,
            ASTRewrite rewrite) {
        ExpressionStatement statement = declaration.getAST().newExpressionStatement(expression);
        ListRewrite statements = rewrite.getListRewrite(
                declaration.getParent(),
                Block.STATEMENTS_PROPERTY);
        if (before) {
            statements.insertBefore(statement, declaration, null);
        } else {
            statements.insertAfter(statement, declaration, null);
        }
    }
}
