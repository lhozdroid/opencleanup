package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EmptyStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Removes standalone empty statements from block statement lists.
 *
 * <p>Empty statements used as control-flow bodies or label bodies are retained because
 * removing them would leave those constructs without a required statement.</p>
 */
public final class RedundantSemicolonRule implements CleanupRule {

    /** The stable identifier used to select redundant-semicolon cleanup. */
    public static final String ID = "semicolons.redundant";

    /**
     * Returns the stable identifier for redundant-semicolon cleanup.
     *
     * @return the redundant-semicolon rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records removal edits for standalone empty statements in block bodies.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one empty statement is scheduled for removal
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Removes an empty statement that is a direct member of a block.
             *
             * @param node the visited empty statement
             * @return {@code true} to continue visiting sibling source nodes
             */
            @Override
            public boolean visit(EmptyStatement node) {
                if (!(node.getParent() instanceof Block)) {
                    return true;
                }

                rewrite.remove(node, null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }
}
