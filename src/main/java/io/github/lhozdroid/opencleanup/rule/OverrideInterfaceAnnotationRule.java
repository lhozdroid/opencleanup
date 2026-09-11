package io.github.lhozdroid.opencleanup.rule;

import java.util.Map;

import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/** Adds {@code @Override} to methods implementing source-local interfaces. */
public final class OverrideInterfaceAnnotationRule implements CleanupRule {

    /** The stable identifier for interface implementation annotations. */
    public static final String ID = "annotations.override-interface";

    /**
     * Returns the stable identifier for interface override annotation cleanup.
     *
     * @return the interface override rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Adds {@code @Override} to methods matching source-local interface contracts.
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
             * Adds an override marker to one method implementing an interface contract.
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
                        .filter(entry -> isInterfaceMethod(entry.getValue(), types))
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
     * Checks whether a method belongs to a source-local interface declaration.
     *
     * @param method the inherited method to inspect
     * @param types source-local type declarations
     * @return {@code true} when its direct parent is an interface
     */
    private boolean isInterfaceMethod(MethodDeclaration method,
            Map<String, AbstractTypeDeclaration> types) {
        org.eclipse.jdt.core.dom.ASTNode parent = method.getParent();
        return parent instanceof TypeDeclaration type && type.isInterface();
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
