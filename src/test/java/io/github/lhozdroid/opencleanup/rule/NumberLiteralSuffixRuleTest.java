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
 * Tests canonical casing for numeric literal suffixes.
 */
class NumberLiteralSuffixRuleTest {

    /**
     * Verifies that lowercase long, float, and double suffixes are uppercased without other edits.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void normalizesSupportedLowercaseSuffixes() throws Exception {
        String source = """
                class Example {
                    long decimal = 42l;
                    long hexadecimal = 0x2al;
                    long binary = 0b1010l;
                    float decimalFloat = 1.25f;
                    double decimalDouble = 2.5d;
                    double hexadecimalDouble = 0x1.0p1d;
                }
                """;

        String expected = """
                class Example {
                    long decimal = 42L;
                    long hexadecimal = 0x2aL;
                    long binary = 0b1010L;
                    float decimalFloat = 1.25F;
                    double decimalDouble = 2.5D;
                    double hexadecimalDouble = 0x1.0p1D;
                }
                """;

        assertEquals(expected, rewrite(source));
    }

    /**
     * Verifies that canonical suffixes and literals without supported lowercase suffixes remain
     * unchanged.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesCanonicalSuffixesAndLiteralStructure() throws Exception {
        String source = """
                class Example {
                    long upperLong = 42L;
                    float upperFloat = 1.25F;
                    double upperDouble = 2.5D;
                    int hexadecimal = 0x2a;
                    double exponent = 1.0e2;
                    int separated = 1_000;
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Parses and rewrites one source string with the numeric literal suffix rule.
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
        configuration.setId(NumberLiteralSuffixRule.ID);
        boolean changed = new NumberLiteralSuffixRule().apply(compilationUnit, rewrite, configuration);
        if (!changed) {
            return source;
        }

        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
