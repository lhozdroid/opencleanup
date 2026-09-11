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
 * Tests conversion of null-safe equality conditionals.
 */
class ObjectsEqualsRuleTest {

    /**
     * Verifies that a standard null-safe equality conditional uses Objects.equals.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void replacesNullSafeEqualityConditional() throws Exception {
        String source = """
                class Example {
                    boolean same(String first, String second) {
                        return first == null ? second == null : first.equals(second);
                    }
                }
                """;

        String rewritten = rewrite(source);

        assertTrue(rewritten.contains("import java.util.Objects;"));
        assertTrue(rewritten.contains("return Objects.equals(first, second);"));
    }

    /**
     * Verifies that ordinary equality and direct equals calls remain unchanged.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesUnsupportedEqualityShapes() throws Exception {
        String source = """
                class Example {
                    boolean same(String first, String second) {
                        boolean direct = first == second;
                        return first.equals(second) || direct;
                    }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Applies the Objects equality rule to one source string.
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
        configuration.setId(ObjectsEqualsRule.ID);
        new ObjectsEqualsRule().apply(compilationUnit, rewrite, configuration);

        Document document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
