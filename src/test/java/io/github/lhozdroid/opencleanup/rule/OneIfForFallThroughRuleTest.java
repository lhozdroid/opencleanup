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
 * Tests merging consecutive duplicate if bodies.
 */
class OneIfForFallThroughRuleTest {

    /**
     * Verifies that consecutive duplicate returning blocks become one if.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void mergesConsecutiveJumpingIfs() throws Exception {
        String source = """
                class Example {
                    int value(boolean first, boolean second) {
                        if (first) {
                            record();
                            return 1;
                        }
                        if (second) {
                            record();
                            return 1;
                        }
                        return 0;
                    }

                    void record() { }
                }
                """;

        String normalized = rewrite(source).replaceAll("\\s+", " ").trim();

        assertTrue(normalized.contains("if (first || second) { record(); return 1; }"), normalized);
        assertEquals(1, normalized.split("if \\(", -1).length - 1);
    }

    /**
     * Verifies that non-jumping bodies, else branches, and comments are preserved.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesUnsupportedCandidates() throws Exception {
        String source = """
                class Example {
                    void nonJump(boolean first, boolean second) {
                        if (first) {
                            record();
                        }
                        if (second) {
                            record();
                        }
                    }

                    void hasElse(boolean first, boolean second) {
                        if (first) {
                            return;
                        } else {
                            record();
                        }
                        if (second) {
                            return;
                        }
                    }

                    void commented(boolean first, boolean second) {
                        if (first) {
                            // Keep this comment.
                            return;
                        }
                        if (second) {
                            return;
                        }
                    }

                    void record() { }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Parses source, applies the one-if fall-through rule, and renders the edits.
     *
     * @param source the Java source to rewrite
     * @return the rewritten source
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
        configuration.setId(OneIfForFallThroughRule.ID);
        boolean changed = new OneIfForFallThroughRule().apply(compilationUnit, rewrite, configuration);
        if (!changed) {
            return source;
        }
        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
