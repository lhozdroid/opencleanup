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
 * Tests merging of conditional branches with identical blocks.
 */
class MergeConditionalRuleTest {

    /**
     * Verifies that equal if and else-if blocks merge their conditions.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void mergesEqualConditionalBlocks() throws Exception {
        String source = """
                class Example {
                    void merge(boolean first, boolean second, boolean third) {
                        if (first) {
                            work();
                        } else if (second) {
                            work();
                        } else if (third) {
                            other();
                        }
                    }

                    void work() {
                    }

                    void other() {
                    }
                }
                """;

        String rewritten = rewrite(source);

        assertTrue(rewritten.contains("if (first || second)"));
        assertTrue(rewritten.contains("else if (third)"));
        assertEquals(1, rewritten.lines().filter(line -> line.contains("work();")).count());
    }

    /**
     * Verifies that different bodies and commented candidates are preserved.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void preservesUncertainShapes() throws Exception {
        String differentBodies = """
                class Example {
                    void merge(boolean first, boolean second) {
                        if (first) {
                            work();
                        } else if (second) {
                            other();
                        }
                    }

                    void work() {
                    }

                    void other() {
                    }
                }
                """;
        String commented = """
                class Example {
                    void merge(boolean first, boolean second) {
                        if (first) {
                            work();
                        } else if (second) {
                            // Keep this branch explicit.
                            work();
                        }
                    }

                    void work() {
                    }
                }
                """;

        assertEquals(differentBodies, rewrite(differentBodies));
        assertEquals(commented, rewrite(commented));
    }

    /**
     * Parses and rewrites one source string with the merge-conditional rule.
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
        configuration.setId(MergeConditionalRule.ID);
        if (!new MergeConditionalRule().apply(compilationUnit, rewrite, configuration)) {
            return source;
        }

        Document document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
