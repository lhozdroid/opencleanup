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
 * Tests syntax-only lambda simplification.
 */
class SimplifyLambdaRuleTest {

    /**
     * Verifies removal of one untyped parameter's parentheses and a return block.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void simplifiesLambdaSyntax() throws Exception {
        String source = """
                import java.util.function.Function;

                class Example {
                    Function<String, String> trim = (value) -> {
                        return value.trim();
                    };
                }
                """;

        String rewritten = rewrite(source);

        assertTrue(rewritten.contains("value -> value.trim()"));
        assertTrue(!rewritten.contains("return value.trim();"));
    }

    /**
     * Verifies that typed parameters remain unchanged while multi-statement blocks retain their body.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void preservesUnsupportedLambdaShapes() throws Exception {
        String source = """
                import java.util.function.Function;

                class Example {
                    Function<String, String> typed = (String value) -> value.trim();
                    Function<String, String> block = (value) -> {
                        log(value);
                        return value.trim();
                    };

                    void log(String value) {
                    }
                }
                """;

        String rewritten = rewrite(source);

        assertTrue(rewritten.contains("(String value) -> value.trim()"));
        assertTrue(rewritten.contains("block = value -> {"));
        assertTrue(rewritten.contains("log(value);"));
        assertTrue(rewritten.contains("return value.trim();"));
    }

    /**
     * Parses and rewrites one source string with the lambda simplification rule.
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
        configuration.setId(SimplifyLambdaRule.ID);
        new SimplifyLambdaRule().apply(compilationUnit, rewrite, configuration);
        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
