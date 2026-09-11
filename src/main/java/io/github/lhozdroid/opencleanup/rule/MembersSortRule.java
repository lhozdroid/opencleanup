package io.github.lhozdroid.opencleanup.rule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import org.eclipse.jdt.core.dom.ASTVisitor;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Sorts type members into a stable, conservative declaration order.
 *
 * <p>The default order is static fields, instance fields, static initializers, instance
 * initializers, constructors, methods, and nested types. Members in the same category retain
 * their original order.</p>
 */
public final class MembersSortRule implements CleanupRule {

    /** The stable identifier for member sorting. */
    public static final String ID = "members.sort";

    /** The default member categories, ordered from highest to lowest priority. */
    private static final List<String> DEFAULT_ORDER = List.of(
            "static-fields",
            "instance-fields",
            "static-initializers",
            "instance-initializers",
            "constructors",
            "methods",
            "types");

    /**
     * Returns the stable identifier for member sorting.
     *
     * @return the member-sorting rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records AST moves that sort body declarations in each supported type.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the optional rule configuration
     * @return {@code true} when at least one type's member order changes
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        List<String> memberOrder = configuredOrder(configuration);
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Sorts members declared directly in a class or interface.
             *
             * @param node the visited type declaration
             * @return {@code true} to continue visiting nested declarations
             */
            @Override
            public boolean visit(TypeDeclaration node) {
                changed[0] |= sortMembers(node, node.getBodyDeclarationsProperty(), memberOrder, rewrite);
                return true;
            }

            /**
             * Sorts members declared directly in an enum.
             *
             * @param node the visited enum declaration
             * @return {@code true} to continue visiting nested declarations
             */
            @Override
            public boolean visit(EnumDeclaration node) {
                changed[0] |= sortMembers(node, node.getBodyDeclarationsProperty(), memberOrder, rewrite);
                return true;
            }

            /**
             * Sorts members declared directly in an annotation type.
             *
             * @param node the visited annotation declaration
             * @return {@code true} to continue visiting nested declarations
             */
            @Override
            public boolean visit(AnnotationTypeDeclaration node) {
                changed[0] |= sortMembers(node, node.getBodyDeclarationsProperty(), memberOrder, rewrite);
                return true;
            }

            /**
             * Sorts members declared directly in a record.
             *
             * @param node the visited record declaration
             * @return {@code true} to continue visiting nested declarations
             */
            @Override
            public boolean visit(RecordDeclaration node) {
                changed[0] |= sortMembers(node, node.getBodyDeclarationsProperty(), memberOrder, rewrite);
                return true;
            }

            /**
             * Sorts members declared in an anonymous class.
             *
             * @param node the visited anonymous class declaration
             * @return {@code true} to continue visiting nested declarations
             */
            @Override
            public boolean visit(AnonymousClassDeclaration node) {
                changed[0] |= sortMembers(
                        node,
                        AnonymousClassDeclaration.BODY_DECLARATIONS_PROPERTY,
                        memberOrder,
                        rewrite);
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Sorts one body-declaration list and schedules moves only when its order changes.
     *
     * @param type the owning type declaration
     * @param property the body-declaration list property
     * @param memberOrder the configured category order
     * @param rewrite the rewrite collecting source edits
     * @return {@code true} when the list order changes
     */
    @SuppressWarnings("unchecked")
    private boolean sortMembers(
            ASTNode type,
            org.eclipse.jdt.core.dom.ChildListPropertyDescriptor property,
            List<String> memberOrder,
            ASTRewrite rewrite) {
        List<BodyDeclaration> current = new ArrayList<>((List<BodyDeclaration>) type.getStructuralProperty(property));
        List<BodyDeclaration> sorted = current.stream()
                .sorted(Comparator.comparingInt(member -> memberOrder(member, memberOrder)))
                .toList();
        if (current.equals(sorted)) {
            return false;
        }

        ListRewrite members = rewrite.getListRewrite(type, property);
        current.forEach(member -> members.remove(member, null));
        sorted.forEach(member -> members.insertLast(rewrite.createMoveTarget(member), null));
        return true;
    }

    /**
     * Returns the default Eclipse-style category order for one body declaration.
     *
     * @param declaration the declaration to classify
     * @return the numeric sort category
     */
    private int memberOrder(BodyDeclaration declaration, List<String> memberOrder) {
        String category = memberCategory(declaration);
        int categoryIndex = categoryIndex(category, memberOrder);
        return categoryIndex * DEFAULT_ORDER.size() + DEFAULT_ORDER.indexOf(category);
    }

    /**
     * Returns the configured category index, accepting grouped category names as aliases.
     *
     * @param category the concrete member category
     * @param memberOrder the configured category order
     * @return the category index used for sorting
     */
    private int categoryIndex(String category, List<String> memberOrder) {
        int exactIndex = memberOrder.indexOf(category);
        if (exactIndex >= 0) {
            return exactIndex;
        }
        String group = category.endsWith("-fields")
                ? "fields"
                : category.endsWith("-initializers") ? "initializers" : category;
        int groupIndex = memberOrder.indexOf(group);
        return groupIndex >= 0 ? groupIndex : DEFAULT_ORDER.indexOf(category);
    }

    /**
     * Classifies a body declaration into one of the supported sort categories.
     *
     * @param declaration the declaration to classify
     * @return the concrete member category
     */
    private String memberCategory(BodyDeclaration declaration) {
        if (declaration instanceof org.eclipse.jdt.core.dom.FieldDeclaration field) {
            return hasStaticModifier(field) ? "static-fields" : "instance-fields";
        }
        if (declaration instanceof Initializer initializer) {
            return hasStaticModifier(initializer) ? "static-initializers" : "instance-initializers";
        }
        if (declaration instanceof MethodDeclaration method) {
            return method.isConstructor() ? "constructors" : "methods";
        }
        return "types";
    }

    /**
     * Reads a comma-separated member category order and fills omitted categories with defaults.
     *
     * @param configuration the optional member-sort configuration
     * @return the effective member category order
     */
    private List<String> configuredOrder(RuleConfiguration configuration) {
        if (configuration == null) {
            return DEFAULT_ORDER;
        }
        String configured = configuration.optionValue("order");
        if (configured == null || configured.isBlank()) {
            return DEFAULT_ORDER;
        }

        List<String> order = new ArrayList<>();
        for (String value : configured.split(",")) {
            String category = value.trim().toLowerCase(Locale.ROOT);
            if (isSupportedCategory(category) && !order.contains(category)) {
                order.add(category);
            }
        }
        DEFAULT_ORDER.stream()
                .filter(category -> !order.contains(category))
                .forEach(order::add);
        return List.copyOf(order);
    }

    /**
     * Checks whether a configured category name is supported.
     *
     * @param category the normalized category name
     * @return {@code true} when the category is recognized
     */
    private boolean isSupportedCategory(String category) {
        return DEFAULT_ORDER.contains(category)
                || category.equals("fields")
                || category.equals("initializers");
    }

    /**
     * Checks whether a declaration has the static modifier.
     *
     * @param declaration the body declaration to inspect
     * @return {@code true} when the declaration is static
     */
    private boolean hasStaticModifier(BodyDeclaration declaration) {
        for (Object modifierObject : declaration.modifiers()) {
            if (modifierObject instanceof Modifier modifier
                    && modifier.getKeyword() == Modifier.ModifierKeyword.STATIC_KEYWORD) {
                return true;
            }
        }
        return false;
    }
}
