package io.github.lhozdroid.opencleanup.rule;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;
import io.github.lhozdroid.opencleanup.rewrite.SourceRewriteRule;
import io.github.lhozdroid.opencleanup.rewrite.SourceRewriteSupport;

/**
 * Formats complete Java source units with the Eclipse JDT formatter.
 */
public final class FormatSourceRule implements SourceRewriteRule {

    /** The stable identifier for whole-source formatting. */
    public static final String ID = "format.source";

    /**
     * Returns the stable identifier for whole-source formatting.
     *
     * @return the source-formatting rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Formats the supplied Java source using Eclipse JDT defaults and supported options.
     *
     * @param source the complete Java source text
     * @param configuration the optional formatter configuration
     * @return formatted source, or the original source when formatting is not possible
     */
    @Override
    public String apply(String source, RuleConfiguration configuration) {
        return SourceRewriteSupport.formatSource(source, configuration);
    }
}
