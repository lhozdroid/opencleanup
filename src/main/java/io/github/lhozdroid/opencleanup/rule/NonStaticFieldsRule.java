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
import org.eclipse.jdt.core.dom.Initializer;
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
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Qualifies conservatively provable non-static field references with {@code this}.
 *
 * <p>The rule only considers fields declared directly by the nearest named type and references
 * that are not already qualified. It skips static contexts, anonymous classes, local shadowing,
 * and the {@code when-necessary} mode because binding-free analysis cannot prove that an
 * unqualified name denotes a shadowed field in that mode.</p>
 */
public final class NonStaticFieldsRule implements CleanupRule {

    /** The stable identifier for non-static field qualification cleanup. */
    public static final String ID = "member-accesses.non-static-fields";

    /** The option value that qualifies every safe field reference. */
    private static final String ALWAYS = "always";

    /**
     * Returns the stable identifier for non-static field qualification cleanup.
     *
     * @return the non-static field rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records {@code this} qualifiers for safe, directly declared instance-field references.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one field reference is scheduled for qualification
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        if (!usesAlwaysMode(configuration)) {
            return false;
        }

        Map<AbstractTypeDeclaration, Set<String>> fields = collectInstanceFields(compilationUnit);
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Qualifies one simple name when it is a safe reference to a declared instance field.
             *
             * @param node the visited simple name
             * @return {@code true} to continue visiting child nodes
             */
            @Override
            public boolean visit(SimpleName node) {
                AbstractTypeDeclaration type = enclosingNamedType(node);
                if (type == null
                        || !fields.getOrDefault(type, Collections.emptySet()).contains(node.getIdentifier())
                        || !isUnqualifiedReference(node)
                        || isStaticContext(node)
                        || hasPotentialShadowing(node, node.getIdentifier())) {
                    return true;
                }

                FieldAccess replacement = node.getAST().newFieldAccess();
                replacement.setExpression(node.getAST().newThisExpression());
                replacement.setName(node.getAST().newSimpleName(node.getIdentifier()));
                rewrite.replace(node, replacement, null);
                changed[0] = true;
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Collects non-static fields declared directly by each named type.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @return instance-field names indexed by their declaring type node
     */
    private Map<AbstractTypeDeclaration, Set<String>> collectInstanceFields(
            CompilationUnit compilationUnit) {
        Map<AbstractTypeDeclaration, Set<String>> fields = new IdentityHashMap<>();
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Records the fragments of one non-static field declaration.
             *
             * @param node the visited field declaration
             * @return {@code true} to continue visiting field initializers
             */
            @Override
            public boolean visit(FieldDeclaration node) {
                AbstractTypeDeclaration type = enclosingNamedType(node);
                if (type == null || hasModifier(node, Modifier.ModifierKeyword.STATIC_KEYWORD)
                        || isInterfaceLike(type)) {
                    return true;
                }

                Set<String> names = fields.computeIfAbsent(type, ignored -> new LinkedHashSet<>());
                for (Object fragmentObject : node.fragments()) {
                    VariableDeclarationFragment fragment = (VariableDeclarationFragment) fragmentObject;
                    names.add(fragment.getName().getIdentifier());
                }
                return true;
            }
        });
        return fields;
    }

    /**
     * Checks whether the configured field mode is the binding-independent {@code always} mode.
     *
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when safe references may be qualified
     */
    private boolean usesAlwaysMode(RuleConfiguration configuration) {
        String mode = configuration.optionValue(ID);
        if (mode == null) {
            mode = configuration.optionValue("mode");
        }
        return mode == null || ALWAYS.equalsIgnoreCase(mode);
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
     * Checks whether a reference occurs in a context where {@code this} is unavailable.
     *
     * @param node the field reference under consideration
     * @return {@code true} for static methods, static initializers, or static field initializers
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
            if (current instanceof FieldDeclaration field) {
                return hasModifier(field, Modifier.ModifierKeyword.STATIC_KEYWORD);
            }
            if (current instanceof AbstractTypeDeclaration) {
                return false;
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * Checks whether a local declaration with the same name makes the reference ambiguous.
     *
     * @param reference the reference being considered
     * @param identifier the candidate field name
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
     * @param node the reference whose enclosing scope is needed
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
}
