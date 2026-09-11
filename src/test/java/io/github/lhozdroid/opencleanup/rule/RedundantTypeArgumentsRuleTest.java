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
 * Tests removal of redundant explicit type arguments.
 */
class RedundantTypeArgumentsRuleTest {

    /**
     * Verifies that diamond and generic method inference remove explicit arguments.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void removesRedundantConstructorAndMethodTypeArguments() throws Exception {
        String source = """
                import java.util.ArrayList;
                import java.util.Collections;
                import java.util.List;

                class Example {
                    void create() {
                        List<String> values = new ArrayList<String>();
                        List<String> empty = Collections.<String>emptyList();
                    }
                }
                """;

        String rewritten = rewrite(source);

        assertTrue(rewritten.contains("new ArrayList<>()"));
        assertTrue(rewritten.contains("Collections.emptyList()"));
    }

    /**
     * Verifies that anonymous classes retain their explicit type arguments.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesAnonymousClassTypeArguments() throws Exception {
        String source = """
                import java.util.ArrayList;
                import java.util.List;

                class Example {
                    List<String> create() {
                        return new ArrayList<String>() { };
                    }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Applies the redundant type-argument rule to one source string.
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
        configuration.setId(RedundantTypeArgumentsRule.ID);
        new RedundantTypeArgumentsRule().apply(compilationUnit, rewrite, configuration);

        Document document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
