package io.github.lhozdroid.opencleanup.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;
import io.github.lhozdroid.opencleanup.rewrite.JavaSourceRewriter;

class ElseIfRuleTest {

    @TempDir
    Path tempDirectory;

    /**
     * Verifies an else block containing one if statement becomes an else-if.
     *
     * @throws Exception if the temporary source file cannot be created or rewritten
     */
    @Test
    void joinsElseIfStatements() throws Exception {
        Path sourceFile = tempDirectory.resolve("Example.java");
        Files.writeString(sourceFile, """
                class Example {
                    void check(int value) {
                        if (value == 1) {
                            first();
                        } else {
                            if (value == 2) {
                                second();
                            }
                        }
                    }

                    void first() {
                    }

                    void second() {
                    }
                }
                """, StandardCharsets.UTF_8);

        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(ElseIfRule.ID);

        new JavaSourceRewriter(new CleanupRuleRegistry())
                .rewrite(List.of(tempDirectory), StandardCharsets.UTF_8, List.of(configuration));

        String source = Files.readString(sourceFile, StandardCharsets.UTF_8);
        assertEquals(1, source.lines().filter(line -> line.contains("} else if (value == 2) {")).count());
        assertEquals(0, source.lines().filter(line -> line.trim().equals("} else {")).count());
    }

    /**
     * Verifies a multi-statement else block remains unchanged.
     *
     * @throws Exception if the temporary source file cannot be created or rewritten
     */
    @Test
    void preservesElseBlocksWithMultipleStatements() throws Exception {
        Path sourceFile = tempDirectory.resolve("MultipleStatements.java");
        String original = """
                class MultipleStatements {
                    void check(boolean value) {
                        if (value) {
                            first();
                        } else {
                            second();
                            third();
                        }
                    }

                    void first() {
                    }

                    void second() {
                    }

                    void third() {
                    }
                }
                """;
        Files.writeString(sourceFile, original, StandardCharsets.UTF_8);

        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(ElseIfRule.ID);

        new JavaSourceRewriter(new CleanupRuleRegistry())
                .rewrite(List.of(tempDirectory), StandardCharsets.UTF_8, List.of(configuration));

        assertEquals(original, Files.readString(sourceFile, StandardCharsets.UTF_8));
    }
}
