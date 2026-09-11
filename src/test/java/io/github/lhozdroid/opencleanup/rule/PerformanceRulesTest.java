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
 * Tests the conservative performance cleanup rules implemented by PERF-A.
 */
class PerformanceRulesTest {

    /**
     * Verifies that a private final field with one read is inlined.
     *
     * @throws Exception if the AST rewrite cannot be applied
     */
    @Test
    void inlinesSingleUseField() throws Exception {
        String source = "class Example { private final int value = 1; int run() { return value; } }";

        String rewritten = rewrite(source, new FieldsSingleUseRule());

        assertTrue(rewritten.contains("int run() { return 1; }"));
        assertTrue(!rewritten.contains("private final int value"));
    }

    /**
     * Verifies that an infinite loop with a leading break guard becomes a conditional loop.
     *
     * @throws Exception if the AST rewrite cannot be applied
     */
    @Test
    void convertsLeadingBreakToLoopCondition() throws Exception {
        String source = "class Example { void run() { while (true) { if (done) break; work(); } } }";

        String rewritten = rewrite(source, new LoopsBreakRule());

        assertTrue(rewritten.contains("while (!done)"));
        assertTrue(rewritten.contains("work();"));
        assertTrue(!rewritten.contains("while (true)"));
    }

    /**
     * Verifies that an independent member class receives a static modifier.
     *
     * @throws Exception if the AST rewrite cannot be applied
     */
    @Test
    void makesIndependentMemberClassStatic() throws Exception {
        String source = "class Outer { class Inner {} }";

        String rewritten = rewrite(source, new StaticInnerRule());

        assertEquals("class Outer { static class Inner {} }", rewritten);
    }

    /**
     * Verifies that string concatenation is represented as a builder chain.
     *
     * @throws Exception if the AST rewrite cannot be applied
     */
    @Test
    void convertsStringConcatenationToBuilder() throws Exception {
        String source = "class Example { String run(String value) { return \"a\" + value; } }";

        String rewritten = rewrite(source, new StringsStringBuilderRule());

        assertEquals(
                "class Example { String run(String value) { return new StringBuilder().append(\"a\").append(value).toString(); } }",
                rewritten);
    }

    /**
     * Verifies that a literal non-regex replacement uses the plain replacement API.
     *
     * @throws Exception if the AST rewrite cannot be applied
     */
    @Test
    void convertsPlainReplacement() throws Exception {
        String source = "class Example { String run(String value) { return value.replaceAll(\"x\", \"y\"); } }";

        String rewritten = rewrite(source, new StringsPlainReplacementRule());

        assertEquals(
                "class Example { String run(String value) { return value.replace(\"x\", \"y\"); } }",
                rewritten);
    }

    /**
     * Verifies that pure boolean eager operators use their short-circuit equivalents.
     *
     * @throws Exception if the AST rewrite cannot be applied
     */
    @Test
    void convertsPureEagerLogicalOperators() throws Exception {
        String source = "class Example { boolean run() { return true & false | true; } }";

        String rewritten = rewrite(source, new OperatorsLazyLogicalRule());

        assertEquals("class Example { boolean run() { return true && false || true; } }", rewritten);
    }

    /**
     * Verifies that wrapper construction uses the corresponding value factory.
     *
     * @throws Exception if the AST rewrite cannot be applied
     */
    @Test
    void convertsWrapperConstructionToValueOf() throws Exception {
        String source = "class Example { Integer run() { return new Integer(1); } }";

        String rewritten = rewrite(source, new BoxingValueOfRule());

        assertEquals("class Example { Integer run() { return Integer.valueOf(1); } }", rewritten);
    }

    /**
     * Verifies that regex metacharacters and side effects are preserved.
     *
     * @throws Exception if the AST rewrite cannot be applied
     */
    @Test
    void preservesUnsafePerformancePatterns() throws Exception {
        String source = "class Example { boolean run(boolean value) { return value & check(); } String text(String value) { return value.replaceAll(\".\", \"x\"); } }";

        String rewritten = rewrite(source, new OperatorsLazyLogicalRule(), new StringsPlainReplacementRule());

        assertEquals(source, rewritten);
    }

    /**
     * Applies one or more cleanup rules to Java source.
     *
     * @param source the Java source string
     * @param rules the cleanup rules to apply
     * @return the rewritten Java source string
     * @throws Exception if parsing or applying edits fails
     */
    private String rewrite(String source, CleanupRule... rules) throws Exception {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setStatementsRecovery(true);
        parser.setBindingsRecovery(true);
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
        ASTRewrite rewrite = ASTRewrite.create(compilationUnit.getAST());
        RuleConfiguration configuration = new RuleConfiguration();
        for (CleanupRule rule : rules) {
            configuration.setId(rule.id());
            rule.apply(compilationUnit, rewrite, configuration);
        }
        Document document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
