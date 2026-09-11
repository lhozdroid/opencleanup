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
 * Tests common-operand factorization for passive boolean expressions.
 */
class OperandFactorizationRuleTest {

    /**
     * Verifies that a common operand is evaluated once in a factored expression.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void factorsCommonOperand() throws Exception {
        String source = """
                class Example {
                    boolean factor(boolean condition, boolean first, boolean second) {
                        return (condition && first) || (condition && second);
                    }
                }
                """;

        String rewritten = rewrite(source);

        assertTrue(rewritten.contains("return condition && (first || second);"));
    }

    /**
     * Verifies that calls and non-matching logical shapes remain unchanged.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void preservesUncertainShapes() throws Exception {
        String source = """
                class Example {
                    boolean calls(boolean first, boolean second) {
                        return (check() && first) || (check() && second);
                    }

                    boolean different(boolean condition, boolean first, boolean second,
                            boolean third) {
                        return (condition && first) || (second && third);
                    }

                    boolean check() {
                        return true;
                    }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Parses and rewrites one source string with the operand-factorization rule.
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
        configuration.setId(OperandFactorizationRule.ID);
        if (!new OperandFactorizationRule().apply(compilationUnit, rewrite, configuration)) {
            return source;
        }

        Document document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
