package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/** Adds {@code @Deprecated} where a declaration has a {@code @deprecated} Javadoc tag. */
public final class DeprecatedAnnotationRule implements CleanupRule {

    /** The stable identifier for deprecated annotation cleanup. */
    public static final String ID = "annotations.deprecated";

    /**
     * Returns the stable identifier for deprecated annotation cleanup.
     *
     * @return the deprecated annotation rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Adds {@code @Deprecated} to declarations documenting deprecation.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when an annotation insertion is scheduled
     */
    @Override
    public boolean apply(CompilationUnit compilationUnit, ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Adds a deprecated marker to one documented declaration.
             *
             * @param node the visited body declaration
             * @return {@code true} to continue visiting child declarations
             */
            @Override
            public void preVisit(ASTNode node) {
                if (node instanceof BodyDeclaration declaration
                        && hasDeprecatedTag(declaration.getJavadoc())) {
                    changed[0] |= MissingCodeSupport.addMarkerAnnotation(
                            declaration, MissingCodeSupport.DEPRECATED, rewrite);
                }
            }
        });
        return changed[0];
    }

    /**
     * Checks whether Javadoc contains the standard deprecation tag.
     *
     * @param javadoc the declaration Javadoc, if present
     * @return {@code true} when a {@code @deprecated} tag is present
     */
    private boolean hasDeprecatedTag(Javadoc javadoc) {
        if (javadoc == null) {
            return false;
        }
        for (Object tagObject : javadoc.tags()) {
            if (tagObject instanceof TagElement tag && "@deprecated".equals(tag.getTagName())) {
                return true;
            }
        }
        return false;
    }
}
