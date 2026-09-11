package io.github.lhozdroid.opencleanup.rule;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Removes unused single-type and single-static imports.
 *
 * <p>Wildcard imports are retained until binding-aware analysis is available. The
 * conservative behavior avoids deleting an import that may provide an unresolved
 * type or static member.</p>
 */
public final class UnusedImportsRule implements CleanupRule {

    public static final String ID = "unused-code.imports";

    /**
     * Returns the stable identifier for unused-import cleanup.
     *
     * @return the unused-import rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records removal edits for unused non-wildcard imports.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one import is scheduled for removal
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        Set<String> usedNames = findUsedNames(compilationUnit);
        ListRewrite imports = rewrite.getListRewrite(compilationUnit, CompilationUnit.IMPORTS_PROPERTY);
        boolean changed = false;

        for (Object value : compilationUnit.imports()) {
            ImportDeclaration importDeclaration = (ImportDeclaration) value;
            if (importDeclaration.isOnDemand()) {
                continue;
            }

            String importedName = importDeclaration.getName().getFullyQualifiedName();
            String simpleName = importedName.substring(importedName.lastIndexOf('.') + 1);
            if (!usedNames.contains(simpleName)) {
                imports.remove(importDeclaration, null);
                changed = true;
            }
        }

        return changed;
    }

    /**
     * Collects simple names used outside import declarations.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @return the simple names found in source code
     */
    private Set<String> findUsedNames(CompilationUnit compilationUnit) {
        Set<String> usedNames = new HashSet<>();
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Skips import declarations so their own names are not counted as uses.
             *
             * @param node the AST node being visited
             * @return {@code false} for imports, otherwise {@code true}
             */
            @Override
            public boolean preVisit2(ASTNode node) {
                return !(node instanceof ImportDeclaration);
            }

            /**
             * Records a simple name encountered in source code.
             *
             * @param node the visited simple-name node
             * @return {@code true} to continue visiting child nodes
             */
            @Override
            public boolean visit(SimpleName node) {
                usedNames.add(node.getIdentifier());
                return true;
            }
        });
        return usedNames;
    }
}
