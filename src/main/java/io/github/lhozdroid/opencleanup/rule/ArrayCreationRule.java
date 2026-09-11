package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Removes an explicit array construction from a varargs-style {@code Arrays.asList} call.
 */
public final class ArrayCreationRule implements CleanupRule {

    /** The stable identifier for unnecessary array creation cleanup. */
    public static final String ID = "arrays.creation";

    /**
     * Returns the stable identifier for this rule.
     *
     * @return the array creation rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacement of explicit array arguments with their initializer expressions.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one array creation is removed
     */
    @Override
    public boolean apply(CompilationUnit compilationUnit, ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Removes one explicit array from a supported varargs invocation.
             *
             * @param node the visited method invocation
             * @return {@code false} after a rewrite, otherwise {@code true}
             */
            @Override
            public boolean visit(MethodInvocation node) {
                if (!isArraysAsList(node) || node.arguments().size() != 1
                        || !(node.arguments().get(0) instanceof ArrayCreation creation)
                        || creation.getInitializer() == null || !creation.dimensions().isEmpty()) {
                    return true;
                }
                ArrayInitializer initializer = creation.getInitializer();
                ListRewrite arguments = rewrite.getListRewrite(node, MethodInvocation.ARGUMENTS_PROPERTY);
                arguments.remove((ASTNode) node.arguments().get(0), null);
                for (Object value : initializer.expressions()) {
                    arguments.insertLast(rewrite.createMoveTarget((Expression) value), null);
                }
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Checks whether an invocation is the standard {@code Arrays.asList} varargs method.
     *
     * @param node the invocation to inspect
     * @return {@code true} for an unqualified Arrays.asList call
     */
    private boolean isArraysAsList(MethodInvocation node) {
        return node.getExpression() != null
                && (node.getExpression().toString().equals("Arrays")
                || node.getExpression().toString().endsWith(".Arrays"))
                && node.getName().getIdentifier().equals("asList");
    }
}
