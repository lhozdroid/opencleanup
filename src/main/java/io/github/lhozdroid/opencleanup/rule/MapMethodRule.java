package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Replaces operations on a map view with the corresponding direct map operation.
 *
 * <p>The rewrite is intentionally syntax based. It handles the side-effect-free receiver forms
 * commonly produced by source code cleanup and does not require bindings.</p>
 */
public final class MapMethodRule implements CleanupRule {

    /** The stable identifier for direct map operations. */
    public static final String ID = "collections.direct-map-method";

    /**
     * Returns the stable identifier for this rule.
     *
     * @return the direct map method rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for map key-set and value-view operations.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one view operation is replaced
     */
    @Override
    public boolean apply(CompilationUnit compilationUnit, ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Replaces one operation on a map view with a direct map operation.
             *
             * @param node the visited method invocation
             * @return {@code true} to continue visiting nested expressions
             */
            @Override
            public boolean visit(MethodInvocation node) {
                MethodInvocation view = viewInvocation(node.getExpression());
                if (view == null || view.getExpression() instanceof ThisExpression
                        || view.getExpression() == null || !supported(view, node)) {
                    return true;
                }
                AST ast = node.getAST();
                MethodInvocation replacement = ast.newMethodInvocation();
                replacement.setExpression((Expression) ASTNode.copySubtree(ast, view.getExpression()));
                replacement.setName(ast.newSimpleName(directName(view, node)));
                for (Object argument : node.arguments()) {
                    replacement.arguments().add(ASTNode.copySubtree(ast, (ASTNode) argument));
                }
                rewrite.replace(node, replacement, null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Returns the map view invocation when an expression is a key-set or values view.
     *
     * @param expression the expression to inspect
     * @return the view invocation, or {@code null} when the expression is not supported
     */
    private MethodInvocation viewInvocation(Expression expression) {
        if (!(expression instanceof MethodInvocation invocation)
                || invocation.arguments().size() != 0
                || !(invocation.getName().getIdentifier().equals("keySet")
                || invocation.getName().getIdentifier().equals("values"))) {
            return null;
        }
        return invocation;
    }

    /**
     * Checks whether an operation has a direct map equivalent.
     *
     * @param name the view operation name
     * @return {@code true} when the operation can be moved to the map
     */
    private boolean supported(MethodInvocation view, MethodInvocation operation) {
        String name = operation.getName().getIdentifier();
        if (name.equals("remove")) {
            return view.getName().getIdentifier().equals("keySet")
                    && operation.getParent().getNodeType() == ASTNode.EXPRESSION_STATEMENT;
        }
        return name.equals("clear") || name.equals("size") || name.equals("isEmpty")
                || name.equals("contains");
    }

    /**
     * Maps a view operation to its direct map method name.
     *
     * @param view the key-set or values view
     * @param operation the operation invoked on the view
     * @return the direct map method name
     */
    private String directName(MethodInvocation view, MethodInvocation operation) {
        String viewName = view.getName().getIdentifier();
        String operationName = operation.getName().getIdentifier();
        if (operationName.equals("contains")) {
            return viewName.equals("keySet") ? "containsKey" : "containsValue";
        }
        return operationName;
    }
}
