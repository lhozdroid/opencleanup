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

    /**
     * Verifies line-content changes from comment wrapping or formatter reflow are not
     * misapplied to an unrelated original line.
     */
    @Test
    void preservesSourceWhenFormatterChangesLineMapping() {
        String source = "@Annotation(first = one,\n"
                + "        second = two)\n"
                + "class Example {}\n";

        assertEquals(source, new IndentationRule().apply(source, null));
    }
}
