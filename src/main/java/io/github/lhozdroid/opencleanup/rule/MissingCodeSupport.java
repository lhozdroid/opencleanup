package io.github.lhozdroid.opencleanup.rule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

/** Shared conservative source-local helpers for missing-code cleanup rules. */
final class MissingCodeSupport {

    /** The marker name used by the override rules. */
    static final String OVERRIDE = "Override";

    /** The marker name used by the deprecated rule. */
    static final String DEPRECATED = "Deprecated";

    /** Creates a support helper without exposing it as plugin API. */
    private MissingCodeSupport() {
    }

    /**
     * Collects named type declarations in one compilation unit.
     *
     * @param compilationUnit the source unit to inspect
     * @return named types indexed by their simple name
     */
    static Map<String, AbstractTypeDeclaration> collectTypes(CompilationUnit compilationUnit) {
        Map<String, AbstractTypeDeclaration> types = new HashMap<>();
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Records a named type declaration.
             *
             * @param node the visited type declaration
             * @return {@code true} to continue visiting nested declarations
             */
            @Override
            public boolean visit(TypeDeclaration node) {
                types.putIfAbsent(node.getName().getIdentifier(), node);
                return true;
            }

            /**
             * Records a named enum declaration.
             *
             * @param node the visited enum declaration
             * @return {@code true} to continue visiting nested declarations
             */
            @Override
            public boolean visit(EnumDeclaration node) {
                types.putIfAbsent(node.getName().getIdentifier(), node);
                return true;
            }

            /**
             * Records a named annotation declaration.
             *
             * @param node the visited annotation declaration
             * @return {@code true} to continue visiting nested declarations
             */
            @Override
            public boolean visit(AnnotationTypeDeclaration node) {
                types.putIfAbsent(node.getName().getIdentifier(), node);
                return true;
            }
        });
        return types;
    }

    /**
     * Returns the simple name at the end of a type reference.
     *
     * @param type the referenced type
     * @return the final identifier, or {@code null} when the type is unavailable
     */
    static String referencedTypeName(Type type) {
        if (!(type instanceof SimpleType simpleType)) {
            return null;
        }
        Name name = simpleType.getName();
        String qualified = name.getFullyQualifiedName();
        int separator = qualified.lastIndexOf('.');
        return separator < 0 ? qualified : qualified.substring(separator + 1);
    }

    /**
     * Returns methods declared directly by a type.
     *
     * @param type the type whose methods are requested
     * @return method declarations directly contained by the type
     */
    static List<MethodDeclaration> declaredMethods(AbstractTypeDeclaration type) {
        List<MethodDeclaration> methods = new ArrayList<>();
        if (type instanceof TypeDeclaration declaration) {
            for (MethodDeclaration method : declaration.getMethods()) {
                methods.add(method);
            }
        } else if (type instanceof EnumDeclaration declaration) {
            for (Object member : declaration.bodyDeclarations()) {
                if (member instanceof MethodDeclaration method) {
                    methods.add(method);
                }
            }
        } else if (type instanceof AnnotationTypeDeclaration declaration) {
            for (Object member : declaration.bodyDeclarations()) {
                if (member instanceof MethodDeclaration method) {
                    methods.add(method);
                }
            }
        }
        return methods;
    }

    /**
     * Returns direct parent types that can provide inherited methods.
     *
     * @param type the type whose parents are requested
     * @param types source-local type declarations
     * @return source-local direct superclass and interface declarations
     */
    static List<AbstractTypeDeclaration> directParents(
            AbstractTypeDeclaration type,
            Map<String, AbstractTypeDeclaration> types) {
        List<AbstractTypeDeclaration> parents = new ArrayList<>();
        if (type instanceof TypeDeclaration declaration) {
            addType(types, parents, declaration.getSuperclassType());
            for (Object interfaceType : declaration.superInterfaceTypes()) {
                addType(types, parents, (Type) interfaceType);
            }
        } else if (type instanceof EnumDeclaration declaration) {
            for (Object interfaceType : declaration.superInterfaceTypes()) {
                addType(types, parents, (Type) interfaceType);
            }
        }
        return parents;
    }

    /**
     * Finds all source-local inherited methods for a type.
     *
     * @param type the type whose ancestors are inspected
     * @param types source-local type declarations
     * @return inherited methods keyed by their conservative signature
     */
    static Map<String, MethodDeclaration> inheritedMethods(
            AbstractTypeDeclaration type,
            Map<String, AbstractTypeDeclaration> types) {
        Map<String, MethodDeclaration> methods = new HashMap<>();
        collectInherited(type, types, methods, new HashSet<>());
        return methods;
    }

    /**
     * Recursively collects methods from source-local ancestors.
     *
     * @param type the current ancestor
     * @param types source-local type declarations
     * @param methods the accumulated method map
     * @param visited names already traversed
     */
    private static void collectInherited(
            AbstractTypeDeclaration type,
            Map<String, AbstractTypeDeclaration> types,
            Map<String, MethodDeclaration> methods,
            Set<String> visited) {
        String typeName = type.getName().getIdentifier();
        if (!visited.add(typeName)) {
            return;
        }
        for (AbstractTypeDeclaration parent : directParents(type, types)) {
            for (MethodDeclaration method : declaredMethods(parent)) {
                if (isInheritableMethod(method)) {
                    methods.putIfAbsent(signature(method), method);
                }
            }
            collectInherited(parent, types, methods, visited);
        }
    }

    /**
     * Determines whether a method can be inherited by a source-local child.
     *
     * @param method the method declaration to inspect
     * @return {@code true} for non-static, non-private methods
     */
    static boolean isInheritableMethod(MethodDeclaration method) {
        return !method.isConstructor()
                && !hasModifier(method, Modifier.ModifierKeyword.STATIC_KEYWORD)
                && !hasModifier(method, Modifier.ModifierKeyword.PRIVATE_KEYWORD);
    }

    /**
     * Determines whether a method declaration is abstract or an interface contract.
     *
     * @param method the method declaration to inspect
     * @param parent the declaration containing the method
     * @return {@code true} when the method requires an implementation
     */
    static boolean isAbstractContract(MethodDeclaration method, AbstractTypeDeclaration parent) {
        if (method.isConstructor() || hasModifier(method, Modifier.ModifierKeyword.STATIC_KEYWORD)
                || hasModifier(method, Modifier.ModifierKeyword.PRIVATE_KEYWORD)) {
            return false;
        }
        if (hasModifier(method, Modifier.ModifierKeyword.ABSTRACT_KEYWORD)) {
            return true;
        }
        return parent instanceof TypeDeclaration declaration
                && declaration.isInterface()
                && method.getBody() == null
                && !hasModifier(method, Modifier.ModifierKeyword.DEFAULT_KEYWORD);
    }

    /**
     * Builds a conservative method signature from name, parameter types, and varargs state.
     *
     * @param method the method declaration to identify
     * @return a source-level method signature
     */
    static String signature(MethodDeclaration method) {
        StringBuilder signature = new StringBuilder(method.getName().getIdentifier()).append('(');
        for (Object parameterObject : method.parameters()) {
            SingleVariableDeclaration parameter = (SingleVariableDeclaration) parameterObject;
            signature.append(parameter.getType()).append(parameter.isVarargs() ? "..." : "").append(';');
        }
        return signature.append(')').toString();
    }

    /**
     * Checks whether a declaration has a modifier with the requested keyword.
     *
     * @param declaration the declaration to inspect
     * @param keyword the modifier keyword to find
     * @return {@code true} when the modifier is present
     */
    static boolean hasModifier(BodyDeclaration declaration, Modifier.ModifierKeyword keyword) {
        for (Object modifierObject : declaration.modifiers()) {
            if (modifierObject instanceof Modifier modifier && modifier.getKeyword() == keyword) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether a declaration already has a marker annotation by simple name.
     *
     * @param declaration the declaration to inspect
     * @param annotationName the marker annotation simple name
     * @return {@code true} when the annotation is already present
     */
    static boolean hasAnnotation(BodyDeclaration declaration, String annotationName) {
        for (Object modifierObject : declaration.modifiers()) {
            if (modifierObject instanceof Annotation annotation
                    && simpleName(annotation.getTypeName().getFullyQualifiedName())
                            .equals(annotationName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the simple segment of a possibly qualified annotation name.
     *
     * @param qualifiedName the annotation type name
     * @return the final name segment
     */
    private static String simpleName(String qualifiedName) {
        int separator = qualifiedName.lastIndexOf('.');
        return separator < 0 ? qualifiedName : qualifiedName.substring(separator + 1);
    }

    /**
     * Adds a marker annotation to a body declaration unless it is already present.
     *
     * @param declaration the declaration receiving the marker
     * @param annotationName the marker annotation simple name
     * @param rewrite the AST rewrite collecting source edits
     * @return {@code true} when an annotation insertion is scheduled
     */
    static boolean addMarkerAnnotation(
            BodyDeclaration declaration,
            String annotationName,
            ASTRewrite rewrite) {
        if (hasAnnotation(declaration, annotationName)) {
            return false;
        }
        Annotation annotation = declaration.getAST().newMarkerAnnotation();
        annotation.setTypeName(declaration.getAST().newSimpleName(annotationName));
        ListRewrite modifiers = rewrite.getListRewrite(declaration, modifiersProperty(declaration));
        modifiers.insertFirst(annotation, null);
        return true;
    }

    /**
     * Finds the concrete body declaration's modifiers list property.
     *
     * @param declaration the body declaration whose property is needed
     * @return its modifiers child-list descriptor
     * @throws IllegalArgumentException when the declaration has no modifiers list
     */
    private static ChildListPropertyDescriptor modifiersProperty(BodyDeclaration declaration) {
        for (Object descriptorObject : declaration.structuralPropertiesForType()) {
            StructuralPropertyDescriptor descriptor = (StructuralPropertyDescriptor) descriptorObject;
            if (descriptor instanceof ChildListPropertyDescriptor childList
                    && "modifiers".equals(childList.getId())) {
                return childList;
            }
        }
        throw new IllegalArgumentException("Body declaration has no modifiers property");
    }

    /**
     * Resolves a source-local type reference.
     *
     * @param types source-local type declarations
     * @param parents destination list
     * @param type the type reference to resolve
     */
    private static void addType(
            Map<String, AbstractTypeDeclaration> types,
            List<AbstractTypeDeclaration> parents,
            Type type) {
        String name = referencedTypeName(type);
        if (name != null && types.containsKey(name)) {
            parents.add(types.get(name));
        }
    }

    /**
     * Checks whether a type declaration is abstract.
     *
     * @param type the type declaration to inspect
     * @return {@code true} for an explicitly abstract type
     */
    static boolean isAbstractType(AbstractTypeDeclaration type) {
        return type instanceof TypeDeclaration declaration
                && hasModifier(declaration, Modifier.ModifierKeyword.ABSTRACT_KEYWORD);
    }

    /**
     * Checks whether a type reference denotes the standard serialization contract.
     *
     * @param type the type reference to inspect
     * @return {@code true} when its simple name is Serializable
     */
    static boolean isSerializable(Type type) {
        String name = referencedTypeName(type);
        return "Serializable".equals(name);
    }

    /**
     * Checks whether a type has a directly declared field with the requested name.
     *
     * @param type the type to inspect
     * @param fieldName the field name to find
     * @return {@code true} when a matching field exists
     */
    static boolean hasField(AbstractTypeDeclaration type, String fieldName) {
        if (!(type instanceof TypeDeclaration declaration)) {
            return false;
        }
        for (Object bodyObject : declaration.bodyDeclarations()) {
            if (!(bodyObject instanceof org.eclipse.jdt.core.dom.FieldDeclaration field)) {
                continue;
            }
            for (Object fragmentObject : field.fragments()) {
                if (((org.eclipse.jdt.core.dom.VariableDeclarationFragment) fragmentObject)
                        .getName().getIdentifier().equals(fieldName)) {
                    return true;
                }
            }
        }
        return false;
    }
}
