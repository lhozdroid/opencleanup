package io.github.lhozdroid.opencleanup.rule;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Qualifies conservatively provable non-static method calls with {@code this}.
 *
 * <p>Only methods declared directly by the nearest named type are considered. Calls with an
 * explicit receiver, calls in static contexts, anonymous classes, local shadowing, and method
 * names shared by static and instance overloads are left unchanged because binding-free analysis
 * cannot determine their target safely.</p>
 */
public final class NonStaticMethodsRule implements CleanupRule {

    /** The stable identifier for non-static method qualification cleanup. */
    public static final String ID = "member-accesses.non-static-methods";

    /** The option value that qualifies every safe method invocation. */
    private static final String ALWAYS = "always";

    /**
     * Returns the stable identifier for non-static method qualification cleanup.
     *
     * @return the non-static method rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records {@code this} qualifiers for safe, directly declared instance-method calls.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one method call is scheduled for qualification
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        if (!usesAlwaysMode(configuration)) {
            return false;
        }

        Map<AbstractTypeDeclaration, Set<String>> methods = collectInstanceMethods(compilationUnit);
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Qualifies one unqualified invocation when its target is a safe instance method.
             *
             * @param node the visited method invocation
             * @return {@code true} to continue visiting invocation arguments
             */
            @Override
            public boolean visit(MethodInvocation node) {
                if (node.getExpression() != null) {
                    return true;
                }

                AbstractTypeDeclaration type = enclosingNamedType(node);
                String methodName = node.getName().getIdentifier();
                if (type == null
                        || !methods.getOrDefault(type, Collections.emptySet()).contains(methodName)
                        || isStaticContext(node)
                        || hasPotentialShadowing(node, methodName)) {
                    return true;
                }

                rewrite.set(node, MethodInvocation.EXPRESSION_PROPERTY,
                        node.getAST().newThisExpression(), null);
                changed[0] = true;
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Collects method names that have only non-static declarations in each named type.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @return safe instance-method names indexed by their declaring type node
     */
    private Map<AbstractTypeDeclaration, Set<String>> collectInstanceMethods(
            CompilationUnit compilationUnit) {
        Map<AbstractTypeDeclaration, Set<String>> instanceMethods = new IdentityHashMap<>();
        Map<AbstractTypeDeclaration, Set<String>> staticMethods = new IdentityHashMap<>();
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Records one method declaration in its nearest named type.
             *
             * @param node the visited method declaration
             * @return {@code true} to continue visiting the method body
             */
            @Override
            public boolean visit(MethodDeclaration node) {
                AbstractTypeDeclaration type = enclosingNamedType(node);
                if (type == null) {
                    return true;
                }

                Map<AbstractTypeDeclaration, Set<String>> target =
                        hasModifier(node, Modifier.ModifierKeyword.STATIC_KEYWORD)
                                ? staticMethods : instanceMethods;
                target.computeIfAbsent(type, ignored -> new LinkedHashSet<>())
                        .add(node.getName().getIdentifier());
                return true;
            }
        });

        for (Map.Entry<AbstractTypeDeclaration, Set<String>> entry : staticMethods.entrySet()) {
            Set<String> safeMethods = instanceMethods.get(entry.getKey());
            if (safeMethods != null) {
                safeMethods.removeAll(entry.getValue());
            }
        }
        return instanceMethods;
    }

    /**
     * Checks whether the configured method mode is the binding-independent {@code always} mode.
     *
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when safe calls may be qualified
     */
    private boolean usesAlwaysMode(RuleConfiguration configuration) {
        String mode = configuration.optionValue(ID);
        if (mode == null) {
            mode = configuration.optionValue("mode");
        }
        return mode == null || ALWAYS.equalsIgnoreCase(mode);
    }

    /**
     * Checks whether an invocation occurs in a context where {@code this} is unavailable.
     *
     * @param node the method invocation under consideration
     * @return {@code true} for static methods or static initializers
     */
    private boolean isStaticContext(ASTNode node) {
        ASTNode current = node.getParent();
        while (current != null) {
            if (current instanceof MethodDeclaration method) {
                return hasModifier(method, Modifier.ModifierKeyword.STATIC_KEYWORD);
            }
            if (current instanceof Initializer initializer) {
                return hasModifier(initializer, Modifier.ModifierKeyword.STATIC_KEYWORD);
            }
            if (current instanceof AbstractTypeDeclaration) {
                return false;
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * Checks whether a local declaration with the same name makes the invocation ambiguous.
     *
     * @param reference the method invocation being considered
     * @param identifier the candidate method name
     * @return {@code true} when an enclosing method or lambda declares the same name
     */
    private boolean hasPotentialShadowing(ASTNode reference, String identifier) {
        ASTNode scope = enclosingScope(reference);
        if (scope == null) {
            return false;
        }

        boolean[] shadowed = {false};
        scope.accept(new ASTVisitor() {
            /**
             * Detects a parameter or local variable with the candidate name.
             *
             * @param node the visited single-variable declaration
             * @return {@code true} to continue visiting the declaration
             */
            @Override
            public boolean visit(SingleVariableDeclaration node) {
                shadowed[0] |= identifier.equals(node.getName().getIdentifier());
                return !shadowed[0];
            }

            /**
             * Detects a local variable fragment with the candidate name.
             *
             * @param node the visited variable declaration fragment
             * @return {@code true} to continue visiting the fragment
             */
            @Override
            public boolean visit(VariableDeclarationFragment node) {
                shadowed[0] |= identifier.equals(node.getName().getIdentifier());
                return !shadowed[0];
            }
        });
        return shadowed[0];
    }

    /**
     * Finds the nearest method or lambda scope that can declare a shadowing name.
     *
     * @param node the invocation whose enclosing scope is needed
     * @return the nearest method or lambda, or {@code null} outside such a scope
     */
    private ASTNode enclosingScope(ASTNode node) {
        ASTNode current = node.getParent();
        ASTNode lambdaScope = null;
        while (current != null) {
            if (current instanceof MethodDeclaration) {
                return current;
            }
            if (current instanceof LambdaExpression && lambdaScope == null) {
                lambdaScope = current;
            }
            if (current instanceof AbstractTypeDeclaration
                    || current instanceof org.eclipse.jdt.core.dom.AnonymousClassDeclaration) {
                return lambdaScope;
            }
            current = current.getParent();
        }
        return lambdaScope;
    }

    /**
     * Finds the nearest named type unless the invocation is inside an anonymous class.
     *
     * @param node the AST node whose type scope is needed
     * @return the nearest named type, or {@code null} for compilation-unit and anonymous scopes
     */
    private AbstractTypeDeclaration enclosingNamedType(ASTNode node) {
        ASTNode current = node.getParent();
        while (current != null) {
            if (current instanceof org.eclipse.jdt.core.dom.AnonymousClassDeclaration) {
                return null;
            }
            if (current instanceof AbstractTypeDeclaration type) {
                return type;
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * Checks whether a method declaration has a selected modifier keyword.
     *
     * @param declaration the method declaration whose modifiers are inspected
     * @param keyword the modifier keyword to find
     * @return {@code true} when the keyword is present
     */
    private boolean hasModifier(BodyDeclaration declaration, Modifier.ModifierKeyword keyword) {
        for (Object modifierObject : declaration.modifiers()) {
            if (modifierObject instanceof Modifier modifier && modifier.getKeyword() == keyword) {
                return true;
            }
        }
        return false;
    }
}
