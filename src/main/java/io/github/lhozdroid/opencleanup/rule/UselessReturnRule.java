package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Removes a top-level empty return that is the final statement of a void method.
 */
public final class UselessReturnRule implements CleanupRule {

    public static final String ID = "returns.useless";

    /**
     * Returns the stable identifier for useless-return cleanup.
     *
     * @return the useless-return rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records removal edits for eligible final return statements.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one return is scheduled for removal
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Removes the final empty return from a void method body.
             *
             * @param node the visited method declaration
             * @return {@code true} to continue visiting nested declarations
             */
            @Override
            public boolean visit(MethodDeclaration node) {
                if (node.getReturnType2() == null || !"void".equals(node.getReturnType2().toString())) {
                    return true;
                }

                Block body = node.getBody();
                if (body == null || body.statements().isEmpty()) {
                    return true;
                }

                Object lastStatement = body.statements().get(body.statements().size() - 1);
                if (lastStatement instanceof ReturnStatement returnStatement
                        && returnStatement.getExpression() == null) {
                    rewrite.remove(returnStatement, null);
                    changed[0] = true;
                }
                return true;
            }
        });
        return changed[0];
    }
}
