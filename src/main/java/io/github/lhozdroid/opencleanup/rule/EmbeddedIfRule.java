package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Combines a conservative embedded {@code if} statement into its parent
 * condition.
 *
 * <p>The supported shape is an outer {@code if} without an {@code else}
 * whose block contains exactly one inner {@code if}, also without an
 * {@code else}. The inner body is retained and the conditions are combined
 * with short-circuit {@code &&}. Candidates containing comments or variable
 * declarations in the retained body are left unchanged.</p>
 */
public final class EmbeddedIfRule implements CleanupRule {

    /** The stable identifier used to select this cleanup rule. */
    public static final String ID = "if.embedded";

    /**
     * Returns the stable identifier for embedded-if cleanup.
     *
     * @return the embedded-if rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records edits that combine eligible nested if conditions.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one embedded if is combined
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Combines one eligible embedded if statement.
             *
             * @param node the visited outer if statement
             * @return {@code false} after a replacement, otherwise {@code true}
             */
            @Override
            public boolean visit(IfStatement node) {
                IfStatement innerIf = eligibleInnerIf(compilationUnit, node);
                if (innerIf == null) {
                    return true;
                }

                AST ast = node.getAST();
                IfStatement replacement = ast.newIfStatement();
                replacement.setExpression(combinedCondition(node, innerIf));
                replacement.setThenStatement((Statement) ASTNode.copySubtree(
                        ast, innerIf.getThenStatement()));
                rewrite.replace(node, replacement, null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Finds the sole eligible inner if statement of an outer if statement.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param outerIf the candidate outer if statement
     * @return the inner if statement, or {@code null} when the shape is not
     *         supported
     */
    private IfStatement eligibleInnerIf(CompilationUnit compilationUnit, IfStatement outerIf) {
        if (outerIf.getElseStatement() != null
                || !(outerIf.getThenStatement() instanceof org.eclipse.jdt.core.dom.Block block)
                || block.statements().size() != 1
                || !(block.statements().get(0) instanceof IfStatement innerIf)
                || innerIf.getElseStatement() != null
                || containsComment(compilationUnit, outerIf)
                || containsVariableDeclaration(innerIf.getThenStatement())) {
            return null;
        }
        return innerIf;
    }

    /**
     * Creates a short-circuit conjunction of the outer and inner conditions.
     *
     * @param outerIf the outer if statement
     * @param innerIf the nested if statement
     * @return a new expression containing both conditions
     */
    private Expression combinedCondition(IfStatement outerIf, IfStatement innerIf) {
        AST ast = outerIf.getAST();
        org.eclipse.jdt.core.dom.InfixExpression conjunction = ast.newInfixExpression();
        conjunction.setOperator(org.eclipse.jdt.core.dom.InfixExpression.Operator.CONDITIONAL_AND);
        conjunction.setLeftOperand((Expression) ASTNode.copySubtree(
                ast, outerIf.getExpression()));
        conjunction.setRightOperand((Expression) ASTNode.copySubtree(
                ast, innerIf.getExpression()));
        return conjunction;
    }

    /**
     * Checks whether a candidate body contains a variable declaration.
     *
     * @param statement the inner body to inspect
     * @return {@code true} when a variable declaration occurs in the body
     */
    private boolean containsVariableDeclaration(Statement statement) {
        boolean[] found = {false};
        statement.accept(new ASTVisitor() {
            /**
             * Records a variable declaration in the candidate body.
             *
             * @param node the visited variable declaration statement
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
     * Checks whether a source range contains a parsed comment.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param node the source range to inspect
     * @return {@code true} when a comment overlaps the node's source range
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
