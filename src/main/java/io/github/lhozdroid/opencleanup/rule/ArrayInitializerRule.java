package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Replaces eligible local array creation initializers with array initializers.
 *
 * <p>For example, {@code int[] values = new int[] {1, 2};} becomes
 * {@code int[] values = {1, 2};}. Only local variable declarations with an
 * un-dimensioned array creation and an explicit array initializer are changed.
 */
public final class ArrayInitializerRule implements CleanupRule {

    public static final String ID = "arrays.initializer";

    /**
     * Returns the stable identifier for array initializer cleanup.
     *
     * @return the array initializer rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records edits for eligible local array variable declarations.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one array creation is simplified
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Replaces eligible initializers in one local variable declaration statement.
             *
             * @param node the visited local variable declaration statement
             * @return {@code true} to continue visiting nested nodes
             */
            @Override
            public boolean visit(VariableDeclarationStatement node) {
                if (!(node.getType() instanceof ArrayType)) {
                    return true;
                }

                for (Object value : node.fragments()) {
                    VariableDeclarationFragment fragment = (VariableDeclarationFragment) value;
                    if (replaceInitializer(fragment, rewrite)) {
                        changed[0] = true;
                    }
                }
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Replaces one eligible array creation with its array initializer.
     *
     * @param fragment the local variable declaration fragment
     * @param rewrite the rewrite collecting source edits
     * @return {@code true} when the fragment initializer is eligible and replaced
     */
    private boolean replaceInitializer(VariableDeclarationFragment fragment, ASTRewrite rewrite) {
        if (!(fragment.getInitializer() instanceof ArrayCreation arrayCreation)
                || arrayCreation.getInitializer() == null
                || !arrayCreation.dimensions().isEmpty()) {
            return false;
        }

        rewrite.replace(
                arrayCreation,
                rewrite.createMoveTarget(arrayCreation.getInitializer()),
                null);
        return true;
    }
}
