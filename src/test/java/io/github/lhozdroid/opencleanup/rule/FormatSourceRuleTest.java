package io.github.lhozdroid.opencleanup.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;
import io.github.lhozdroid.opencleanup.config.RuleOption;

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

    /**
     * Verifies comment wrapping can use a different width from ordinary source lines.
     */
    @Test
    void supportsIndependentCommentLineLength() {
        String source = "/** This comment contains enough words to require wrapping when the comment width is small. */\n"
                + "class Example { int value; }\n";
        RuleOption commentLength = new RuleOption();
        commentLength.setName("comment-line-length");
        commentLength.setValue("40");
        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setOptions(List.of(commentLength));

        String formatted = new FormatSourceRule().apply(source, configuration);

        assertTrue(formatted.lines()
                .filter(line -> line.trim().startsWith("*") || line.trim().startsWith("/**"))
                .allMatch(line -> line.length() <= 40));
        assertTrue(formatted.contains("class Example {"));
    }

    /**
     * Verifies malformed formatter widths leave the original source untouched.
     */
    @Test
    void rejectsInvalidFormatterWidths() {
        String source = "class Example{void run(){int value=1;}}";
        RuleOption commentLength = new RuleOption();
        commentLength.setName("comment-line-length");
        commentLength.setValue("-1");
        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setOptions(List.of(commentLength));

        assertEquals(source, new FormatSourceRule().apply(source, configuration));
    }

    /**
     * Verifies malformed Java is preserved by the source formatter.
     */
    @Test
    void preservesMalformedSource() {
        String source = "class Example { void run( { int value = 1; }";

        assertEquals(source, new FormatSourceRule().apply(source, null));
    }
}
