package io.github.lhozdroid.opencleanup.rule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Organizes imports into normal and static groups with deterministic ordering.
 */
public final class OrganizeImportsRule implements CleanupRule {

    public static final String ID = "imports.organize";

    /**
     * Returns the stable identifier for import organization.
     *
     * @return the import organization rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records import moves needed to place imports in deterministic order.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when import order is changed
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        List<ImportDeclaration> current = new ArrayList<>();
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Collects each import declaration in source order.
             *
             * @param node the visited import declaration
             * @return {@code false} because imports have no relevant child imports
             */
            @Override
            public boolean visit(ImportDeclaration node) {
                current.add(node);
                return false;
            }
        });

        List<ImportDeclaration> sorted = current.stream()
                .sorted(Comparator.comparing(this::sortKey))
                .toList();
        if (current.equals(sorted)) {
            return false;
        }

        ListRewrite imports = rewrite.getListRewrite(compilationUnit, CompilationUnit.IMPORTS_PROPERTY);
        current.forEach(importDeclaration -> imports.remove(importDeclaration, null));
        sorted.forEach(importDeclaration -> imports.insertLast(
                rewrite.createMoveTarget(importDeclaration), null));
        return true;
    }

    /**
     * Creates the ordering key for an import declaration.
     *
     * @param importDeclaration the import to order
     * @return a key that places normal imports before static imports
     */
    private String sortKey(ImportDeclaration importDeclaration) {
        return (importDeclaration.isStatic() ? "1" : "0")
                + importDeclaration.getName().getFullyQualifiedName()
                + (importDeclaration.isOnDemand() ? ".*" : "");
    }
}
