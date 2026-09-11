package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * A source transformation that can be selected from Maven configuration.
 */
public interface CleanupRule {

    /**
     * Returns the stable OpenCleanup identifier for this rule.
     *
     * @return the rule identifier
     */
    String id();

    /**
     * Records this rule's source changes in the supplied AST rewrite.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when this rule records at least one source change
     */
    boolean apply(CompilationUnit compilationUnit, ASTRewrite rewrite, RuleConfiguration configuration);
}
