package io.github.lhozdroid.opencleanup.rule;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Replaces literal-delimited string concatenation with {@code String.join}.
 */
public final class StringsJoinRule implements CleanupRule {

    /** The stable identifier for string joining cleanup. */
    public static final String ID = "strings.join";

    /**
     * Returns the stable identifier for string joining cleanup.
     *
     * @return the string-join rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for concatenations with one repeated literal delimiter.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one concatenation is converted
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Replaces one eligible string concatenation.
             *
             * @param node the visited infix expression
             * @return {@code false} after replacement, otherwise {@code true}
             */
            @Override
            public boolean visit(InfixExpression node) {
                if (node.getOperator() != InfixExpression.Operator.PLUS
                        || node.extendedOperands().isEmpty()
                        || hasPlusAncestor(node)) {
                    return true;
                }
                JoinShape shape = JoinShape.from(node);
                if (shape == null) {
                    return true;
                }
                rewrite.replace(node, shape.toInvocation(node.getAST()), null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Checks whether an infix expression is nested below another concatenation.
     *
     * @param node the candidate concatenation
     * @return {@code true} when an enclosing plus expression owns the flattened operation
     */
    private boolean hasPlusAncestor(InfixExpression node) {
        ASTNode parent = node.getParent();
        return parent instanceof InfixExpression infix
                && infix.getOperator() == InfixExpression.Operator.PLUS;
    }

    /**
     * Describes a delimiter-based concatenation.
     *
     * @param delimiter the literal delimiter
     * @param values the concatenated values
     */
    private record JoinShape(StringLiteral delimiter, List<Expression> values) {

        /**
         * Extracts alternating values and identical string delimiters.
         *
         * @param node the concatenation to inspect
         * @return the join shape, or {@code null} when the syntax is unsupported
         */
        private static JoinShape from(InfixExpression node) {
            List<Expression> operands = new ArrayList<>();
            operands.add(node.getLeftOperand());
            operands.add(node.getRightOperand());
            operands.addAll(node.extendedOperands());
            if (operands.size() < 3 || operands.size() % 2 == 0) {
                return null;
            }

            if (!(operands.get(1) instanceof StringLiteral delimiter)) {
                return null;
            }
            List<Expression> values = new ArrayList<>();
            values.add(operands.get(0));
            for (int index = 1; index < operands.size(); index += 2) {
                if (!(operands.get(index) instanceof StringLiteral current)
                        || !delimiter.getEscapedValue().equals(current.getEscapedValue())) {
                    return null;
                }
                values.add(operands.get(index + 1));
            }
            return new JoinShape(delimiter, values);
        }

        /**
         * Creates a fully qualified String.join invocation.
         *
         * @param ast the AST receiving the invocation
         * @return the generated join invocation
         */
        private MethodInvocation toInvocation(AST ast) {
            MethodInvocation invocation = ast.newMethodInvocation();
            invocation.setExpression(ast.newSimpleName("String"));
            invocation.setName(ast.newSimpleName("join"));
            invocation.arguments().add(ASTNode.copySubtree(ast, delimiter));
            for (Expression value : values) {
                invocation.arguments().add(ASTNode.copySubtree(ast, value));
            }
            return invocation;
        }
    }
}
