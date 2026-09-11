package io.github.lhozdroid.opencleanup.rule;

import java.util.Map;

import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/** Adds {@code @Override} to methods overriding source-local parent methods. */
public final class OverrideAnnotationRule implements CleanupRule {

    /** The stable identifier for class and interface override annotations. */
    public static final String ID = "annotations.override";

    /**
     * Returns the stable identifier for override annotation cleanup.
     *
     * @return the override annotation rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Adds {@code @Override} where a method matches a source-local inherited method.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when an annotation insertion is scheduled
     */
    @Override
    public boolean apply(CompilationUnit compilationUnit, ASTRewrite rewrite,
            RuleConfiguration configuration) {
        Map<String, AbstractTypeDeclaration> types = MissingCodeSupport.collectTypes(compilationUnit);
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Adds an override marker to one class method with a source-local ancestor match.
             *
             * @param node the visited method declaration
             * @return {@code true} to continue visiting the method body
             */
            @Override
            public boolean visit(MethodDeclaration node) {
                if (node.isConstructor() || !(enclosingType(node) instanceof TypeDeclaration type)
                        || type.isInterface() || MissingCodeSupport.hasAnnotation(
                                node, MissingCodeSupport.OVERRIDE)) {
                    return true;
                }
                if (MissingCodeSupport.inheritedMethods(type, types).entrySet().stream()
                        .filter(entry -> !(entry.getValue().getParent() instanceof TypeDeclaration parent)
                                || !parent.isInterface())
                        .anyMatch(entry -> entry.getKey().equals(MissingCodeSupport.signature(node)))) {
                    changed[0] |= MissingCodeSupport.addMarkerAnnotation(
                            node, MissingCodeSupport.OVERRIDE, rewrite);
                }
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Finds the nearest named type containing a method.
     *
     * @param node the method declaration under consideration
     * @return the containing type, or {@code null} outside a named type
     */
    private AbstractTypeDeclaration enclosingType(MethodDeclaration node) {
        org.eclipse.jdt.core.dom.ASTNode current = node.getParent();
        while (current != null) {
            if (current instanceof AbstractTypeDeclaration type) {
                return type;
            }
            current = current.getParent();
        }
        return null;
    }
}
