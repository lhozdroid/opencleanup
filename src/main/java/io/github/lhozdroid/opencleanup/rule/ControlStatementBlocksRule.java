package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Comment;
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

    private static final String ALWAYS = "always";
    private static final String NEVER = "never";
    private static final String JDT_STYLE = "jdt-style";

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
        String mode = configuredMode(configuration);
        if (mode == null) {
            return false;
        }

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
                changed[0] |= rewriteBody(
                        compilationUnit,
                        node,
                        IfStatement.THEN_STATEMENT_PROPERTY,
                        node.getThenStatement(),
                        mode,
                        rewrite);
                changed[0] |= rewriteBody(
                        compilationUnit,
                        node,
                        IfStatement.ELSE_STATEMENT_PROPERTY,
                        node.getElseStatement(),
                        mode,
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
                changed[0] |= rewriteBody(
                        compilationUnit,
                        node,
                        WhileStatement.BODY_PROPERTY,
                        node.getBody(),
                        mode,
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
                changed[0] |= rewriteBody(
                        compilationUnit,
                        node,
                        DoStatement.BODY_PROPERTY,
                        node.getBody(),
                        mode,
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
                changed[0] |= rewriteBody(
                        compilationUnit,
                        node,
                        ForStatement.BODY_PROPERTY,
                        node.getBody(),
                        mode,
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
                changed[0] |= rewriteBody(
                        compilationUnit,
                        node,
                        EnhancedForStatement.BODY_PROPERTY,
                        node.getBody(),
                        mode,
                        rewrite);
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Resolves the configured block mode, defaulting to the historical add-block behavior.
     *
     * @param configuration the Maven configuration for this rule
     * @return the normalized supported mode, or {@code null} for an unsupported mode
     */
    private String configuredMode(RuleConfiguration configuration) {
        if (configuration == null) {
            return ALWAYS;
        }

        String configuredMode = configuration.optionValue(ID);
        if (configuredMode == null || configuredMode.isBlank()) {
            return ALWAYS;
        }

        String normalizedMode = configuredMode.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalizedMode) {
            case ALWAYS, NEVER, JDT_STYLE -> normalizedMode;
            default -> null;
        };
    }

    /**
     * Adds or removes a block according to the selected block mode.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param parent the control statement owning the body property
     * @param property the body property to replace
     * @param body the current control-statement body
     * @param mode the normalized block mode
     * @param rewrite the rewrite collecting source edits
     * @return {@code true} when a block edit is scheduled
     */
    private boolean rewriteBody(
            CompilationUnit compilationUnit,
            ASTNode parent,
            StructuralPropertyDescriptor property,
            Statement body,
            String mode,
            ASTRewrite rewrite) {
        if (NEVER.equals(mode)) {
            return removeBlock(compilationUnit, parent, property, body, rewrite);
        }
        if (JDT_STYLE.equals(mode) && !isMultiLine(compilationUnit, body)) {
            return false;
        }
        return addBlock(parent, property, body, rewrite);
    }

    /**
     * Adds a block around a non-block statement.
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

    /**
     * Removes a block containing exactly one safe statement.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param parent the control statement owning the body property
     * @param property the body property to replace
     * @param body the current control-statement body
     * @param rewrite the rewrite collecting source edits
     * @return {@code true} when a block removal is scheduled
     */
    private boolean removeBlock(
            CompilationUnit compilationUnit,
            ASTNode parent,
            StructuralPropertyDescriptor property,
            Statement body,
            ASTRewrite rewrite) {
        if (!(body instanceof Block block)
                || block.statements().size() != 1
                || containsComment(compilationUnit, block)
                || changesDanglingElseMeaning(parent, block)) {
            return false;
        }

        Statement statement = (Statement) block.statements().get(0);
        rewrite.set(parent, property, rewrite.createMoveTarget(statement), null);
        return true;
    }

    /**
     * Checks whether removing a block would rebind an outer {@code else} clause.
     *
     * @param parent the control statement owning the candidate block
     * @param block the candidate block
     * @return {@code true} when the dangling-else behavior could change
     */
    private boolean changesDanglingElseMeaning(ASTNode parent, Block block) {
        if (!(parent instanceof IfStatement ifStatement)
                || ifStatement.getElseStatement() == null
                || ifStatement.getThenStatement() != block
                || !(block.statements().get(0) instanceof IfStatement nestedIf)) {
            return false;
        }
        return nestedIf.getElseStatement() == null;
    }

    /**
     * Checks whether a source statement spans more than one source line.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param statement the statement to inspect
     * @return {@code true} when the statement has a line break in its source range
     */
    private boolean isMultiLine(CompilationUnit compilationUnit, Statement statement) {
        if (statement == null) {
            return false;
        }
        int start = statement.getStartPosition();
        int end = start + statement.getLength();
        return start >= 0 && end > start
                && compilationUnit.getLineNumber(start) != compilationUnit.getLineNumber(end - 1);
    }

    /**
     * Checks whether a parsed comment overlaps a node's source range.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param node the source range to inspect
     * @return {@code true} when a comment overlaps the node
     */
    private boolean containsComment(CompilationUnit compilationUnit, ASTNode node) {
        int nodeStart = node.getStartPosition();
        int nodeEnd = nodeStart + node.getLength();
        for (Object value : compilationUnit.getCommentList()) {
            Comment comment = (Comment) value;
            int commentStart = comment.getStartPosition();
            int commentEnd = commentStart + comment.getLength();
            if (commentStart < nodeEnd && nodeStart < commentEnd) {
                return true;
            }
        }
        return false;
    }
}
