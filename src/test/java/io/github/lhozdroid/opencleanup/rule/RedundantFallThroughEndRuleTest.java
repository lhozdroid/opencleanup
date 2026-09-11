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
 * Tests removal of redundant fall-through block ends.
 */
class RedundantFallThroughEndRuleTest {

    /**
     * Verifies that a branch return duplicated after an if is removed.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void removesRedundantReturn() throws Exception {
        String source = """
                class Example {
                    int value(boolean condition, int result) {
                        if (condition) {
                            record();
                            return result;
                        }
                        return result;
                    }

                    void record() { }
                }
                """;

        String rewritten = rewrite(source);

        assertTrue(rewritten.contains("record();\n            }")
                || rewritten.contains("record();\n        }"), rewritten);
        assertEquals(1, rewritten.lines().filter(line -> line.contains("return result;")).count());
    }

    /**
     * Verifies that different jumps and comments are preserved.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesUnsupportedCandidates() throws Exception {
        String source = """
                class Example {
                    int different(boolean condition, int result) {
                        if (condition) {
                            return result;
                        }
                        return result + 1;
                    }

                    int commented(boolean condition, int result) {
                        if (condition) {
                            // Keep this return attached.
                            return result;
                        }
                        return result;
                    }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Parses source, applies the redundant fall-through-end rule, and renders the edits.
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
        configuration.setId(RedundantFallThroughEndRule.ID);
        boolean changed = new RedundantFallThroughEndRule().apply(compilationUnit, rewrite, configuration);
        if (!changed) {
            return source;
        }
        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
