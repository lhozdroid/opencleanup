package io.github.lhozdroid.opencleanup.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests whole-source formatting through the Eclipse JDT formatter.
 */
class FormatSourceRuleTest {

    /**
     * Verifies spacing and block indentation are corrected by whole-source formatting.
     */
    @Test
    void formatsJavaSource() {
        String source = "class Example{void run(){int value=1;}}";
        String expected = "class Example {\n"
                + "\tvoid run() {\n"
                + "\t\tint value = 1;\n"
                + "\t}\n"
                + "}";

        assertEquals(expected, new FormatSourceRule().apply(source, null));
    }
}
