package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Replaces exact null-safe equality conditionals with {@code Objects.equals}.
 */
public final class ObjectsEqualsRule implements CleanupRule {

    /** The stable identifier used to select this cleanup rule. */
    public static final String ID = "objects.equals";

    /**
     * Returns the stable identifier for Objects equality cleanup.
     *
     * @return the Objects equality rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for exact null-safe equality conditionals.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one conditional is replaced
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Replaces one eligible conditional equality expression.
             *
             * @param node the visited conditional expression
             * @return {@code false} after replacement, otherwise {@code true}
             */
            @Override
            public boolean visit(ConditionalExpression node) {
                Expression[] operands = equalityOperands(node);
                if (operands == null) {
                    return true;
                }
                rewrite.replace(node, objectsEquals(node.getAST(), operands[0], operands[1]), null);
                ensureObjectsImport((CompilationUnit) node.getRoot(), rewrite);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Extracts the compared operands from one supported null-safe conditional.
     *
     * @param conditional the conditional expression to inspect
     * @return the compared operands, or {@code null} when the shape is unsupported
     */
    private Expression[] equalityOperands(ConditionalExpression conditional) {
        if (!(conditional.getExpression() instanceof InfixExpression condition)
                || !isEquality(condition)
                || !(conditional.getThenExpression() instanceof InfixExpression thenExpression)
                || !isEquality(thenExpression)
                || !(conditional.getElseExpression() instanceof MethodInvocation equalsCall)
                || !"equals".equals(equalsCall.getName().getIdentifier())
                || equalsCall.arguments().size() != 1
                || !(equalsCall.getExpression() instanceof SimpleName receiver)
                || !(condition.getLeftOperand() instanceof SimpleName first)
                || !(condition.getRightOperand() instanceof NullLiteral)
                || !receiver.getIdentifier().equals(first.getIdentifier())
                || condition.getOperator() != InfixExpression.Operator.EQUALS
                || thenExpression.getOperator() != InfixExpression.Operator.EQUALS
                || !(thenExpression.getLeftOperand() instanceof SimpleName second)
                || !(thenExpression.getRightOperand() instanceof NullLiteral)
                || !equalsCall.arguments().get(0).toString().equals(second.getIdentifier())
                || !condition.extendedOperands().isEmpty()
                || !thenExpression.extendedOperands().isEmpty()) {
            return null;
        }
        return new Expression[] {first, second};
    }

    /**
     * Checks whether an infix expression is an equality comparison.
     *
     * @param expression the expression to inspect
     * @return {@code true} for the equality operator
     */
    private boolean isEquality(InfixExpression expression) {
        return expression.getOperator() == InfixExpression.Operator.EQUALS;
    }

    /**
     * Creates an {@code Objects.equals(first, second)} invocation.
     *
     * @param ast the AST owning the replacement
     * @param first the first compared operand
     * @param second the second compared operand
     * @return the replacement method invocation
     */
    private MethodInvocation objectsEquals(AST ast, Expression first, Expression second) {
        MethodInvocation replacement = ast.newMethodInvocation();
        replacement.setExpression(ast.newSimpleName("Objects"));
        replacement.setName(ast.newSimpleName("equals"));
        replacement.arguments().add(ASTNode.copySubtree(ast, first));
        replacement.arguments().add(ASTNode.copySubtree(ast, second));
        return replacement;
    }

    /**
     * Adds the java.util.Objects import unless the source already provides it.
     *
     * @param compilationUnit the compilation unit receiving the import
     * @param rewrite the AST rewrite collecting source edits
     */
    private void ensureObjectsImport(CompilationUnit compilationUnit, ASTRewrite rewrite) {
        for (Object object : compilationUnit.imports()) {
            org.eclipse.jdt.core.dom.ImportDeclaration declaration =
                    (org.eclipse.jdt.core.dom.ImportDeclaration) object;
            if (!declaration.isStatic()
                    && (declaration.isOnDemand()
                    ? "java.util".equals(declaration.getName().getFullyQualifiedName())
                    : "java.util.Objects".equals(declaration.getName().getFullyQualifiedName()))) {
                return;
            }
        }
        org.eclipse.jdt.core.dom.ImportDeclaration importDeclaration =
                compilationUnit.getAST().newImportDeclaration();
        importDeclaration.setName(compilationUnit.getAST().newName("java.util.Objects"));
        ListRewrite imports = rewrite.getListRewrite(compilationUnit, CompilationUnit.IMPORTS_PROPERTY);
        imports.insertLast(importDeclaration, null);
    }
}
