package io.github.lhozdroid.opencleanup;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenCleanupMojoTest {

    /** A temporary Maven project directory used by source-root tests. */
    @TempDir
    Path temporaryDirectory;

    /**
     * Verifies that the entry point extends Maven's Mojo base class.
     */
    @Test
    void isMavenMojo() {
        assertTrue(AbstractMojo.class.isAssignableFrom(OpenCleanupMojo.class));
    }

    /**
     * Verifies omitted source roots resolve to Maven compile source roots.
     *
     * @throws Exception if the private configuration fields cannot be inspected
     */
    @Test
    void defaultsToMavenCompileSourceRoots() throws Exception {
        MavenProject project = projectWithCompileRoots("src/main/java");
        OpenCleanupMojo mojo = mojoWithProject(project, List.of());

        assertEquals(
                List.of(temporaryDirectory.resolve("src/main/java")),
                invokeSourceRoots(mojo));
    }

    /**
     * Verifies explicit source roots replace Maven's default source-root list.
     *
     * @throws Exception if the private configuration fields cannot be inspected
     */
    @Test
    void resolvesExplicitSourceRootsAgainstProjectDirectory() throws Exception {
        MavenProject project = projectWithCompileRoots("src/main/java");
        OpenCleanupMojo mojo = mojoWithProject(project, List.of("generated/java"));

        assertEquals(
                List.of(temporaryDirectory.resolve("generated/java")),
                invokeSourceRoots(mojo));
    }

    /**
     * Creates a Maven project rooted at the temporary test directory.
     *
     * @param roots Maven compile source roots
     * @return a configured Maven project
     */
    private MavenProject projectWithCompileRoots(String... roots) {
        MavenProject project = new MavenProject();
        project.setFile(temporaryDirectory.resolve("pom.xml").toFile());
        for (String root : roots) {
            project.addCompileSourceRoot(root);
        }
        return project;
    }

    /**
     * Injects the project and source-root values into a Maven mojo for unit testing.
     *
     * @param project Maven project metadata
     * @param roots configured source roots
     * @return a mojo with test configuration injected
     * @throws ReflectiveOperationException if a field cannot be set
     */
    private OpenCleanupMojo mojoWithProject(MavenProject project, List<String> roots)
            throws ReflectiveOperationException {
        OpenCleanupMojo mojo = new OpenCleanupMojo();
        Field projectField = OpenCleanupMojo.class.getDeclaredField("project");
        projectField.setAccessible(true);
        projectField.set(mojo, project);
        Field rootsField = OpenCleanupMojo.class.getDeclaredField("sourceRoots");
        rootsField.setAccessible(true);
        rootsField.set(mojo, roots);
        return mojo;
    }

    /**
     * Invokes the source-root resolver under test.
     *
     * @param mojo configured Maven mojo
     * @return resolved source-root paths
     * @throws ReflectiveOperationException if the resolver cannot be invoked
     */
    @SuppressWarnings("unchecked")
    private List<Path> invokeSourceRoots(OpenCleanupMojo mojo) throws ReflectiveOperationException {
        Method sourceRoots = OpenCleanupMojo.class.getDeclaredMethod("sourceRoots");
        sourceRoots.setAccessible(true);
        return (List<Path>) sourceRoots.invoke(mojo);
    }
}
