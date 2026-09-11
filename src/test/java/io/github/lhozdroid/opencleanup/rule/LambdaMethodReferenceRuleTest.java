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
 * Tests conservative lambda-to-method-reference conversion.
 */
class LambdaMethodReferenceRuleTest {

    /**
     * Verifies conversion of explicit typed receivers and constructor lambdas.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void convertsTypedReceiverAndConstructor() throws Exception {
        String source = """
                import java.util.function.Function;

                class Example {
                    Function<String, String> trim = (String value) -> value.trim();
                    Function<String, String> copy = value -> new String(value);
                }
                """;

        String rewritten = rewrite(source);

        assertTrue(rewritten.contains("Function<String, String> trim = String::trim;"));
        assertTrue(rewritten.contains("Function<String, String> copy = String::new;"));
    }

    /**
     * Verifies conversion of bound instance calls and preservation of ambiguous lambdas.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void convertsBoundCallAndPreservesImplicitReceiver() throws Exception {
        String source = """
                import java.util.function.Function;

                class Example {
                    Function<String, String> bound = value -> this.normalize(value);
                    Function<String, String> ambiguous = value -> value.trim();

                    String normalize(String value) {
                        return value.trim();
                    }
                }
                """;

        String rewritten = rewrite(source);

        assertTrue(rewritten.contains("Function<String, String> bound = this::normalize;"));
        assertEquals(source, rewritten.replace("this::normalize", "value -> this.normalize(value)"));
        assertTrue(rewritten.contains("Function<String, String> ambiguous = value -> value.trim();"));
    }

    /**
     * Verifies that multi-statement lambda bodies are not changed.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void preservesMultiStatementBody() throws Exception {
        String source = """
                import java.util.function.Function;

                class Example {
                    Function<String, String> value = (String input) -> {
                        String trimmed = input.trim();
                        return trimmed;
                    };
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Parses and rewrites one source string with the lambda method-reference rule.
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
        configuration.setId(LambdaMethodReferenceRule.ID);
        new LambdaMethodReferenceRule().apply(compilationUnit, rewrite, configuration);
        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
