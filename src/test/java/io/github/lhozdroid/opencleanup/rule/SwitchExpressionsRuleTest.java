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
 * Tests conversion of direct-return switch statements.
 */
class SwitchExpressionsRuleTest {

    /**
     * Verifies conversion to a colon-form switch expression with yield statements.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void convertsDirectReturnSwitch() throws Exception {
        String source = """
                class Example {
                    String describe(int value) {
                        switch (value) {
                            case 1:
                            case 2:
                                return "small";
                            default:
                                return "other";
                        }
                    }
                }
                """;

        String rewritten = rewrite(source);

        assertTrue(rewritten.contains("return switch (value)"));
        assertTrue(rewritten.contains("yield \"small\";"));
        assertTrue(rewritten.contains("yield \"other\";"));
        assertTrue(!rewritten.contains("return \"small\";"));
    }

    /**
     * Verifies that a case with additional statements remains unchanged.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void preservesNonDirectCases() throws Exception {
        String source = """
                class Example {
                    String describe(int value) {
                        switch (value) {
                            case 1:
                                log(value);
                                return "small";
                            default:
                                return "other";
                        }
                    }

                    void log(int value) {
                    }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Parses and rewrites one source string with the switch-expression rule.
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
        configuration.setId(SwitchExpressionsRule.ID);
        new SwitchExpressionsRule().apply(compilationUnit, rewrite, configuration);
        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
