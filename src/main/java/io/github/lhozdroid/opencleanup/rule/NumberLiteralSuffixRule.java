package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Normalizes lowercase numeric literal suffixes to their canonical uppercase forms.
 *
 * <p>The rule changes only the final suffix character of a numeric token. It does not add,
 * remove, or otherwise alter digits, radix prefixes, separators, exponents, or suffix kinds.</p>
 */
public final class NumberLiteralSuffixRule implements CleanupRule {

    /** The stable identifier for numeric literal suffix cleanup. */
    public static final String ID = "number-literals.suffix";

    /**
     * Returns the stable identifier for numeric literal suffix cleanup.
     *
     * @return the numeric literal suffix rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Replaces lowercase long, float, and double suffixes with uppercase suffixes.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one suffix is scheduled for replacement
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Normalizes the suffix of one numeric literal when it is lowercase.
             *
             * @param node the visited numeric literal
             * @return {@code true} to continue visiting child nodes
             */
            @Override
            public boolean visit(NumberLiteral node) {
                String token = node.getToken();
                if (token.isEmpty()) {
                    return true;
                }

                char suffix = token.charAt(token.length() - 1);
                char normalizedSuffix = normalizeSuffix(suffix);
                if (suffix == normalizedSuffix) {
                    return true;
                }

                NumberLiteral replacement = node.getAST().newNumberLiteral(
                        token.substring(0, token.length() - 1) + normalizedSuffix);
                rewrite.replace(node, replacement, null);
                changed[0] = true;
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Converts a supported lowercase numeric suffix to its canonical uppercase form.
     *
     * @param suffix the final character of a numeric literal token
     * @return the normalized suffix, or the original character when it is not supported
     */
    private char normalizeSuffix(char suffix) {
        return switch (suffix) {
            case 'l' -> 'L';
            case 'f' -> 'F';
            case 'd' -> 'D';
            default -> suffix;
        };
    }
}
