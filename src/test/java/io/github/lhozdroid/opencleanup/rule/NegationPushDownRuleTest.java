package io.github.lhozdroid.opencleanup.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.junit.jupiter.api.Test;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

class NegationPushDownRuleTest {

    /**
     * Verifies De Morgan conversion preserves operand order for conjunctions and disjunctions.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void pushesNegationDownInSourceOrder() throws Exception {
        String source = """
                class Example {
                    boolean conjunction(boolean first, boolean second, boolean third) {
                        return !(first && second && third);
                    }

                    boolean disjunction(boolean first, boolean second) {
                        return !(first || second);
                    }
                }
                """;

        String rewrittenSource = rewrite(source);
        assertEquals(1, rewrittenSource.lines()
                .filter(line -> line.contains("return !first || !second || !third;"))
                .count());
        assertEquals(1, rewrittenSource.lines()
                .filter(line -> line.contains("return !first && !second;"))
                .count());
    }

    /**
     * Verifies unparenthesized, non-short-circuit, and nested forms remain unchanged.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void preservesUnsupportedForms() throws Exception {
        String original = """
                class Unsupported {
                    boolean unparenthesized(boolean first, boolean second) {
                        return !first && second;
                    }

                    boolean bitwise(boolean first, boolean second) {
                        return !(first & second);
                    }

                    boolean nested(boolean first, boolean second) {
                        return !(!(first && second));
                    }
                }
                """;

        assertEquals(original, rewrite(original));
    }

    /**
     * Creates an enabled configuration for the negation push-down rule.
     *
     * @return the configured negation push-down rule
     */
    private RuleConfiguration configuration() {
        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(NegationPushDownRule.ID);
        return configuration;
    }

    /**
     * Applies the rule directly to one parsed source unit.
     *
     * @param source the Java source text to rewrite
     * @return the rewritten source text
     * @throws Exception if the source cannot be parsed or the edit cannot be applied
     */
    private String rewrite(String source) throws Exception {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setStatementsRecovery(true);
        parser.setBindingsRecovery(true);
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
        ASTRewrite rewrite = ASTRewrite.create(compilationUnit.getAST());
        if (!new NegationPushDownRule().apply(compilationUnit, rewrite, configuration())) {
            return source;
        }

        Document document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
