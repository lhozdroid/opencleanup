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
 * Tests conservative standardization of literal-left relational comparisons.
 */
class StandardizeComparisonRuleTest {

    /**
     * Verifies that supported numeric and character relational comparisons are swapped and reversed.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void standardizesLiteralLeftRelationalComparisons() throws Exception {
        String source = """
                class Example {
                    boolean less(int value) {
                        return 10 < value;
                    }

                    boolean lessOrEqual(int value) {
                        return 10 <= value;
                    }

                    boolean greater(int value) {
                        return 10 > value;
                    }

                    boolean greaterOrEqual(int value) {
                        return 10 >= value;
                    }

                    boolean character(char value) {
                        return 'a' < value;
                    }
                }
                """;

        String expected = """
                class Example {
                    boolean less(int value) {
                        return value > 10;
                    }

                    boolean lessOrEqual(int value) {
                        return value >= 10;
                    }

                    boolean greater(int value) {
                        return value < 10;
                    }

                    boolean greaterOrEqual(int value) {
                        return value <= 10;
                    }

                    boolean character(char value) {
                        return value > 'a';
                    }
                }
                """;

        assertEquals(expected, rewrite(source));
    }

    /**
     * Verifies that unsupported comparison shapes remain unchanged.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesUnsupportedComparisonShapes() throws Exception {
        String source = """
                class Example {
                    boolean alreadyStandard(int value) {
                        return value > 10;
                    }

                    boolean equality(int value) {
                        return 10 == value;
                    }

                    boolean literalToLiteral() {
                        return 10 < 20;
                    }

                    boolean nonLiteralLeft(int value, int other) {
                        return value < other;
                    }

                    boolean extended(int value, int other) {
                        return value > 10 && other > 0;
                    }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Parses and rewrites one source string with the comparison standardization rule.
     *
     * @param source the Java source text to rewrite
     * @return the rewritten Java source text
     * @throws Exception if the source rewrite cannot be applied
     */
    private String rewrite(String source) throws Exception {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);

        ASTRewrite rewrite = ASTRewrite.create(compilationUnit.getAST());
        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(StandardizeComparisonRule.ID);
        new StandardizeComparisonRule().apply(compilationUnit, rewrite, configuration);

        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
