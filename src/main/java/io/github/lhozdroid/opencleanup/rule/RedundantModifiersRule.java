package io.github.lhozdroid.opencleanup.rule;

import java.util.Set;

import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Removes modifiers that Java syntax guarantees are implicit for selected declarations.
 *
 * <p>The rule only handles interface fields, interface methods, annotation elements, and member
 * types declared directly inside an interface-like type. Other declaration forms are left
 * unchanged because their effective modifiers may depend on context or intent.</p>
 */
public final class RedundantModifiersRule implements CleanupRule {

    /** The stable identifier used to select redundant-modifier cleanup. */
    public static final String ID = "modifiers.redundant";

    /** Modifiers implicit on every interface field. */
    private static final Set<Modifier.ModifierKeyword> INTERFACE_FIELD_MODIFIERS = Set.of(
            Modifier.ModifierKeyword.PUBLIC_KEYWORD,
            Modifier.ModifierKeyword.STATIC_KEYWORD,
            Modifier.ModifierKeyword.FINAL_KEYWORD);

    /** Modifiers implicit on an annotation type element. */
    private static final Set<Modifier.ModifierKeyword> ANNOTATION_ELEMENT_MODIFIERS = Set.of(
            Modifier.ModifierKeyword.PUBLIC_KEYWORD,
            Modifier.ModifierKeyword.ABSTRACT_KEYWORD);

    /** Modifiers implicit on a member type declared in an interface-like type. */
    private static final Set<Modifier.ModifierKeyword> INTERFACE_MEMBER_TYPE_MODIFIERS = Set.of(
            Modifier.ModifierKeyword.PUBLIC_KEYWORD,
            Modifier.ModifierKeyword.STATIC_KEYWORD);

    /** The modifier implicit on an interface method when explicitly declared abstract. */
    private static final Set<Modifier.ModifierKeyword> INTERFACE_METHOD_MODIFIERS = Set.of(
            Modifier.ModifierKeyword.PUBLIC_KEYWORD,
            Modifier.ModifierKeyword.ABSTRACT_KEYWORD);

    /**
     * Returns the stable identifier for redundant-modifier cleanup.
     *
     * @return the redundant-modifier rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records removals for modifiers that are implicit in the visited declaration context.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one redundant modifier is scheduled for removal
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Removes implicit public, static, and final modifiers from interface fields.
             *
             * @param node the visited field declaration
             * @return {@code true} to continue visiting the field's children
             */
            @Override
            public boolean visit(FieldDeclaration node) {
                if (node.getParent() instanceof TypeDeclaration parent && parent.isInterface()) {
                    changed[0] |= removeModifiers(node, INTERFACE_FIELD_MODIFIERS, rewrite);
                }
                return true;
            }

            /**
             * Removes implicit public and explicit abstract modifiers from interface methods.
             *
             * @param node the visited method declaration
             * @return {@code true} to continue visiting the method's children
             */
            @Override
            public boolean visit(MethodDeclaration node) {
                if (node.getParent() instanceof TypeDeclaration parent && parent.isInterface()) {
                    changed[0] |= removeInterfaceMethodModifiers(node, rewrite);
                }
                return true;
            }

            /**
             * Removes the implicit public and abstract modifiers from an annotation element.
             *
             * @param node the visited annotation type element
             * @return {@code true} to continue visiting the element's children
             */
            @Override
            public boolean visit(AnnotationTypeMemberDeclaration node) {
                if (node.getParent() instanceof AnnotationTypeDeclaration) {
                    changed[0] |= removeModifiers(node, ANNOTATION_ELEMENT_MODIFIERS, rewrite);
                }
                return true;
            }

            /**
             * Removes implicit public and static modifiers from interface-like member classes.
             *
             * @param node the visited class or interface declaration
             * @return {@code true} to continue visiting nested declarations
             */
            @Override
            public boolean visit(TypeDeclaration node) {
                changed[0] |= removeMemberTypeModifiers(node, rewrite);
                return true;
            }

            /**
             * Removes implicit public and static modifiers from interface-like member enums.
             *
             * @param node the visited enum declaration
             * @return {@code true} to continue visiting nested declarations
             */
            @Override
            public boolean visit(EnumDeclaration node) {
                changed[0] |= removeMemberTypeModifiers(node, rewrite);
                return true;
            }

            /**
             * Removes implicit public and static modifiers from nested annotation types.
             *
             * @param node the visited annotation type declaration
             * @return {@code true} to continue visiting nested declarations
             */
            @Override
            public boolean visit(AnnotationTypeDeclaration node) {
                changed[0] |= removeMemberTypeModifiers(node, rewrite);
                return true;
            }

            /**
             * Removes implicit public and static modifiers from interface-like member records.
             *
             * @param node the visited record declaration
             * @return {@code true} to continue visiting nested declarations
             */
            @Override
            public boolean visit(RecordDeclaration node) {
                changed[0] |= removeMemberTypeModifiers(node, rewrite);
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Removes member-type modifiers when the direct parent makes them implicit.
     *
     * @param declaration the visited type declaration
     * @param rewrite the rewrite collecting source edits
     * @return {@code true} when at least one modifier is scheduled for removal
     */
    private boolean removeMemberTypeModifiers(
            AbstractTypeDeclaration declaration,
            ASTRewrite rewrite) {
        if (!isInterfaceLike(declaration.getParent())) {
            return false;
        }
        return removeModifiers(declaration, INTERFACE_MEMBER_TYPE_MODIFIERS, rewrite);
    }

    /**
     * Removes the modifiers that are redundant on an interface method.
     *
     * @param declaration the interface method declaration
     * @param rewrite the rewrite collecting source edits
     * @return {@code true} when at least one modifier is scheduled for removal
     */
    private boolean removeInterfaceMethodModifiers(
            MethodDeclaration declaration,
            ASTRewrite rewrite) {
        Set<Modifier.ModifierKeyword> redundantModifiers = INTERFACE_METHOD_MODIFIERS;
        if (!hasModifier(declaration, Modifier.ModifierKeyword.ABSTRACT_KEYWORD)) {
            redundantModifiers = Set.of(Modifier.ModifierKeyword.PUBLIC_KEYWORD);
        }
        return removeModifiers(declaration, redundantModifiers, rewrite);
    }

    /**
     * Removes selected keyword modifiers from a body declaration.
     *
     * @param declaration the declaration whose modifiers are inspected
     * @param redundantModifiers modifier keywords proven redundant for the declaration
     * @param rewrite the rewrite collecting source edits
     * @return {@code true} when at least one modifier is scheduled for removal
     */
    private boolean removeModifiers(
            BodyDeclaration declaration,
            Set<Modifier.ModifierKeyword> redundantModifiers,
            ASTRewrite rewrite) {
        boolean changed = false;
        for (Object modifierObject : declaration.modifiers()) {
            if (!(modifierObject instanceof Modifier modifier)
                    || !redundantModifiers.contains(modifier.getKeyword())) {
                continue;
            }
            rewrite.remove(modifier, null);
            changed = true;
        }
        return changed;
    }

    /**
     * Checks whether a declaration is directly contained by an interface or annotation type.
     *
     * @param parent the declaration's direct parent node
     * @return {@code true} when the parent makes member type modifiers implicitly public static
     */
    private boolean isInterfaceLike(ASTNode parent) {
        return parent instanceof TypeDeclaration typeDeclaration && typeDeclaration.isInterface()
                || parent instanceof AnnotationTypeDeclaration;
    }

    /**
     * Checks whether a body declaration contains a selected modifier keyword.
     *
     * @param declaration the declaration whose modifiers are inspected
     * @param keyword the modifier keyword to find
     * @return {@code true} when the declaration explicitly contains the keyword
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
