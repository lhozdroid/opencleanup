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
 * Tests redundant negated else-if conditions.
 */
class RedundantIfConditionRuleTest {

    /**
     * Verifies that a passive negated else-if condition is removed.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void removesNegatedElseIfCondition() throws Exception {
        String source = """
                class Example {
                    int value(boolean valid) {
                        if (valid) {
                            return 1;
                        } else if (!valid) {
                            return 2;
                        }
                        return 3;
                    }
                }
                """;

        String normalized = rewrite(source).replaceAll("\\s+", " ").trim();

        assertTrue(normalized.contains("if (valid) { return 1; } else { return 2; }"), normalized);
        assertEquals(0, normalized.split("else if", -1).length - 1);
    }

    /**
     * Verifies that non-negated, side-effecting, and commented conditions remain unchanged.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesUnsupportedConditions() throws Exception {
        String source = """
                class Example {
                    int sideEffect(boolean valid) {
                        if (valid) {
                            return 1;
                        } else if (!check()) {
                            return 2;
                        }
                        return 3;
                    }

                    int notNegative(boolean valid) {
                        if (valid) {
                            return 1;
                        } else if (valid) {
                            return 2;
                        }
                        return 3;
                    }

                    int check() { return 0; }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Parses source, applies the redundant-if rule, and renders the edits.
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
        configuration.setId(RedundantIfConditionRule.ID);
        boolean changed = new RedundantIfConditionRule().apply(compilationUnit, rewrite, configuration);
        if (!changed) {
            return source;
        }
        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
