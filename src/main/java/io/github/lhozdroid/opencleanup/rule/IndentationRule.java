package io.github.lhozdroid.opencleanup.rule;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;
import io.github.lhozdroid.opencleanup.rewrite.SourceRewriteRule;
import io.github.lhozdroid.opencleanup.rewrite.SourceRewriteSupport;

/**
 * Corrects indentation while preserving source line content and line structure.
 */
public final class IndentationRule implements SourceRewriteRule {

    /** The stable identifier for indentation cleanup. */
    public static final String ID = "format.indentation";

    /**
     * Returns the stable identifier for indentation cleanup.
     *
     * @return the indentation rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Copies only indentation from JDT formatter output when it preserves line structure.
     *
     * @param source the complete Java source text
     * @param configuration the optional formatter configuration
     * @return source with conservative indentation-only changes
     */
    @Override
    public String apply(String source, RuleConfiguration configuration) {
        String formatted = SourceRewriteSupport.formatSource(source, configuration);
        return SourceRewriteSupport.replaceIndentation(source, formatted);
    }
}
