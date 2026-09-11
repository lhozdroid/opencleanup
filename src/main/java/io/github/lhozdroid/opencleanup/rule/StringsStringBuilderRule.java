package io.github.lhozdroid.opencleanup.rule;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Rewrites eligible string concatenations as fluent {@code StringBuilder} expressions.
 */
public final class StringsStringBuilderRule implements CleanupRule {

    /** The stable identifier for StringBuilder conversion. */
    public static final String ID = "strings.string-builder";

    /**
     * Returns the stable identifier for this cleanup rule.
     *
     * @return the StringBuilder rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for string concatenations containing a string literal.
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
             * Replaces one outermost string concatenation.
             *
             * @param node the visited plus expression
             * @return {@code false} after replacement, otherwise {@code true}
             */
            @Override
            public boolean visit(InfixExpression node) {
                if (node.getOperator() != InfixExpression.Operator.PLUS
                        || hasPlusAncestor(node)) {
                    return true;
                }
                List<Expression> operands = operands(node);
                if (operands.size() < 2 || operands.stream().noneMatch(StringLiteral.class::isInstance)) {
                    return true;
                }
                rewrite.replace(node, toBuilder(node.getAST(), operands), null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Checks whether a concatenation is nested below another plus expression.
     *
     * @param node the candidate concatenation
     * @return {@code true} when an enclosing plus expression owns the operation
     */
    private boolean hasPlusAncestor(InfixExpression node) {
        return node.getParent() instanceof InfixExpression parent
                && parent.getOperator() == InfixExpression.Operator.PLUS;
    }

    /**
     * Flattens the operands of one plus expression.
     *
     * @param node the plus expression
     * @return its operands in evaluation order
     */
    private List<Expression> operands(InfixExpression node) {
        List<Expression> result = new ArrayList<>();
        result.add(node.getLeftOperand());
        result.add(node.getRightOperand());
        result.addAll(node.extendedOperands());
        return result;
    }

    /**
     * Builds a StringBuilder append chain ending in toString.
     *
     * @param ast the owning AST
     * @param operands the concatenation operands
     * @return the generated builder expression
     */
    private Expression toBuilder(AST ast, List<Expression> operands) {
        ClassInstanceCreation creation = ast.newClassInstanceCreation();
        creation.setType(ast.newSimpleType(ast.newSimpleName("StringBuilder")));
        Expression current = creation;
        for (Expression operand : operands) {
            MethodInvocation append = ast.newMethodInvocation();
            append.setExpression(current);
            append.setName(ast.newSimpleName("append"));
            append.arguments().add(ASTNode.copySubtree(ast, operand));
            current = append;
        }
        MethodInvocation result = ast.newMethodInvocation();
        result.setExpression(current);
        result.setName(ast.newSimpleName("toString"));
        return result;
    }
}
