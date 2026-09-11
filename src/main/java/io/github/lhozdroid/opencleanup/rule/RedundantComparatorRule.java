package io.github.lhozdroid.opencleanup.rule;

import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Removes natural-order comparators from standard sorting and extrema operations.
 */
public final class RedundantComparatorRule implements CleanupRule {

    /** The stable identifier for redundant comparator cleanup. */
    public static final String ID = "comparators.redundant";

    /**
     * Returns the stable identifier for this rule.
     *
     * @return the redundant comparator rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for comparator arguments whose natural ordering is implicit.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one comparator is removed
     */
    @Override
    public boolean apply(CompilationUnit compilationUnit, ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Removes one redundant comparator argument.
             *
             * @param node the visited method invocation
             * @return {@code false} after a rewrite, otherwise {@code true}
             */
            @Override
            public boolean visit(MethodInvocation node) {
                if (node.arguments().size() == 2 && isCollectionsOperation(node)
                        && isNaturalComparator((Expression) node.arguments().get(1))) {
                    ListRewrite arguments = rewrite.getListRewrite(node, MethodInvocation.ARGUMENTS_PROPERTY);
                    arguments.remove((ASTNode) node.arguments().get(1), null);
                    changed[0] = true;
                    return false;
                }
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Checks whether an invocation is a supported static Collections operation.
     *
     * @param node the invocation to inspect
     * @return {@code true} for sort, min, or max on the Collections type
     */
    private boolean isCollectionsOperation(MethodInvocation node) {
        if (node.getExpression() == null || !typeName(node.getExpression(), "Collections")) {
            return false;
        }
        String method = node.getName().getIdentifier();
        return method.equals("sort") || method.equals("min") || method.equals("max");
    }

    /**
     * Checks whether a comparator expression is natural order or an equivalent simple comparator.
     *
     * @param expression the comparator expression
     * @return {@code true} when the comparator is safe to remove
     */
    private boolean isNaturalComparator(Expression expression) {
        if (expression instanceof MethodInvocation invocation
                && invocation.getExpression() != null
                && typeName(invocation.getExpression(), "Comparator")
                && invocation.getName().getIdentifier().equals("naturalOrder")
                && invocation.arguments().isEmpty()) {
            return true;
        }
        return expression instanceof ClassInstanceCreation creation && isNaturalAnonymous(creation);
    }

    /**
     * Checks whether a name expression ends in a requested simple type name.
     *
     * @param expression the name expression
     * @param simpleName the expected terminal name
     * @return {@code true} when the textual name is qualified or unqualified with that name
     */
    private boolean typeName(Expression expression, String simpleName) {
        String text = expression.toString();
        return text.equals(simpleName) || text.endsWith("." + simpleName);
    }

    /**
     * Checks whether an anonymous comparator compares two values with compareTo in forward order.
     *
     * @param creation the anonymous comparator construction
     * @return {@code true} when its sole compare method is a natural comparison
     */
    private boolean isNaturalAnonymous(ClassInstanceCreation creation) {
        AnonymousClassDeclaration anonymous = creation.getAnonymousClassDeclaration();
        if (anonymous == null || !creation.arguments().isEmpty() || anonymous.bodyDeclarations().size() != 1
                || !(anonymous.bodyDeclarations().get(0) instanceof MethodDeclaration method)
                || !method.getName().getIdentifier().equals("compare") || method.parameters().size() != 2
                || method.getBody() == null || method.getBody().statements().size() != 1
                || !(method.getBody().statements().get(0) instanceof ReturnStatement result)) {
            return false;
        }
        if (!(result.getExpression() instanceof MethodInvocation comparison)
                || !comparison.getName().getIdentifier().equals("compareTo")
                || comparison.arguments().size() != 1
                || !(comparison.getExpression() instanceof SimpleName first)
                || !(comparison.arguments().get(0) instanceof SimpleName second)) {
            return false;
        }
        String firstParameter = ((SingleVariableDeclaration) method.parameters().get(0)).getName().getIdentifier();
        String secondParameter = ((SingleVariableDeclaration) method.parameters().get(1)).getName().getIdentifier();
        return first.getIdentifier().equals(firstParameter) && second.getIdentifier().equals(secondParameter);
    }
}
