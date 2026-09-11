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

class UselessReturnRuleTest {

    @TempDir
    Path tempDirectory;

    /**
     * Verifies that only a final empty return in a void method is removed.
     *
     * @throws Exception if the temporary source file cannot be created or rewritten
     */
    @Test
    void removesOnlyFinalVoidReturn() throws Exception {
        Path sourceFile = tempDirectory.resolve("Example.java");
        Files.writeString(sourceFile, """
                class Example {
                    void noOp() {
                        return;
                    }

                    void earlyReturn(boolean active) {
                        if (active) {
                            return;
                        }
                    }
                }
                """, StandardCharsets.UTF_8);

        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(UselessReturnRule.ID);

        new JavaSourceRewriter(new CleanupRuleRegistry())
                .rewrite(List.of(tempDirectory), StandardCharsets.UTF_8, List.of(configuration));

        long returnStatements = Files.readString(sourceFile).lines()
                .filter(line -> "return;".equals(line.trim()))
                .count();
        assertEquals(1, returnStatements);
    }
}
