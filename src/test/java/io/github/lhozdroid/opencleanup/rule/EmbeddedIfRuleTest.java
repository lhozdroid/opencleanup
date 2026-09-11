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
 * Tests conservative restructuring of embedded if statements.
 */
class EmbeddedIfRuleTest {

    /**
     * Verifies that an eligible nested if combines conditions and keeps its body.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void combinesEligibleEmbeddedIf() throws Exception {
        String source = """
                class Example {
                    void check(boolean outer, boolean inner) {
                        if (outer) {
                            if (inner) {
                                record();
                            }
                        }
                    }

                    void record() {
                    }
                }
                """;

        String rewrittenSource = rewrite(source);

        String normalized = rewrittenSource.replaceAll("\\s+", " ").trim();
        assertTrue(normalized.contains("if (outer && inner) { record(); }"), rewrittenSource);
        assertEquals(0, rewrittenSource.lines().filter(line -> line.trim().startsWith("if (inner")).count());
    }

    /**
     * Verifies that else branches, declarations, and comments-sensitive shapes remain unchanged.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesUnsupportedEmbeddedIfShapes() throws Exception {
        String source = """
                class Example {
                    void outerElse(boolean outer, boolean inner) {
                        if (outer) {
                            if (inner) {
                                record();
                            }
                        } else {
                            fallback();
                        }
                    }

                    void innerElse(boolean outer, boolean inner) {
                        if (outer) {
                            if (inner) {
                                record();
                            } else {
                                fallback();
                            }
                        }
                    }

                    void declaration(boolean outer, boolean inner) {
                        if (outer) {
                            if (inner) {
                                int value = 1;
                                record(value);
                            }
                        }
                    }

                    void comment(boolean outer, boolean inner) {
                        if (outer) {
                            // Keep this comment attached to the original shape.
                            if (inner) {
                                record();
                            }
                        }
                    }

                    void record() {
                    }

                    void record(int value) {
                    }

                    void fallback() {
                    }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Parses source, applies the embedded-if rule, and renders the AST edits.
     *
     * @param source the Java source to rewrite
     * @return the rewritten Java source
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
        configuration.setId(EmbeddedIfRule.ID);
        new EmbeddedIfRule().apply(compilationUnit, rewrite, configuration);

        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
