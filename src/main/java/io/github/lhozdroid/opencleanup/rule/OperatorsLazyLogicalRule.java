package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Replaces eager boolean conjunction and disjunction with short-circuit operators when safe.
 */
public final class OperatorsLazyLogicalRule implements CleanupRule {

    /** The stable identifier for lazy logical operator cleanup. */
    public static final String ID = "operators.lazy-logical";

    /**
     * Returns the stable identifier for this cleanup rule.
     *
     * @return the lazy-logical rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records safe replacements for eager boolean operators.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one operator is changed
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Replaces one eager boolean operator when both sides are pure boolean expressions.
             *
             * @param node the visited infix expression
             * @return {@code true} to continue visiting nested expressions
             */
            @Override
            public boolean visit(InfixExpression node) {
                InfixExpression.Operator replacement = switch (node.getOperator().toString()) {
                    case "&" -> InfixExpression.Operator.CONDITIONAL_AND;
                    case "|" -> InfixExpression.Operator.CONDITIONAL_OR;
                    default -> null;
                };
                if (replacement != null
                        && node.extendedOperands().isEmpty()
                        && isBooleanExpression(node.getLeftOperand())
                        && isBooleanExpression(node.getRightOperand())
                        && isPure(node.getRightOperand())) {
                    rewrite.set(node, InfixExpression.OPERATOR_PROPERTY, replacement, null);
                    changed[0] = true;
                }
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Checks whether an expression is syntactically known to have boolean type.
     *
     * @param expression the expression to inspect
     * @return {@code true} when its syntax establishes boolean type
     */
    private boolean isBooleanExpression(Expression expression) {
        if (expression instanceof BooleanLiteral || expression instanceof PrefixExpression prefix
                && prefix.getOperator() == PrefixExpression.Operator.NOT) {
            return true;
        }
        if (expression instanceof InfixExpression infix) {
            return switch (infix.getOperator().toString()) {
                case "==", "!=", "<", "<=", ">", ">=", "&&", "||", "&", "|", "^" -> true;
                default -> false;
            };
        }
        return expression instanceof org.eclipse.jdt.core.dom.ParenthesizedExpression parenthesized
                && isBooleanExpression(parenthesized.getExpression());
    }

    /**
     * Checks whether an expression contains no obvious side effects.
     *
     * @param expression the expression to inspect
     * @return {@code true} when evaluating it is syntactically passive
     */
    private boolean isPure(Expression expression) {
        final boolean[] pure = {true};
        expression.accept(new ASTVisitor() {
            /**
             * Rejects method calls because their evaluation can be observable.
             *
             * @param node the visited method invocation
             * @return {@code false} to stop at the side effect
             */
            @Override
            public boolean visit(MethodInvocation node) {
                pure[0] = false;
                return false;
            }

            /**
             * Rejects object creation because it can be observable.
             *
             * @param node the visited class creation
             * @return {@code false} to stop at the side effect
             */
            @Override
            public boolean visit(ClassInstanceCreation node) {
                pure[0] = false;
                return false;
            }

            /**
             * Rejects assignments because they must still be evaluated.
             *
             * @param node the visited assignment
             * @return {@code false} to stop at the side effect
             */
            @Override
            public boolean visit(Assignment node) {
                pure[0] = false;
                return false;
            }

            /**
             * Rejects postfix updates because they have side effects.
             *
             * @param node the visited postfix expression
             * @return {@code false} to stop at the side effect
             */
            @Override
            public boolean visit(PostfixExpression node) {
                pure[0] = false;
                return false;
            }

            /**
             * Rejects prefix updates while allowing logical negation.
             *
             * @param node the visited prefix expression
             * @return {@code true} for logical negation, otherwise {@code false}
             */
            @Override
            public boolean visit(PrefixExpression node) {
                if (node.getOperator() == PrefixExpression.Operator.INCREMENT
                        || node.getOperator() == PrefixExpression.Operator.DECREMENT) {
                    pure[0] = false;
                    return false;
                }
                return true;
            }
        });
        return pure[0];
    }
}
