package io.github.lhozdroid.opencleanup.rule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Replaces conservative equality-based conditional chains with switch statements.
 *
 * <p>The selector must be a syntactically declared primitive integral or
 * character local or parameter. This avoids changing reference equality,
 * unboxing, or unresolved overload behavior. Branches are isolated in blocks
 * so local declarations retain their original scope.</p>
 */
public final class UseSwitchRule implements CleanupRule {

    /** The stable identifier used to select this cleanup rule. */
    public static final String ID = "control-statements.use-switch";

    /**
     * Returns the stable identifier for conditional-switch cleanup.
     *
     * @return the conditional-switch rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for eligible equality conditional chains.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one conditional chain is replaced
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Replaces one eligible if chain and skips its old branch nodes.
             *
             * @param node the visited if statement
             * @return {@code false} after replacement, otherwise {@code true}
             */
            @Override
            public boolean visit(IfStatement node) {
                SwitchCandidate candidate = candidate(compilationUnit, node);
                if (candidate == null) {
                    return true;
                }

                rewrite.replace(node, createSwitch(candidate, node.getAST()), null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Collects the equality branches of a candidate conditional chain.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param root the first if statement in the chain
     * @return the collected candidate, or {@code null} when syntax is not safe
     */
    private SwitchCandidate candidate(CompilationUnit compilationUnit, IfStatement root) {
        if (containsComment(compilationUnit, root)) {
            return null;
        }

        List<Branch> branches = new ArrayList<>();
        Set<String> labels = new HashSet<>();
        IfStatement current = root;
        SimpleName selector = null;
        String selectorType = null;
        Statement defaultBody = null;

        while (true) {
            Comparison comparison = comparison(current.getExpression());
            if (comparison == null) {
                return null;
            }
            if (selector == null) {
                selector = comparison.selector();
                selectorType = primitiveSelectorType(root, selector.getIdentifier());
                if (selectorType == null) {
                    return null;
                }
            } else if (!selector.getIdentifier().equals(comparison.selector().getIdentifier())) {
                return null;
            }
            if (!isCompatibleLabel(comparison.literal(), selectorType)
                    || !labels.add(comparison.literal().toString())
                    || containsUnsupportedTransfer(current.getThenStatement())) {
                return null;
            }

            branches.add(new Branch(comparison.literal(), current.getThenStatement()));
            Statement elseStatement = current.getElseStatement();
            if (elseStatement instanceof IfStatement nestedIf) {
                current = nestedIf;
                continue;
            }
            defaultBody = elseStatement;
            break;
        }

        if (branches.size() < 2
                || (defaultBody != null && containsUnsupportedTransfer(defaultBody))) {
            return null;
        }
        return new SwitchCandidate(selector, branches, defaultBody);
    }

    /**
     * Extracts a simple-name equality comparison from a conditional expression.
     *
     * @param expression the conditional expression to inspect
     * @return the selector and literal comparison, or {@code null} when unsupported
     */
    private Comparison comparison(Expression expression) {
        if (!(expression instanceof InfixExpression infix)
                || infix.getOperator() != InfixExpression.Operator.EQUALS
                || !infix.extendedOperands().isEmpty()) {
            return null;
        }

        if (infix.getLeftOperand() instanceof SimpleName selector
                && isSupportedLabel(infix.getRightOperand())) {
            return new Comparison(selector, infix.getRightOperand());
        }
        if (infix.getRightOperand() instanceof SimpleName selector
                && isSupportedLabel(infix.getLeftOperand())) {
            return new Comparison(selector, infix.getLeftOperand());
        }
        return null;
    }

    /**
     * Checks whether a switch label has a supported literal syntax.
     *
     * @param expression the candidate case label
     * @return {@code true} for numeric and character literals
     */
    private boolean isSupportedLabel(Expression expression) {
        return expression instanceof NumberLiteral || expression instanceof CharacterLiteral;
    }

    /**
     * Checks whether a literal can be used with the known primitive selector type.
     *
     * @param expression the candidate case label
     * @param selectorType the primitive selector type
     * @return {@code true} when the label is conservatively compatible
     */
    private boolean isCompatibleLabel(Expression expression, String selectorType) {
        if (expression instanceof CharacterLiteral) {
            return "char".equals(selectorType) || "int".equals(selectorType);
        }
        if (!(expression instanceof NumberLiteral number)) {
            return false;
        }
        String token = number.getToken();
        if (token.endsWith("l") || token.endsWith("L")
                || token.endsWith("f") || token.endsWith("F")
                || token.endsWith("d") || token.endsWith("D")) {
            return false;
        }
        return "byte".equals(selectorType)
                || "short".equals(selectorType)
                || "int".equals(selectorType);
    }

    /**
     * Finds the primitive type of a selector declared in the containing method.
     *
     * @param root the conditional chain root
     * @param selectorName the selector variable name
     * @return the supported primitive type, or {@code null} when unresolved
     */
    private String primitiveSelectorType(IfStatement root, String selectorName) {
        MethodDeclaration method = enclosingMethod(root);
        if (method == null) {
            return null;
        }

        for (Object value : method.parameters()) {
            SingleVariableDeclaration parameter = (SingleVariableDeclaration) value;
            if (selectorName.equals(parameter.getName().getIdentifier())) {
                return primitiveSwitchType(parameter.getType());
            }
        }

        String[] type = {null};
        if (method.getBody() != null) {
            method.getBody().accept(new ASTVisitor() {
                /**
                 * Finds a primitive local declaration matching the selector.
                 *
                 * @param node the visited local declaration statement
                 * @return {@code true} to continue scanning declarations
                 */
                @Override
                public boolean visit(VariableDeclarationStatement node) {
                    if (type[0] == null && node.getStartPosition() < root.getStartPosition()) {
                        for (Object value : node.fragments()) {
                            VariableDeclarationFragment fragment = (VariableDeclarationFragment) value;
                            if (selectorName.equals(fragment.getName().getIdentifier())) {
                                type[0] = primitiveSwitchType(node.getType());
                            }
                        }
                    }
                    return true;
                }

                /**
                 * Finds a primitive local declaration expression matching the selector.
                 *
                 * @param node the visited local declaration expression
                 * @return {@code true} to continue scanning declarations
                 */
                @Override
                public boolean visit(VariableDeclarationExpression node) {
                    if (type[0] == null && node.getStartPosition() < root.getStartPosition()) {
                        for (Object value : node.fragments()) {
                            VariableDeclarationFragment fragment = (VariableDeclarationFragment) value;
                            if (selectorName.equals(fragment.getName().getIdentifier())) {
                                type[0] = primitiveSwitchType(node.getType());
                            }
                        }
                    }
                    return true;
                }
            });
        }
        return type[0];
    }

    /**
     * Returns the enclosing method declaration for an AST node.
     *
     * @param node the node whose method should be found
     * @return the enclosing method, or {@code null} outside a method
     */
    private MethodDeclaration enclosingMethod(ASTNode node) {
        ASTNode current = node.getParent();
        while (current != null && !(current instanceof MethodDeclaration)) {
            current = current.getParent();
        }
        return (MethodDeclaration) current;
    }

    /**
     * Returns the switch-compatible name of a primitive type.
     *
     * @param type the declared type to inspect
     * @return {@code byte}, {@code short}, {@code char}, or {@code int}; otherwise {@code null}
     */
    private String primitiveSwitchType(org.eclipse.jdt.core.dom.Type type) {
        if (!(type instanceof PrimitiveType primitiveType)) {
            return null;
        }
        String name = primitiveType.getPrimitiveTypeCode().toString();
        return switch (name) {
            case "byte", "short", "char", "int" -> name;
            default -> null;
        };
    }

    /**
     * Creates a switch statement from collected branches.
     *
     * @param candidate the validated conditional-chain candidate
     * @param ast the AST owning the replacement
     * @return the generated switch statement
     */
    private SwitchStatement createSwitch(SwitchCandidate candidate, AST ast) {
        SwitchStatement switchStatement = ast.newSwitchStatement();
        switchStatement.setExpression((Expression) ASTNode.copySubtree(ast, candidate.selector()));
        for (Branch branch : candidate.branches()) {
            addCase(switchStatement, branch.label(), branch.body(), ast);
        }
        if (candidate.defaultBody() != null) {
            SwitchCase defaultCase = ast.newSwitchCase();
            defaultCase.setSwitchLabeledRule(false);
            switchStatement.statements().add(defaultCase);
            addBranchBody(switchStatement, candidate.defaultBody(), ast);
        }
        return switchStatement;
    }

    /**
     * Adds one labeled case and its isolated branch body.
     *
     * @param switchStatement the switch being built
     * @param label the case label expression
     * @param body the original conditional branch
     * @param ast the AST owning the replacement
     */
    private void addCase(
            SwitchStatement switchStatement,
            Expression label,
            Statement body,
            AST ast) {
        SwitchCase switchCase = ast.newSwitchCase();
        switchCase.expressions().add(ASTNode.copySubtree(ast, label));
        switchCase.setSwitchLabeledRule(false);
        switchStatement.statements().add(switchCase);
        addBranchBody(switchStatement, body, ast);
    }

    /**
     * Adds an isolated branch block and a break when the branch can complete normally.
     *
     * @param switchStatement the switch being built
     * @param body the original conditional branch
     * @param ast the AST owning the replacement
     */
    private void addBranchBody(SwitchStatement switchStatement, Statement body, AST ast) {
        Block branchBlock = ast.newBlock();
        if (body instanceof Block originalBlock) {
            for (Object value : originalBlock.statements()) {
                branchBlock.statements().add(ASTNode.copySubtree(ast, (ASTNode) value));
            }
        } else {
            branchBlock.statements().add(ASTNode.copySubtree(ast, body));
        }
        switchStatement.statements().add(branchBlock);
        if (completesNormally(body)) {
            org.eclipse.jdt.core.dom.BreakStatement breakStatement = ast.newBreakStatement();
            switchStatement.statements().add(breakStatement);
        }
    }

    /**
     * Determines whether a branch can complete normally using syntax alone.
     *
     * @param statement the branch to inspect
     * @return {@code true} when execution may continue after the branch
     */
    private boolean completesNormally(Statement statement) {
        if (statement instanceof org.eclipse.jdt.core.dom.ReturnStatement
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
     * Checks whether a branch contains break or continue control transfers.
     *
     * @param statement the branch to inspect
     * @return {@code true} when a break or continue occurs in the branch
     */
    private boolean containsUnsupportedTransfer(Statement statement) {
        boolean[] found = {false};
        statement.accept(new ASTVisitor() {
            /**
             * Records a break statement in the branch.
             *
             * @param node the visited break statement
             * @return {@code false} because the candidate is already rejected
             */
            @Override
            public boolean visit(BreakStatement node) {
                found[0] = true;
                return false;
            }

            /**
             * Records a continue statement in the branch.
             *
             * @param node the visited continue statement
             * @return {@code false} because the candidate is already rejected
             */
            @Override
            public boolean visit(ContinueStatement node) {
                found[0] = true;
                return false;
            }
        });
        return found[0];
    }

    /**
     * Checks whether a comment overlaps a source range.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param node the source range to inspect
     * @return {@code true} when a comment overlaps the source range
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

    /** A validated equality comparison used as one switch branch. */
    private record Comparison(SimpleName selector, Expression literal) {
    }

    /** A conditional branch used to build one switch case. */
    private record Branch(Expression label, Statement body) {
    }

    /** A validated if-chain ready for switch generation. */
    private record SwitchCandidate(SimpleName selector, List<Branch> branches, Statement defaultBody) {
    }
}
