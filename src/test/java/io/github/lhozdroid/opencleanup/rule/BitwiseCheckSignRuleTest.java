package io.github.lhozdroid.opencleanup.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
 * Tests bitwise sign-check cleanup.
 */
class BitwiseCheckSignRuleTest {

    /**
     * Verifies that a bitwise expression compared greater than zero uses {@code != 0}.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void replacesBitwiseGreaterThanZero() throws Exception {
        String source = "class Example { boolean check(int value) { return (value & 1) > 0; } }";

        assertEquals(
                "class Example { boolean check(int value) { return (value & 1) != 0; } }",
                rewrite(source));
    }

    /**
     * Verifies that unrelated comparisons remain unchanged.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesUnsupportedComparisons() throws Exception {
        String source = "class Example { boolean check(int value) { return (value & 1) >= 0; } }";

        assertEquals(source, rewrite(source));
    }

    /**
     * Applies the bitwise sign-check rule to one source string.
     *
     * @param source the Java source to rewrite
     * @return the rewritten Java source
     * @throws Exception if parsing or applying AST edits fails
     */
    private String rewrite(String source) throws Exception {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
        ASTRewrite rewrite = ASTRewrite.create(compilationUnit.getAST());
        new BitwiseCheckSignRule().apply(compilationUnit, rewrite, configuration());
        Document document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }

    /**
     * Creates the default rule configuration used by the test.
     *
     * @return a configuration selecting the bitwise sign-check rule
     */
    private RuleConfiguration configuration() {
        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(BitwiseCheckSignRule.ID);
        return configuration;
    }
}
