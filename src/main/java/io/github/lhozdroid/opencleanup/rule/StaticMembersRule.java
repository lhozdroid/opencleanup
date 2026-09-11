package io.github.lhozdroid.opencleanup.rule;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Qualifies directly declared static fields and methods with their declaring type.
 *
 * <p>This binding-independent implementation handles only unqualified references inside the
 * same named type. It skips inherited, statically imported, already-qualified, shadowed, and
 * declaration names, as well as method names that are shared by static and instance methods.
 * Static nested types and enum constants are intentionally outside this rule's syntax-only scope.</p>
 */
public final class StaticMembersRule implements CleanupRule {

    /** The stable identifier for static-member qualification cleanup. */
    public static final String ID = "member-accesses.static-members";

    /** The option value that enables static-member qualification. */
    private static final String ENABLED = "true";

    /**
     * Returns the stable identifier for static-member qualification cleanup.
     *
     * @return the static-member rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records declaring-type qualifiers for safe static field references and method calls.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one static member is scheduled for qualification
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        if (!isEnabled(configuration)) {
            return false;
        }

        MemberNames memberNames = collectStaticMembers(compilationUnit);
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Qualifies one unqualified static field reference when its target is unambiguous.
             *
             * @param node the visited simple name
             * @return {@code true} to continue visiting child nodes
             */
            @Override
            public boolean visit(SimpleName node) {
                AbstractTypeDeclaration type = enclosingNamedType(node);
                if (type == null
                        || !isUnqualifiedReference(node)
                        || hasPotentialShadowing(node, node.getIdentifier())) {
                    return true;
                }

                Set<String> fields = memberNames.fields().getOrDefault(type, Collections.emptySet());
                if (!fields.contains(node.getIdentifier())) {
                    return true;
                }

                QualifiedName replacement = node.getAST().newQualifiedName(
                        node.getAST().newSimpleName(type.getName().getIdentifier()),
                        node.getAST().newSimpleName(node.getIdentifier()));
                rewrite.replace(node, replacement, null);
                changed[0] = true;
                return true;
            }

            /**
             * Qualifies one unqualified static method invocation when its target is unambiguous.
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
                Set<String> methods = memberNames.methods().getOrDefault(type, Collections.emptySet());
                if (type == null || !methods.contains(methodName)
                        || hasPotentialShadowing(node, methodName)) {
                    return true;
                }

                rewrite.set(node, MethodInvocation.EXPRESSION_PROPERTY,
                        node.getAST().newSimpleName(type.getName().getIdentifier()), null);
                changed[0] = true;
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Collects static fields and methods declared directly by each named type.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @return static member names indexed by declaring type
     */
    private MemberNames collectStaticMembers(CompilationUnit compilationUnit) {
        Map<AbstractTypeDeclaration, Set<String>> fields = new IdentityHashMap<>();
        Map<AbstractTypeDeclaration, Set<String>> methods = new IdentityHashMap<>();
        Map<AbstractTypeDeclaration, Set<String>> instanceMethods = new IdentityHashMap<>();
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Records static fields declared directly by a named type.
             *
             * @param node the visited field declaration
             * @return {@code true} to continue visiting field initializers
             */
            @Override
            public boolean visit(FieldDeclaration node) {
                AbstractTypeDeclaration type = enclosingNamedType(node);
                if (type == null || (!hasModifier(node, Modifier.ModifierKeyword.STATIC_KEYWORD)
                        && !isInterfaceLike(type))) {
                    return true;
                }

                Set<String> names = fields.computeIfAbsent(type, ignored -> new LinkedHashSet<>());
                for (Object fragmentObject : node.fragments()) {
                    VariableDeclarationFragment fragment = (VariableDeclarationFragment) fragmentObject;
                    names.add(fragment.getName().getIdentifier());
                }
                return true;
            }

            /**
             * Records whether a directly declared method is static or instance-bound.
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
                                ? methods : instanceMethods;
                target.computeIfAbsent(type, ignored -> new LinkedHashSet<>())
                        .add(node.getName().getIdentifier());
                return true;
            }
        });

        for (Map.Entry<AbstractTypeDeclaration, Set<String>> entry : instanceMethods.entrySet()) {
            Set<String> staticNames = methods.get(entry.getKey());
            if (staticNames != null) {
                staticNames.removeAll(entry.getValue());
            }
        }
        return new MemberNames(fields, methods);
    }

    /**
     * Checks whether the static-member option is enabled.
     *
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when no option disables the rule or its value is {@code true}
     */
    private boolean isEnabled(RuleConfiguration configuration) {
        String value = configuration.optionValue(ID);
        if (value == null) {
            value = configuration.optionValue("enabled");
        }
        return value == null || ENABLED.equalsIgnoreCase(value);
    }

    /**
     * Checks whether a simple name is an unqualified expression reference rather than syntax.
     *
     * @param node the simple name under consideration
     * @return {@code true} when the name can safely be treated as an unqualified expression
     */
    private boolean isUnqualifiedReference(SimpleName node) {
        ASTNode parent = node.getParent();
        if (parent instanceof QualifiedName
                || parent instanceof FieldAccess
                || parent instanceof SuperFieldAccess
                || (parent instanceof MethodInvocation invocation && invocation.getName() == node)
                || parent instanceof Type
                || parent instanceof Annotation
                || parent instanceof ImportDeclaration
                || parent instanceof PackageDeclaration
                || parent instanceof MethodReference
                || parent instanceof EnumConstantDeclaration
                || parent instanceof SimpleType
                || parent instanceof VariableDeclarationFragment
                || parent instanceof SingleVariableDeclaration
                || node.isDeclaration()) {
            return false;
        }
        return !(parent instanceof MethodDeclaration method && method.getName() == node);
    }

    /**
     * Checks whether a local declaration with the same name makes a member reference ambiguous.
     *
     * @param reference the member reference being considered
     * @param identifier the candidate member name
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
     * @param node the member reference whose enclosing scope is needed
     * @return the nearest method or lambda, or {@code null} outside such a scope
     */
    private ASTNode enclosingScope(ASTNode node) {
        ASTNode current = node.getParent();
        ASTNode lambdaScope = null;
        while (current != null) {
            if (current instanceof MethodDeclaration) {
                return current;
            }
            if (current instanceof org.eclipse.jdt.core.dom.LambdaExpression && lambdaScope == null) {
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
     * Finds the nearest named type unless the reference is inside an anonymous class.
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
     * Checks whether a declaration has a selected modifier keyword.
     *
     * @param declaration the declaration whose modifiers are inspected
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

    /**
     * Checks whether a type gives its fields implicit static semantics.
     *
     * @param type the declaring type
     * @return {@code true} for interfaces and annotation types
     */
    private boolean isInterfaceLike(AbstractTypeDeclaration type) {
        return type instanceof TypeDeclaration declaration && declaration.isInterface()
                || type instanceof AnnotationTypeDeclaration;
    }

    /** The static field and method names declared by one compilation unit's named types. */
    private record MemberNames(
            Map<AbstractTypeDeclaration, Set<String>> fields,
            Map<AbstractTypeDeclaration, Set<String>> methods) {
    }
}
