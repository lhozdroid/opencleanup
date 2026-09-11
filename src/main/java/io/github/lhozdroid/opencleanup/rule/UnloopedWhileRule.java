package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Converts a while loop that always exits during its first iteration into an if statement.
 */
public final class UnloopedWhileRule implements CleanupRule {

    /** The stable identifier for unlooped while cleanup. */
    public static final String ID = "loops.unlooped-while";

    /**
     * Returns the stable identifier for this rule.
     *
     * @return the unlooped while rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for while loops whose top-level final statement is an unconditional break.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one while loop becomes an if statement
     */
    @Override
    public boolean apply(CompilationUnit compilationUnit, ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Converts one eligible while statement into an if statement.
             *
             * @param node the visited while statement
             * @return {@code false} after replacement, otherwise {@code true}
             */
            @Override
            public boolean visit(WhileStatement node) {
                if (!eligible(node)) {
                    return true;
                }
                AST ast = node.getAST();
                IfStatement replacement = ast.newIfStatement();
                replacement.setExpression((Expression) ASTNode.copySubtree(ast, node.getExpression()));
                Block body = (Block) ASTNode.copySubtree(ast, node.getBody());
                body.statements().remove(body.statements().size() - 1);
                replacement.setThenStatement(body);
                rewrite.replace(node, replacement, null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Checks the conservative shape required for a one-iteration loop conversion.
     *
     * @param node the while statement to inspect
     * @return {@code true} when the body is a block ending in an unlabeled break
     */
    private boolean eligible(WhileStatement node) {
        if (!(node.getBody() instanceof Block block) || block.statements().isEmpty()) {
            return false;
        }
        Statement last = (Statement) block.statements().get(block.statements().size() - 1);
        if (!(last instanceof BreakStatement breakStatement) || breakStatement.getLabel() != null) {
            return false;
        }
        for (Object value : block.statements()) {
            if (value instanceof org.eclipse.jdt.core.dom.ContinueStatement) {
                return false;
            }
        }
        return true;
    }
}
