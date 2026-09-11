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
 * Tests replacement of supported system-property lookups.
 */
class SystemPropertyConstantsRuleTest {

    /**
     * Verifies that platform separator properties use modern APIs and constants.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void replacesSupportedSeparatorProperties() throws Exception {
        String source = """
                class Example {
                    String line() { return System.getProperty("line.separator"); }
                    String file() { return System.getProperty("file.separator"); }
                    String path() { return System.getProperty("path.separator"); }
                }
                """;

        String rewritten = rewrite(source);

        assertTrue(rewritten.contains("import java.io.File;"));
        assertTrue(rewritten.contains("System.lineSeparator()"));
        assertTrue(rewritten.contains("File.separator"));
        assertTrue(rewritten.contains("File.pathSeparator"));
    }

    /**
     * Verifies that unsupported properties remain ordinary lookups.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesUnsupportedProperties() throws Exception {
        String source = """
                class Example {
                    String home() { return System.getProperty("user.home"); }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Applies the system-property constants rule to one source string.
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
        configuration.setId(SystemPropertyConstantsRule.ID);
        new SystemPropertyConstantsRule().apply(compilationUnit, rewrite, configuration);

        Document document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
