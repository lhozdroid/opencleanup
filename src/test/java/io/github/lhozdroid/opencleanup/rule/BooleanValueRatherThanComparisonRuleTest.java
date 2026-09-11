package io.github.lhozdroid.opencleanup.rule;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

import io.github.lhozdroid.opencleanup.rewrite.JavaSourceRewriter;

class BooleanValueRatherThanComparisonRuleTest {

    @TempDir
    Path tempDirectory;

    /**
     * Verifies that equality and inequality comparisons with boolean literals are simplified.
     *
     * @throws Exception if the temporary source file cannot be created or rewritten
     */
    @Test
    void simplifiesBooleanComparisons() throws Exception {
        Path sourceFile = tempDirectory.resolve("Example.java");
        Files.writeString(sourceFile, """
                class Example {
                    void check(boolean active) {
                        if (active == true) {
                            return;
                        }
                        if (false != active) {
                            return;
                        }
                        if (active != false) {
                            return;
                        }
                    }
                }
                """, StandardCharsets.UTF_8);

        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(BooleanValueRatherThanComparisonRule.ID);

        new JavaSourceRewriter(new CleanupRuleRegistry())
                .rewrite(List.of(tempDirectory), StandardCharsets.UTF_8, List.of(configuration));

        String rewrittenSource = Files.readString(sourceFile);
        assertTrue(rewrittenSource.contains("if (active)"));
        assertFalse(rewrittenSource.contains("active == true"));
        assertFalse(rewrittenSource.contains("false != active"));
        assertFalse(rewrittenSource.contains("active != false"));
    }
}
