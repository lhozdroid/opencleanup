package io.github.lhozdroid.opencleanup.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.junit.jupiter.api.Test;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

class EnhancedForRuleTest {

    /**
     * Verifies that a simple array index loop becomes an enhanced-for loop.
     *
     * @throws Exception if the temporary source file cannot be created or rewritten
     */
    @Test
    void convertsArrayIndexLoop() throws Exception {
        String source = """
                class Example {
                    void print(String[] values) {
                        for (int i = 0; i < values.length; i++) {
                            consume(values[i]);
                        }
                    }

                    void consume(String value) {
                    }
                }
                """;

        String rewritten = rewrite(source);

        assertTrue(rewritten.contains("for (var element : values)"));
        assertTrue(rewritten.contains("consume(element);"));
        assertTrue(!rewritten.contains("int i = 0"));
    }

    /**
     * Verifies that a simple list index loop becomes an enhanced-for loop.
     *
     * @throws Exception if the temporary source file cannot be created or rewritten
     */
    @Test
    void convertsListIndexLoop() throws Exception {
        String source = """
                import java.util.List;

                class Example {
                    void print(List<String> values) {
                        for (int i = 0; i < values.size(); ++i) {
                            consume(values.get(i));
                        }
                    }

                    void consume(String value) {
                    }
                }
                """;

        String rewritten = rewrite(source);

        assertTrue(rewritten.contains("for (var element : values)"));
        assertTrue(rewritten.contains("consume(element);"));
        assertTrue(!rewritten.contains("++i"));
    }

    /**
     * Verifies that uncertain loop shapes remain unchanged.
     *
     * @throws Exception if the temporary source file cannot be created or rewritten
     */
    @Test
    void preservesUncertainLoops() throws Exception {
        String source = """
                class Example {
                    void unsupported(String[] values, int start) {
                        for (int i = start; i < values.length; i++) {
                            consume(values[i]);
                        }
                        for (int i = 0; i < values.length; i++) {
                            values[i] = null;
                        }
                        for (int i = 0; i < values.length; i++) {
                            consume(values[i]);
                            consume(values[i]);
                        }
                        for (int i = 0; i < values.length; i++) {
                            consume(values[i]);
                            consume(i);
                        }
                    }

                    void consume(Object value) {
                    }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Applies the enhanced-for rule to one temporary Java source file.
     *
     * @param source the Java source string to rewrite
     * @return the rewritten Java source string
     * @throws Exception if the temporary source file cannot be created or rewritten
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
        configuration.setId(EnhancedForRule.ID);
        new EnhancedForRule().apply(compilationUnit, rewrite, configuration);

        Document document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
