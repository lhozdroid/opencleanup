package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.PatternInstanceofExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypePattern;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Converts a directly guarded cast declaration to an {@code instanceof}
 * pattern variable.
 *
 * <p>This rule intentionally handles only the conservative shape where the
 * condition checks a stable simple name, the then block starts with one plain
 * local declaration initialized by the matching cast, and no else branch is
 * present.</p>
 */
public final class InstanceofPatternMatchingRule implements CleanupRule {

    /** The stable identifier used to select this cleanup rule. */
    public static final String ID = "instanceof.pattern-matching";

    /**
     * Returns the stable identifier for this cleanup rule.
     *
     * @return the pattern matching rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for eligible cast declarations following an
     * {@code instanceof} condition.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one declaration is converted
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Converts one eligible if statement.
             *
             * @param node the visited if statement
             * @return {@code true} to continue visiting nested statements
             */
            @Override
            public boolean visit(IfStatement node) {
                if (node.getElseStatement() != null
                        || !(node.getExpression() instanceof InstanceofExpression instanceofExpression)
                        || !(node.getThenStatement() instanceof Block block)
                        || block.statements().isEmpty()
                        || !(block.statements().get(0) instanceof VariableDeclarationStatement declaration)
                        || !matches(instanceofExpression, declaration)) {
                    return true;
                }

                rewrite.replace(
                        instanceofExpression,
                        patternExpression(instanceofExpression, declaration),
                        null);
                rewrite.remove(declaration, null);
                changed[0] = true;
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Checks whether a local declaration is the exact cast counterpart of an
     * {@code instanceof} condition.
     *
     * @param instanceofExpression the condition being inspected
     * @param declaration the first statement in the then block
     * @return {@code true} when the declaration can be safely converted
     */
    private boolean matches(
            InstanceofExpression instanceofExpression,
            VariableDeclarationStatement declaration) {
        if (!declaration.modifiers().isEmpty()
                || declaration.fragments().size() != 1
                || !(declaration.fragments().get(0) instanceof VariableDeclarationFragment fragment)
                || !fragment.extraDimensions().isEmpty()
                || !(fragment.getInitializer() instanceof CastExpression cast)
                || !(fragment.getName() instanceof SimpleName)) {
            return false;
        }

        return stableOperand(instanceofExpression.getLeftOperand())
                && sameTree(instanceofExpression.getRightOperand(), declaration.getType())
                && sameTree(instanceofExpression.getRightOperand(), cast.getType())
                && sameTree(instanceofExpression.getLeftOperand(), cast.getExpression());
    }

    /**
     * Checks whether an expression can be evaluated once without changing the
     * supported transformation's behavior.
     *
     * @param expression the expression used by the instanceof condition
     * @return {@code true} only for a simple name
     */
    private boolean stableOperand(Expression expression) {
        return expression instanceof SimpleName;
    }

    /**
     * Compares two AST subtrees without requiring resolved bindings.
     *
     * @param first the first subtree
     * @param second the second subtree
     * @return {@code true} when both subtrees have the same structure
     */
    private boolean sameTree(ASTNode first, ASTNode second) {
        return first.subtreeMatch(new ASTMatcher(), second);
    }

    /**
     * Creates the pattern form of an eligible instanceof expression.
     *
     * @param instanceofExpression the original instanceof expression
     * @param declaration the declaration supplying the pattern variable
     * @return the new pattern instanceof expression
     */
    private PatternInstanceofExpression patternExpression(
            InstanceofExpression instanceofExpression,
            VariableDeclarationStatement declaration) {
        AST ast = instanceofExpression.getAST();
        VariableDeclarationFragment fragment =
                (VariableDeclarationFragment) declaration.fragments().get(0);

        SingleVariableDeclaration patternVariable = ast.newSingleVariableDeclaration();
        patternVariable.setType((org.eclipse.jdt.core.dom.Type) ASTNode.copySubtree(
                ast, declaration.getType()));
        patternVariable.setName((SimpleName) ASTNode.copySubtree(ast, fragment.getName()));

        TypePattern typePattern = ast.newTypePattern();
        typePattern.setPatternVariable(patternVariable);

        PatternInstanceofExpression patternExpression = ast.newPatternInstanceofExpression();
        patternExpression.setLeftOperand((Expression) ASTNode.copySubtree(
                ast, instanceofExpression.getLeftOperand()));
        patternExpression.setPattern(typePattern);
        return patternExpression;
    }
}
