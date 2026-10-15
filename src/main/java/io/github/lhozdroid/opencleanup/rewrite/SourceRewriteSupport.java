package io.github.lhozdroid.opencleanup.rewrite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Shared helpers for source-level cleanup rules.
 */
public final class SourceRewriteSupport {

    /**
     * The Java source level used for parsing and formatting modern language features.
     *
     * <p>Follow-up: this should be derived from the project's compiler level
     * ({@code maven.compiler.release} or {@code maven.compiler.source}) or exposed as a plugin
     * option, with {@code "21"} as the fallback default.
     */
    public static final String JAVA_SOURCE_LEVEL = "21";

    /**
     * Builds JDT compiler options that parse and format at {@link #JAVA_SOURCE_LEVEL}.
     *
     * @return a mutable copy of the default JDT options with source and compliance set
     */
    public static Map<String, String> javaSourceOptions() {
        Map<String, String> options = new HashMap<>(JavaCore.getOptions());
        options.put(JavaCore.COMPILER_SOURCE, JAVA_SOURCE_LEVEL);
        options.put(JavaCore.COMPILER_COMPLIANCE, JAVA_SOURCE_LEVEL);
        return options;
    }

    /**
     * Creates the utility class.
     */
    private SourceRewriteSupport() {
    }

    /**
     * Formats a complete Java source unit with Eclipse JDT's standard formatter.
     *
     * @param source the complete Java source text
     * @param configuration the optional formatter configuration
     * @return formatted source, or the original source when JDT cannot format it
     */
    public static String formatSource(String source, RuleConfiguration configuration) {
        if (hasSyntaxErrors(source)) {
            return source;
        }
        Map<String, String> options = javaSourceOptions();
        if (!applyFormatterOption(
                options,
                configuration,
                "tab-character",
                DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR)
                || !applyFormatterOption(
                options,
                configuration,
                "tab-size",
                DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE)
                || !applyFormatterOption(
                options,
                configuration,
                "indentation-size",
                DefaultCodeFormatterConstants.FORMATTER_INDENTATION_SIZE)
                || !applyFormatterOption(
                options,
                configuration,
                "line-split",
                DefaultCodeFormatterConstants.FORMATTER_LINE_SPLIT)
                || !applyFormatterOption(
                options,
                configuration,
                "comment-line-length",
                DefaultCodeFormatterConstants.FORMATTER_COMMENT_LINE_LENGTH)) {
            return source;
        }

        try {
            CodeFormatter formatter = ToolFactory.createCodeFormatter(options);
            int kind = CodeFormatter.K_COMPILATION_UNIT | CodeFormatter.F_INCLUDE_COMMENTS;
            TextEdit edit = formatter.format(
                    kind,
                    source,
                    0,
                    source.length(),
                    0,
                    lineDelimiter(source));
            if (edit == null) {
                return source;
            }
            String formatted = applyTextEdit(source, edit);
            return hasSyntaxErrors(formatted) ? source : formatted;
        } catch (RuntimeException exception) {
            return source;
        }
    }

    /**
     * Checks whether a source unit contains parser errors before formatting.
     *
     * @param source the Java source text to inspect
     * @return {@code true} when parsing reports at least one error
     */
    private static boolean hasSyntaxErrors(String source) {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setStatementsRecovery(true);
        parser.setBindingsRecovery(true);
        parser.setCompilerOptions(javaSourceOptions());
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
        for (IProblem problem : compilationUnit.getProblems()) {
            if (problem.isError()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Applies a JFace text edit to source text.
     *
     * @param source the original source text
     * @param edit the edit produced by a source transformation
     * @return the edited source text
     * @throws IllegalStateException if the edit cannot be applied to the document
     */
    public static String applyTextEdit(String source, TextEdit edit) {
        Document document = new Document(source);
        try {
            edit.apply(document);
            return document.get();
        } catch (BadLocationException exception) {
            throw new IllegalStateException("Could not apply source rewrite", exception);
        }
    }

    /**
     * Splits source text into lines while retaining each line's original delimiter.
     *
     * @param source the source text to split
     * @return source lines in their original order
     */
    public static List<SourceLine> splitLines(String source) {
        List<SourceLine> lines = new ArrayList<>();
        int contentStart = 0;
        int index = 0;
        while (index < source.length()) {
            char character = source.charAt(index);
            if (character != '\n' && character != '\r') {
                index++;
                continue;
            }

            int delimiterLength = character == '\r'
                    && index + 1 < source.length()
                    && source.charAt(index + 1) == '\n' ? 2 : 1;
            lines.add(new SourceLine(
                    source.substring(contentStart, index),
                    source.substring(index, index + delimiterLength)));
            index += delimiterLength;
            contentStart = index;
        }
        lines.add(new SourceLine(source.substring(contentStart), ""));
        return List.copyOf(lines);
    }

    /**
     * Joins lines produced by {@link #splitLines(String)}.
     *
     * @param lines the lines to join
     * @return source text with each line's delimiter restored
     */
    public static String joinLines(List<SourceLine> lines) {
        StringBuilder source = new StringBuilder();
        for (SourceLine line : lines) {
            source.append(line.content()).append(line.delimiter());
        }
        return source.toString();
    }

    /**
     * Replaces only leading whitespace while preserving each original line's content and delimiter.
     *
     * @param original the source whose line content must be preserved
     * @param formatted the formatter output used as the indentation source
     * @return source with indentation copied from the formatted output, or the original source when
     *         the formatter changed the line structure
     */
    public static String replaceIndentation(String original, String formatted) {
        List<SourceLine> originalLines = splitLines(original);
        List<SourceLine> formattedLines = splitLines(formatted);
        if (originalLines.size() != formattedLines.size()) {
            return original;
        }

        for (int index = 0; index < originalLines.size(); index++) {
            if (!originalLines.get(index).content().stripLeading()
                    .equals(formattedLines.get(index).content().stripLeading())) {
                return original;
            }
        }

        List<SourceLine> rewrittenLines = new ArrayList<>(originalLines.size());
        for (int index = 0; index < originalLines.size(); index++) {
            SourceLine originalLine = originalLines.get(index);
            SourceLine formattedLine = formattedLines.get(index);
            String content = originalLine.content();
            if (content.isBlank()) {
                rewrittenLines.add(originalLine);
                continue;
            }

            int firstContent = firstNonWhitespace(content);
            String formattedIndentation = formattedLine.content().substring(
                    0,
                    firstNonWhitespace(formattedLine.content()));
            rewrittenLines.add(new SourceLine(
                    formattedIndentation + content.substring(firstContent),
                    originalLine.delimiter()));
        }
        return joinLines(rewrittenLines);
    }

    /**
     * Returns the first character that is not horizontal line indentation.
     *
     * @param line the line content to inspect
     * @return the first non-space or non-tab index, or the line length when it is blank
     */
    public static int firstNonWhitespace(String line) {
        int index = 0;
        while (index < line.length()
                && (line.charAt(index) == ' ' || line.charAt(index) == '\t')) {
            index++;
        }
        return index;
    }

    /**
     * Returns the first line delimiter found in source text.
     *
     * @param source the source text to inspect
     * @return {@code "\r\n"}, {@code "\r"}, {@code "\n"}, or the platform delimiter when none exists
     */
    public static String lineDelimiter(String source) {
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '\r') {
                return index + 1 < source.length() && source.charAt(index + 1) == '\n'
                        ? "\r\n"
                        : "\r";
            }
            if (character == '\n') {
                return "\n";
            }
        }
        return System.lineSeparator();
    }

    /**
     * Copies a supported formatter option from rule configuration into JDT options.
     *
     * @param options the formatter options to update
     * @param configuration the optional rule configuration
     * @param optionName the OpenCleanup option name
     * @param formatterKey the corresponding JDT formatter key
     * @return {@code true} when the option is absent or valid
     */
    private static boolean applyFormatterOption(
            Map<String, String> options,
            RuleConfiguration configuration,
            String optionName,
            String formatterKey) {
        if (configuration == null) {
            return true;
        }
        String value = configuration.optionValue(optionName);
        if (value != null && !value.isBlank()) {
            String normalized = value.trim();
            if ("tab-character".equals(optionName)
                    && !normalized.equals("tab")
                    && !normalized.equals("space")
                    && !normalized.equals("mixed")) {
                return false;
            }
            if (!"tab-character".equals(optionName) && !isPositiveInteger(normalized)) {
                return false;
            }
            options.put(formatterKey, normalized);
        }
        return true;
    }

    /**
     * Checks whether a formatter numeric option is a positive decimal integer.
     *
     * @param value the option value to validate
     * @return {@code true} when the value is a positive integer accepted by the plugin
     */
    private static boolean isPositiveInteger(String value) {
        try {
            return Integer.parseInt(value) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    /**
     * A source line and the delimiter that followed it in the original source.
     */
    public static final class SourceLine {

        private final String content;
        private final String delimiter;

        /**
         * Creates a source line value.
         *
         * @param content the line content without its delimiter
         * @param delimiter the original line delimiter
         */
        public SourceLine(String content, String delimiter) {
            this.content = content;
            this.delimiter = delimiter;
        }

        /**
         * Returns the line content without its delimiter.
         *
         * @return the line content
         */
        public String content() {
            return content;
        }

        /**
         * Returns the original line delimiter.
         *
         * @return the line delimiter, possibly empty for the final line
         */
        public String delimiter() {
            return delimiter;
        }
    }
}
