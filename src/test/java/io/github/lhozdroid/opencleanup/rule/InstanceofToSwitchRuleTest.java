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
 * Tests conversion of type-pattern chains to switch statements.
 */
class InstanceofToSwitchRuleTest {

    /**
     * Verifies conversion of a two-branch pattern chain with an unmatched branch.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void convertsPatternChain() throws Exception {
        String source = """
                class Example {
                    String describe(Object value) {
                        if (value instanceof String text) {
                            return text;
                        } else if (value instanceof Integer number) {
                            return number.toString();
                        } else {
                            return "other";
                        }
                    }
                }
                """;

        String rewritten = rewrite(source);

        assertTrue(rewritten.contains("switch (value)"));
        assertTrue(rewritten.contains("case String text ->"));
        assertTrue(rewritten.contains("case Integer number ->"));
        assertTrue(rewritten.contains("default ->"));
        assertTrue(!rewritten.contains("else if"));
    }

    /**
     * Verifies that mixed selectors and non-pattern conditions remain unchanged.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void preservesUncertainChains() throws Exception {
        String source = """
                class Example {
                    void check(Object first, Object second) {
                        if (first instanceof String text) {
                            use(text);
                        } else if (second instanceof Integer number) {
                            use(number);
                        }
                    }

                    void use(Object value) {
                    }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Parses and rewrites one source string with the instanceof-to-switch rule.
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
        configuration.setId(InstanceofToSwitchRule.ID);
        new InstanceofToSwitchRule().apply(compilationUnit, rewrite, configuration);
        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
