package io.github.lhozdroid.opencleanup.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests indentation-only source cleanup.
 */
class IndentationRuleTest {

    /**
     * Verifies indentation changes preserve every non-leading character and line boundary.
     */
    @Test
    void correctsIndentationWithoutFormattingExpressions() {
        String source = "class Example {\n"
                + "void run() {\n"
                + "if (true) {\n"
                + "action();\n"
                + "}\n"
                + "}\n"
                + "}\n";
        String expected = "class Example {\n"
                + "\tvoid run() {\n"
                + "\t\tif (true) {\n"
                + "\t\t\taction();\n"
                + "\t\t}\n"
                + "\t}\n"
                + "}\n";

        assertEquals(expected, new IndentationRule().apply(source, null));
    }
}
