package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Adds blocks around control-statement bodies that do not already have them.
 */
public final class ControlStatementBlocksRule implements CleanupRule {

    public static final String ID = "control-statements.blocks";

    /**
     * Returns the stable identifier for control-statement block cleanup.
     *
     * @return the control-statement block rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records edits that add blocks around eligible control-statement bodies.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one block is scheduled for insertion
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Adds blocks around the then and else branches of an if statement.
             *
             * @param node the visited if statement
             * @return {@code true} to continue visiting nested statements
             */
            @Override
            public boolean visit(IfStatement node) {
                changed[0] |= addBlock(
                        node,
                        IfStatement.THEN_STATEMENT_PROPERTY,
                        node.getThenStatement(),
                        rewrite);
                changed[0] |= addBlock(
                        node,
                        IfStatement.ELSE_STATEMENT_PROPERTY,
                        node.getElseStatement(),
                        rewrite);
                return true;
            }

            /**
             * Adds a block around a while-loop body when needed.
             *
             * @param node the visited while statement
             * @return {@code true} to continue visiting nested statements
             */
            @Override
            public boolean visit(WhileStatement node) {
                changed[0] |= addBlock(
                        node,
                        WhileStatement.BODY_PROPERTY,
                        node.getBody(),
                        rewrite);
                return true;
            }

            /**
             * Adds a block around a do-while-loop body when needed.
             *
             * @param node the visited do-while statement
             * @return {@code true} to continue visiting nested statements
             */
            @Override
            public boolean visit(DoStatement node) {
                changed[0] |= addBlock(
                        node,
                        DoStatement.BODY_PROPERTY,
                        node.getBody(),
                        rewrite);
                return true;
            }

            /**
             * Adds a block around a classic for-loop body when needed.
             *
             * @param node the visited for statement
             * @return {@code true} to continue visiting nested statements
             */
            @Override
            public boolean visit(ForStatement node) {
                changed[0] |= addBlock(
                        node,
                        ForStatement.BODY_PROPERTY,
                        node.getBody(),
                        rewrite);
                return true;
            }

            /**
             * Adds a block around an enhanced for-loop body when needed.
             *
             * @param node the visited enhanced for statement
             * @return {@code true} to continue visiting nested statements
             */
            @Override
            public boolean visit(EnhancedForStatement node) {
                changed[0] |= addBlock(
                        node,
                        EnhancedForStatement.BODY_PROPERTY,
                        node.getBody(),
                        rewrite);
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Wraps a non-block statement in a new block while moving the original node into it.
     *
     * @param parent the control statement owning the body property
     * @param property the body property to replace
     * @param body the current control-statement body
     * @param rewrite the rewrite collecting source edits
     * @return {@code true} when a block replacement is scheduled
     */
    private boolean addBlock(
            ASTNode parent,
            StructuralPropertyDescriptor property,
            Statement body,
            ASTRewrite rewrite) {
        if (body == null || body instanceof Block) {
            return false;
        }

        Block block = parent.getAST().newBlock();
        ListRewrite blockStatements = rewrite.getListRewrite(block, Block.STATEMENTS_PROPERTY);
        blockStatements.insertLast(rewrite.createMoveTarget(body), null);
        rewrite.set(parent, property, block, null);
        return true;
    }
}
