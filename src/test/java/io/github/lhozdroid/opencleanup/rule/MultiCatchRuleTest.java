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
 * Tests conservative combination of catch clauses.
 */
class MultiCatchRuleTest {

    /**
     * Verifies that adjacent catches with the same body become one union catch.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void combinesCatchClausesWithTheSameBody() throws Exception {
        String source = """
                class Example {
                    void run() {
                        try {
                            work();
                        } catch (java.io.IOException exception) {
                            recover();
                        } catch (java.sql.SQLException exception) {
                            recover();
                        }
                    }

                    void work() { }
                    void recover() { }
                }
                """;

        String rewritten = rewrite(source);

        assertTrue(rewritten.contains("catch (java.io.IOException | java.sql.SQLException exception)"));
        assertEquals(1, rewritten.lines().filter(line -> line.contains("catch (")).count());
    }

    /**
     * Verifies that catches with different bodies remain separate.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesCatchClausesWithDifferentBodies() throws Exception {
        String source = """
                class Example {
                    void run() {
                        try {
                            work();
                        } catch (java.io.IOException exception) {
                            recoverIo();
                        } catch (java.sql.SQLException exception) {
                            recoverSql();
                        }
                    }

                    void work() { }
                    void recoverIo() { }
                    void recoverSql() { }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Applies the multi-catch rule to one source string.
     *
     * @param source the Java source string to rewrite
     * @return the rewritten Java source string
     * @throws Exception if the source cannot be parsed or rewritten
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
        configuration.setId(MultiCatchRule.ID);
        new MultiCatchRule().apply(compilationUnit, rewrite, configuration);

        Document document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
