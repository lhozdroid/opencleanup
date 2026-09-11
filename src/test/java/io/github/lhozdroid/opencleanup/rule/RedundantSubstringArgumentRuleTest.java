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
 * Tests conservative removal of redundant substring arguments.
 */
class RedundantSubstringArgumentRuleTest {

    /**
     * Verifies that the redundant length argument is removed for a stable simple-name receiver.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void removesRedundantLengthArgument() throws Exception {
        String source = """
                class Example {
                    String copy(String value) {
                        return value.substring(0, value.length());
                    }
                }
                """;

        String expected = """
                class Example {
                    String copy(String value) {
                        return value.substring(0);
                    }
                }
                """;

        assertEquals(expected, rewrite(source));
    }

    /**
     * Verifies that non-matching receiver, start-index, and argument shapes remain unchanged.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesUnsupportedSubstringShapes() throws Exception {
        String source = """
                class Example {
                    String differentReceiver(String value, String other) {
                        return value.substring(0, other.length());
                    }

                    String nonSimpleReceiver(String value) {
                        return value.trim().substring(0, value.trim().length());
                    }

                    String nonZeroStart(String value) {
                        return value.substring(1, value.length());
                    }

                    String nonLengthEnd(String value) {
                        return value.substring(0, value.hashCode());
                    }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Parses and rewrites one source string with the redundant substring-argument rule.
     *
     * @param source the Java source text to rewrite
     * @return the rewritten Java source text
     * @throws Exception if the source cannot be parsed or rewritten
     */
    private String rewrite(String source) throws Exception {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);

        ASTRewrite rewrite = ASTRewrite.create(compilationUnit.getAST());
        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(RedundantSubstringArgumentRule.ID);
        boolean changed = new RedundantSubstringArgumentRule().apply(
                compilationUnit,
                rewrite,
                configuration);
        if (!changed) {
            return source;
        }

        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
