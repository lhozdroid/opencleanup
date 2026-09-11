package io.github.lhozdroid.opencleanup.rewrite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;
import io.github.lhozdroid.opencleanup.rule.CleanupRuleRegistry;
import io.github.lhozdroid.opencleanup.rule.TrailingWhitespaceRule;

/** Tests the complete source-level rewrite pipeline. */
class JavaSourceRewriterTest {

    /** A temporary source root used by the pipeline test. */
    @TempDir
    Path temporaryDirectory;

    /**
     * Verifies that a source-level rule is resolved, applied, and reported by the rewriter.
     *
     * @throws Exception if the temporary source cannot be read or written
     */
    @Test
    void appliesAndReportsSourceRule() throws Exception {
        Path sourceFile = temporaryDirectory.resolve("Example.java");
        Files.writeString(sourceFile, "class Example {  \n}\n", StandardCharsets.UTF_8);

        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(TrailingWhitespaceRule.ID);

        RewriteReport report = new JavaSourceRewriter(new CleanupRuleRegistry())
                .rewrite(List.of(temporaryDirectory), StandardCharsets.UTF_8, List.of(configuration));

        assertEquals("class Example {\n}\n", Files.readString(sourceFile, StandardCharsets.UTF_8));
        assertEquals(1, report.getFilesChanged());
        assertTrue(report.getAppliedRules().contains(TrailingWhitespaceRule.ID));
    }
}
