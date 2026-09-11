package io.github.lhozdroid.opencleanup.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;
import io.github.lhozdroid.opencleanup.config.RuleOption;

/**
 * Tests the safe add-blocks behavior for control statements.
 */
class ControlStatementBlocksRuleTest {

    @TempDir
    Path tempDirectory;

    /**
     * Verifies non-block bodies are wrapped while their original statements are preserved.
     *
     * @throws Exception if the temporary source file cannot be created or rewritten
     */
    @Test
    void addsBlocksToNonBlockControlStatementBodies() throws Exception {
        Path sourceFile = tempDirectory.resolve("Example.java");
        Files.writeString(sourceFile, """
                class Example {
                    void check(boolean condition, int[] values) {
                        if (condition) first(); else second();
                        while (condition) loop();
                        do loop(); while (condition);
                        for (int value : values) consume(value);
                        for (int index = 0; index < values.length; index++) consume(values[index]);
                    }

                    void first() {
                    }

                    void second() {
                    }

                    void loop() {
                    }

                    void consume(int value) {
                    }
                }
                """, StandardCharsets.UTF_8);

        String rewritten = rewrite(Files.readString(sourceFile, StandardCharsets.UTF_8));
        Files.writeString(sourceFile, rewritten, StandardCharsets.UTF_8);

        String source = Files.readString(sourceFile, StandardCharsets.UTF_8);
        String normalized = source.replaceAll("\\s+", " ").trim();
        assertTrue(normalized.contains("if (condition) { first(); } else { second(); }"));
        assertTrue(normalized.contains("while (condition) { loop(); }"));
        assertTrue(normalized.contains("do { loop(); } while (condition);"));
        assertTrue(normalized.contains("for (int value : values) { consume(value); }"));
        assertTrue(normalized.contains(
                "for (int index = 0; index < values.length; index++) { consume(values[index]); }"));
    }

    /**
     * Verifies existing blocks remain unchanged and no block-removal behavior is attempted.
     *
     * @throws Exception if the temporary source file cannot be created or rewritten
     */
    @Test
    void preservesExistingBlocks() throws Exception {
        Path sourceFile = tempDirectory.resolve("AlreadyBlocked.java");
        String original = """
                class AlreadyBlocked {
                    void check(boolean condition) {
                        if (condition) {
                            first();
                        } else {
                            second();
                        }
                        while (condition) {
                            loop();
                        }
                    }

                    void first() {
                    }

                    void second() {
                    }

                    void loop() {
                    }
                }
                """;
        Files.writeString(sourceFile, original, StandardCharsets.UTF_8);

        String rewritten = rewrite(original);
        Files.writeString(sourceFile, rewritten, StandardCharsets.UTF_8);

        assertEquals(original, Files.readString(sourceFile, StandardCharsets.UTF_8));
    }

    /**
     * Verifies the never mode removes only safe single-statement blocks.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void removesSafeBlocksInNeverMode() throws Exception {
        String original = """
                class Example {
                    void check(boolean condition) {
                        if (condition) {
                            first();
                        }
                        while (condition) {
                            loop();
                        }
                    }

                    void first() {
                    }

                    void loop() {
                    }
                }
                """;

        String rewritten = rewrite(original, "never");
        String normalized = rewritten.replaceAll("\\s+", " ").trim();
        assertTrue(normalized.contains("if (condition) first();"));
        assertTrue(normalized.contains("while (condition) loop();"));
    }

    /**
     * Verifies the JDT-style mode adds blocks only to multi-line bodies.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void addsBlocksOnlyToMultiLineBodiesInJdtStyleMode() throws Exception {
        String original = """
                class Example {
                    void check(boolean condition) {
                        if (condition) first();
                        if (condition) second(
                                1);
                    }

                    void first() {
                    }

                    void second(int value) {
                    }
                }
                """;

        String rewritten = rewrite(original, "jdt-style");
        String normalized = rewritten.replaceAll("\\s+", " ").trim();
        assertTrue(normalized.contains("if (condition) first();"));
        assertTrue(normalized.contains("if (condition) { second( 1); }"));
    }

    /**
     * Rewrites the temporary source with the control-statement blocks rule.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    private String rewrite(String source) throws Exception {
        return rewrite(source, null);
    }

    /**
     * Rewrites source with an optional control-statement block mode.
     *
     * @param source the Java source text
     * @param mode the optional block mode
     * @return the rewritten source text
     * @throws Exception if the source cannot be parsed or rewritten
     */
    private String rewrite(String source, String mode) throws Exception {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setSource(source.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setStatementsRecovery(true);
        parser.setBindingsRecovery(true);
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
        ASTRewrite rewrite = ASTRewrite.create(compilationUnit.getAST());

        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(ControlStatementBlocksRule.ID);
        if (mode != null) {
            RuleOption option = new RuleOption();
            option.setName(ControlStatementBlocksRule.ID);
            option.setValue(mode);
            configuration.setOptions(java.util.List.of(option));
        }
        boolean changed = new ControlStatementBlocksRule().apply(compilationUnit, rewrite, configuration);
        if (!changed) {
            return source;
        }

        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
