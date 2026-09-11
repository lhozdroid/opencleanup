package io.github.lhozdroid.opencleanup.rule;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Replaces a generated {@code hashCode} accumulator with {@code Objects.hash}.
 */
public final class HashModernizeRule implements CleanupRule {

    /** The stable identifier used to select this cleanup rule. */
    public static final String ID = "hash.modernize";

    /**
     * Returns the stable identifier for hash modernization cleanup.
     *
     * @return the hash modernization rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for exact hash accumulator method bodies.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one accumulator is modernized
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Modernizes one exact hash accumulator block.
             *
             * @param node the visited block
             * @return {@code false} after a rewrite, otherwise {@code true}
             */
            @Override
            public boolean visit(Block node) {
                if (modernize(node, rewrite)) {
                    changed[0] = true;
                    return false;
                }
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Converts a block containing only the standard result-plus-31 hash sequence.
     *
     * @param block the block to inspect
     * @param rewrite the AST rewrite collecting source edits
     * @return {@code true} when the block has a scheduled modernization
     */
    private boolean modernize(Block block, ASTRewrite rewrite) {
        if (block.statements().size() < 3
                || !(block.statements().get(0) instanceof VariableDeclarationStatement declaration)
                || declaration.fragments().size() != 1
                || !declaration.modifiers().isEmpty()
                || !"int".equals(declaration.getType().toString())) {
            return false;
        }

        VariableDeclarationFragment fragment =
                (VariableDeclarationFragment) declaration.fragments().get(0);
        if (!(fragment.getInitializer() instanceof NumberLiteral initializer)
                || !"1".equals(initializer.getToken())) {
            return false;
        }

        String resultName = fragment.getName().getIdentifier();
        List<Expression> values = new ArrayList<>();
        int statementIndex = 1;
        while (statementIndex < block.statements().size() - 1) {
            if (!(block.statements().get(statementIndex) instanceof ExpressionStatement assignment)
                    || !(assignment.getExpression() instanceof org.eclipse.jdt.core.dom.Assignment expression)
                    || expression.getOperator() != org.eclipse.jdt.core.dom.Assignment.Operator.ASSIGN
                    || !(expression.getLeftHandSide() instanceof SimpleName left)
                    || !resultName.equals(left.getIdentifier())) {
                return false;
            }
            Expression value = hashValue(expression.getRightHandSide(), resultName);
            if (value == null) {
                return false;
            }
            values.add(value);
            statementIndex++;
        }

        if (!(block.statements().get(statementIndex) instanceof ReturnStatement returnStatement)
                || !(returnStatement.getExpression() instanceof SimpleName returned)
                || !resultName.equals(returned.getIdentifier())) {
            return false;
        }

        AST ast = block.getAST();
        MethodInvocation objectsHash = ast.newMethodInvocation();
        objectsHash.setExpression(ast.newSimpleName("Objects"));
        objectsHash.setName(ast.newSimpleName("hash"));
        for (Expression value : values) {
            objectsHash.arguments().add(ASTNode.copySubtree(ast, value));
        }
        ReturnStatement replacement = ast.newReturnStatement();
        replacement.setExpression(objectsHash);
        rewrite.replace(returnStatement, replacement, null);
        rewrite.remove(declaration, null);
        for (int index = 1; index < statementIndex; index++) {
            rewrite.remove((Statement) block.statements().get(index), null);
        }
        ensureObjectsImport((CompilationUnit) block.getRoot(), rewrite);
        return true;
    }

    /**
     * Extracts the value term from a standard {@code 31 * result + value} assignment.
     *
     * @param rightHandSide the assignment right-hand side
     * @param resultName the accumulator variable name
     * @return the value term, or {@code null} when the expression is not standard
     */
    private Expression hashValue(Expression rightHandSide, String resultName) {
        if (!(rightHandSide instanceof InfixExpression addition)
                || addition.getOperator() != InfixExpression.Operator.PLUS
                || !addition.extendedOperands().isEmpty()
                || !(addition.getLeftOperand() instanceof InfixExpression multiplication)
                || multiplication.getOperator() != InfixExpression.Operator.TIMES
                || !multiplication.extendedOperands().isEmpty()) {
            return null;
        }
        if (!isThirtyOne(multiplication.getLeftOperand())
                || !(multiplication.getRightOperand() instanceof SimpleName accumulator)
                || !resultName.equals(accumulator.getIdentifier())) {
            return null;
        }
        return addition.getRightOperand();
    }

    /**
     * Checks whether an expression is the decimal literal {@code 31}.
     *
     * @param expression the expression to inspect
     * @return {@code true} for the exact decimal 31 literal
     */
    private boolean isThirtyOne(Expression expression) {
        return expression instanceof NumberLiteral literal && "31".equals(literal.getToken());
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
        org.eclipse.jdt.core.dom.rewrite.ListRewrite imports = rewrite.getListRewrite(
                compilationUnit, CompilationUnit.IMPORTS_PROPERTY);
        imports.insertLast(importDeclaration, null);
    }
}
