package io.github.lhozdroid.opencleanup;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;
import io.github.lhozdroid.opencleanup.rewrite.JavaSourceRewriter;
import io.github.lhozdroid.opencleanup.rewrite.RewriteException;
import io.github.lhozdroid.opencleanup.rewrite.RewriteReport;
import io.github.lhozdroid.opencleanup.rule.CleanupRuleRegistry;

/**
 * Entry point for the OpenCleanup source rewrite goal.
 */
@Mojo(
        name = "rewrite",
        defaultPhase = LifecyclePhase.PROCESS_SOURCES,
        requiresProject = true,
        threadSafe = true)
public final class OpenCleanupMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter
    private List<RuleConfiguration> rules = new ArrayList<>();

    /**
     * Applies the enabled cleanup rules to the project's compile source roots.
     *
     * @throws MojoExecutionException if source rewriting fails or the configuration is invalid
     */
    @Override
    public void execute() throws MojoExecutionException {
        try {
            JavaSourceRewriter rewriter = new JavaSourceRewriter(new CleanupRuleRegistry());
            List<Path> sourceRoots = project.getCompileSourceRoots().stream()
                    .map(Path::of)
                    .toList();
            RewriteReport report = rewriter.rewrite(sourceRoots, sourceCharset(), rules);
            getLog().info("OpenCleanup visited " + report.getFilesVisited()
                    + " Java files and changed " + report.getFilesChanged() + ".");
            if (!report.getAppliedRules().isEmpty()) {
                getLog().info("Applied rules: " + String.join(", ", report.getAppliedRules()));
            }
        } catch (RewriteException | java.io.IOException | IllegalArgumentException exception) {
            throw new MojoExecutionException("OpenCleanup could not rewrite the project sources.", exception);
        }
    }

    /**
     * Resolves the source encoding configured on the Maven project.
     *
     * @return the configured source charset, or UTF-8 when no encoding is configured
     * @throws IllegalArgumentException if the configured encoding is unsupported
     */
    private Charset sourceCharset() {
        String encoding = project.getProperties().getProperty("project.build.sourceEncoding");
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unsupported source encoding: " + encoding, exception);
        }
    }
}
