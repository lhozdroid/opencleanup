package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/** Adds all source-provable missing annotations covered by this workstream. */
public final class MissingAnnotationsRule implements CleanupRule {

    /** The stable identifier for the aggregate missing-annotations cleanup. */
    public static final String ID = "annotations.missing";

    /**
     * Returns the stable identifier for aggregate missing-annotations cleanup.
     *
     * @return the aggregate annotation rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Adds source-provable {@code @Override} and {@code @Deprecated} markers.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one marker is scheduled
     */
    @Override
    public boolean apply(CompilationUnit compilationUnit, ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean changed = new OverrideAnnotationRule().apply(compilationUnit, rewrite, configuration);
        changed |= new OverrideInterfaceAnnotationRule().apply(
                compilationUnit, rewrite, configuration);
        changed |= new DeprecatedAnnotationRule().apply(compilationUnit, rewrite, configuration);
        return changed;
    }
}
