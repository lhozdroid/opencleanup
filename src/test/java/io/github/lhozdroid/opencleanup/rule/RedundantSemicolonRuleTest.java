package io.github.lhozdroid.opencleanup.rule;

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

/**
 * Tests conservative removal of redundant semicolons.
 */
class RedundantSemicolonRuleTest {

    /**
     * Verifies standalone empty statements in a method block are removed.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void removesStandaloneBlockSemicolons() throws Exception {
        String source = """
                class Example {
                    void clean() {
                        ;
                        int value = 1;
                        ;;
                        value++;
                    }
                }
                """;

        assertEquals("""
                class Example {
                    void clean() {
                        int value = 1;
                        value++;
                    }
                }
                """, rewrite(source));
    }

    /**
     * Verifies empty statements required as direct control-flow or label bodies remain.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void preservesRequiredEmptyStatementBodies() throws Exception {
        String original = """
                class RequiredBodies {
                    void preserve(boolean active) {
                        if (active) ;
                        while (active) ;
                        label: ;
                    }
                }
                """;

        assertEquals(original, rewrite(original));
    }

    /**
     * Parses source, applies the rule, and returns the rewritten source.
     *
     * @param source the Java source to rewrite
     * @return the rewritten Java source
     * @throws Exception if the source cannot be parsed or the edit cannot be applied
     */
    private String rewrite(String source) throws Exception {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);

        ASTRewrite rewrite = ASTRewrite.create(compilationUnit.getAST());
        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(RedundantSemicolonRule.ID);
        new RedundantSemicolonRule().apply(compilationUnit, rewrite, configuration);

        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
