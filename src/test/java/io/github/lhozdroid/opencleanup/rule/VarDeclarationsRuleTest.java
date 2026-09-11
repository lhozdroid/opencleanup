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
 * Tests conservative conversion of local declarations to {@code var}.
 */
class VarDeclarationsRuleTest {

    /**
     * Verifies exact literal and construction types are converted.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void convertsExactInferredTypes() throws Exception {
        String source = """
                class Example {
                    void run() {
                        String text = "value";
                        int count = 1;
                        String copy = new String();
                        String[] values = new String[1];
                    }
                }
                """;

        String rewritten = rewrite(source);

        assertEquals(4, rewritten.lines().filter(line -> line.contains("var ")).count());
        assertTrue(rewritten.contains("var text = \"value\";"));
        assertTrue(rewritten.contains("var count = 1;"));
        assertTrue(rewritten.contains("var values = new String[1];"));
    }

    /**
     * Verifies that binding-dependent or type-changing declarations remain unchanged.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void preservesUncertainTypes() throws Exception {
        String source = """
                import java.util.ArrayList;
                import java.util.List;

                class Example {
                    void run() {
                        List<String> values = new ArrayList<>();
                        long count = 1;
                        String text = read();
                        Object nothing = null;
                    }

                    String read() {
                        return "value";
                    }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Parses and rewrites one source string with the var-declarations rule.
     *
     * @param source the source string to rewrite
     * @return the rewritten source string
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
        configuration.setId(VarDeclarationsRule.ID);
        new VarDeclarationsRule().apply(compilationUnit, rewrite, configuration);
        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
