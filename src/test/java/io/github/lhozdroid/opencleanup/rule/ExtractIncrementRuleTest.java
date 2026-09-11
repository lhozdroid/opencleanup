package io.github.lhozdroid.opencleanup.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.junit.jupiter.api.Test;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Tests conservative extraction of direct increments from local variable declarations.
 */
class ExtractIncrementRuleTest {

    /**
     * Verifies that prefix and postfix increment/decrement operations are extracted in order.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void extractsDirectIncrementAndDecrementOperations() throws Exception {
        String source = """
                class Example {
                    void run(int value) {
                        int postIncrement = value++;
                        int prefixIncrement = ++value;
                        int postDecrement = value--;
                        int prefixDecrement = --value;
                    }
                }
                """;

        String expected = """
                class Example {
                    void run(int value) {
                        int postIncrement = value;
                        value++;
                        value++;
                        int prefixIncrement = value;
                        int postDecrement = value;
                        value--;
                        value--;
                        int prefixDecrement = value;
                    }
                }
                """;

        assertEquals(
                expected.replaceAll("\\s+", " ").trim(),
                rewrite(source).replaceAll("\\s+", " ").trim());
    }

    /**
     * Verifies that unsupported expression contexts and ambiguous declarations remain unchanged.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void preservesUnsupportedIncrementContexts() throws Exception {
        String source = """
                class Example {
                    void run(int value, int[] values) {
                        int first = values[value++];
                        int second = (value++);
                        int firstFragment = value++, secondFragment = value++;
                        consume(value++);
                        for (int index = value++; index < values.length; index++) {
                            consume(index);
                        }
                    }

                    void consume(int value) {
                    }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Parses and rewrites one source string with the increment extraction rule.
     *
     * @param source the Java source to rewrite
     * @return the rewritten Java source
     * @throws Exception if the source cannot be parsed or rewritten
     */
    private String rewrite(String source) throws Exception {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setSource(source.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setStatementsRecovery(true);
        parser.setBindingsRecovery(true);
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
        ASTRewrite rewrite = ASTRewrite.create(compilationUnit.getAST());

        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(ExtractIncrementRule.ID);
        boolean changed = new ExtractIncrementRule().apply(compilationUnit, rewrite, configuration);
        if (!changed) {
            return source;
        }

        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
