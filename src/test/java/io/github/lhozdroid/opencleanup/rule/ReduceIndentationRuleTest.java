package io.github.lhozdroid.opencleanup.rule;

import static org.junit.jupiter.api.Assertions.assertTrue;

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

/** Tests conservative reductions of avoidable if-statement indentation. */
class ReduceIndentationRuleTest {

    /**
     * Verifies an else branch follows a then branch that always exits.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void movesElseAfterNonCompletingThenBranch() throws Exception {
        String source = """
                class Example {
                    void check(boolean condition) {
                        if (condition) {
                            return;
                        } else {
                            work();
                        }
                    }

                    void work() {
                    }
                }
                """;

        String rewritten = rewrite(source);
        String normalized = rewritten.replaceAll("\\s+", " ").trim();
        assertTrue(normalized.contains("if (condition) { return; } work();"));
    }

    /**
     * Verifies a final two-branch if is inverted and its then branch is unindented.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void invertsFinalTwoBranchIf() throws Exception {
        String source = """
                class Example {
                    void check(boolean condition) {
                        if (condition) {
                            first();
                        } else {
                            second();
                        }
                    }

                    void first() {
                    }

                    void second() {
                    }
                }
                """;

        String normalized = rewrite(source).replaceAll("\\s+", " ").trim();
        assertTrue(normalized.contains("if (!condition) { second(); } first();"));
    }

    /**
     * Verifies a non-terminal two-branch if remains unchanged.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void preservesNonTerminalTwoBranchIf() throws Exception {
        String source = """
                class Example {
                    void check(boolean condition) {
                        if (condition) {
                            first();
                        } else {
                            second();
                        }
                        after();
                    }

                    void first() {
                    }

                    void second() {
                    }

                    void after() {
                    }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Applies the indentation-reduction rule to source text.
     *
     * @param source the Java source text
     * @return the rewritten source text
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
        configuration.setId(ReduceIndentationRule.ID);
        boolean changed = new ReduceIndentationRule().apply(compilationUnit, rewrite, configuration);
        if (!changed) {
            return source;
        }

        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
