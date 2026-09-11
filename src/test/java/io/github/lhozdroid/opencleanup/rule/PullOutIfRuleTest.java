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
 * Tests pulling a duplicate inner if condition out of both outer branches.
 */
class PullOutIfRuleTest {

    /**
     * Verifies that a passive shared condition is evaluated once outside the branches.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void pullsOutSharedInnerIf() throws Exception {
        String source = """
                class Example {
                    void run(boolean active, boolean found) {
                        if (active) {
                            if (found) {
                                first();
                            }
                        } else {
                            if (found) {
                                second();
                            }
                        }
                    }

                    void first() { }
                    void second() { }
                }
                """;

        String normalized = rewrite(source).replaceAll("\\s+", " ").trim();

        assertTrue(normalized.contains("if (found) { if (active) { first(); } else { second(); } }"),
                normalized);
        assertEquals(1, normalized.split("if \\(found\\)", -1).length - 1);
    }

    /**
     * Verifies that unequal conditions and side-effecting conditions remain unchanged.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesUnsupportedShapes() throws Exception {
        String source = """
                class Example {
                    void unequal(boolean active, boolean found, boolean other) {
                        if (active) {
                            if (found) {
                                first();
                            }
                        } else {
                            if (other) {
                                second();
                            }
                        }
                    }

                    void sideEffect(boolean active) {
                        if (active) {
                            if (check()) {
                                first();
                            }
                        } else {
                            if (check()) {
                                second();
                            }
                        }
                    }

                    boolean check() { return true; }
                    void first() { }
                    void second() { }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Parses source, applies the pull-out-if rule, and renders the edits.
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
        configuration.setId(PullOutIfRule.ID);
        boolean changed = new PullOutIfRule().apply(compilationUnit, rewrite, configuration);
        if (!changed) {
            return source;
        }
        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
