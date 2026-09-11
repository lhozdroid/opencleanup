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
 * Tests conservative deprecated-method replacement cleanup.
 */
class DeprecatedReplaceMethodRuleTest {

    /**
     * Verifies inlining of a bound deprecated forwarding method.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void inlinesBoundDeprecatedForwarder() throws Exception {
        String source = """
                class Example {
                    /** @deprecated use {@link #replacement(int)} */
                    @Deprecated
                    int old(int value) { return replacement(value); }

                    int replacement(int value) { return value + 1; }

                    int call(int value) { return old(value); }
                }
                """;

        String rewritten = rewrite(source, true);

        assertTrue(rewritten.contains("int call(int value) { return replacement(value); }"), rewritten);
        assertTrue(rewritten.contains("int old(int value) { return replacement(value); }"));
    }

    /**
     * Verifies that unresolved deprecated calls are preserved.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesCallsWithoutBindings() throws Exception {
        String source = "class Example { int call(Example value) { return value.old(); } }";

        assertEquals(source, rewrite(source, false));
    }

    /**
     * Applies the deprecated-method rule to one source string.
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
        new DeprecatedReplaceMethodRule().apply(compilationUnit, rewrite, configuration());
        Document document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }

    /**
     * Creates the default rule configuration used by the test.
     *
     * @return a configuration selecting deprecated-method replacement
     */
    private RuleConfiguration configuration() {
        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(DeprecatedReplaceMethodRule.ID);
        return configuration;
    }
}
