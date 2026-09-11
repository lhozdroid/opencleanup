package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Removes a redundant length argument from a simple substring invocation.
 */
public final class RedundantSubstringArgumentRule implements CleanupRule {

    /** The stable identifier used to select redundant substring-argument cleanup. */
    public static final String ID = "strings.redundant-substring-argument";

    /**
     * Returns the stable identifier for redundant substring-argument cleanup.
     *
     * @return the redundant substring-argument rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records removal edits for eligible simple-name substring invocations.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one argument is scheduled for removal
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Removes the redundant end argument from an eligible substring invocation.
             *
             * @param node the visited method invocation
             * @return {@code true} to continue visiting nested expressions
             */
            @Override
            public boolean visit(MethodInvocation node) {
                if (isEligible(node)) {
                    ListRewrite arguments = rewrite.getListRewrite(
                            node,
                            MethodInvocation.ARGUMENTS_PROPERTY);
                    arguments.remove((ASTNode) node.arguments().get(1), null);
                    changed[0] = true;
                }
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Checks whether a method invocation has the supported redundant substring shape.
     *
     * @param node the method invocation to inspect
     * @return {@code true} when the invocation can safely lose its end argument
     */
    private boolean isEligible(MethodInvocation node) {
        if (!"substring".equals(node.getName().getIdentifier())
                || !(node.getExpression() instanceof SimpleName receiver)
                || node.arguments().size() != 2
                || !(node.arguments().get(0) instanceof NumberLiteral start)
                || !"0".equals(start.getToken())) {
            return false;
        }

        if (!(node.arguments().get(1) instanceof MethodInvocation end)) {
            return false;
        }

        return "length".equals(end.getName().getIdentifier())
                && end.arguments().isEmpty()
                && end.getExpression() instanceof SimpleName lengthReceiver
                && receiver.getIdentifier().equals(lengthReceiver.getIdentifier());
    }
}
