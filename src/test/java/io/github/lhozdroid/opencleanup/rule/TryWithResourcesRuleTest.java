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
 * Tests conservative conversion to try-with-resources.
 */
class TryWithResourcesRuleTest {

    /**
     * Verifies that a declaration followed by a single close call becomes a resource.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void convertsResourceFinallyPattern() throws Exception {
        String source = """
                class Example {
                    void use() throws Exception {
                        Resource resource = acquire();
                        try {
                            resource.use();
                        } finally {
                            resource.close();
                        }
                    }

                    Resource acquire() { return null; }

                    static class Resource {
                        void use() { }
                        void close() { }
                    }
                }
                """;

        String rewritten = rewrite(source);

        assertTrue(rewritten.contains("try (Resource resource = acquire())"));
        assertTrue(rewritten.contains("resource.use();"));
        assertEquals(0, rewritten.lines().filter(line -> line.contains("resource.close()")).count());
    }

    /**
     * Verifies that catches are preserved by skipping the transformation.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesTryStatementsWithCatches() throws Exception {
        String source = """
                class Example {
                    void use() throws Exception {
                        Resource resource = acquire();
                        try {
                            resource.use();
                        } catch (Exception exception) {
                            recover();
                        } finally {
                            resource.close();
                        }
                    }

                    void recover() { }
                    Resource acquire() { return null; }
                    static class Resource {
                        void use() { }
                        void close() { }
                    }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Applies the try-with-resources rule to one source string.
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
        configuration.setId(TryWithResourcesRule.ID);
        new TryWithResourcesRule().apply(compilationUnit, rewrite, configuration);

        Document document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
