package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Replaces literal regular-expression substitutions with plain string replacement calls.
 */
public final class StringsPlainReplacementRule implements CleanupRule {

    /** The stable identifier for plain replacement cleanup. */
    public static final String ID = "strings.plain-replacement";

    /**
     * Returns the stable identifier for this cleanup rule.
     *
     * @return the plain replacement rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for safe literal {@code replaceAll} calls.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one call is changed
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Changes one replaceAll invocation when both arguments are safe literals.
             *
             * @param node the visited method invocation
             * @return {@code true} to continue visiting nested expressions
             */
            @Override
            public boolean visit(MethodInvocation node) {
                if (!"replaceAll".equals(node.getName().getIdentifier())
                        || node.arguments().size() != 2
                        || !(node.arguments().get(0) instanceof StringLiteral pattern)
                        || !(node.arguments().get(1) instanceof StringLiteral replacement)
                        || !isPlainPattern(pattern.getLiteralValue())
                        || !isPlainReplacement(replacement.getLiteralValue())) {
                    return true;
                }
                rewrite.replace(node.getName(), node.getAST().newSimpleName("replace"), null);
                changed[0] = true;
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Checks whether a regex pattern has no metacharacters.
     *
     * @param pattern the decoded pattern literal
     * @return {@code true} when the pattern denotes plain text
     */
    private boolean isPlainPattern(String pattern) {
        return pattern.chars().noneMatch(character -> ".^$|?*+()[]{}\\".indexOf(character) >= 0);
    }

    /**
     * Checks whether a replacement string has no regex replacement escapes.
     *
     * @param replacement the decoded replacement literal
     * @return {@code true} when replacement semantics are identical
     */
    private boolean isPlainReplacement(String replacement) {
        return replacement.chars().noneMatch(character -> character == '$' || character == '\\');
    }
}
