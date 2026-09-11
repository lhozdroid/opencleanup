package io.github.lhozdroid.opencleanup.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * Tests conservative simplification of boolean literal expressions.
 */
class BooleanLiteralRuleTest {

    /**
     * Verifies that literal negation and logical combinations are folded.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void foldsLiteralBooleanExpressions() throws Exception {
        String source = """
                class Example {
                    boolean first() {
                        return !true;
                    }

                    boolean second() {
                        return !false;
                    }

                    boolean third() {
                        return true && (!false || false);
                    }
                }
                """;

        String rewritten = rewrite(source);

        assertTrue(rewritten.contains("return false;"));
        assertTrue(rewritten.contains("return true;"));
        assertEquals(3, rewritten.lines().filter(line -> line.contains("return true;")
                || line.contains("return false;")).count());
        assertFalse(rewritten.contains("!true"));
        assertFalse(rewritten.contains("!false"));
    }

    /**
     * Verifies that expressions with non-literal operands remain unchanged.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesExpressionsThatCouldInvolveBoxedBooleanValues() throws Exception {
        String source = """
                class Example {
                    boolean check(boolean primitive, Boolean boxed) {
                        boolean first = !primitive;
                        boolean second = boxed && true;
                        boolean third = true || boxed;
                        return first || second || third;
                    }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Parses and rewrites one source string with the boolean literal rule.
     *
     * @param source the Java source to rewrite
     * @return the rewritten Java source
     * @throws Exception if the source cannot be parsed or rewritten
     */
    private String rewrite(String source) throws Exception {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setSource(source.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setStatementsRecovery(true);
        parser.setBindingsRecovery(true);
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
        ASTRewrite rewrite = ASTRewrite.create(compilationUnit.getAST());

        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(BooleanLiteralRule.ID);
        boolean changed = new BooleanLiteralRule().apply(compilationUnit, rewrite, configuration);
        if (!changed) {
            return source;
        }

        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
