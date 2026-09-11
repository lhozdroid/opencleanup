package io.github.lhozdroid.opencleanup.rewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * A cleanup transformation that operates on complete Java source text.
 *
 * <p>This contract complements the AST-based {@code CleanupRule} contract for transformations
 * such as whitespace cleanup that have no reliable AST node representation.</p>
 */
public interface SourceRewriteRule {

    /**
     * Returns the stable OpenCleanup identifier for this source rule.
     *
     * @return the rule identifier
     */
    String id();

    /**
     * Rewrites the supplied source text according to this rule.
     *
     * @param source the complete Java source text
     * @param configuration the Maven configuration for this rule
     * @return the rewritten source, or the original source when no safe rewrite is available
     */
    String apply(String source, RuleConfiguration configuration);
}
