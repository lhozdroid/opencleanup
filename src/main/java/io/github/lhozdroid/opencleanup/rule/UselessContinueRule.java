package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Removes a final unlabeled continue from while and do-while loop bodies.
 */
public final class UselessContinueRule implements CleanupRule {

    public static final String ID = "continues.useless";

    /**
     * Returns the stable identifier for useless-continue cleanup.
     *
     * @return the useless-continue rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records removal edits for final unlabeled continues whose loop behavior is unchanged.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one continue is scheduled for removal
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Removes a final continue from a while loop body when eligible.
             *
             * @param node the visited while statement
             * @return {@code true} to continue visiting nested statements
             */
            @Override
            public boolean visit(WhileStatement node) {
                changed[0] |= removeFinalContinue(node.getBody(), rewrite);
                return true;
            }

            /**
             * Removes a final continue from a do-while loop body when eligible.
             *
             * @param node the visited do-while statement
             * @return {@code true} to continue visiting nested statements
             */
            @Override
            public boolean visit(DoStatement node) {
                changed[0] |= removeFinalContinue(node.getBody(), rewrite);
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Removes a final unlabeled continue from a block body.
     *
     * @param body the loop body to inspect
     * @param rewrite the rewrite collecting source edits
     * @return {@code true} when a continue is scheduled for removal
     */
    private boolean removeFinalContinue(Statement body, ASTRewrite rewrite) {
        if (!(body instanceof Block block) || block.statements().isEmpty()) {
            return false;
        }

        Statement lastStatement = (Statement) block.statements().get(block.statements().size() - 1);
        if (!(lastStatement instanceof ContinueStatement continueStatement)
                || continueStatement.getLabel() != null) {
            return false;
        }

        rewrite.remove(continueStatement, null);
        return true;
    }
}
