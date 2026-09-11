package io.github.lhozdroid.opencleanup.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.junit.jupiter.api.Test;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;
import io.github.lhozdroid.opencleanup.config.RuleOption;

/**
 * Tests conservative removal of optional parentheses.
 */
class ParenthesesRuleTest {

    /**
     * Verifies that simple expressions are unwrapped in safe expression positions.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void removesParenthesesAroundSimpleExpressions() throws Exception {
        String source = """
                class Example {
                    String field = (\"field\");

                    Object value(String name, boolean condition) {
                        Object local = (name);
                        if ((condition)) {
                            return (this);
                        }
                        consume((null));
                        return (local);
                    }

                    void consume(Object value) {
                    }
                }
                """;

        String rewritten = rewrite(source);

        assertTrue(rewritten.contains("String field = \"field\";"));
        assertTrue(rewritten.contains("Object local = name;"));
        assertTrue(rewritten.contains("if (condition) {"));
        assertTrue(rewritten.contains("return this;"));
        assertTrue(rewritten.contains("consume(null);"));
        assertTrue(rewritten.contains("return local;"));
    }

    /**
     * Verifies that explicit always and never modes produce their requested safe formatting.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void honorsAlwaysAndNeverModes() throws Exception {
        String mixedSource = """
                class Example {
                    boolean value(boolean first, boolean second, boolean third) {
                        return first && second || third;
                    }
                }
                """;

        String alwaysRewritten = rewrite(mixedSource, ParenthesesRule.ALWAYS);
        assertTrue(alwaysRewritten.contains("return (first && second) || third;"));

        String groupedSource = """
                class Example {
                    boolean value(boolean first, boolean second, boolean third) {
                        return (first && second) || third;
                    }
                }
                """;

        String neverRewritten = rewrite(groupedSource, ParenthesesRule.NEVER);
        assertTrue(neverRewritten.contains("return first && second || third;"));
    }

    /**
     * Verifies that non-simple expressions and unsupported contexts retain their parentheses.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesNonSimpleExpressionsAndUnsafeContexts() throws Exception {
        String source = """
                class Example {
                    void assign(int value) {
                        (value) = 1;
                    }

                    int calculate(int value, int other) {
                        int result = (value + other) * 2;
                        return ((String) null).length() + (value + 1);
                    }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Parses and rewrites one source string with the parentheses rule.
     *
     * @param source the Java source to rewrite
     * @return the rewritten Java source
     * @throws Exception if the source cannot be parsed or rewritten
     */
    private String rewrite(String source) throws Exception {
        return rewrite(source, null);
    }

    /**
     * Parses and rewrites one source string with an optional parentheses mode.
     *
     * @param source the Java source to rewrite
     * @param mode the configured mode, or {@code null} for the default mode
     * @return the rewritten Java source
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
        configuration.setId(ParenthesesRule.ID);
        if (mode != null) {
            RuleOption option = new RuleOption();
            option.setName(ParenthesesRule.ID);
            option.setValue(mode);
            configuration.setOptions(List.of(option));
        }
        boolean changed = new ParenthesesRule().apply(compilationUnit, rewrite, configuration);
        if (!changed) {
            return source;
        }

        Document document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
