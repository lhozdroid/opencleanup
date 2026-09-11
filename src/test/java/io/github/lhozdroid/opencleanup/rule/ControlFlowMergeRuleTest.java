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
 * Tests conservative control-flow merging.
 */
class ControlFlowMergeRuleTest {

    /**
     * Verifies that an identical trailing statement is moved after both branches.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void mergesIdenticalTrailingStatements() throws Exception {
        String source = """
                class Example {
                    void run(boolean condition) {
                        if (condition) {
                            first();
                            finish();
                        } else {
                            second();
                            finish();
                        }
                    }

                    void first() { }
                    void second() { }
                    void finish() { }
                }
                """;

        String normalized = rewrite(source).replaceAll("\\s+", " ").trim();

        assertTrue(normalized.contains("if (condition) { first(); } else { second(); } finish();"),
                normalized);
        assertEquals(1, normalized.split("finish\\(\\);", -1).length - 1);
    }

    /**
     * Verifies that unequal branches and commented candidates remain unchanged.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesUncertainCandidates() throws Exception {
        String source = """
                class Example {
                    void unequal(boolean condition) {
                        if (condition) {
                            finish();
                        } else {
                            other();
                        }
                    }

                    void commented(boolean condition) {
                        if (condition) {
                            // Keep branch attachment.
                            finish();
                        } else {
                            finish();
                        }
                    }

                    void finish() { }
                    void other() { }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Parses source, applies the control-flow merge rule, and renders the edits.
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
        configuration.setId(ControlFlowMergeRule.ID);
        boolean changed = new ControlFlowMergeRule().apply(compilationUnit, rewrite, configuration);
        if (!changed) {
            return source;
        }
        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
