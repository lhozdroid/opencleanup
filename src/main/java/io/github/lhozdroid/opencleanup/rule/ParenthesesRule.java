package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.AssertStatement;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.YieldStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Removes parentheses around simple expressions in grammar-safe expression positions.
 */
public final class ParenthesesRule implements CleanupRule {

    public static final String ID = "expressions.parentheses";

    /**
     * Returns the stable identifier for parentheses cleanup.
     *
     * @return the parentheses rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records edits that unwrap simple expressions where the surrounding grammar permits it.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one pair of parentheses is removed
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Removes one eligible pair of parentheses.
             *
             * @param node the visited parenthesized expression
             * @return {@code true} to continue visiting nested expressions
             */
            @Override
            public boolean visit(ParenthesizedExpression node) {
                if (isSimpleExpression(node.getExpression()) && isSafeContext(node)) {
                    rewrite.replace(node, rewrite.createMoveTarget(node.getExpression()), null);
                    changed[0] = true;
                }
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Checks whether an expression is one of the simple expression forms handled by this rule.
     *
     * @param expression the expression inside a pair of parentheses
     * @return {@code true} for names, literals, {@code this}, and {@code null}
     */
    private boolean isSimpleExpression(ASTNode expression) {
        return expression instanceof Name
                || expression instanceof BooleanLiteral
                || expression instanceof CharacterLiteral
                || expression instanceof NumberLiteral
                || expression instanceof StringLiteral
                || expression instanceof TextBlock
                || expression instanceof ThisExpression
                || expression instanceof NullLiteral;
    }

    /**
     * Checks whether replacing the parenthesized expression with its simple child is grammar-safe.
     *
     * @param node the parenthesized expression under consideration
     * @return {@code true} when the parent AST slot accepts the unwrapped expression
     */
    private boolean isSafeContext(ParenthesizedExpression node) {
        ASTNode parent = node.getParent();
        Object property = node.getLocationInParent();
        return (parent instanceof ArrayAccess
                    && (property == ArrayAccess.ARRAY_PROPERTY || property == ArrayAccess.INDEX_PROPERTY))
                || (parent instanceof ArrayCreation && property == ArrayCreation.DIMENSIONS_PROPERTY)
                || (parent instanceof ArrayInitializer && property == ArrayInitializer.EXPRESSIONS_PROPERTY)
                || (parent instanceof AssertStatement
                    && (property == AssertStatement.EXPRESSION_PROPERTY
                        || property == AssertStatement.MESSAGE_PROPERTY))
                || (parent instanceof Assignment && property == Assignment.RIGHT_HAND_SIDE_PROPERTY)
                || (parent instanceof CastExpression && property == CastExpression.EXPRESSION_PROPERTY)
                || (parent instanceof ClassInstanceCreation && property == ClassInstanceCreation.ARGUMENTS_PROPERTY)
                || (parent instanceof ConditionalExpression
                    && (property == ConditionalExpression.EXPRESSION_PROPERTY
                        || property == ConditionalExpression.THEN_EXPRESSION_PROPERTY
                        || property == ConditionalExpression.ELSE_EXPRESSION_PROPERTY))
                || (parent instanceof ConstructorInvocation && property == ConstructorInvocation.ARGUMENTS_PROPERTY)
                || (parent instanceof DoStatement && property == DoStatement.EXPRESSION_PROPERTY)
                || (parent instanceof EnhancedForStatement && property == EnhancedForStatement.EXPRESSION_PROPERTY)
                || (parent instanceof EnumConstantDeclaration && property == EnumConstantDeclaration.ARGUMENTS_PROPERTY)
                || (parent instanceof ExpressionMethodReference
                    && property == ExpressionMethodReference.EXPRESSION_PROPERTY)
                || (parent instanceof FieldAccess && property == FieldAccess.EXPRESSION_PROPERTY)
                || (parent instanceof ForStatement && property == ForStatement.EXPRESSION_PROPERTY)
                || (parent instanceof IfStatement && property == IfStatement.EXPRESSION_PROPERTY)
                || (parent instanceof InfixExpression
                    && (property == InfixExpression.LEFT_OPERAND_PROPERTY
                        || property == InfixExpression.RIGHT_OPERAND_PROPERTY
                        || property == InfixExpression.EXTENDED_OPERANDS_PROPERTY))
                || (parent instanceof LambdaExpression && property == LambdaExpression.BODY_PROPERTY)
                || (parent instanceof MethodInvocation
                    && (property == MethodInvocation.ARGUMENTS_PROPERTY
                        || property == MethodInvocation.EXPRESSION_PROPERTY))
                || (parent instanceof ParenthesizedExpression
                    && property == ParenthesizedExpression.EXPRESSION_PROPERTY)
                || (parent instanceof PostfixExpression && property == PostfixExpression.OPERAND_PROPERTY)
                || (parent instanceof PrefixExpression && property == PrefixExpression.OPERAND_PROPERTY)
                || (parent instanceof ReturnStatement && property == ReturnStatement.EXPRESSION_PROPERTY)
                || (parent instanceof SuperConstructorInvocation
                    && property == SuperConstructorInvocation.ARGUMENTS_PROPERTY)
                || (parent instanceof SuperMethodInvocation
                    && property == SuperMethodInvocation.ARGUMENTS_PROPERTY)
                || (parent instanceof SwitchStatement && property == SwitchStatement.EXPRESSION_PROPERTY)
                || (parent instanceof SynchronizedStatement && property == SynchronizedStatement.EXPRESSION_PROPERTY)
                || (parent instanceof ThrowStatement && property == ThrowStatement.EXPRESSION_PROPERTY)
                || (parent instanceof WhileStatement && property == WhileStatement.EXPRESSION_PROPERTY)
                || (parent instanceof YieldStatement && property == YieldStatement.EXPRESSION_PROPERTY)
                || (parent instanceof VariableDeclarationFragment
                    && property == VariableDeclarationFragment.INITIALIZER_PROPERTY);
    }
}
