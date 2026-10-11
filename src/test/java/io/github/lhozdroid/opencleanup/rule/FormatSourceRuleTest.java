package io.github.lhozdroid.opencleanup.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /**
     * Verifies a record whose header type needs an import is formatted to the full expected
     * output and that the formatting is idempotent.
     */
    @Test
    void formatsRecordDeclarationIdempotently() {
        String source = "package example;\n"
                + "\n"
                + "import java.time.Clock;\n"
                + "\n"
                + "record Zone(Clock clock) implements SimClock{void tick(){long n=1;}}\n";
        String expected = "package example;\n"
                + "\n"
                + "import java.time.Clock;\n"
                + "\n"
                + "record Zone(Clock clock) implements SimClock {\n"
                + "\tvoid tick() {\n"
                + "\t\tlong n = 1;\n"
                + "\t}\n"
                + "}\n";

        String formatted = new FormatSourceRule().apply(source, null);

        assertEquals(expected, formatted);
        assertEquals(expected, new FormatSourceRule().apply(formatted, null));
    }

    /**
     * Verifies modern record constructs survive formatting and remain idempotent.
     */
    @Test
    void formatsRecordConstructs() {
        String source = "package example;\n"
                + "\n"
                + "import java.util.List;\n"
                + "\n"
                + "sealed interface Shape permits Circle {\n"
                + "    record Circle(@Deprecated List<String>[] names, int... extra) implements Shape {\n"
                + "        Circle {\n"
                + "            if (names.length == 0) { throw new IllegalArgumentException(); }\n"
                + "        }\n"
                + "        String text() {\n"
                + "            return \"\"\"\n"
                + "                keep\n"
                + "                \"\"\";\n"
                + "        }\n"
                + "        void nested() {\n"
                + "            record Local(int value) {\n"
                + "            }\n"
                + "        }\n"
                + "    }\n"
                + "}\n";

        String formatted = new FormatSourceRule().apply(source, null);

        assertTrue(formatted.contains("sealed interface Shape permits Circle {"));
        assertTrue(formatted.contains(
                "record Circle(@Deprecated List<String>[] names, int... extra) implements Shape {"));
        assertTrue(formatted.contains("\t\tCircle {"));
        assertTrue(formatted.contains("record Local(int value) {"));
        assertTrue(formatted.contains("\"\"\""));
        assertEquals(formatted, new FormatSourceRule().apply(formatted, null));
    }
}
