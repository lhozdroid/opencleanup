package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Joins an else block containing one if statement into an else-if statement.
 */
public final class ElseIfRule implements CleanupRule {

    public static final String ID = "control-statements.else-if";

    /**
     * Returns the stable identifier for else-if cleanup.
     *
     * @return the else-if rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records edits that remove braces around a sole nested if statement.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one else block is flattened
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Flattens an else block containing only a nested if statement.
             *
             * @param node the visited if statement
             * @return {@code true} to continue visiting nested statements
             */
            @Override
            public boolean visit(IfStatement node) {
                Statement elseStatement = node.getElseStatement();
                if (elseStatement instanceof Block block && block.statements().size() == 1
                        && block.statements().get(0) instanceof IfStatement nestedIf) {
                    rewrite.set(
                            node,
                            IfStatement.ELSE_STATEMENT_PROPERTY,
                            rewrite.createMoveTarget(nestedIf),
                            null);
                    changed[0] = true;
                }
                return true;
            }
        });
        return changed[0];
    }
}
