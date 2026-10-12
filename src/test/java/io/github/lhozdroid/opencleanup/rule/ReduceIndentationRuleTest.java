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
     * Verifies a final two-branch if whose else branch terminates is inverted
     * and its then branch is unindented.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void invertsFinalTwoBranchIfWithTerminatingElse() throws Exception {
        String source = """
                class Example {
                    void check(boolean condition) {
                        if (condition) {
                            first();
                        } else {
                            return;
                        }
                    }

                    void first() {
                    }
                }
                """;

        String normalized = rewrite(source).replaceAll("\\s+", " ").trim();
        assertTrue(normalized.contains("if (!condition) { return; } first();"));
    }

    /**
     * Verifies a final two-branch if is left unchanged when the else branch can
     * complete normally, because hoisting the then branch would run it on the
     * else path too.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void preservesFinalTwoBranchIfWhenElseCompletesNormally() throws Exception {
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

        assertEquals(source, rewrite(source));
    }

    /**
     * Verifies statements duplicated across both branches are not hoisted out
     * of their block when the else branch completes normally.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void preservesDuplicatedStatementsAcrossBranches() throws Exception {
        String source = """
                class Example {
                    void run(boolean inward, int[] counts, int index, int delta) {
                        if (inward) {
                            counts[index] += delta;
                        } else {
                            final int removed = Math.min(delta, counts[index]);
                            counts[index] -= removed;
                        }
                    }
                }
                """;

        assertEquals(source, rewrite(source));
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
