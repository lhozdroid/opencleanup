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

class AdditionalUnnecessaryCodeRulesTest {

    @TempDir
    Path tempDirectory;

    /**
     * Verifies that the three narrow unnecessary-code rules rewrite their eligible forms.
     *
     * @throws Exception if the temporary source file cannot be created or rewritten
     */
    @Test
    void appliesAdditionalRules() throws Exception {
        Path sourceFile = tempDirectory.resolve("Example.java");
        Files.writeString(sourceFile, """
                class Example extends Base {
                    Example() {
                        super();
                    }

                    boolean value(boolean active) {
                        return !!active;
                    }

                    void loop(boolean active) {
                        while (active) {
                            continue;
                        }
                    }
                }

                class Base {
                }
                """, StandardCharsets.UTF_8);

        List<RuleConfiguration> configurations = List.of(
                configuration(RedundantSuperCallRule.ID),
                configuration(DoubleNegationRule.ID),
                configuration(UselessContinueRule.ID));

        new JavaSourceRewriter(new CleanupRuleRegistry())
                .rewrite(List.of(tempDirectory), StandardCharsets.UTF_8, configurations);

        String rewrittenSource = Files.readString(sourceFile);
        assertFalse(rewrittenSource.contains("super();"));
        assertFalse(rewrittenSource.contains("return !!active;"));
        assertFalse(rewrittenSource.contains("continue;"));
        assertTrue(rewrittenSource.contains("return active;"));
    }

    /**
     * Creates an enabled rule configuration with no additional options.
     *
     * @param ruleId the rule identifier
     * @return an enabled rule configuration
     */
    private RuleConfiguration configuration(String ruleId) {
        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(ruleId);
        return configuration;
    }
}
