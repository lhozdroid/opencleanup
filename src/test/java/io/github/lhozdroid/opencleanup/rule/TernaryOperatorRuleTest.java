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
 * Tests conversion of passive boolean selections to ternary expressions.
 */
class TernaryOperatorRuleTest {

    /**
     * Verifies that both branch orders produce the same ternary expression.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void convertsBothBranchOrders() throws Exception {
        String source = """
                class Example {
                    boolean first(boolean condition, boolean whenTrue, boolean whenFalse) {
                        return (condition && whenTrue) || (!condition && whenFalse);
                    }

                    boolean second(boolean condition, boolean whenTrue, boolean whenFalse) {
                        return (!condition && whenFalse) || (condition && whenTrue);
                    }
                }
                """;

        String rewritten = rewrite(source);

        assertEquals(2, rewritten.lines()
                .filter(line -> line.contains("return condition ? whenTrue : whenFalse;"))
                .count());
    }

    /**
     * Verifies that calls and non-complementary boolean branches are preserved.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void preservesUncertainShapes() throws Exception {
        String source = """
                class Example {
                    boolean calls(boolean condition, boolean whenTrue, boolean whenFalse) {
                        return (check() && whenTrue) || (!check() && whenFalse);
                    }

                    boolean notComplementary(boolean condition, boolean first, boolean second) {
                        return (condition && first) || (condition && second);
                    }

                    boolean check() {
                        return true;
                    }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Parses and rewrites one source string with the ternary-operator rule.
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
        configuration.setId(TernaryOperatorRule.ID);
        if (!new TernaryOperatorRule().apply(compilationUnit, rewrite, configuration)) {
            return source;
        }

        Document document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
