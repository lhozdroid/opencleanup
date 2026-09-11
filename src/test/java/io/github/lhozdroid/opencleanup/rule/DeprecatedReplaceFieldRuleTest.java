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
 * Tests conservative deprecated-field replacement cleanup.
 */
class DeprecatedReplaceFieldRuleTest {

    /**
     * Verifies inlining of a bound deprecated compile-time constant.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void inlinesBoundDeprecatedConstant() throws Exception {
        String source = """
                class Example {
                    /** @deprecated use a replacement constant */
                    @Deprecated
                    static final int OLD = 7;

                    int value() { return OLD; }
                }
                """;

        String rewritten = rewrite(source, true);

        assertTrue(rewritten.contains("int value() { return 7; }"));
        assertTrue(rewritten.contains("static final int OLD = 7;"));
    }

    /**
     * Verifies that unresolved field references are preserved.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesFieldsWithoutBindings() throws Exception {
        String source = "class Example { int value() { return OLD; } }";

        assertEquals(source, rewrite(source, false));
    }

    /**
     * Applies the deprecated-field rule to one source string.
     *
     * @param source the Java source to rewrite
     * @param resolveBindings whether JDT should resolve source bindings
     * @return the rewritten Java source
     * @throws Exception if parsing or applying AST edits fails
     */
    private String rewrite(String source, boolean resolveBindings) throws Exception {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setResolveBindings(resolveBindings);
        if (resolveBindings) {
            parser.setEnvironment(
                    new String[] {System.getProperty("java.class.path")},
                    null,
                    null,
                    true);
            parser.setUnitName("Example.java");
        }
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
        ASTRewrite rewrite = ASTRewrite.create(compilationUnit.getAST());
        new DeprecatedReplaceFieldRule().apply(compilationUnit, rewrite, configuration());
        Document document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }

    /**
     * Creates the default rule configuration used by the test.
     *
     * @return a configuration selecting deprecated-field replacement
     */
    private RuleConfiguration configuration() {
        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(DeprecatedReplaceFieldRule.ID);
        return configuration;
    }
}
