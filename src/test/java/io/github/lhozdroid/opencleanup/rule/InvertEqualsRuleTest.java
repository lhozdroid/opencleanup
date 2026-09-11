package io.github.lhozdroid.opencleanup.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

class InvertEqualsRuleTest {

    /**
     * Verifies equality and inequality comparisons with a left null literal are inverted.
     *
     * @throws Exception if the source rewrite cannot be applied
     */
    @Test
    void invertsNullComparisons() throws Exception {
        String source = """
                class Example {
                    boolean equalsCheck(Object value) {
                        return null == value;
                    }

                    boolean notEqualsCheck(Object value) {
                        return null != value;
                    }
                }
                """;

        String rewritten = rewrite(source);

        String expected = """
                class Example {
                    boolean equalsCheck(Object value) {
                        return value == null;
                    }

                    boolean notEqualsCheck(Object value) {
                        return value != null;
                    }
                }
                """;
        assertEquals(expected, rewritten);
    }

    /**
     * Verifies comparisons that are already in the safe form or compare two null literals remain unchanged.
     *
     * @throws Exception if the source rewrite cannot be applied
     */
    @Test
    void preservesUnsupportedComparisons() throws Exception {
        String source = """
                class Example {
                    boolean alreadyInverted(Object value) {
                        return value == null;
                    }

                    boolean sideEffectingExpression(Object value) {
                        return nextValue() == null;
                    }

                    boolean nullToNull() {
                        return null == null;
                    }

                    Object nextValue() {
                        return null;
                    }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Applies the rule to source text using JDT's parser and rewrite engine.
     *
     * @param source the Java source text to rewrite
     * @return the rewritten Java source text
     * @throws Exception if the source rewrite cannot be applied
     */
    private String rewrite(String source) throws Exception {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);

        ASTRewrite rewrite = ASTRewrite.create(compilationUnit.getAST());
        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(InvertEqualsRule.ID);
        new InvertEqualsRule().apply(compilationUnit, rewrite, configuration);

        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
