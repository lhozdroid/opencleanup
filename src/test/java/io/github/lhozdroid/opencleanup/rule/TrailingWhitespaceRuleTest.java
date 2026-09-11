package io.github.lhozdroid.opencleanup.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;
import io.github.lhozdroid.opencleanup.config.RuleOption;

/**
 * Tests line-preserving trailing-whitespace cleanup.
 */
class TrailingWhitespaceRuleTest {

    /**
     * Verifies ordinary line endings are cleaned while text-block content is preserved.
     */
    @Test
    void removesTrailingWhitespaceButPreservesTextBlocks() {
        String source = "class Example {  \n"
                + "\n"
                + "\t \n"
                + "    String block = \"\"\"\n"
                + "        keep semantic spaces  \n"
                + "        \"\"\";  \n"
                + "}\n";
        String expected = "class Example {\n"
                + "\n"
                + "\n"
                + "    String block = \"\"\"\n"
                + "        keep semantic spaces  \n"
                + "        \"\"\";\n"
                + "}\n";

        assertEquals(expected, new TrailingWhitespaceRule().apply(source, null));
    }

    /**
     * Verifies the ignore-empty-lines mode removes whitespace only from non-empty lines.
     */
    @Test
    void canPreserveWhitespaceOnEmptyLines() {
        String source = "class Example {  \n"
                + "\t \n"
                + "    void run() {  \n"
                + "    }\n"
                + "}\n";
        RuleConfiguration configuration = new RuleConfiguration();
        RuleOption mode = new RuleOption();
        mode.setName("mode");
        mode.setValue("ignore-empty-lines");
        configuration.setOptions(List.of(mode));

        String expected = "class Example {\n"
                + "\t \n"
                + "    void run() {\n"
                + "    }\n"
                + "}\n";
        assertEquals(expected, new TrailingWhitespaceRule().apply(source, configuration));
    }
}
