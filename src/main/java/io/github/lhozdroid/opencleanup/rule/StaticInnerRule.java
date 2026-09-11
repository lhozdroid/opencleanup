package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Makes a member class static when all of its members are already independent of an outer instance.
 */
public final class StaticInnerRule implements CleanupRule {

    /** The stable identifier for static-inner-class cleanup. */
    public static final String ID = "classes.static-inner";

    /**
     * Returns the stable identifier for this cleanup rule.
     *
     * @return the static-inner rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records static modifiers for safe member classes.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one class is made static
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Adds static to a member class with only static or nested-type members.
             *
             * @param node the visited type declaration
             * @return {@code true} to continue visiting nested declarations
             */
            @Override
            public boolean visit(TypeDeclaration node) {
                if (node.isInterface()
                        || !(node.getParent() instanceof AbstractTypeDeclaration)
                        || Modifier.isStatic(node.getModifiers())
                        || !isIndependent(node)) {
                    return true;
                }
                rewrite.getListRewrite(node, TypeDeclaration.MODIFIERS2_PROPERTY)
                        .insertLast(node.getAST().newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD), null);
                changed[0] = true;
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Checks that every member declaration is independent of an enclosing instance.
     *
     * @param node the member class to inspect
     * @return {@code true} when the class has no non-static member state or behavior
     */
    private boolean isIndependent(TypeDeclaration node) {
        for (Object object : node.bodyDeclarations()) {
            BodyDeclaration declaration = (BodyDeclaration) object;
            if (declaration instanceof FieldDeclaration field
                    && !Modifier.isStatic(field.getModifiers())) {
                return false;
            }
            if (declaration instanceof MethodDeclaration method
                    && !Modifier.isStatic(method.getModifiers())) {
                return false;
            }
            if (declaration instanceof Initializer initializer
                    && !Modifier.isStatic(initializer.getModifiers())) {
                return false;
            }
        }
        return true;
    }
}
