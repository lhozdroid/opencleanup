package io.github.lhozdroid.opencleanup.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * Tests literal-delimited string concatenation cleanup.
 */
class StringsJoinRuleTest {

    /**
     * Verifies conversion of a repeated literal-delimited concatenation.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void convertsDelimitedConcatenation() throws Exception {
        String source = """
                class Example {
                    String join(String first, String second, String third) {
                        return first + ", " + second + ", " + third;
                    }
                }
                """;

        String rewritten = rewrite(source);

        assertTrue(rewritten.contains("String.join(\", \", first, second, third)"));
        assertTrue(!rewritten.contains("first + \", \" + second"));
    }

    /**
     * Verifies that non-literal or inconsistent delimiters remain unchanged.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void preservesUnsupportedConcatenation() throws Exception {
        String source = """
                class Example {
                    String one(String first, String second, String delimiter) {
                        return first + delimiter + second;
                    }

                    String two(String first, String second) {
                        return first + ", " + second + ";" + first;
                    }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Parses and rewrites one source string with the String.join rule.
     *
     * @param source the source string to rewrite
     * @return the rewritten source string
     * @throws Exception if parsing or applying AST edits fails
     */
    private String rewrite(String source) throws Exception {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setStatementsRecovery(true);
        parser.setBindingsRecovery(true);
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
        ASTRewrite rewrite = ASTRewrite.create(compilationUnit.getAST());
        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(StringsJoinRule.ID);
        new StringsJoinRule().apply(compilationUnit, rewrite, configuration);
        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
