package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Removes two consecutive boolean negations.
 */
public final class DoubleNegationRule implements CleanupRule {

    public static final String ID = "booleans.double-negation";

    /**
     * Returns the stable identifier for double-negation cleanup.
     *
     * @return the double-negation rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for consecutive logical-not expressions.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one double negation is scheduled for removal
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Replaces one pair of consecutive logical-not operators.
             *
             * @param node the visited prefix expression
             * @return {@code false} after replacement, otherwise {@code true}
             */
            @Override
            public boolean visit(PrefixExpression node) {
                if (node.getOperator() != PrefixExpression.Operator.NOT
                        || !(node.getOperand() instanceof PrefixExpression nested)
                        || nested.getOperator() != PrefixExpression.Operator.NOT) {
                    return true;
                }

                Expression replacement = (Expression) ASTNode.copySubtree(
                        node.getAST(), nested.getOperand());
                rewrite.replace(node, replacement, null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }
}
