package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Replaces a direct {@code Comparator.compare} criterion with
 * {@code Comparator.comparing}.
 */
public final class ComparatorCriteriaRule implements CleanupRule {

    /** The stable identifier for comparator criteria construction. */
    public static final String ID = "comparators.criteria";

    /**
     * Returns the stable identifier for comparator criteria construction.
     *
     * @return the comparator criteria rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for eligible anonymous comparator arguments.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one comparator is simplified
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Replaces one anonymous comparator passed to a sorting operation.
             *
             * @param node the visited class instance creation
             * @return {@code false} after replacement, otherwise {@code true}
             */
            @Override
            public boolean visit(ClassInstanceCreation node) {
                if (!isComparator(node) || !(node.getParent() instanceof MethodInvocation invocation)) {
                    return true;
                }
                int argumentIndex = comparatorArgumentIndex(invocation, node);
                if (argumentIndex < 0) {
                    return true;
                }
                MethodDeclaration compare = compareMethod(node);
                MethodInvocation criterion = criterion(compare);
                if (criterion == null) {
                    return true;
                }

                rewrite.replace(node, comparatorFor(node.getAST(), compare, criterion), null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Checks whether an anonymous class visibly implements Comparator syntax.
     *
     * @param node the anonymous class creation
     * @return {@code true} for a Comparator type with no constructor arguments
     */
    private boolean isComparator(ClassInstanceCreation node) {
        String type = node.getType().toString();
        int genericStart = type.indexOf('<');
        String base = genericStart < 0 ? type : type.substring(0, genericStart);
        return !node.arguments().isEmpty() ? false : base.endsWith("Comparator");
    }

    /**
     * Finds the comparator argument in a supported sort invocation.
     *
     * @param invocation the enclosing method invocation
     * @param comparator the anonymous comparator expression
     * @return the comparator argument index, or {@code -1} when unsupported
     */
    private int comparatorArgumentIndex(MethodInvocation invocation, ClassInstanceCreation comparator) {
        String method = invocation.getName().getIdentifier();
        if ("sort".equals(method) && invocation.arguments().size() == 1) {
            return invocation.arguments().get(0) == comparator ? 0 : -1;
        }
        if ("sort".equals(method) && invocation.arguments().size() == 2) {
            return invocation.arguments().get(1) == comparator ? 1 : -1;
        }
        return -1;
    }

    /**
     * Finds the single overridden compare method in an anonymous comparator.
     *
     * @param node the anonymous comparator creation
     * @return the compare method, or {@code null} when its body is ambiguous
     */
    private MethodDeclaration compareMethod(ClassInstanceCreation node) {
        if (node.getAnonymousClassDeclaration() == null
                || node.getAnonymousClassDeclaration().bodyDeclarations().size() != 1
                || !(node.getAnonymousClassDeclaration().bodyDeclarations().get(0)
                        instanceof MethodDeclaration method)
                || !"compare".equals(method.getName().getIdentifier())
                || method.parameters().size() != 2
                || method.getBody() == null
                || method.getBody().statements().size() != 1
                || !(method.getBody().statements().get(0) instanceof ReturnStatement)) {
            return null;
        }
        return method;
    }

    /**
     * Extracts a criterion from {@code left.key().compareTo(right.key())}.
     *
     * @param method the comparator method
     * @return the left-side criterion invocation, or {@code null} when unsupported
     */
    private MethodInvocation criterion(MethodDeclaration method) {
        ReturnStatement returnStatement = (ReturnStatement) method.getBody().statements().get(0);
        if (!(returnStatement.getExpression() instanceof MethodInvocation compareTo)
                || !"compareTo".equals(compareTo.getName().getIdentifier())
                || compareTo.arguments().size() != 1
                || !(compareTo.getExpression() instanceof MethodInvocation leftCriterion)
                || !(compareTo.arguments().get(0) instanceof MethodInvocation rightCriterion)
                || !sameCriterion(leftCriterion, rightCriterion, method)) {
            return null;
        }
        return leftCriterion;
    }

    /**
     * Checks that both compare-to operands use the same no-argument criterion on opposite parameters.
     *
     * @param left the left criterion invocation
     * @param right the right criterion invocation
     * @param method the comparator method containing both parameters
     * @return {@code true} when the criterion is syntax-equivalent
     */
    private boolean sameCriterion(MethodInvocation left, MethodInvocation right, MethodDeclaration method) {
        if (!left.arguments().isEmpty()
                || !right.arguments().isEmpty()
                || !(left.getExpression() instanceof SimpleName leftName)
                || !(right.getExpression() instanceof SimpleName rightName)
                || method.parameters().size() != 2) {
            return false;
        }
        String first = ((SingleVariableDeclaration) method.parameters().get(0)).getName().getIdentifier();
        String second = ((SingleVariableDeclaration) method.parameters().get(1)).getName().getIdentifier();
        return first.equals(leftName.getIdentifier())
                && second.equals(rightName.getIdentifier())
                && left.getName().getIdentifier().equals(right.getName().getIdentifier());
    }

    /**
     * Builds a fully qualified comparator criterion invocation.
     *
     * @param ast the AST receiving the replacement
     * @param method the original comparator method
     * @param criterion the left-side criterion
     * @return the generated comparing invocation
     */
    private MethodInvocation comparatorFor(AST ast, MethodDeclaration method, MethodInvocation criterion) {
        MethodInvocation key = (MethodInvocation) ASTNode.copySubtree(ast, criterion);
        key.setExpression(ast.newSimpleName("value"));

        LambdaExpression lambda = ast.newLambdaExpression();
        lambda.setParentheses(false);
        VariableDeclarationFragment parameter = ast.newVariableDeclarationFragment();
        parameter.setName(ast.newSimpleName("value"));
        lambda.parameters().add(parameter);
        lambda.setBody(key);

        MethodInvocation comparing = ast.newMethodInvocation();
        comparing.setExpression(ast.newName("java.util.Comparator"));
        comparing.setName(ast.newSimpleName("comparing"));
        comparing.arguments().add(lambda);
        return comparing;
    }
}
