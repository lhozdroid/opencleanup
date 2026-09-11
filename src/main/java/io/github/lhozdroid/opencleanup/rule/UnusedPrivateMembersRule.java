package io.github.lhozdroid.opencleanup.rule;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Removes private declarations that have no source references.
 *
 * <p>The rule deliberately uses source references instead of guessed bindings. Private methods
 * with invocations are retained, and private method parameters are changed only when their
 * declaring method has no invocation in the compilation unit.</p>
 */
public final class UnusedPrivateMembersRule implements CleanupRule {

    /** The stable identifier for unused private member cleanup. */
    public static final String ID = "unused-code.private-members";

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
     * Records removals for unused private declarations.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one declaration is scheduled for removal
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        Set<ASTNode> removed = new HashSet<>();
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Removes unused private fields.
             *
             * @param node the visited field declaration
             * @return {@code true} to continue visiting nested declarations
             */
            @Override
            public boolean visit(FieldDeclaration node) {
                if (!Modifier.isPrivate(node.getModifiers())) {
                    return true;
                }
                ListRewrite fragments = rewrite.getListRewrite(
                        node, FieldDeclaration.FRAGMENTS_PROPERTY);
                for (Object value : node.fragments()) {
                    VariableDeclarationFragment fragment = (VariableDeclarationFragment) value;
                    if (referenceCount(compilationUnit, fragment.getName()) == 0) {
                        if (node.fragments().size() == 1) {
                            rewrite.remove(node, null);
                            removed.add(node);
                        } else {
                            fragments.remove(fragment, null);
                            removed.add(fragment);
                        }
                        changed[0] = true;
                    }
                }
                return true;
            }

            /**
             * Removes unused private methods and constructors, and unused parameters from
             * private methods that have no invocation in the compilation unit.
             *
             * @param node the visited method declaration
             * @return {@code true} to continue visiting method contents
             */
            @Override
            public boolean visit(MethodDeclaration node) {
                if (!Modifier.isPrivate(node.getModifiers())) {
                    return true;
                }
                if (referenceCount(compilationUnit, node.getName()) == 0) {
                    rewrite.remove(node, null);
                    removed.add(node);
                    changed[0] = true;
                    return false;
                }
                if (!node.isConstructor() && !hasInvocation(compilationUnit, node.getName())) {
                    ListRewrite parameters = rewrite.getListRewrite(
                            node, MethodDeclaration.PARAMETERS_PROPERTY);
                    for (Object value : node.parameters()) {
                        SingleVariableDeclaration parameter = (SingleVariableDeclaration) value;
                        if (referenceCount(compilationUnit, parameter.getName()) == 0) {
                            parameters.remove(parameter, null);
                            changed[0] = true;
                        }
                    }
                }
                return true;
            }

            /**
             * Removes an unused private nested type.
             *
             * @param node the visited nested type declaration
             * @return {@code true} to continue visiting nested declarations
             */
            @Override
            public boolean preVisit2(ASTNode node) {
                if (node instanceof AbstractTypeDeclaration declaration
                        && node.getParent() instanceof BodyDeclaration
                        && Modifier.isPrivate(declaration.getModifiers())
                        && referenceCount(compilationUnit, declaration.getName()) == 0) {
                    rewrite.remove(declaration, null);
                    removed.add(declaration);
                    changed[0] = true;
                    return false;
                }
                return true;
            }
        });
        return changed[0] && !removed.isEmpty();
    }

    /**
     * Counts references to a declaration name outside that declaration's own name node.
     *
     * @param compilationUnit the compilation unit to inspect
     * @param declarationName the declaration name
     * @return the number of source references to the name
     */
    private int referenceCount(CompilationUnit compilationUnit, SimpleName declarationName) {
        int[] count = {0};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Counts a simple-name occurrence outside the declaration name.
             *
             * @param node the visited simple name
             * @return {@code true} to continue visiting child nodes
             */
            @Override
            public boolean visit(SimpleName node) {
                if (node != declarationName && node.getIdentifier().equals(declarationName.getIdentifier())) {
                    count[0]++;
                }
                return true;
            }
        });
        return count[0];
    }

    /**
     * Checks whether a simple name is used as a method invocation name.
     *
     * @param compilationUnit the compilation unit to inspect
     * @param methodName the private method name
     * @return {@code true} when an invocation with that name is present
     */
    private boolean hasInvocation(CompilationUnit compilationUnit, SimpleName methodName) {
        boolean[] found = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Detects a method invocation name matching the private method.
             *
             * @param node the visited simple name
             * @return {@code false} after a match, otherwise {@code true}
             */
            @Override
            public boolean visit(SimpleName node) {
                if (node != methodName && node.getIdentifier().equals(methodName.getIdentifier())
                        && node.getParent() instanceof org.eclipse.jdt.core.dom.MethodInvocation invocation
                        && invocation.getName() == node) {
                    found[0] = true;
                    return false;
                }
                return true;
            }
        });
        return found[0];
    }
}
