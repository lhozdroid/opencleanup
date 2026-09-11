package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Removes unnecessary {@code String} object creation for empty and literal strings.
 */
public final class NoStringCreationRule implements CleanupRule {

    /** The stable identifier used to select this cleanup rule. */
    public static final String ID = "strings.no-string-creation";

    /**
     * Returns the stable identifier for no-string-creation cleanup.
     *
     * @return the no-string-creation rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for empty and literal String constructors.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one String construction is removed
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Replaces one supported String construction with a literal.
             *
             * @param node the visited class instance creation
             * @return {@code false} after replacement, otherwise {@code true}
             */
            @Override
            public boolean visit(ClassInstanceCreation node) {
                Expression replacement = replacement(node);
                if (replacement != null) {
                    rewrite.replace(node, replacement, null);
                    changed[0] = true;
                    return false;
                }
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Builds a String literal for an exact empty or literal constructor.
     *
     * @param node the candidate String construction
     * @return the replacement literal, or {@code null} when unsupported
     */
    private Expression replacement(ClassInstanceCreation node) {
        if (!(node.getType() instanceof SimpleType type)
                || !"String".equals(type.getName().getFullyQualifiedName())
                || node.getAnonymousClassDeclaration() != null) {
            return null;
        }
        if (node.arguments().isEmpty()) {
            StringLiteral literal = node.getAST().newStringLiteral();
            literal.setLiteralValue("");
            return literal;
        }
        if (node.arguments().size() == 1
                && node.arguments().get(0) instanceof StringLiteral literal) {
            return (Expression) ASTNode.copySubtree(node.getAST(), literal);
        }
        return null;
    }
}
