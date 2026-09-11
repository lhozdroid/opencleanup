package io.github.lhozdroid.opencleanup;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.maven.plugin.AbstractMojo;
import org.junit.jupiter.api.Test;

class OpenCleanupMojoTest {

    /**
     * Verifies that the entry point extends Maven's Mojo base class.
     */
    @Test
    void isMavenMojo() {
        assertTrue(AbstractMojo.class.isAssignableFrom(OpenCleanupMojo.class));
    }
}
