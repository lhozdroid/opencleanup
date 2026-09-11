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

/**
 * Tests the syntax-only {@link StringsIsBlankRule} transformation.
 */
class StringsIsBlankRuleTest {

    @TempDir
    Path tempDirectory;

    /**
     * Verifies that the exact simple-name receiver pattern becomes {@code isBlank()}.
     *
     * @throws Exception if the temporary source file cannot be created or rewritten
     */
    @Test
    void rewritesExactTrimmedEmptyCall() throws Exception {
        Path sourceFile = tempDirectory.resolve("Example.java");
        Files.writeString(sourceFile, """
                class Example {
                    boolean check(String text) {
                        return text.trim().isEmpty();
                    }
                }
                """, StandardCharsets.UTF_8);

        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(StringsIsBlankRule.ID);

        new JavaSourceRewriter(new CleanupRuleRegistry())
                .rewrite(List.of(tempDirectory), StandardCharsets.UTF_8, List.of(configuration));

        String rewrittenSource = Files.readString(sourceFile);
        assertTrue(rewrittenSource.contains("return text.isBlank();"));
        assertFalse(rewrittenSource.contains("text.trim().isEmpty()"));
    }

    /**
     * Verifies that unsupported receiver and argument shapes remain unchanged.
     *
     * @throws Exception if the temporary source file cannot be created or rewritten
     */
    @Test
    void preservesUnsupportedCallShapes() throws Exception {
        Path sourceFile = tempDirectory.resolve("Example.java");
        Files.writeString(sourceFile, """
                class Example {
                    boolean check(String text) {
                        return getText().trim().isEmpty()
                                || text.trim().isEmpty(1);
                    }

                    String getText() {
                        return "";
                    }
                }
                """, StandardCharsets.UTF_8);

        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(StringsIsBlankRule.ID);

        new JavaSourceRewriter(new CleanupRuleRegistry())
                .rewrite(List.of(tempDirectory), StandardCharsets.UTF_8, List.of(configuration));

        String rewrittenSource = Files.readString(sourceFile);
        assertTrue(rewrittenSource.contains("getText().trim().isEmpty()"));
        assertTrue(rewrittenSource.contains("text.trim().isEmpty(1)"));
    }
}
