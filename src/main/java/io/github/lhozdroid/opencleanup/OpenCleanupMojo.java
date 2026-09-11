package io.github.lhozdroid.opencleanup;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

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

    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("OpenCleanup rewrite goal is not implemented yet; no source files were changed.");
    }
}
