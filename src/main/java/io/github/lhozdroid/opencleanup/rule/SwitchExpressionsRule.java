package io.github.lhozdroid.opencleanup.rule;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.YieldStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Converts switch statements whose every case directly returns into switch expressions.
 */
public final class SwitchExpressionsRule implements CleanupRule {

    /** The stable identifier for switch-expression conversion. */
    public static final String ID = "switch.expressions";

    /**
     * Returns the stable identifier for switch-expression conversion.
     *
     * @return the switch-expression rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for direct-return switch statements.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one switch is converted
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Converts one switch whose case groups each contain one return.
             *
             * @param node the visited switch statement
             * @return {@code false} after conversion, otherwise {@code true}
             */
            @Override
            public boolean visit(SwitchStatement node) {
                SwitchShape shape = SwitchShape.from(node);
                if (shape == null) {
                    return true;
                }

                rewrite.replace(node, shape.toReturn(node.getAST()), null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Describes a direct-return switch.
     *
     * @param selector the selector expression
     * @param branches the case groups and return expressions
     */
    private record SwitchShape(Expression selector, List<Branch> branches) {

        /**
         * Extracts a switch shape without requiring type bindings.
         *
         * @param node the switch statement to inspect
         * @return the supported shape, or {@code null} when any case is uncertain
         */
        private static SwitchShape from(SwitchStatement node) {
            if (node.getExpression() == null) {
                return null;
            }

            List<Branch> branches = new ArrayList<>();
            List<SwitchCase> currentCases = new ArrayList<>();
            ReturnStatement currentReturn = null;
            boolean hasDefault = false;
            for (Object value : node.statements()) {
                if (value instanceof SwitchCase switchCase) {
                    if (currentReturn != null) {
                        branches.add(new Branch(currentCases, currentReturn));
                        currentCases = new ArrayList<>();
                        currentReturn = null;
                    }
                    if (currentCases.isEmpty() || currentReturn == null) {
                        currentCases.add(switchCase);
                    } else {
                        return null;
                    }
                    hasDefault |= switchCase.isDefault();
                } else if (value instanceof ReturnStatement returnStatement
                        && !currentCases.isEmpty()
                        && returnStatement.getExpression() != null
                        && currentReturn == null) {
                    currentReturn = returnStatement;
                } else {
                    return null;
                }
            }
            if (currentCases.isEmpty() || currentReturn == null) {
                return null;
            }
            branches.add(new Branch(currentCases, currentReturn));
            return hasDefault ? new SwitchShape(node.getExpression(), branches) : null;
        }

        /**
         * Creates a return statement containing the generated switch expression.
         *
         * @param ast the AST receiving the replacement
         * @return the replacement return statement
         */
        private ReturnStatement toReturn(AST ast) {
            SwitchExpression switchExpression = ast.newSwitchExpression();
            switchExpression.setExpression((Expression) ASTNode.copySubtree(ast, selector));
            for (Branch branch : branches) {
                branch.addTo(ast, switchExpression);
            }

            ReturnStatement replacement = ast.newReturnStatement();
            replacement.setExpression(switchExpression);
            return replacement;
        }
    }

    /**
     * Describes one switch case group and its returned expression.
     *
     * @param switchCases the original switch labels in this group
     * @param returnStatement the original return statement
     */
    private record Branch(List<SwitchCase> switchCases, ReturnStatement returnStatement) {

        /**
         * Adds this branch to a colon-form switch expression.
         *
         * @param ast the AST receiving the copied branch
         * @param switchExpression the generated switch expression
         */
        @SuppressWarnings("unchecked")
        private void addTo(AST ast, SwitchExpression switchExpression) {
            for (SwitchCase switchCase : switchCases) {
                SwitchCase replacementCase = ast.newSwitchCase();
                replacementCase.setSwitchLabeledRule(false);
                for (Object expression : switchCase.expressions()) {
                    replacementCase.expressions().add(ASTNode.copySubtree(ast, (ASTNode) expression));
                }
                if (switchCase.isDefault()) {
                    replacementCase.setExpression(null);
                }
                switchExpression.statements().add(replacementCase);
            }

            YieldStatement yield = ast.newYieldStatement();
            yield.setExpression((Expression) ASTNode.copySubtree(ast, returnStatement.getExpression()));
            switchExpression.statements().add(yield);
        }
    }
}
