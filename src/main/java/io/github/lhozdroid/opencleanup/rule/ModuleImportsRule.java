package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Guards the Java 25 module-import cleanup.
 *
 * <p>The plugin parses source with the Java 21 JDT AST. Java 21 has no AST
 * representation that can emit {@code import module}, so this rule safely
 * skips source rather than producing syntax that the configured Java level
 * cannot compile.</p>
 */
public final class ModuleImportsRule implements CleanupRule {

    /** The stable identifier for module-aware imports. */
    public static final String ID = "modules.use-module-imports";

    /**
     * Returns the stable identifier for module-aware imports.
     *
     * @return the module-import rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Skips the rule because the plugin's Java 21 AST cannot emit Java 25
     * module-import syntax.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return always {@code false} for the Java 21-compatible implementation
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        return false;
    }
}
