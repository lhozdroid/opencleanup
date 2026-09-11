package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Replaces supported separator system-property lookups with Java constants or APIs.
 */
public final class SystemPropertyConstantsRule implements CleanupRule {

    /** The stable identifier used to select this cleanup rule. */
    public static final String ID = "system-properties.constants";

    /**
     * Returns the stable identifier for system-property constant cleanup.
     *
     * @return the system-property constants rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for known platform-separator property lookups.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one lookup is replaced
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Replaces one supported System property lookup.
             *
             * @param node the visited method invocation
             * @return {@code false} after replacement, otherwise {@code true}
             */
            @Override
            public boolean visit(MethodInvocation node) {
                if (!(node.getExpression() instanceof SimpleName system)
                        || !"System".equals(system.getIdentifier())
                        || !"getProperty".equals(node.getName().getIdentifier())
                        || node.arguments().size() != 1
                        || !(node.arguments().get(0) instanceof StringLiteral property)) {
                    return true;
                }
                String propertyName = property.getLiteralValue();
                if ("line.separator".equals(propertyName)) {
                    rewrite.replace(node, lineSeparator(node.getAST()), null);
                    changed[0] = true;
                } else if ("file.separator".equals(propertyName)
                        || "path.separator".equals(propertyName)) {
                    rewrite.replace(node, fileConstant(node.getAST(), propertyName), null);
                    ensureFileImport((CompilationUnit) node.getRoot(), rewrite);
                    changed[0] = true;
                } else {
                    return true;
                }
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Creates a call to the Java 7 line-separator API.
     *
     * @param ast the AST owning the replacement
     * @return the line-separator method invocation
     */
    private MethodInvocation lineSeparator(AST ast) {
        MethodInvocation replacement = ast.newMethodInvocation();
        replacement.setExpression(ast.newSimpleName("System"));
        replacement.setName(ast.newSimpleName("lineSeparator"));
        return replacement;
    }

    /**
     * Creates a File separator constant reference.
     *
     * @param ast the AST owning the replacement
     * @param propertyName the supported property name
     * @return the File constant reference
     */
    private QualifiedName fileConstant(AST ast, String propertyName) {
        String constant = "file.separator".equals(propertyName) ? "separator" : "pathSeparator";
        return ast.newQualifiedName(ast.newSimpleName("File"), ast.newSimpleName(constant));
    }

    /**
     * Adds the java.io.File import unless the source already provides it.
     *
     * @param compilationUnit the compilation unit receiving the import
     * @param rewrite the AST rewrite collecting source edits
     */
    private void ensureFileImport(CompilationUnit compilationUnit, ASTRewrite rewrite) {
        for (Object object : compilationUnit.imports()) {
            org.eclipse.jdt.core.dom.ImportDeclaration declaration =
                    (org.eclipse.jdt.core.dom.ImportDeclaration) object;
            if (!declaration.isStatic()
                    && (declaration.isOnDemand()
                    ? "java.io".equals(declaration.getName().getFullyQualifiedName())
                    : "java.io.File".equals(declaration.getName().getFullyQualifiedName()))) {
                return;
            }
        }
        org.eclipse.jdt.core.dom.ImportDeclaration importDeclaration =
                compilationUnit.getAST().newImportDeclaration();
        importDeclaration.setName(compilationUnit.getAST().newName("java.io.File"));
        ListRewrite imports = rewrite.getListRewrite(compilationUnit, CompilationUnit.IMPORTS_PROPERTY);
        imports.insertLast(importDeclaration, null);
    }
}
