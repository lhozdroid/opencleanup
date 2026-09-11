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

/** Tests conservative enhanced-for to {@code addAll} rewrites. */
class UseAddAllRuleTest {

    /**
     * Verifies a loop that adds each source element becomes one bulk operation.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void replacesSimpleAddLoop() throws Exception {
        String source = """
                class Example {
                    void copy(java.util.List<String> target, java.util.List<String> source) {
                        for (String value : source) {
                            target.add(value);
                        }
                    }
                }
                """;

        String normalized = rewrite(source).replaceAll("\\s+", " ").trim();
        assertTrue(normalized.contains("target.addAll(source);"));
    }

    /**
     * Verifies loops with extra work or complex expressions remain unchanged.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void preservesUnsafeAddLoops() throws Exception {
        String source = """
                class Example {
                    void copy(java.util.List<String> target, java.util.List<String> source) {
                        for (String value : source) {
                            log(value);
                            target.add(value);
                        }
                        for (String value : source) {
                            target.add(transform(value));
                        }
                        for (String value : source) {
                            getTarget().add(value);
                        }
                    }

                    void log(String value) {
                    }

                    String transform(String value) {
                        return value;
                    }

                    java.util.List<String> getTarget() {
                        return null;
                    }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Applies the bulk-add rule to source text.
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
        configuration.setId(UseAddAllRule.ID);
        boolean changed = new UseAddAllRule().apply(compilationUnit, rewrite, configuration);
        if (!changed) {
            return source;
        }

        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
