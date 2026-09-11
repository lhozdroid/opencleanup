package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Removes an explicit no-argument superclass constructor invocation from a constructor.
 */
public final class RedundantSuperCallRule implements CleanupRule {

    public static final String ID = "constructors.redundant-super";

    /**
     * Returns the stable identifier for redundant-super cleanup.
     *
     * @return the redundant-super rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records removal edits for explicit no-argument superclass constructor calls.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one superclass call is scheduled for removal
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Removes a redundant first statement from a constructor body.
             *
             * @param node the visited method declaration
             * @return {@code true} to continue visiting nested declarations
             */
            @Override
            public boolean visit(MethodDeclaration node) {
                if (!node.isConstructor() || node.getBody() == null || node.getBody().statements().isEmpty()) {
                    return true;
                }

                Statement firstStatement = (Statement) node.getBody().statements().get(0);
                if (firstStatement instanceof SuperConstructorInvocation superCall
                        && superCall.getExpression() == null
                        && superCall.arguments().isEmpty()) {
                    rewrite.remove(superCall, null);
                    changed[0] = true;
                }
                return true;
            }
        });
        return changed[0];
    }
}
