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

/**
 * Tests boolean equality and exclusive-or normalization.
 */
class StrictlyEqualOrDifferentRuleTest {

    /**
     * Verifies equal and different boolean combinations use direct operators.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void convertsEqualityAndDifference() throws Exception {
        String source = """
                class Example {
                    boolean equal(boolean first, boolean second) {
                        return (first && second) || (!first && !second);
                    }

                    boolean different(boolean first, boolean second) {
                        return (first && !second) || (!first && second);
                    }
                }
                """;

        String rewritten = rewrite(source);

        assertTrue(rewritten.contains("return first == second;"));
        assertTrue(rewritten.contains("return first ^ second;"));
    }

    /**
     * Verifies that calls and non-complementary combinations are preserved.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void preservesUncertainShapes() throws Exception {
        String source = """
                class Example {
                    boolean calls(boolean first, boolean second) {
                        return (check() && second) || (!check() && !second);
                    }

                    boolean notComplementary(boolean first, boolean second) {
                        return (first && second) || (!first && second);
                    }

                    boolean check() {
                        return true;
                    }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Parses and rewrites one source string with the strict equality rule.
     *
     * @param source the Java source text to rewrite
     * @return the rewritten source text
     * @throws Exception if parsing or applying the AST edit fails
     */
    private String rewrite(String source) throws Exception {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
        ASTRewrite rewrite = ASTRewrite.create(compilationUnit.getAST());
        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(StrictlyEqualOrDifferentRule.ID);
        if (!new StrictlyEqualOrDifferentRule().apply(compilationUnit, rewrite, configuration)) {
            return source;
        }

        Document document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
