package io.github.lhozdroid.opencleanup.rule;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Replaces safe fully qualified type names with imports and simple names.
 *
 * <p>The rule deliberately leaves ambiguous names qualified. Without a configured classpath, a
 * source-only rewrite cannot prove which type an existing simple name denotes, so explicit import
 * collisions and repeated simple names are treated conservatively.</p>
 */
public final class UseSimpleTypeNamesRule implements CleanupRule {

    /** The stable identifier for fully qualified type-name cleanup. */
    public static final String ID = "imports.use-simple-names";

    /**
     * Returns the stable identifier for simple type-name cleanup.
     *
     * @return the simple type-name rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records imports and type-name replacements for safe fully qualified type references.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one type name is shortened
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        SourceTypes sourceTypes = collectSourceTypes(compilationUnit);
        Map<String, String> shortenings = findSafeShortenings(sourceTypes);
        if (shortenings.isEmpty()) {
            return false;
        }

        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Shortens one fully qualified type reference when its simple name is safe.
             *
             * @param node the visited simple type
             * @return {@code true} to continue visiting child nodes
             */
            @Override
            public boolean visit(SimpleType node) {
                changed[0] |= replaceTypeName(node.getName(), shortenings, rewrite);
                return true;
            }

            /**
             * Shortens one fully qualified marker-annotation type when it is safe.
             *
             * @param node the visited marker annotation
             * @return {@code true} to continue visiting annotation children
             */
            @Override
            public boolean visit(MarkerAnnotation node) {
                changed[0] |= replaceTypeName(node.getTypeName(), shortenings, rewrite);
                return true;
            }

            /**
             * Shortens one fully qualified normal-annotation type when it is safe.
             *
             * @param node the visited normal annotation
             * @return {@code true} to continue visiting annotation children
             */
            @Override
            public boolean visit(NormalAnnotation node) {
                changed[0] |= replaceTypeName(node.getTypeName(), shortenings, rewrite);
                return true;
            }

            /**
             * Shortens one fully qualified single-member annotation type when it is safe.
             *
             * @param node the visited single-member annotation
             * @return {@code true} to continue visiting annotation children
             */
            @Override
            public boolean visit(SingleMemberAnnotation node) {
                changed[0] |= replaceTypeName(node.getTypeName(), shortenings, rewrite);
                return true;
            }
        });

        if (!changed[0]) {
            return false;
        }
        for (Map.Entry<String, String> shortening : shortenings.entrySet()) {
            addImportIfNeeded(compilationUnit, rewrite, shortening.getKey());
        }
        return true;
    }

    /**
     * Collects qualified and unqualified type names that affect safe shortening decisions.
     *
     * @param compilationUnit the compilation unit to inspect
     * @return source type information used by the rewrite
     */
    private SourceTypes collectSourceTypes(CompilationUnit compilationUnit) {
        Map<String, Set<String>> qualifiedTypes = new LinkedHashMap<>();
        Set<String> unqualifiedTypes = new HashSet<>();
        Map<String, Set<String>> explicitImports = new HashMap<>();
        Set<String> declaredTypes = new HashSet<>();
        Set<String> typeParameters = new HashSet<>();

        for (Object value : compilationUnit.imports()) {
            ImportDeclaration importDeclaration = (ImportDeclaration) value;
            if (!importDeclaration.isStatic() && !importDeclaration.isOnDemand()) {
                addName(explicitImports,
                        simpleName(importDeclaration.getName().getFullyQualifiedName()),
                        importDeclaration.getName().getFullyQualifiedName());
            }
        }

        compilationUnit.accept(new ASTVisitor() {
            /**
             * Collects each declared type that may shadow an import.
             *
             * @param node the visited AST node
             * @return {@code true} to continue visiting the node
             */
            @Override
            public boolean preVisit2(ASTNode node) {
                if (node instanceof AbstractTypeDeclaration declaration) {
                    declaredTypes.add(declaration.getName().getIdentifier());
                }
                return true;
            }

            /**
             * Collects a type parameter that may shadow an imported type.
             *
             * @param node the visited type parameter
             * @return {@code true} to continue visiting its bounds
             */
            @Override
            public boolean visit(TypeParameter node) {
                typeParameters.add(node.getName().getIdentifier());
                return true;
            }

            /**
             * Collects a type name from a simple type node.
             *
             * @param node the visited simple type
             * @return {@code true} to continue visiting child nodes
             */
            @Override
            public boolean visit(SimpleType node) {
                collectTypeName(node.getName(), qualifiedTypes, unqualifiedTypes);
                return true;
            }

            /**
             * Collects a type name from a marker annotation.
             *
             * @param node the visited marker annotation
             * @return {@code true} to continue visiting annotation children
             */
            @Override
            public boolean visit(MarkerAnnotation node) {
                collectTypeName(node.getTypeName(), qualifiedTypes, unqualifiedTypes);
                return true;
            }

            /**
             * Collects a type name from a normal annotation.
             *
             * @param node the visited normal annotation
             * @return {@code true} to continue visiting annotation children
             */
            @Override
            public boolean visit(NormalAnnotation node) {
                collectTypeName(node.getTypeName(), qualifiedTypes, unqualifiedTypes);
                return true;
            }

            /**
             * Collects a type name from a single-member annotation.
             *
             * @param node the visited single-member annotation
             * @return {@code true} to continue visiting annotation children
             */
            @Override
            public boolean visit(SingleMemberAnnotation node) {
                collectTypeName(node.getTypeName(), qualifiedTypes, unqualifiedTypes);
                return true;
            }
        });

        return new SourceTypes(
                qualifiedTypes,
                unqualifiedTypes,
                explicitImports,
                declaredTypes,
                typeParameters);
    }

    /**
     * Collects one source type name by classifying its qualification.
     *
     * @param name the type name to classify
     * @param qualifiedTypes qualified type names indexed by simple name
     * @param unqualifiedTypes simple type names already used in source
     */
    private void collectTypeName(
            Name name,
            Map<String, Set<String>> qualifiedTypes,
            Set<String> unqualifiedTypes) {
        TypeReference reference = typeReference(name);
        if (reference != null) {
            qualifiedTypes.computeIfAbsent(reference.simpleName(), ignored -> new LinkedHashSet<>())
                    .add(reference.qualifiedName());
        } else if (name instanceof SimpleName) {
            unqualifiedTypes.add(name.getFullyQualifiedName());
        }
    }

    /**
     * Finds qualified types whose simple names can be used without changing resolution.
     *
     * @param sourceTypes the source type information collected from the compilation unit
     * @return qualified type names mapped to their safe simple names
     */
    private Map<String, String> findSafeShortenings(SourceTypes sourceTypes) {
        Map<String, String> shortenings = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : sourceTypes.qualifiedTypes().entrySet()) {
            String simpleName = entry.getKey();
            Set<String> qualifiedNames = entry.getValue();
            Set<String> importedNames = sourceTypes.explicitImports().getOrDefault(
                    simpleName,
                    Set.of());

            if (sourceTypes.declaredTypes().contains(simpleName)
                    || sourceTypes.typeParameters().contains(simpleName)) {
                continue;
            }

            if (importedNames.size() == 1) {
                String importedName = importedNames.iterator().next();
                if (qualifiedNames.contains(importedName)) {
                    shortenings.put(importedName, simpleName);
                }
                continue;
            }
            if (!importedNames.isEmpty() || qualifiedNames.size() != 1) {
                continue;
            }

            String qualifiedName = qualifiedNames.iterator().next();
            TypeReference reference = typeReference(qualifiedName);
            if (reference == null || sourceTypes.unqualifiedTypes().contains(simpleName)) {
                continue;
            }
            shortenings.put(qualifiedName, simpleName);
        }
        return shortenings;
    }

    /**
     * Replaces a qualified type or annotation name with its safe simple name.
     *
     * @param name the AST name to inspect
     * @param shortenings qualified names mapped to simple names
     * @param rewrite the rewrite collecting source edits
     * @return {@code true} when a replacement was recorded
     */
    private boolean replaceTypeName(
            Name name,
            Map<String, String> shortenings,
            ASTRewrite rewrite) {
        String simpleName = shortenings.get(name.getFullyQualifiedName());
        if (simpleName == null) {
            return false;
        }
        rewrite.replace(name, name.getAST().newSimpleName(simpleName), null);
        return true;
    }

    /**
     * Adds one ordinary import unless Java already provides it implicitly or explicitly.
     *
     * @param compilationUnit the compilation unit receiving the import
     * @param rewrite the rewrite collecting source edits
     * @param qualifiedName the type name to import
     */
    private void addImportIfNeeded(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            String qualifiedName) {
        TypeReference reference = typeReference(qualifiedName);
        if (reference == null
                || "java.lang".equals(reference.packageName())
                || reference.packageName().equals(packageName(compilationUnit))
                || hasExplicitImport(compilationUnit, qualifiedName)) {
            return;
        }

        AST ast = compilationUnit.getAST();
        ImportDeclaration importDeclaration = ast.newImportDeclaration();
        importDeclaration.setName(ast.newName(qualifiedName));
        ListRewrite imports = rewrite.getListRewrite(compilationUnit, CompilationUnit.IMPORTS_PROPERTY);
        ImportDeclaration firstStaticImport = null;
        ImportDeclaration lastOrdinaryImport = null;
        for (Object value : compilationUnit.imports()) {
            ImportDeclaration current = (ImportDeclaration) value;
            if (current.isStatic()) {
                if (firstStaticImport == null) {
                    firstStaticImport = current;
                }
            } else {
                lastOrdinaryImport = current;
            }
        }

        if (lastOrdinaryImport != null) {
            imports.insertAfter(importDeclaration, lastOrdinaryImport, null);
        } else if (firstStaticImport != null) {
            imports.insertBefore(importDeclaration, firstStaticImport, null);
        } else {
            imports.insertLast(importDeclaration, null);
        }
    }

    /**
     * Checks whether a non-static explicit import already names the supplied type.
     *
     * @param compilationUnit the compilation unit to inspect
     * @param qualifiedName the type name to find
     * @return {@code true} when the type is already explicitly imported
     */
    private boolean hasExplicitImport(CompilationUnit compilationUnit, String qualifiedName) {
        for (Object value : compilationUnit.imports()) {
            ImportDeclaration importDeclaration = (ImportDeclaration) value;
            if (!importDeclaration.isStatic()
                    && !importDeclaration.isOnDemand()
                    && qualifiedName.equals(importDeclaration.getName().getFullyQualifiedName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds one value to a set-valued map.
     *
     * @param values the map to update
     * @param key the map key
     * @param value the value to add
     */
    private void addName(Map<String, Set<String>> values, String key, String value) {
        values.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(value);
    }

    /**
     * Parses one qualified name into a type reference using Java's conventional package casing.
     *
     * @param name the qualified name to inspect
     * @return the type reference, or {@code null} when the name is not clearly fully qualified
     */
    private TypeReference typeReference(Name name) {
        return typeReference(name.getFullyQualifiedName());
    }

    /**
     * Parses one qualified name into a type reference using Java's conventional package casing.
     *
     * @param qualifiedName the qualified name to inspect
     * @return the type reference, or {@code null} when the name is not clearly fully qualified
     */
    private TypeReference typeReference(String qualifiedName) {
        String[] components = qualifiedName.split("\\.");
        if (components.length < 2) {
            return null;
        }

        int firstTypeComponent = -1;
        for (int index = 0; index < components.length; index++) {
            if (!components[index].isEmpty()
                    && Character.isUpperCase(components[index].codePointAt(0))) {
                firstTypeComponent = index;
                break;
            }
        }
        if (firstTypeComponent <= 0) {
            return null;
        }

        String packageName = String.join(".", java.util.Arrays.copyOf(
                components,
                firstTypeComponent));
        return new TypeReference(
                qualifiedName,
                components[components.length - 1],
                packageName);
    }

    /**
     * Returns the simple final component of a qualified name.
     *
     * @param qualifiedName the qualified name to inspect
     * @return the final name component
     */
    private String simpleName(String qualifiedName) {
        return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
    }

    /**
     * Returns the declared package name of a compilation unit.
     *
     * @param compilationUnit the compilation unit to inspect
     * @return the package name, or an empty string for the unnamed package
     */
    private String packageName(CompilationUnit compilationUnit) {
        return compilationUnit.getPackage() == null
                ? ""
                : compilationUnit.getPackage().getName().getFullyQualifiedName();
    }

    /**
     * Stores source names needed to decide whether shortening is safe.
     *
     * @param qualifiedTypes qualified type names indexed by simple name
     * @param unqualifiedTypes simple type names already used in source
     * @param explicitImports explicit non-static imports indexed by simple name
     * @param declaredTypes type names declared in the compilation unit
     * @param typeParameters type parameter names declared in the compilation unit
     */
    private record SourceTypes(
            Map<String, Set<String>> qualifiedTypes,
            Set<String> unqualifiedTypes,
            Map<String, Set<String>> explicitImports,
            Set<String> declaredTypes,
            Set<String> typeParameters) {
    }

    /**
     * Stores the qualified and simple parts of one fully qualified type name.
     *
     * @param qualifiedName the complete type name
     * @param simpleName the final type component
     * @param packageName the package prefix
     */
    private record TypeReference(String qualifiedName, String simpleName, String packageName) {
    }
}
