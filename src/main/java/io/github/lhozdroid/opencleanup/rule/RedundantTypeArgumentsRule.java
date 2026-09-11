package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.WildcardType;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Removes explicit generic arguments where Java 21 can infer them.
 */
public final class RedundantTypeArgumentsRule implements CleanupRule {

    /** The stable identifier used to select this cleanup rule. */
    public static final String ID = "type-parameters.remove-redundant";

    /**
     * Returns the stable identifier for redundant type-argument cleanup.
     *
     * @return the redundant type-argument rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records removals for eligible method and constructor type arguments.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one type-argument list is removed
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Removes explicit method type arguments when their syntax is inferable.
             *
             * @param node the visited method invocation
             * @return {@code true} to continue visiting nested expressions
             */
            @Override
            public boolean visit(MethodInvocation node) {
                if (!node.typeArguments().isEmpty() && allConcrete(node.typeArguments())) {
                    ListRewrite arguments = rewrite.getListRewrite(
                            node, MethodInvocation.TYPE_ARGUMENTS_PROPERTY);
                    node.typeArguments().forEach(argument -> arguments.remove(
                            (org.eclipse.jdt.core.dom.ASTNode) argument, null));
                    changed[0] = true;
                }
                return true;
            }

            /**
             * Replaces explicit constructor type arguments with the Java 21 diamond form.
             *
             * @param node the visited class instance creation
             * @return {@code true} to continue visiting nested expressions
             */
            @Override
            public boolean visit(ClassInstanceCreation node) {
                if (!(node.getType() instanceof ParameterizedType type)
                        || type.typeArguments().isEmpty()
                        || node.getAnonymousClassDeclaration() != null
                        || !allConcrete(type.typeArguments())) {
                    return true;
                }
                ListRewrite arguments = rewrite.getListRewrite(
                        type, ParameterizedType.TYPE_ARGUMENTS_PROPERTY);
                type.typeArguments().forEach(argument -> arguments.remove(
                        (org.eclipse.jdt.core.dom.ASTNode) argument, null));
                changed[0] = true;
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Checks that a type-argument list contains no wildcard whose inference could differ.
     *
     * @param arguments the type arguments to inspect
     * @return {@code true} when every argument is a concrete non-wildcard type
     */
    private boolean allConcrete(java.util.List<?> arguments) {
        return arguments.stream().noneMatch(WildcardType.class::isInstance);
    }
}
