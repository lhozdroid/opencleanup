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
 * Tests removal of modifiers whose implicit meaning is guaranteed by Java grammar.
 */
class RedundantModifiersRuleTest {

    /**
     * Verifies redundant modifiers are removed from interface members and annotation elements.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void removesImplicitInterfaceModifiers() throws Exception {
        String source = """
                interface Example {
                    public static final int VALUE = 1;
                    public abstract void abstractMethod();
                    public default void defaultMethod() {}
                    public static void staticMethod() {}
                    private void privateMethod() {}
                    public static class Nested {}
                }
                @interface AnnotationExample {
                    public abstract String value();
                }
                """;

        assertEquals("""
                interface Example {
                    int VALUE = 1;
                    void abstractMethod();
                    default void defaultMethod() {}
                    static void staticMethod() {}
                    private void privateMethod() {}
                    class Nested {}
                }
                @interface AnnotationExample {
                    String value();
                }
                """, rewrite(source));
    }

    /**
     * Verifies modifiers on ordinary class members are preserved because they are not implicit.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void preservesNonImplicitModifiers() throws Exception {
        String source = """
                class Example {
                    public static final int VALUE = 1;
                    public void method() {}
                    public static class Nested {}
                }
                """;

        assertEquals(source, rewrite(source));
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
        configuration.setId(RedundantModifiersRule.ID);
        new RedundantModifiersRule().apply(compilationUnit, rewrite, configuration);

        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
