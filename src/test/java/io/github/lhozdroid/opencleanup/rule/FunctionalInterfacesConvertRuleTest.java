package io.github.lhozdroid.opencleanup.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
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
import io.github.lhozdroid.opencleanup.config.RuleOption;

/**
 * Tests conversion between recognized functional-interface forms.
 */
class FunctionalInterfacesConvertRuleTest {

    /**
     * Verifies conversion of a simple Runnable anonymous class to a lambda.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void convertsAnonymousClassToLambda() throws Exception {
        String source = """
                class Example {
                    void run() {
                        Runnable task = new Runnable() {
                            @Override
                            public void run() {
                                work();
                            }
                        };
                        task.run();
                    }

                    void work() {
                    }
                }
                """;

        String rewritten = rewrite(source, "lambda");

        assertTrue(rewritten.contains("Runnable task = () ->"));
        assertTrue(!rewritten.contains("new Runnable()"));
    }

    /**
     * Verifies conversion of a target-typed Runnable lambda to an anonymous class.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void convertsLambdaToAnonymousClass() throws Exception {
        String source = """
                class Example {
                    void run() {
                        Runnable task = () -> work();
                        task.run();
                    }

                    void work() {
                    }
                }
                """;

        String rewritten = rewrite(source, "anonymous");

        assertTrue(rewritten.contains("new Runnable()"));
        assertTrue(rewritten.contains("public void run()"));
        assertTrue(rewritten.contains("work();"));
    }

    /**
     * Verifies that missing direction options and uncertain anonymous bodies are preserved.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void preservesUnsupportedForms() throws Exception {
        String source = """
                class Example {
                    void run() {
                        Runnable task = new Runnable() {
                            public void run() {
                                System.out.println(this);
                            }
                        };
                    }
                }
                """;

        assertEquals(source, rewrite(source, "lambda"));
    }

    /**
     * Parses and rewrites one source string in the requested conversion direction.
     *
     * @param source the source string to rewrite
     * @param mode the conversion option value
     * @return the rewritten source string
     * @throws Exception if parsing or applying AST edits fails
     */
    private String rewrite(String source, String mode) throws Exception {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setStatementsRecovery(true);
        parser.setBindingsRecovery(true);
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
        ASTRewrite rewrite = ASTRewrite.create(compilationUnit.getAST());
        RuleOption option = new RuleOption();
        option.setName(FunctionalInterfacesConvertRule.ID);
        option.setValue(mode);
        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(FunctionalInterfacesConvertRule.ID);
        configuration.setOptions(List.of(option));
        new FunctionalInterfacesConvertRule().apply(compilationUnit, rewrite, configuration);
        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
