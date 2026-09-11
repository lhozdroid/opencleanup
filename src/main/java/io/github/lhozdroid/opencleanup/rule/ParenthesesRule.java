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
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InstanceofExpression;
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
 * Adds or removes optional parentheses in expression trees.
 *
 * <p>The {@code always} mode adds parentheses only around mixed infix operators and
 * {@code instanceof} expressions, matching the conservative part of Eclipse's paranoid
 * parentheses cleanup. The {@code never} mode removes only parentheses that this rule can prove
 * unnecessary from the AST structure. When no mode is configured, {@code never} is used for
 * backwards compatibility with the original implementation.</p>
 */
public final class ParenthesesRule implements CleanupRule {

    /** The stable identifier used to select this cleanup rule. */
    public static final String ID = "expressions.parentheses";

    /** The option value that requests additional safe parentheses. */
    public static final String ALWAYS = "always";

    /** The option value that requests removal of unnecessary parentheses. */
    public static final String NEVER = "never";

    /**
     * Creates a parentheses cleanup rule.
     */
    public ParenthesesRule() {
    }

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
        String mode = configuredMode(configuration);
        if (ALWAYS.equalsIgnoreCase(mode)) {
            return addParentheses(compilationUnit, rewrite);
        }
        if (NEVER.equalsIgnoreCase(mode)) {
            return removeParentheses(compilationUnit, rewrite);
        }
        return false;
    }

    /**
     * Resolves the configured parentheses mode.
     *
     * @param configuration the Maven configuration for this rule
     * @return {@code always}, {@code never}, or an unsupported value
     */
    private String configuredMode(RuleConfiguration configuration) {
        if (configuration == null) {
            return NEVER;
        }

        String mode = configuration.optionValue(ID);
        if (mode == null) {
            mode = configuration.optionValue("mode");
        }
        return mode == null ? NEVER : mode;
    }

    /**
     * Records parentheses around mixed-precedence infix children.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @return {@code true} when at least one pair of parentheses is added
     */
    private boolean addParentheses(CompilationUnit compilationUnit, ASTRewrite rewrite) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Adds parentheses around one mixed-precedence infix child.
             *
             * @param node the visited AST node
             */
            @Override
            public void postVisit(ASTNode node) {
                if (needsParentheses(node)) {
                    ParenthesizedExpression replacement = node.getAST().newParenthesizedExpression();
                    replacement.setExpression((org.eclipse.jdt.core.dom.Expression)
                            rewrite.createCopyTarget(node));
                    rewrite.replace(node, replacement, null);
                    changed[0] = true;
                }
            }
        });
        return changed[0];
    }

    /**
     * Records replacements that remove unnecessary parentheses.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @return {@code true} when at least one pair of parentheses is removed
     */
    private boolean removeParentheses(CompilationUnit compilationUnit, ASTRewrite rewrite) {
        java.util.List<ParenthesizedExpression> candidates = new java.util.ArrayList<>();
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Collects one removable parenthesized expression.
             *
             * @param node the visited parenthesized expression
             * @return {@code true} to continue visiting nested expressions
             */
            @Override
            public boolean visit(ParenthesizedExpression node) {
                if (canRemove(node)) {
                    candidates.add(node);
                }
                return true;
            }
        });

        boolean changed = false;
        for (ParenthesizedExpression candidate : candidates) {
            if (hasRemovableAncestor(candidate, candidates)) {
                continue;
            }
            rewrite.replace(candidate, rewrite.createMoveTarget(deepestExpression(candidate)), null);
            changed = true;
        }
        return changed;
    }

    /**
     * Checks whether an AST node needs parentheses in the always mode.
     *
     * @param node the AST node under consideration
     * @return {@code true} when the node is an eligible infix child
     */
    private boolean needsParentheses(ASTNode node) {
        if (!(node.getParent() instanceof InfixExpression parent)) {
            return false;
        }
        if (node instanceof InstanceofExpression) {
            return true;
        }
        return node instanceof InfixExpression child
                && child.getOperator() != parent.getOperator();
    }

    /**
     * Checks whether one pair of parentheses can be removed without changing the parsed form.
     *
     * @param node the parenthesized expression under consideration
     * @return {@code true} when removing its outer parentheses is safe
     */
    private boolean canRemove(ParenthesizedExpression node) {
        return canReplace(node, deepestExpression(node));
    }

    /**
     * Checks whether a parenthesized expression can be replaced by an effective child expression.
     *
     * @param node the parenthesized expression under consideration
     * @param expression the child expression after nested parentheses are unwrapped
     * @return {@code true} when the replacement is grammar-safe and semantically conservative
     */
    private boolean canReplace(ParenthesizedExpression node, org.eclipse.jdt.core.dom.Expression expression) {
        ASTNode parent = node.getParent();
        if (parent instanceof ParenthesizedExpression) {
            return true;
        }
        if (isSimpleExpression(expression)) {
            return isSafeContext(node);
        }
        if (parent instanceof InfixExpression infix) {
            return canRemoveFromInfix(expression, infix);
        }
        if (parent instanceof PrefixExpression
                && expression instanceof PrefixExpression) {
            return true;
        }
        return isSafeComplexContext(node, expression);
    }

    /**
     * Checks whether an expression can be unwrapped from an infix operand.
     *
     * @param expression the effective child expression
     * @param parent the containing infix expression
     * @return {@code true} when the child precedence and associativity are safe
     */
    private boolean canRemoveFromInfix(
            org.eclipse.jdt.core.dom.Expression expression,
            InfixExpression parent) {
        if (expression instanceof ConditionalExpression
                || expression instanceof Assignment
                || expression instanceof LambdaExpression
                || expression instanceof InstanceofExpression) {
            return false;
        }
        if (!(expression instanceof InfixExpression child)) {
            return true;
        }

        int childPrecedence = precedence(child.getOperator());
        int parentPrecedence = precedence(parent.getOperator());
        if (childPrecedence > parentPrecedence) {
            return true;
        }
        if (childPrecedence < parentPrecedence
                || child.getOperator() != parent.getOperator()) {
            return false;
        }
        return isAssociative(parent.getOperator());
    }

    /**
     * Returns the precedence used by Java infix operators.
     *
     * @param operator the infix operator to rank
     * @return a larger number for a tighter-binding operator
     */
    private int precedence(InfixExpression.Operator operator) {
        if (operator == InfixExpression.Operator.CONDITIONAL_OR) {
            return 1;
        }
        if (operator == InfixExpression.Operator.CONDITIONAL_AND) {
            return 2;
        }
        if (operator == InfixExpression.Operator.OR) {
            return 3;
        }
        if (operator == InfixExpression.Operator.XOR) {
            return 4;
        }
        if (operator == InfixExpression.Operator.AND) {
            return 5;
        }
        if (operator == InfixExpression.Operator.EQUALS
                || operator == InfixExpression.Operator.NOT_EQUALS) {
            return 6;
        }
        if (operator == InfixExpression.Operator.LESS
                || operator == InfixExpression.Operator.LESS_EQUALS
                || operator == InfixExpression.Operator.GREATER
                || operator == InfixExpression.Operator.GREATER_EQUALS) {
            return 7;
        }
        if (operator == InfixExpression.Operator.LEFT_SHIFT
                || operator == InfixExpression.Operator.RIGHT_SHIFT_SIGNED
                || operator == InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED) {
            return 8;
        }
        if (operator == InfixExpression.Operator.PLUS
                || operator == InfixExpression.Operator.MINUS) {
            return 9;
        }
        return 10;
    }

    /**
     * Checks whether the supplied operator is safe to regroup syntactically.
     *
     * @param operator the infix operator to inspect
     * @return {@code true} for short-circuit and bitwise associative operators
     */
    private boolean isAssociative(InfixExpression.Operator operator) {
        return operator == InfixExpression.Operator.CONDITIONAL_AND
                || operator == InfixExpression.Operator.CONDITIONAL_OR
                || operator == InfixExpression.Operator.AND
                || operator == InfixExpression.Operator.OR
                || operator == InfixExpression.Operator.XOR;
    }

    /**
     * Checks whether a non-simple expression is safe in its parent AST slot.
     *
     * @param node the parenthesized expression under consideration
     * @param expression the effective child expression
     * @return {@code true} for expression slots where parentheses are optional
     */
    private boolean isSafeComplexContext(
            ParenthesizedExpression node,
            org.eclipse.jdt.core.dom.Expression expression) {
        ASTNode parent = node.getParent();
        Object property = node.getLocationInParent();
        if (parent instanceof Assignment) {
            return property == Assignment.RIGHT_HAND_SIDE_PROPERTY;
        }
        if (parent instanceof ArrayAccess) {
            return property == ArrayAccess.INDEX_PROPERTY;
        }
        if (parent instanceof ArrayCreation
                || parent instanceof ArrayInitializer
                || parent instanceof AssertStatement
                || parent instanceof ClassInstanceCreation
                || parent instanceof ConstructorInvocation
                || parent instanceof DoStatement
                || parent instanceof EnhancedForStatement
                || parent instanceof EnumConstantDeclaration
                || parent instanceof ExpressionStatement
                || parent instanceof ForStatement
                || parent instanceof IfStatement
                || parent instanceof MethodInvocation
                || parent instanceof ReturnStatement
                || parent instanceof SuperConstructorInvocation
                || parent instanceof SuperMethodInvocation
                || parent instanceof SwitchStatement
                || parent instanceof SynchronizedStatement
                || parent instanceof ThrowStatement
                || parent instanceof VariableDeclarationFragment
                || parent instanceof WhileStatement
                || parent instanceof YieldStatement) {
            return isSafeExpressionProperty(parent, property);
        }
        return parent instanceof LambdaExpression
                && property == LambdaExpression.BODY_PROPERTY
                && !(expression instanceof ConditionalExpression)
                && !(expression instanceof Assignment);
    }

    /**
     * Checks whether an AST property accepts an unparenthesized complex expression.
     *
     * @param parent the parent AST node
     * @param property the child property containing the expression
     * @return {@code true} when the property is an expression position
     */
    private boolean isSafeExpressionProperty(ASTNode parent, Object property) {
        return (parent instanceof ArrayCreation && property == ArrayCreation.DIMENSIONS_PROPERTY)
                || (parent instanceof ArrayInitializer && property == ArrayInitializer.EXPRESSIONS_PROPERTY)
                || (parent instanceof AssertStatement
                    && (property == AssertStatement.EXPRESSION_PROPERTY
                        || property == AssertStatement.MESSAGE_PROPERTY))
                || (parent instanceof ClassInstanceCreation
                    && property == ClassInstanceCreation.ARGUMENTS_PROPERTY)
                || (parent instanceof ConstructorInvocation
                    && property == ConstructorInvocation.ARGUMENTS_PROPERTY)
                || (parent instanceof DoStatement && property == DoStatement.EXPRESSION_PROPERTY)
                || (parent instanceof EnhancedForStatement
                    && property == EnhancedForStatement.EXPRESSION_PROPERTY)
                || (parent instanceof EnumConstantDeclaration
                    && property == EnumConstantDeclaration.ARGUMENTS_PROPERTY)
                || (parent instanceof ExpressionStatement
                    && property == ExpressionStatement.EXPRESSION_PROPERTY)
                || (parent instanceof ForStatement && property == ForStatement.EXPRESSION_PROPERTY)
                || (parent instanceof IfStatement && property == IfStatement.EXPRESSION_PROPERTY)
                || (parent instanceof MethodInvocation
                    && property == MethodInvocation.ARGUMENTS_PROPERTY)
                || (parent instanceof ReturnStatement
                    && property == ReturnStatement.EXPRESSION_PROPERTY)
                || (parent instanceof SuperConstructorInvocation
                    && property == SuperConstructorInvocation.ARGUMENTS_PROPERTY)
                || (parent instanceof SuperMethodInvocation
                    && property == SuperMethodInvocation.ARGUMENTS_PROPERTY)
                || (parent instanceof SwitchStatement && property == SwitchStatement.EXPRESSION_PROPERTY)
                || (parent instanceof SynchronizedStatement
                    && property == SynchronizedStatement.EXPRESSION_PROPERTY)
                || (parent instanceof ThrowStatement && property == ThrowStatement.EXPRESSION_PROPERTY)
                || (parent instanceof VariableDeclarationFragment
                    && property == VariableDeclarationFragment.INITIALIZER_PROPERTY)
                || (parent instanceof WhileStatement && property == WhileStatement.EXPRESSION_PROPERTY)
                || (parent instanceof YieldStatement && property == YieldStatement.EXPRESSION_PROPERTY);
    }

    /**
     * Returns the deepest expression in a chain of parenthesized expressions.
     *
     * @param node the outer parenthesized expression
     * @return the effective expression after unwrapping nested parentheses
     */
    private org.eclipse.jdt.core.dom.Expression deepestExpression(ParenthesizedExpression node) {
        org.eclipse.jdt.core.dom.Expression expression = node.getExpression();
        while (expression instanceof ParenthesizedExpression parenthesized) {
            expression = parenthesized.getExpression();
        }
        return expression;
    }

    /**
     * Checks whether a candidate is covered by an outer removable candidate.
     *
     * @param candidate the candidate being filtered
     * @param candidates all collected removable candidates
     * @return {@code true} when an outer candidate will remove this candidate's parentheses
     */
    private boolean hasRemovableAncestor(
            ParenthesizedExpression candidate,
            java.util.List<ParenthesizedExpression> candidates) {
        ASTNode ancestor = candidate.getParent();
        while (ancestor instanceof ParenthesizedExpression parenthesized) {
            if (candidates.contains(parenthesized) && canRemove(parenthesized)) {
                return true;
            }
            ancestor = ancestor.getParent();
        }
        return false;
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
                    && property == VariableDeclarationFragment.INITIALIZER_PROPERTY)
                || (parent instanceof ExpressionStatement
                    && property == ExpressionStatement.EXPRESSION_PROPERTY)
                || (parent instanceof InstanceofExpression
                    && property == InstanceofExpression.LEFT_OPERAND_PROPERTY);
    }
}
