package io.github.lhozdroid.opencleanup.rule;

import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * Detects Lombok declarations whose generated members depend on source fields.
 */
final class LombokSupport {

    private static final Set<String> FIELD_ANNOTATIONS = Set.of(
            "Builder",
            "Data",
            "Delegate",
            "FieldNameConstants",
            "Getter",
            "NoArgsConstructor",
            "Setter",
            "Singular",
            "Value",
            "With");

    private static final Set<String> TYPE_ANNOTATIONS = Set.of(
            "AllArgsConstructor",
            "Builder",
            "Data",
            "Delegate",
            "EqualsAndHashCode",
            "FieldNameConstants",
            "Getter",
            "NoArgsConstructor",
            "RequiredArgsConstructor",
            "Setter",
            "SuperBuilder",
            "ToString",
            "UtilityClass",
            "Value",
            "With");

    private static final Set<String> SETTER_ANNOTATIONS = Set.of("Data", "Setter");

    private static final Set<String> GETTER_ANNOTATIONS = Set.of("Data", "Getter", "Value");

    private static final Set<String> WITH_ANNOTATIONS = Set.of("With");

    private static final Set<String> ACCESSORS_ANNOTATIONS = Set.of("Accessors");

    private static final Set<String> NON_FINAL_ANNOTATIONS = Set.of("NonFinal");

    private static final Set<String> METHOD_ANNOTATIONS = Set.of("Builder", "Delegate", "Include");

    private static final Set<String> NESTED_ANNOTATION_HOLDERS = Set.of(
            "Builder",
            "EqualsAndHashCode",
            "FieldNameConstants",
            "SuperBuilder",
            "ToString");

    /**
     * Creates the utility class.
     */
    private LombokSupport() {
    }

    /**
     * Checks whether Lombok may generate a member that depends on a field.
     *
     * @param field the field declaration to inspect
     * @return {@code true} when the field must be preserved for Lombok-generated behavior
     */
    static boolean isManagedField(FieldDeclaration field) {
        if (hasLombokAnnotation(field, FIELD_ANNOTATIONS)) {
            return true;
        }
        AbstractTypeDeclaration enclosingType = enclosingType(field);
        return enclosingType != null && hasLombokAnnotation(enclosingType, TYPE_ANNOTATIONS);
    }

    /**
     * Checks whether Lombok may generate a setter for a field.
     *
     * @param field the field declaration to inspect
     * @return {@code true} when making the field final could remove a generated setter
     */
    static boolean hasGeneratedSetter(FieldDeclaration field) {
        if (hasLombokAnnotation(field, SETTER_ANNOTATIONS)) {
            return true;
        }
        AbstractTypeDeclaration enclosingType = enclosingType(field);
        return enclosingType != null && hasLombokAnnotation(enclosingType, SETTER_ANNOTATIONS);
    }

    /**
     * Checks whether Lombok explicitly requires a field to remain non-final.
     *
     * @param field the field declaration to inspect
     * @return {@code true} when Lombok's {@code NonFinal} contract applies
     */
    static boolean isExplicitlyNonFinal(FieldDeclaration field) {
        if (hasLombokAnnotation(field, NON_FINAL_ANNOTATIONS)) {
            return true;
        }
        AbstractTypeDeclaration enclosingType = enclosingType(field);
        return enclosingType != null && hasLombokAnnotation(enclosingType, NON_FINAL_ANNOTATIONS);
    }

    /**
     * Checks whether Lombok may generate or expose a private method.
     *
     * @param method the method declaration to inspect
     * @return {@code true} when the method participates in generated Lombok behavior
     */
    static boolean isManagedMethod(MethodDeclaration method) {
        return hasLombokAnnotation(method, METHOD_ANNOTATIONS);
    }

    /**
     * Checks whether Lombok may generate a method matching an inherited contract.
     *
     * @param type the concrete type under consideration
     * @param method the inherited abstract method
     * @return {@code true} when Lombok may already provide the method
     */
    static boolean mayGenerateMethod(TypeDeclaration type, MethodDeclaration method) {
        for (Object bodyDeclaration : type.bodyDeclarations()) {
            if (bodyDeclaration instanceof FieldDeclaration field
                    && mayGenerateMethod(field, method, field, type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether field-level Lombok annotations generate the required method.
     *
     * @param field the candidate source field
     * @param method the inherited method contract
     * @param fieldAnnotations declaration providing field annotations
     * @param typeAnnotations declaration providing type annotations, or {@code null}
     * @return {@code true} when the field annotations can generate the contract
     */
    private static boolean mayGenerateMethod(
            FieldDeclaration field,
            MethodDeclaration method,
            BodyDeclaration fieldAnnotations,
            TypeDeclaration typeAnnotations) {
        if (field.fragments().isEmpty() || Modifier.isStatic(field.getModifiers())) {
            return false;
        }
        boolean getter = hasLombokAnnotation(fieldAnnotations, GETTER_ANNOTATIONS)
                || typeAnnotations != null
                        && hasLombokAnnotation(typeAnnotations, GETTER_ANNOTATIONS);
        boolean setter = hasLombokAnnotation(fieldAnnotations, SETTER_ANNOTATIONS)
                || typeAnnotations != null
                        && hasLombokAnnotation(typeAnnotations, SETTER_ANNOTATIONS);
        boolean with = hasLombokAnnotation(fieldAnnotations, WITH_ANNOTATIONS)
                || typeAnnotations != null
                        && hasLombokAnnotation(typeAnnotations, WITH_ANNOTATIONS);
        boolean fluent = hasLombokAnnotation(fieldAnnotations, ACCESSORS_ANNOTATIONS)
                || typeAnnotations != null
                        && hasLombokAnnotation(typeAnnotations, ACCESSORS_ANNOTATIONS);
        boolean booleanField = field.getType() instanceof PrimitiveType primitive
                && primitive.getPrimitiveTypeCode() == PrimitiveType.BOOLEAN;
        for (Object fragmentObject : field.fragments()) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment) fragmentObject;
            if (matchesGeneratedMethod(
                    fragment.getName().getIdentifier(),
                    method,
                    getter,
                    setter,
                    with,
                    fluent,
                    booleanField,
                    Modifier.isFinal(field.getModifiers()),
                    field.getType())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Matches one Lombok-generated accessor against a method contract.
     *
     * @param fieldName the candidate field name
     * @param method the inherited method contract
     * @param getter whether a getter may be generated
     * @param setter whether a setter may be generated
     * @param with whether a wither may be generated
     * @param fluent whether fluent accessor names may be generated
     * @param booleanField whether the field has primitive boolean type
     * @param finalField whether the field is final
     * @param fieldType the declared field type
     * @return {@code true} when Lombok may generate the method
     */
    private static boolean matchesGeneratedMethod(
            String fieldName,
            MethodDeclaration method,
            boolean getter,
            boolean setter,
            boolean with,
            boolean fluent,
            boolean booleanField,
            boolean finalField,
            Type fieldType) {
        String methodName = method.getName().getIdentifier();
        String capitalized = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        if (getter && method.parameters().isEmpty()
                && method.getReturnType2() != null
                && fieldType.toString().equals(method.getReturnType2().toString())
                && (methodName.equals("get" + capitalized)
                        || booleanField && methodName.equals("is" + capitalized)
                        || fluent && methodName.equals(fieldName))) {
            return true;
        }
        if (setter && !finalField
                && method.parameters().size() == 1
                && method.parameters().get(0) instanceof SingleVariableDeclaration parameter
                && fieldType.toString().equals(parameter.getType().toString())
                && (methodName.equals("set" + capitalized)
                        || fluent && methodName.equals(fieldName))) {
            return true;
        }
        return with
                && method.parameters().size() == 1
                && (methodName.equals("with" + capitalized)
                        || fluent && methodName.equals(fieldName));
    }

    /**
     * Checks whether a declaration carries any annotation that may be consumed by a framework.
     *
     * @param declaration the declaration to inspect
     * @return {@code true} when at least one annotation is present
     */
    static boolean hasAnyAnnotation(BodyDeclaration declaration) {
        return declaration.modifiers().stream().anyMatch(Annotation.class::isInstance);
    }

    /**
     * Checks whether a field or method belongs to an annotated enclosing type.
     *
     * @param node the member whose enclosing type should be inspected
     * @return {@code true} when an enclosing type carries an annotation
     */
    static boolean hasAnyEnclosingTypeAnnotation(ASTNode node) {
        AbstractTypeDeclaration enclosingType = enclosingType(node);
        return enclosingType != null && hasAnyAnnotation(enclosingType);
    }

    /**
     * Checks whether another member in the enclosing type is annotation-driven.
     *
     * @param node the member whose enclosing type should be inspected
     * @return {@code true} when an annotated member may depend on field shape
     */
    static boolean hasAnyAnnotatedMember(ASTNode node) {
        AbstractTypeDeclaration enclosingType = enclosingType(node);
        if (enclosingType == null) {
            return false;
        }
        for (Object value : enclosingType.bodyDeclarations()) {
            if (value instanceof BodyDeclaration declaration && hasAnyAnnotation(declaration)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the nearest enclosing type declaration.
     *
     * @param node the node whose enclosing type is required
     * @return the enclosing type declaration, or {@code null} when none exists
     */
    private static AbstractTypeDeclaration enclosingType(ASTNode node) {
        ASTNode current = node.getParent();
        while (current != null
                && !(current instanceof AbstractTypeDeclaration)
                && !(current instanceof AnonymousClassDeclaration)) {
            current = current.getParent();
        }
        if (current instanceof AnonymousClassDeclaration) {
            return null;
        }
        return (AbstractTypeDeclaration) current;
    }

    /**
     * Checks a declaration for one of the supported Lombok annotations.
     *
     * @param declaration the declaration to inspect
     * @param annotationNames supported annotation simple names
     * @return {@code true} when a supported Lombok annotation is present
     */
    private static boolean hasLombokAnnotation(
            BodyDeclaration declaration,
            Set<String> annotationNames) {
        for (Object modifier : declaration.modifiers()) {
            if (!(modifier instanceof Annotation annotation)) {
                continue;
            }
            String annotationName = annotation.getTypeName().getFullyQualifiedName();
            String simpleName = annotationName.substring(annotationName.lastIndexOf('.') + 1);
            if (annotationNames.contains(simpleName)
                    && isLombokAnnotation(annotation, annotationName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether an annotation resolves to the Lombok package from source imports or its name.
     *
     * @param annotation the annotation to inspect
     * @param annotationName the annotation's fully qualified or simple source name
     * @return {@code true} when the annotation is imported from Lombok
     */
    private static boolean isLombokAnnotation(Annotation annotation, String annotationName) {
        if (annotationName.startsWith("lombok.")) {
            String simpleName = annotationName.substring(annotationName.lastIndexOf('.') + 1);
            String suffix = annotationName.substring("lombok.".length());
            int separator = suffix.indexOf('.');
            if (separator < 0) {
                return true;
            }
            if (("experimental." + simpleName).equals(suffix)) {
                return true;
            }
            String topLevelName = suffix.substring(0, separator);
            return NESTED_ANNOTATION_HOLDERS.contains(topLevelName)
                    && (topLevelName + "." + simpleName).equals(suffix);
        }
        if (!(annotation.getRoot() instanceof CompilationUnit compilationUnit)) {
            return false;
        }
        for (Object value : compilationUnit.imports()) {
            ImportDeclaration importDeclaration = (ImportDeclaration) value;
            String importedName = importDeclaration.getName().getFullyQualifiedName();
            String topLevelName = annotationName.contains(".")
                    ? annotationName.substring(0, annotationName.indexOf('.'))
                    : annotationName;
            if (!importDeclaration.isStatic()
                    && (isLombokTypeImport(importDeclaration, importedName, annotationName)
                            || isLombokTypeImport(importDeclaration, importedName, topLevelName))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether an import supplies a Lombok annotation type.
     *
     * @param importDeclaration the source import
     * @param importedName the imported source name
     * @param annotationName the annotation name to resolve
     * @return {@code true} when the import is a matching Lombok import
     */
    private static boolean isLombokTypeImport(
            ImportDeclaration importDeclaration,
            String importedName,
            String annotationName) {
        return ("lombok." + annotationName).equals(importedName)
                || ("lombok.experimental." + annotationName).equals(importedName)
                || importDeclaration.isOnDemand()
                        && ("lombok".equals(importedName)
                                || "lombok.experimental".equals(importedName));
    }
}
