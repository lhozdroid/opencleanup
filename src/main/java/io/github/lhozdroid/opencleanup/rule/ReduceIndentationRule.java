package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.AST;
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
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Reduces indentation in safe terminal {@code if} statements.
 *
 * <p>The syntax-only implementation handles an {@code else} branch after a
 * non-completing then branch, and a final two-branch if by inverting its
 * condition. Candidates containing comments or declarations in statements
 * moved outside their original block are intentionally skipped.</p>
 */
public final class ReduceIndentationRule implements CleanupRule {

    /** The stable identifier used to select this cleanup rule. */
    public static final String ID = "control-statements.reduce-indentation";

    /**
     * Returns the stable identifier for indentation-reduction cleanup.
     *
     * @return the indentation-reduction rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records edits for eligible terminal if statements.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one indentation reduction is scheduled
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Reduces indentation for one eligible if statement.
             *
             * @param node the visited if statement
             * @return {@code false} after a rewrite, otherwise {@code true}
             */
            @Override
            public boolean visit(IfStatement node) {
                if (reduceElseIndentation(compilationUnit, node, rewrite)
                        || reduceThenIndentation(compilationUnit, node, rewrite)) {
                    changed[0] = true;
                    return false;
                }
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Moves an else branch after an if whose then branch cannot complete normally.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param node the candidate if statement
     * @param rewrite the rewrite collecting source edits
     * @return {@code true} when the else branch is moved
     */
    private boolean reduceElseIndentation(
            CompilationUnit compilationUnit,
            IfStatement node,
            ASTRewrite rewrite) {
        Statement elseStatement = node.getElseStatement();
        if (!(node.getParent() instanceof Block parentBlock)
                || elseStatement == null
                || elseStatement instanceof IfStatement
                || completesNormally(node.getThenStatement())
                || containsVariableDeclaration(elseStatement)
                || containsComment(compilationUnit, node)) {
            return false;
        }

        ASTNode movedStatements = bodyMoveTarget(elseStatement, rewrite);
        if (movedStatements == null) {
            return false;
        }

        ListRewrite parentStatements = rewrite.getListRewrite(parentBlock, Block.STATEMENTS_PROPERTY);
        parentStatements.insertAfter(movedStatements, node, null);
        rewrite.set(node, IfStatement.ELSE_STATEMENT_PROPERTY, null, null);
        return true;
    }

    /**
     * Inverts a final two-branch if and moves its then branch after the if.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param node the candidate if statement
     * @param rewrite the rewrite collecting source edits
     * @return {@code true} when the then branch is moved
     */
    private boolean reduceThenIndentation(
            CompilationUnit compilationUnit,
            IfStatement node,
            ASTRewrite rewrite) {
        Statement elseStatement = node.getElseStatement();
        if (!(node.getParent() instanceof Block parentBlock)
                || elseStatement == null
                || elseStatement instanceof IfStatement
                || !isLastStatement(parentBlock, node)
                || !completesNormally(node.getThenStatement())
                || !completesNormally(elseStatement)
                || containsVariableDeclaration(node.getThenStatement())
                || containsComment(compilationUnit, node)) {
            return false;
        }

        ASTNode movedStatements = bodyMoveTarget(node.getThenStatement(), rewrite);
        if (movedStatements == null) {
            return false;
        }

        AST ast = node.getAST();
        org.eclipse.jdt.core.dom.PrefixExpression negated = ast.newPrefixExpression();
        negated.setOperator(org.eclipse.jdt.core.dom.PrefixExpression.Operator.NOT);
        negated.setOperand((org.eclipse.jdt.core.dom.Expression) ASTNode.copySubtree(
                ast, node.getExpression()));

        ASTNode elseBody = rewrite.createMoveTarget(elseStatement);
        rewrite.replace(node.getExpression(), negated, null);
        rewrite.set(node, IfStatement.THEN_STATEMENT_PROPERTY, elseBody, null);
        rewrite.set(node, IfStatement.ELSE_STATEMENT_PROPERTY, null, null);
        rewrite.getListRewrite(parentBlock, Block.STATEMENTS_PROPERTY)
                .insertAfter(movedStatements, node, null);
        return true;
    }

    /**
     * Creates a move target for a statement or all statements in its block.
     *
     * @param statement the statement whose body should be moved
     * @param rewrite the rewrite collecting source edits
     * @return the move target, or {@code null} for an empty block
     */
    private ASTNode bodyMoveTarget(Statement statement, ASTRewrite rewrite) {
        if (!(statement instanceof Block block)) {
            return rewrite.createMoveTarget(statement);
        }
        if (block.statements().isEmpty()) {
            return null;
        }

        ListRewrite statements = rewrite.getListRewrite(block, Block.STATEMENTS_PROPERTY);
        Statement first = (Statement) block.statements().get(0);
        Statement last = (Statement) block.statements().get(block.statements().size() - 1);
        return statements.createMoveTarget(first, last);
    }

    /**
     * Checks whether an if statement is the final statement in its containing block.
     *
     * @param block the containing block
     * @param statement the candidate statement
     * @return {@code true} when the candidate is the block's final statement
     */
    private boolean isLastStatement(Block block, Statement statement) {
        return !block.statements().isEmpty()
                && block.statements().get(block.statements().size() - 1) == statement;
    }

    /**
     * Determines whether a statement can complete normally using syntax alone.
     *
     * @param statement the statement to inspect
     * @return {@code true} when execution may continue after the statement
     */
    private boolean completesNormally(Statement statement) {
        if (statement instanceof ReturnStatement
                || statement instanceof ThrowStatement
                || statement instanceof BreakStatement
                || statement instanceof ContinueStatement) {
            return false;
        }
        if (statement instanceof Block block) {
            if (block.statements().isEmpty()) {
                return true;
            }
            return completesNormally((Statement) block.statements().get(block.statements().size() - 1));
        }
        if (statement instanceof IfStatement ifStatement && ifStatement.getElseStatement() != null) {
            return completesNormally(ifStatement.getThenStatement())
                    || completesNormally(ifStatement.getElseStatement());
        }
        return true;
    }

    /**
     * Checks whether a branch contains a local variable declaration.
     *
     * @param statement the branch to inspect
     * @return {@code true} when a variable declaration occurs in the branch
     */
    private boolean containsVariableDeclaration(Statement statement) {
        boolean[] found = {false};
        statement.accept(new ASTVisitor() {
            /**
             * Records a local variable declaration in the branch.
             *
             * @param node the visited declaration statement
             * @return {@code false} because no nested traversal is needed
             */
            @Override
            public boolean visit(VariableDeclarationStatement node) {
                found[0] = true;
                return false;
            }
        });
        return found[0];
    }

    /**
     * Checks whether a parsed comment overlaps a source range.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param node the source range to inspect
     * @return {@code true} when a comment overlaps the node
     */
    private boolean containsComment(CompilationUnit compilationUnit, ASTNode node) {
        int nodeStart = node.getStartPosition();
        int nodeEnd = nodeStart + node.getLength();
        for (Object value : compilationUnit.getCommentList()) {
            org.eclipse.jdt.core.dom.Comment comment = (org.eclipse.jdt.core.dom.Comment) value;
            int commentStart = comment.getStartPosition();
            int commentEnd = commentStart + comment.getLength();
            if (commentStart < nodeEnd && nodeStart < commentEnd) {
                return true;
            }
        }
        return false;
    }
}
