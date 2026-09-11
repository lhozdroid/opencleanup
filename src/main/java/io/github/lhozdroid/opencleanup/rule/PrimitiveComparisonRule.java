package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Replaces comparisons between explicit primitive wrapper factories with primitive operations.
 */
public final class PrimitiveComparisonRule implements CleanupRule {

    /** The stable identifier used to select this cleanup rule. */
    public static final String ID = "boxing.primitive-comparison";

    /**
     * Returns the stable identifier for primitive comparison cleanup.
     *
     * @return the primitive comparison rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for exact wrapper equality and ordering comparisons.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one comparison is replaced
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Replaces one explicit wrapper comparison.
             *
             * @param node the visited method invocation
             * @return {@code true} to continue visiting nested expressions
             */
            @Override
            public boolean visit(MethodInvocation node) {
                Expression replacement = replacement(node);
                if (replacement != null) {
                    rewrite.replace(node, replacement, null);
                    changed[0] = true;
                    return false;
                }
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Builds a primitive comparison for a supported wrapper invocation.
     *
     * @param node the method invocation to inspect
     * @return the replacement expression, or {@code null} when unsupported
     */
    private Expression replacement(MethodInvocation node) {
        if (node.arguments().size() != 1
                || !(node.getExpression() instanceof MethodInvocation leftFactory)
                || !(node.arguments().get(0) instanceof MethodInvocation rightFactory)) {
            return null;
        }
        Expression left = factoryArgument(leftFactory);
        Expression right = factoryArgument(rightFactory);
        String wrapper = factoryType(leftFactory);
        if (left == null || right == null || wrapper == null
                || !wrapper.equals(factoryType(rightFactory))) {
            return null;
        }
        AST ast = node.getAST();
        if ("equals".equals(node.getName().getIdentifier())) {
            InfixExpression equality = ast.newInfixExpression();
            equality.setLeftOperand((Expression) ASTNode.copySubtree(ast, left));
            equality.setOperator(InfixExpression.Operator.EQUALS);
            equality.setRightOperand((Expression) ASTNode.copySubtree(ast, right));
            return equality;
        }
        if ("compareTo".equals(node.getName().getIdentifier())) {
            MethodInvocation comparison = ast.newMethodInvocation();
            comparison.setExpression(ast.newSimpleName(wrapper));
            comparison.setName(ast.newSimpleName("compare"));
            comparison.arguments().add(ASTNode.copySubtree(ast, left));
            comparison.arguments().add(ASTNode.copySubtree(ast, right));
            return comparison;
        }
        return null;
    }

    /**
     * Returns the argument of an exact simple-name wrapper value factory.
     *
     * @param invocation the candidate factory invocation
     * @return the factory argument, or {@code null} when the shape is unsupported
     */
    private Expression factoryArgument(MethodInvocation invocation) {
        if (!(invocation.getExpression() instanceof SimpleName)
                || !"valueOf".equals(invocation.getName().getIdentifier())
                || invocation.arguments().size() != 1) {
            return null;
        }
        return (Expression) invocation.arguments().get(0);
    }

    /**
     * Returns the simple wrapper name of an exact value factory.
     *
     * @param invocation the candidate factory invocation
     * @return the wrapper name, or {@code null} when the invocation is unsupported
     */
    private String factoryType(MethodInvocation invocation) {
        if (!(invocation.getExpression() instanceof SimpleName receiver)
                || !"valueOf".equals(invocation.getName().getIdentifier())) {
            return null;
        }
        return switch (receiver.getIdentifier()) {
            case "Boolean", "Byte", "Short", "Integer", "Long", "Float", "Double", "Character" ->
                    receiver.getIdentifier();
            default -> null;
        };
    }
}
