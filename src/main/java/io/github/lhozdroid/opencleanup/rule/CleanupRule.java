package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * A source transformation that can be selected from Maven configuration.
 */
public interface CleanupRule {

    String id();

    boolean apply(CompilationUnit compilationUnit, ASTRewrite rewrite, RuleConfiguration configuration);
}
