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

    /** The Maven project whose source roots and encoding are used by the rewrite. */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /** The cleanup rules and groups selected for this execution. */
    @Parameter
    private List<RuleConfiguration> rules = new ArrayList<>();

    /** Optional source roots; when omitted, Maven compile source roots are used. */
    @Parameter(property = "opencleanup.sourceRoots")
    private List<String> sourceRoots;

    /**
     * Applies the enabled cleanup rules to the configured source roots.
     *
     * <p>When {@code opencleanup.sourceRoots} is not configured, Maven compile source roots
     * are used.</p>
     *
     * @throws MojoExecutionException if source rewriting fails or the configuration is invalid
     */
    @Override
    public void execute() throws MojoExecutionException {
        try {
            JavaSourceRewriter rewriter = new JavaSourceRewriter(new CleanupRuleRegistry());
            RewriteReport report = rewriter.rewrite(sourceRoots(), sourceCharset(), rules);
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
     * Resolves the source roots configured for this execution.
     *
     * @return configured source roots, or Maven compile source roots when none were configured
     */
    private List<Path> sourceRoots() {
        if (sourceRoots == null || sourceRoots.isEmpty()) {
            return project.getCompileSourceRoots().stream()
                    .map(this::resolveSourceRoot)
                    .toList();
        }
        return sourceRoots.stream()
                .filter(root -> root != null && !root.isBlank())
                .map(this::resolveSourceRoot)
                .toList();
    }

    /**
     * Resolves a source-root path against the Maven project directory when it is relative.
     *
     * @param root the configured or Maven-provided source-root path
     * @return an absolute source-root path
     */
    private Path resolveSourceRoot(String root) {
        Path path = Path.of(root);
        return path.isAbsolute() ? path : project.getBasedir().toPath().resolve(path);
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
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported source encoding: " + encoding, exception);
        }
    }
}
