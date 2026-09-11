package io.github.lhozdroid.opencleanup.rule;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Removes provably empty or unnecessary {@code SuppressWarnings} entries.
 *
 * <p>Only the empty annotation and the {@code unused} and {@code deprecation} tokens are
 * removed. Those tokens are removed only when this compilation unit contains no corresponding
 * syntactic reason for the suppression; unknown warning tokens are retained.</p>
 */
public final class SuppressWarningsRule implements CleanupRule {

    /** The stable identifier for suppress-warnings cleanup. */
    public static final String ID = "unused-code.suppress-warnings";

    /**
     * Returns the stable identifier for this rule.
     *
     * @return the rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records removals for unnecessary suppression annotations and tokens.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one suppression is scheduled for removal
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean unusedNeeded = hasPrivateUnusedDeclaration(compilationUnit);
        boolean deprecationNeeded = hasDeprecatedAnnotation(compilationUnit);
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Simplifies one {@code SuppressWarnings} annotation.
             *
             * @param node the visited annotation
             * @return {@code true} to continue visiting annotation children
             */
            @Override
            public boolean visit(NormalAnnotation node) {
                if (isSuppressWarnings(node)) {
                    changed[0] |= simplifyNormal(node, rewrite, unusedNeeded, deprecationNeeded);
                }
                return true;
            }

            /**
             * Simplifies one single-value {@code SuppressWarnings} annotation.
             *
             * @param node the visited annotation
             * @return {@code true} to continue visiting annotation children
             */
            @Override
            public boolean visit(SingleMemberAnnotation node) {
                if (!isSuppressWarnings(node)) {
                    return true;
                }
                if (node.getValue() instanceof StringLiteral literal
                        && removable(literal.getLiteralValue(), unusedNeeded, deprecationNeeded)) {
                    rewrite.remove(node, null);
                    changed[0] = true;
                } else if (node.getValue() instanceof ArrayInitializer initializer) {
                    changed[0] |= simplifyArray(node, initializer, rewrite,
                            unusedNeeded, deprecationNeeded);
                }
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Simplifies a normal annotation containing a {@code value} member.
     *
     * @param annotation the annotation to inspect
     * @param rewrite the rewrite collecting source edits
     * @param unusedNeeded whether an unused suppression is needed
     * @param deprecationNeeded whether a deprecation suppression is needed
     * @return {@code true} when an edit is scheduled
     */
    private boolean simplifyNormal(
            NormalAnnotation annotation,
            ASTRewrite rewrite,
            boolean unusedNeeded,
            boolean deprecationNeeded) {
        Object member = annotation.values().stream()
                .filter(value -> value instanceof org.eclipse.jdt.core.dom.MemberValuePair pair
                        && "value".equals(pair.getName().getIdentifier()))
                .findFirst()
                .orElse(null);
        if (!(member instanceof org.eclipse.jdt.core.dom.MemberValuePair pair)) {
            return false;
        }
        if (pair.getValue() instanceof StringLiteral literal
                && removable(literal.getLiteralValue(), unusedNeeded, deprecationNeeded)) {
            rewrite.remove(annotation, null);
            return true;
        }
        if (!(pair.getValue() instanceof ArrayInitializer initializer)) {
            return false;
        }
        Set<ASTNode> removable = new HashSet<>();
        for (Object value : initializer.expressions()) {
            if (value instanceof StringLiteral literal
                    && removable(literal.getLiteralValue(), unusedNeeded, deprecationNeeded)) {
                removable.add(literal);
            }
        }
        if (removable.isEmpty()) {
            return false;
        }
        if (removable.size() == initializer.expressions().size()) {
            rewrite.remove(annotation, null);
            return true;
        }
        ListRewrite values = rewrite.getListRewrite(initializer, ArrayInitializer.EXPRESSIONS_PROPERTY);
        removable.forEach(value -> values.remove(value, null));
        return true;
    }

    /**
     * Simplifies warning tokens in a single-member annotation whose value is an array.
     *
     * @param annotation the annotation to inspect
     * @param initializer the warning token array
     * @param rewrite the rewrite collecting source changes
     * @param unusedNeeded whether an unused suppression is needed
     * @param deprecationNeeded whether a deprecation suppression is needed
     * @return {@code true} when an edit is scheduled
     */
    private boolean simplifyArray(
            SingleMemberAnnotation annotation,
            ArrayInitializer initializer,
            ASTRewrite rewrite,
            boolean unusedNeeded,
            boolean deprecationNeeded) {
        Set<ASTNode> removable = new HashSet<>();
        for (Object value : initializer.expressions()) {
            if (value instanceof StringLiteral literal
                    && removable(literal.getLiteralValue(), unusedNeeded, deprecationNeeded)) {
                removable.add(literal);
            }
        }
        if (removable.isEmpty()) {
            return false;
        }
        if (removable.size() == initializer.expressions().size()) {
            rewrite.remove(annotation, null);
            return true;
        }
        ListRewrite values = rewrite.getListRewrite(initializer, ArrayInitializer.EXPRESSIONS_PROPERTY);
        removable.forEach(value -> values.remove(value, null));
        return true;
    }

    /**
     * Checks whether an annotation is the standard suppress-warnings annotation.
     *
     * @param annotation the annotation to inspect
     * @return {@code true} for {@code java.lang.SuppressWarnings} by simple name
     */
    private boolean isSuppressWarnings(Annotation annotation) {
        return !(annotation instanceof MarkerAnnotation)
                && "SuppressWarnings".equals(annotation.getTypeName().getFullyQualifiedName());
    }

    /**
     * Checks whether a warning token can be safely removed.
     *
     * @param token the warning token
     * @param unusedNeeded whether unused declarations exist
     * @param deprecationNeeded whether deprecated declarations exist
     * @return {@code true} when the token has no corresponding syntactic need
     */
    private boolean removable(String token, boolean unusedNeeded, boolean deprecationNeeded) {
        return ("unused".equals(token) && !unusedNeeded)
                || ("deprecation".equals(token) && !deprecationNeeded);
    }

    /**
     * Checks whether this unit contains a private declaration with no references.
     *
     * @param compilationUnit the compilation unit to inspect
     * @return {@code true} when an unused private declaration is present
     */
    private boolean hasPrivateUnusedDeclaration(CompilationUnit compilationUnit) {
        boolean[] found = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Detects a private field or method with no other matching name.
             *
             * @param node the visited declaration node
             * @return {@code false} after a match, otherwise {@code true}
             */
            @Override
            public boolean preVisit2(ASTNode node) {
                if (node instanceof org.eclipse.jdt.core.dom.FieldDeclaration field
                        && org.eclipse.jdt.core.dom.Modifier.isPrivate(field.getModifiers())) {
                    for (Object value : field.fragments()) {
                        if (referenceCount(compilationUnit,
                                ((org.eclipse.jdt.core.dom.VariableDeclarationFragment) value).getName()) == 0) {
                            found[0] = true;
                            return false;
                        }
                    }
                }
                if (node instanceof org.eclipse.jdt.core.dom.MethodDeclaration method
                        && org.eclipse.jdt.core.dom.Modifier.isPrivate(method.getModifiers())
                        && referenceCount(compilationUnit, method.getName()) == 0) {
                    found[0] = true;
                    return false;
                }
                return !found[0];
            }
        });
        return found[0];
    }

    /**
     * Counts source references outside a declaration name.
     *
     * @param compilationUnit the compilation unit to inspect
     * @param declarationName the declaration name
     * @return the reference count
     */
    private int referenceCount(CompilationUnit compilationUnit, org.eclipse.jdt.core.dom.SimpleName declarationName) {
        int[] count = {0};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Counts a matching simple name.
             *
             * @param node the visited simple name
             * @return {@code true} to continue visiting children
             */
            @Override
            public boolean visit(org.eclipse.jdt.core.dom.SimpleName node) {
                if (node != declarationName && node.getIdentifier().equals(declarationName.getIdentifier())) {
                    count[0]++;
                }
                return true;
            }
        });
        return count[0];
    }

    /**
     * Checks whether a deprecated annotation is present in the unit.
     *
     * @param compilationUnit the compilation unit to inspect
     * @return {@code true} when a deprecated annotation is present
     */
    private boolean hasDeprecatedAnnotation(CompilationUnit compilationUnit) {
        boolean[] found = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Detects a deprecated annotation.
             *
             * @param node the visited annotation
             * @return {@code false} after a match, otherwise {@code true}
             */
            @Override
            public boolean preVisit2(ASTNode node) {
                if (node instanceof Annotation annotation
                        && "Deprecated".equals(annotation.getTypeName().getFullyQualifiedName())) {
                    found[0] = true;
                    return false;
                }
                return true;
            }
        });
        return found[0];
    }
}
