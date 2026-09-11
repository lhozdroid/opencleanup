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
 * Tests conservative extraction of assignments from if conditions.
 */
class PullUpAssignmentRuleTest {

    /**
     * Verifies that a direct local assignment is moved before an if statement.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void pullsUpDirectAssignment() throws Exception {
        String source = """
                class Example {
                    boolean check(boolean value, boolean other) {
                        if (value = other) {
                            return value;
                        }
                        return false;
                    }
                }
                """;

        String rewritten = rewrite(source);

        assertTrue(rewritten.contains("value = other;"));
        assertTrue(rewritten.contains("if (value) {"));
    }

    /**
     * Verifies that nested conditions and unstable assignment targets remain unchanged.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesUnsupportedAssignments() throws Exception {
        String source = """
                class Example {
                    void check(boolean value, boolean other) {
                        if (other && (value = other)) {
                            use(value);
                        }
                        if (values[next()] = other) {
                            use(other);
                        }
                    }

                    boolean[] values = new boolean[1];

                    int next() {
                        return 0;
                    }

                    void use(boolean value) {
                    }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Parses and rewrites one source string with the assignment pull-up rule.
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
        configuration.setId(PullUpAssignmentRule.ID);
        boolean changed = new PullUpAssignmentRule().apply(compilationUnit, rewrite, configuration);
        if (!changed) {
            return source;
        }

        Document document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
