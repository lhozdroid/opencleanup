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
 * Tests conservative autoboxing substitutions.
 */
class AutoboxingRuleTest {

    /**
     * Verifies that literal wrapper factories in wrapper declarations are removed.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void replacesLiteralValueFactoriesWithAutoboxing() throws Exception {
        String source = """
                class Example {
                    Integer integerValue = Integer.valueOf(1);
                    Boolean booleanValue = Boolean.valueOf(true);
                    Long longValue = Long.valueOf(1L);
                }
                """;

        String rewritten = rewrite(source);

        assertTrue(rewritten.contains("Integer integerValue = 1;"));
        assertTrue(rewritten.contains("Boolean booleanValue = true;"));
        assertTrue(rewritten.contains("Long longValue = 1L;"));
    }

    /**
     * Verifies that nonliteral or non-wrapper factories remain unchanged.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesUncertainFactories() throws Exception {
        String source = """
                class Example {
                    Integer integerValue(int value) { return Integer.valueOf(value); }
                    String stringValue() { return String.valueOf(1); }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Applies the autoboxing rule to one source string.
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
        configuration.setId(AutoboxingRule.ID);
        new AutoboxingRule().apply(compilationUnit, rewrite, configuration);

        Document document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
