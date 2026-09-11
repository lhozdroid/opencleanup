package io.github.lhozdroid.opencleanup.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.function.Supplier;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/** Focused tests for conservative missing-code rewrites. */
class MissingCodeRulesTest {

    /**
     * Verifies source-local class overrides receive an override marker.
     *
     * @throws Exception if parsing or applying an edit fails
     */
    @Test
    void addsOverrideAnnotation() throws Exception {
        String source = "class Parent { void run() {} } class Child extends Parent { void run() {} }";
        String rewritten = rewrite(source, OverrideAnnotationRule.ID, OverrideAnnotationRule::new);
        assertTrue(rewritten.contains("@Override\nvoid run()"));
    }

    /**
     * Verifies interface implementations receive an override marker.
     *
     * @throws Exception if parsing or applying an edit fails
     */
    @Test
    void addsInterfaceOverrideAnnotation() throws Exception {
        String source = "interface Contract { void run(); } class Example implements Contract { public void run() {} }";
        String rewritten = rewrite(source, OverrideInterfaceAnnotationRule.ID,
                OverrideInterfaceAnnotationRule::new);
        assertTrue(rewritten.contains("@Override\npublic void run()"));
    }

    /**
     * Verifies Javadoc deprecation markers are converted to annotations.
     *
     * @throws Exception if parsing or applying an edit fails
     */
    @Test
    void addsDeprecatedAnnotation() throws Exception {
        String source = "/** @deprecated use newer() */ class Example { }";
        String rewritten = rewrite(source, DeprecatedAnnotationRule.ID, DeprecatedAnnotationRule::new);
        assertTrue(rewritten.contains("@Deprecated\nclass Example"));
    }

    /**
     * Verifies default serialVersionUID generation skips existing declarations.
     *
     * @throws Exception if parsing or applying an edit fails
     */
    @Test
    void addsSerialVersionUid() throws Exception {
        String source = "import java.io.Serializable; class Example implements Serializable { }";
        String rewritten = rewrite(source, SerialVersionUidRule.ID, SerialVersionUidRule::new);
        assertTrue(rewritten.contains("private static final long serialVersionUID = 1L;"));
    }

    /**
     * Verifies generated serialVersionUID mode produces a stable long literal.
     *
     * @throws Exception if parsing or applying an edit fails
     */
    @Test
    void generatesSerialVersionUid() throws Exception {
        String source = "import java.io.Serializable; class Example implements Serializable { }";
        String rewritten = rewrite(source, SerialVersionUidRule.ID, SerialVersionUidRule::new,
                "generated");
        assertTrue(rewritten.matches("(?s).*serialVersionUID = [0-9]+L;.*"));
    }

    /**
     * Verifies concrete classes receive stubs for local interface contracts.
     *
     * @throws Exception if parsing or applying an edit fails
     */
    @Test
    void addsUnimplementedMethodStub() throws Exception {
        String source = "interface Contract { int value(); } class Example implements Contract { }";
        String rewritten = rewrite(source, UnimplementedMethodsRule.ID, UnimplementedMethodsRule::new);
        assertTrue(rewritten.contains("public int value()"));
        assertTrue(rewritten.contains("return 0;"));
    }

    /**
     * Verifies aggregate missing annotations applies both supported annotation families.
     *
     * @throws Exception if parsing or applying an edit fails
     */
    @Test
    void addsAggregateAnnotations() throws Exception {
        String source = "/** @deprecated old */ class Parent { void run() {} }"
                + " class Child extends Parent { void run() {} }";
        String rewritten = rewrite(source, MissingAnnotationsRule.ID, MissingAnnotationsRule::new);
        assertTrue(rewritten.contains("@Deprecated"));
        assertTrue(rewritten.contains("@Override"));
    }

    /**
     * Parses, applies, and renders one rule transformation.
     *
     * @param source the Java source to transform
     * @param id the rule identifier
     * @param factory the rule factory
     * @return the transformed source
     * @throws Exception if parsing or applying an edit fails
     */
    private String rewrite(String source, String id,
            Supplier<CleanupRule> factory) throws Exception {
        return rewrite(source, id, factory, null);
    }

    /**
     * Parses, configures, applies, and renders one rule transformation.
     *
     * @param source the Java source to transform
     * @param id the rule identifier
     * @param factory the rule factory
     * @param optionValue an optional rule option value
     * @return the transformed source
     * @throws Exception if parsing or applying an edit fails
     */
    private String rewrite(String source, String id,
            Supplier<CleanupRule> factory, String optionValue) throws Exception {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
        ASTRewrite rewrite = ASTRewrite.create(compilationUnit.getAST());
        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(id);
        if (optionValue != null) {
            io.github.lhozdroid.opencleanup.config.RuleOption option =
                    new io.github.lhozdroid.opencleanup.config.RuleOption();
            option.setName(id);
            option.setValue(optionValue);
            configuration.setOptions(java.util.List.of(option));
        }
        CleanupRule rule = factory.get();
        assertTrue(rule.apply(compilationUnit, rewrite, configuration));
        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
