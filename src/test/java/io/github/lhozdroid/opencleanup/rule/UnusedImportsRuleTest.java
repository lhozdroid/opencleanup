package io.github.lhozdroid.opencleanup.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;
import io.github.lhozdroid.opencleanup.rewrite.JavaSourceRewriter;
import io.github.lhozdroid.opencleanup.rewrite.RewriteReport;

class UnusedImportsRuleTest {

    @TempDir
    Path tempDirectory;

    /**
     * Verifies that an unused single-type import is removed and reported.
     *
     * @throws Exception if the temporary source file cannot be created or rewritten
     */
    @Test
    void removesUnusedSingleTypeImport() throws Exception {
        Path sourceFile = tempDirectory.resolve("Example.java");
        Files.writeString(sourceFile, """
                import java.util.List;
                import java.util.Set;

                class Example {
                    List<String> values;
                }
                """, StandardCharsets.UTF_8);

        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(UnusedImportsRule.ID);

        RewriteReport report = new JavaSourceRewriter(new CleanupRuleRegistry())
                .rewrite(List.of(tempDirectory), StandardCharsets.UTF_8, List.of(configuration));

        String rewrittenSource = Files.readString(sourceFile);
        assertFalse(rewrittenSource.contains("import java.util.Set;"));
        assertEquals(1, report.getFilesVisited());
        assertEquals(1, report.getFilesChanged());
        assertEquals(List.of(sourceFile), report.getChangedFiles());
    }

    /**
     * Verifies that wildcard imports are retained by the conservative rule.
     *
     * @throws Exception if the temporary source file cannot be created or rewritten
     */
    @Test
    void retainsWildcardImport() throws Exception {
        Path sourceFile = tempDirectory.resolve("Example.java");
        Files.writeString(sourceFile, """
                import java.util.*;

                class Example {
                }
                """, StandardCharsets.UTF_8);

        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(UnusedImportsRule.ID);

        new JavaSourceRewriter(new CleanupRuleRegistry())
                .rewrite(List.of(tempDirectory), StandardCharsets.UTF_8, List.of(configuration));

        assertEquals("import java.util.*;\n\nclass Example {\n}\n", Files.readString(sourceFile));
    }
}
