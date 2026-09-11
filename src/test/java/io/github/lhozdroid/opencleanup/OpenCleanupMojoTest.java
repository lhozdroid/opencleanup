package io.github.lhozdroid.opencleanup;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.maven.plugin.AbstractMojo;
import org.junit.jupiter.api.Test;

class OpenCleanupMojoTest {

    @Test
    void isMavenMojo() {
        assertTrue(AbstractMojo.class.isAssignableFrom(OpenCleanupMojo.class));
    }
}
