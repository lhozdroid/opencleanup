package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Replaces simple array-filling loops with {@code java.util.Arrays.fill} calls.
 *
 * <p>Only loops with a local zero-based counter, an exclusive {@code array.length} bound, a
 * single increment, and a passive assignment body are transformed.</p>
 */
public final class ArraysFillRule implements CleanupRule {

    /** The stable identifier for array fill cleanup. */
    public static final String ID = "arrays.fill";

    /**
     * Returns the stable identifier for this rule.
     *
     * @return the rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for simple array-filling loops.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source changes
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one loop is replaced
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Replaces one eligible for loop with an Arrays.fill invocation.
             *
             * @param node the visited for statement
             * @return {@code false} after replacement, otherwise {@code true}
             */
            @Override
            public boolean visit(ForStatement node) {
                FillCandidate candidate = candidate(node);
                if (candidate == null) {
                    return true;
                }
                rewrite.replace(node, fillStatement(node.getAST(), candidate), null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Finds the array and fill value represented by a loop.
     *
     * @param loop the candidate loop
     * @return the candidate details, or {@code null} when the loop is unsupported
     */
    private FillCandidate candidate(ForStatement loop) {
        if (loop.initializers().size() != 1 || loop.updaters().size() != 1
                || !(loop.initializers().get(0) instanceof VariableDeclarationExpression initializer)
                || initializer.fragments().size() != 1
                || !(loop.updaters().get(0) instanceof PostfixExpression update)
                || update.getOperator() != PostfixExpression.Operator.INCREMENT) {
            return null;
        }
        VariableDeclarationFragment fragment = (VariableDeclarationFragment) initializer.fragments().get(0);
        if (!(fragment.getInitializer() instanceof org.eclipse.jdt.core.dom.NumberLiteral number)
                || !"0".equals(number.getToken())
                || !(update.getOperand() instanceof org.eclipse.jdt.core.dom.SimpleName counter)
                || !counter.getIdentifier().equals(fragment.getName().getIdentifier())) {
            return null;
        }
        if (!(loop.getExpression() instanceof InfixExpression condition)
                || condition.getOperator() != InfixExpression.Operator.LESS
                || !(condition.getLeftOperand() instanceof org.eclipse.jdt.core.dom.SimpleName conditionCounter)
                || !conditionCounter.getIdentifier().equals(counter.getIdentifier())
                || !(condition.getRightOperand() instanceof org.eclipse.jdt.core.dom.QualifiedName length)
                || !"length".equals(length.getName().getIdentifier())
                || !(length.getQualifier() instanceof org.eclipse.jdt.core.dom.SimpleName arrayName)) {
            return null;
        }
        Statement body = loop.getBody();
        if (body instanceof Block block) {
            if (block.statements().size() != 1) {
                return null;
            }
            body = (Statement) block.statements().get(0);
        }
        if (!(body instanceof ExpressionStatement expressionStatement)
                || !(expressionStatement.getExpression() instanceof org.eclipse.jdt.core.dom.Assignment assignment)
                || assignment.getOperator() != org.eclipse.jdt.core.dom.Assignment.Operator.ASSIGN
                || !(assignment.getLeftHandSide() instanceof ArrayAccess access)
                || !(access.getArray() instanceof org.eclipse.jdt.core.dom.SimpleName assignedArray)
                || !assignedArray.getIdentifier().equals(arrayName.getIdentifier())
                || !(access.getIndex() instanceof org.eclipse.jdt.core.dom.SimpleName index)
                || !index.getIdentifier().equals(counter.getIdentifier())
                || !isPassive(assignment.getRightHandSide())) {
            return null;
        }
        return new FillCandidate(arrayName, assignment.getRightHandSide());
    }

    /**
     * Creates a fully-qualified Arrays.fill statement without requiring an import edit.
     *
     * @param ast the AST factory
     * @param candidate the loop details
     * @return the replacement statement
     */
    private Statement fillStatement(AST ast, FillCandidate candidate) {
        MethodInvocation invocation = ast.newMethodInvocation();
        invocation.setExpression(ast.newName("java.util.Arrays"));
        invocation.setName(ast.newSimpleName("fill"));
        invocation.arguments().add(ASTNode.copySubtree(ast, candidate.array()));
        invocation.arguments().add(ASTNode.copySubtree(ast, candidate.value()));
        return ast.newExpressionStatement(invocation);
    }

    /**
     * Checks whether an expression has no evaluation side effects.
     *
     * @param expression the expression to inspect
     * @return {@code true} for names, literals, and simple field access
     */
    private boolean isPassive(Expression expression) {
        return expression instanceof org.eclipse.jdt.core.dom.Name
                || expression instanceof org.eclipse.jdt.core.dom.BooleanLiteral
                || expression instanceof org.eclipse.jdt.core.dom.CharacterLiteral
                || expression instanceof org.eclipse.jdt.core.dom.NullLiteral
                || expression instanceof org.eclipse.jdt.core.dom.NumberLiteral
                || expression instanceof org.eclipse.jdt.core.dom.StringLiteral
                || expression instanceof org.eclipse.jdt.core.dom.QualifiedName;
    }

    /** Details extracted from a supported filling loop. */
    private record FillCandidate(
            org.eclipse.jdt.core.dom.SimpleName array,
            Expression value) {
    }
}
