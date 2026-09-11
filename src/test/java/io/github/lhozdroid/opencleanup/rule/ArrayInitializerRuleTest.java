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

class ArrayInitializerRuleTest {

    /**
     * Verifies that a local array creation initializer becomes an array initializer.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void replacesLocalArrayCreationInitializer() throws Exception {
        String source = """
                class Example {
                    void values() {
                        String[] names = new String[] {"Luis", "Salas"};
                    }
                }
                """;

        String rewrittenSource = rewrite(source);

        assertTrue(rewrittenSource.contains("String[] names = {\"Luis\", \"Salas\"};"));
        assertEquals(0, rewrittenSource.lines().filter(line -> line.contains("new String[]")).count());
    }

    /**
     * Verifies that unsupported contexts and dimensioned array creations remain unchanged.
     *
     * @throws Exception if the temporary source file cannot be created or rewritten
     */
    @Test
    void preservesUnsupportedContextsAndDimensionedArrays() throws Exception {
        String source = """
                class Example {
                    private String[] field = new String[] {"field"};

                    void consume(String[] values) {
                        consume(new String[] {"argument"});
                    }

                    String[] create() {
                        return new String[] {"return"};
                    }

                    void declarations() {
                        String[] dimensioned = new String[1];
                    }

                    void consume(String value) {
                    }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Applies the array initializer rule to one source string.
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
        configuration.setId(ArrayInitializerRule.ID);
        new ArrayInitializerRule().apply(compilationUnit, rewrite, configuration);

        Document document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
