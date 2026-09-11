package io.github.lhozdroid.opencleanup.rule;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.PatternInstanceofExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.TypePattern;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Converts a conservative chain of type-pattern checks into a Java 21 pattern switch.
 *
 * <p>The selector must be one stable simple name. Every branch must use a
 * distinct type pattern and the chain may have an optional final else branch.
 * The generated switch includes a null case so the conversion preserves the
 * fact that {@code instanceof} returns false for {@code null}.</p>
 */
public final class InstanceofToSwitchRule implements CleanupRule {

    /** The stable identifier for this cleanup rule. */
    public static final String ID = "instanceof.to-switch";

    /**
     * Returns the stable identifier for conversion to a pattern switch.
     *
     * @return the instanceof-to-switch rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for eligible if/else-if chains.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one chain is converted
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Converts one eligible if/else-if chain and skips nested branches.
             *
             * @param node the visited if statement
             * @return {@code false} after conversion, otherwise {@code true}
             */
            @Override
            public boolean visit(IfStatement node) {
                if (node.getParent() instanceof IfStatement parent
                        && parent.getElseStatement() == node) {
                    return false;
                }
                Chain chain = Chain.from(node);
                if (chain == null) {
                    return true;
                }

                rewrite.replace(node, chain.toSwitch(node.getAST()), null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Describes the supported type-pattern chain.
     *
     * @param selectorName the stable selector identifier
     * @param branches the ordered pattern branches
     * @param otherwise the optional unmatched branch
     */
    private record Chain(
            String selectorName,
            List<Branch> branches,
            Statement otherwise) {

        /**
         * Extracts a supported chain without requiring bindings.
         *
         * @param root the first if statement in the chain
         * @return the chain description, or {@code null} when syntax is uncertain
         */
        private static Chain from(IfStatement root) {
            List<Branch> branches = new ArrayList<>();
            String selectorName = null;
            IfStatement current = root;

            while (true) {
                if (!(current.getExpression() instanceof PatternInstanceofExpression pattern)
                        || !(pattern.getLeftOperand() instanceof SimpleName selector)
                        || !(pattern.getPattern() instanceof TypePattern typePattern)
                        || typePattern.getPatternVariable() == null
                        || current.getThenStatement() == null) {
                    return null;
                }
                if (selectorName == null) {
                    selectorName = selector.getIdentifier();
                } else if (!selectorName.equals(selector.getIdentifier())) {
                    return null;
                }

                SingleVariableDeclaration variable = typePattern.getPatternVariable();
                String typeText = variable.getType().toString();
                if (branches.stream().anyMatch(branch -> branch.typeText().equals(typeText))) {
                    return null;
                }
                branches.add(new Branch(typeText, variable, current.getThenStatement()));

                if (!(current.getElseStatement() instanceof IfStatement next)) {
                    return new Chain(selectorName, branches, current.getElseStatement());
                }
                current = next;
            }
        }

        /**
         * Builds a Java 21 switch statement using arrow pattern labels.
         *
         * @param ast the AST receiving the new switch
         * @return the generated switch statement
         */
        private SwitchStatement toSwitch(AST ast) {
            SwitchStatement replacement = ast.newSwitchStatement();
            replacement.setExpression(ast.newSimpleName(selectorName));
            for (Branch branch : branches) {
                addBranch(ast, replacement, branch.typePattern(ast), branch.body());
            }
            addNullAndDefaultBranches(ast, replacement);
            return replacement;
        }

        /**
         * Adds one pattern case and its moved branch body to a switch.
         *
         * @param ast the AST receiving the case
         * @param replacement the replacement switch statement
         * @param label the copied switch label
         * @param body the original if branch body
         */
        @SuppressWarnings("unchecked")
        private void addBranch(
                AST ast,
                SwitchStatement replacement,
                ASTNode label,
                Statement body) {
            SwitchCase switchCase = ast.newSwitchCase();
            switchCase.setSwitchLabeledRule(true);
            switchCase.expressions().add(label);
            replacement.statements().add(switchCase);
            replacement.statements().add((Statement) ASTNode.copySubtree(ast, body));
        }

        /**
         * Adds null and unmatched handling that preserves instanceof semantics.
         *
         * @param ast the AST receiving the cases
         * @param replacement the replacement switch statement
         */
        private void addNullAndDefaultBranches(AST ast, SwitchStatement replacement) {
            if (otherwise == null) {
                addEmptyBranch(ast, replacement, true);
                addEmptyBranch(ast, replacement, false);
                return;
            }

            addBranch(ast, replacement, ast.newNullLiteral(), otherwise);
            addDefaultBranch(ast, replacement, otherwise);
        }

        /**
         * Adds an empty null or default arrow branch.
         *
         * @param ast the AST receiving the branch
         * @param replacement the replacement switch statement
         * @param nullCase whether to create a null case instead of default
         */
        private void addEmptyBranch(AST ast, SwitchStatement replacement, boolean nullCase) {
            SwitchCase switchCase = ast.newSwitchCase();
            switchCase.setSwitchLabeledRule(true);
            if (nullCase) {
                switchCase.expressions().add(ast.newNullLiteral());
            } else {
                switchCase.setExpression(null);
            }
            replacement.statements().add(switchCase);
            replacement.statements().add(ast.newBlock());
        }

        /**
         * Adds a default branch containing a copied unmatched body.
         *
         * @param ast the AST receiving the branch
         * @param replacement the replacement switch statement
         * @param body the original unmatched body
         */
        private void addDefaultBranch(AST ast, SwitchStatement replacement, Statement body) {
            SwitchCase switchCase = ast.newSwitchCase();
            switchCase.setSwitchLabeledRule(true);
            switchCase.setExpression(null);
            replacement.statements().add(switchCase);
            replacement.statements().add((Statement) ASTNode.copySubtree(ast, body));
        }

    }

    /**
     * Describes one type-pattern branch.
     *
     * @param typeText the source representation of the pattern type
     * @param variable the original pattern variable
     * @param body the original branch body
     */
    private record Branch(String typeText, SingleVariableDeclaration variable, Statement body) {

        /**
         * Copies this branch's type pattern into another AST.
         *
         * @param ast the AST receiving the copy
         * @return the copied type pattern
         */
        private TypePattern typePattern(AST ast) {
            TypePattern pattern = ast.newTypePattern();
            pattern.setPatternVariable((SingleVariableDeclaration) ASTNode.copySubtree(ast, variable));
            return pattern;
        }
    }
}
