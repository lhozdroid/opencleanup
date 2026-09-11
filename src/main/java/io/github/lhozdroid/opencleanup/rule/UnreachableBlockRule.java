package io.github.lhozdroid.opencleanup.rule;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Removes statements after a guaranteed terminating statement in a block.
 */
public final class UnreachableBlockRule implements CleanupRule {

    /** The stable identifier for unreachable-block cleanup. */
    public static final String ID = "blocks.unreachable";

    /**
     * Returns the stable identifier for this rule.
     *
     * @return the rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records removals for direct statements that follow a terminating statement.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source changes
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one unreachable statement is removed
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Removes the suffix after the first terminating block statement.
             *
             * @param node the visited block
             * @return {@code true} to continue visiting nested blocks
             */
            @Override
            public boolean visit(Block node) {
                List<?> statements = node.statements();
                int terminalIndex = -1;
                for (int index = 0; index < statements.size(); index++) {
                    if (alwaysCompletes((Statement) statements.get(index))) {
                        terminalIndex = index;
                        break;
                    }
                }
                if (terminalIndex < 0 || terminalIndex == statements.size() - 1) {
                    return true;
                }
                List<Statement> unreachable = new ArrayList<>();
                for (int index = terminalIndex + 1; index < statements.size(); index++) {
                    unreachable.add((Statement) statements.get(index));
                }
                ListRewrite listRewrite = rewrite.getListRewrite(node, Block.STATEMENTS_PROPERTY);
                unreachable.forEach(statement -> listRewrite.remove(statement, null));
                changed[0] = true;
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Determines whether a statement always transfers control away from its block.
     *
     * @param statement the statement to inspect
     * @return {@code true} when normal completion cannot reach the next statement
     */
    private boolean alwaysCompletes(Statement statement) {
        if (statement instanceof ReturnStatement
                || statement instanceof ThrowStatement
                || statement instanceof BreakStatement
                || statement instanceof ContinueStatement) {
            return true;
        }
        if (statement instanceof Block block && !block.statements().isEmpty()) {
            return alwaysCompletes((Statement) block.statements().get(block.statements().size() - 1));
        }
        if (statement instanceof IfStatement ifStatement) {
            return ifStatement.getElseStatement() != null
                    && alwaysCompletes(ifStatement.getThenStatement())
                    && alwaysCompletes(ifStatement.getElseStatement());
        }
        return false;
    }
}
