package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Replaces direct, unsynchronized {@code StringBuffer} constructions with {@code StringBuilder}.
 */
public final class BufferToBuilderRule implements CleanupRule {

    /** The stable identifier used to select this cleanup rule. */
    public static final String ID = "strings.buffer-to-builder";

    /**
     * Returns the stable identifier for buffer-to-builder cleanup.
     *
     * @return the buffer-to-builder rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for direct StringBuffer declarations.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one declaration is converted
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Converts one local StringBuffer declaration.
             *
             * @param node the visited local declaration
             * @return {@code true} to continue visiting nested expressions
             */
            @Override
            public boolean visit(VariableDeclarationStatement node) {
                if (replaceable(node.getType(), node.fragments())) {
                    replace(node.getAST(), node.getType(), node.fragments(), rewrite);
                    changed[0] = true;
                }
                return true;
            }

            /**
             * Converts one field StringBuffer declaration.
             *
             * @param node the visited field declaration
             * @return {@code true} to continue visiting nested expressions
             */
            @Override
            public boolean visit(FieldDeclaration node) {
                if (replaceable(node.getType(), node.fragments())) {
                    replace(node.getAST(), node.getType(), node.fragments(), rewrite);
                    changed[0] = true;
                }
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Checks whether every fragment is a direct StringBuffer construction.
     *
     * @param type the declaration type
     * @param fragments the declaration fragments
     * @return {@code true} when the complete declaration is safely replaceable
     */
    private boolean replaceable(org.eclipse.jdt.core.dom.Type type, java.util.List<?> fragments) {
        if (!(type instanceof SimpleType simpleType)
                || !"StringBuffer".equals(simpleType.getName().getFullyQualifiedName())) {
            return false;
        }
        for (Object value : fragments) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment) value;
            if (!(fragment.getInitializer() instanceof ClassInstanceCreation creation)
                    || !(creation.getType() instanceof SimpleType creationType)
                    || !"StringBuffer".equals(creationType.getName().getFullyQualifiedName())
                    || creation.getAnonymousClassDeclaration() != null) {
                return false;
            }
        }
        return !fragments.isEmpty();
    }

    /**
     * Replaces the declaration and each matching constructor type with StringBuilder.
     *
     * @param ast the owning AST
     * @param type the declaration type
     * @param fragments the declaration fragments
     * @param rewrite the AST rewrite collecting source edits
     */
    private void replace(
            org.eclipse.jdt.core.dom.AST ast,
            org.eclipse.jdt.core.dom.Type type,
            java.util.List<?> fragments,
            ASTRewrite rewrite) {
        rewrite.replace(type, ast.newSimpleType(ast.newSimpleName("StringBuilder")), null);
        for (Object value : fragments) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment) value;
            ClassInstanceCreation creation = (ClassInstanceCreation) fragment.getInitializer();
            rewrite.replace(creation.getType(), ast.newSimpleType(ast.newSimpleName("StringBuilder")), null);
        }
    }
}
