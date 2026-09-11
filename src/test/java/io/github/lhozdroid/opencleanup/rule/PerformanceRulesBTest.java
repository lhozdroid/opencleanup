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
 * Tests the remaining performance rules in workstream PERF-B.
 */
class PerformanceRulesBTest {

    /**
     * Verifies that explicit wrapper comparisons use primitive operations.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void replacesPrimitiveComparisons() throws Exception {
        String source = "class Example { boolean same(int a, int b) { return "
                + "Integer.valueOf(a).equals(Integer.valueOf(b)); } }";

        String rewritten = rewrite(source, new PrimitiveComparisonRule());

        assertTrue(rewritten.contains("a == b"));
    }

    /**
     * Verifies that primitive declarations use primitive parsing methods.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void replacesWrapperParsing() throws Exception {
        String source = "class Example { int value(String text) { int number = Integer.valueOf(text); "
                + "return number; } }";

        String rewritten = rewrite(source, new PrimitiveParsingRule());

        assertTrue(rewritten.contains("Integer.parseInt(text)"));
    }

    /**
     * Verifies that wrapper serialization uses the static primitive form.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void replacesPrimitiveSerialization() throws Exception {
        String source = "class Example { String value(int number) { return "
                + "Integer.valueOf(number).toString(); } }";

        String rewritten = rewrite(source, new PrimitiveSerializationRule());

        assertTrue(rewritten.contains("Integer.toString(number)"));
    }

    /**
     * Verifies that a literal local wrapper becomes a primitive local.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void prefersPrimitiveLocal() throws Exception {
        String source = "class Example { int value() { Integer number = 1; return number; } }";

        String rewritten = rewrite(source, new PrimitiveRatherThanWrapperRule());

        assertTrue(rewritten.contains("int number = 1;"));
    }

    /**
     * Verifies that repeated string matches use one compiled pattern.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void precompilesRepeatedRegularExpression() throws Exception {
        String source = "class Example { boolean valid(String first, String second) { "
                + "String regex = \"[a-z]+\"; return first.matches(regex) && second.matches(regex); } }";

        String rewritten = rewrite(source, new RegexPrecompileRule());

        assertTrue(rewritten.contains("java.util.regex.Pattern regexPattern ="));
        assertTrue(rewritten.contains("regexPattern.matcher(first).matches()"));
        assertTrue(rewritten.contains("regexPattern.matcher(second).matches()"));
    }

    /**
     * Verifies that direct StringBuffer construction becomes StringBuilder construction.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void replacesBufferWithBuilder() throws Exception {
        String source = "class Example { String value() { StringBuffer buffer = new StringBuffer(); "
                + "buffer.append(\"x\"); return buffer.toString(); } }";

        String rewritten = rewrite(source, new BufferToBuilderRule());

        assertTrue(rewritten.contains("StringBuilder buffer = new StringBuilder();"));
    }

    /**
     * Verifies that empty and literal String constructors are removed.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void removesUnnecessaryStringCreation() throws Exception {
        String source = "class Example { String empty() { return new String(); } "
                + "String literal() { return new String(\"value\"); } }";

        String rewritten = rewrite(source, new NoStringCreationRule());

        assertTrue(rewritten.contains("return \"\";"));
        assertTrue(rewritten.contains("return \"value\";"));
        assertEquals(0, rewritten.lines().filter(line -> line.contains("new String")).count());
    }

    /**
     * Verifies that uncertain transformations remain unchanged.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesUncertainPerformanceCases() throws Exception {
        String source = "class Example { String value(String text) { Integer number = null; "
                + "return new String(text); } }";

        assertEquals(source, rewrite(source, new PrimitiveRatherThanWrapperRule()));
        assertEquals(source, rewrite(source, new NoStringCreationRule()));
    }

    /**
     * Applies one cleanup rule to a source string.
     *
     * @param source the Java source string to rewrite
     * @param rule the cleanup rule to apply
     * @return the rewritten Java source string
     * @throws Exception if parsing or applying AST edits fails
     */
    private String rewrite(String source, CleanupRule rule) throws Exception {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setStatementsRecovery(true);
        parser.setBindingsRecovery(true);
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
        ASTRewrite rewrite = ASTRewrite.create(compilationUnit.getAST());
        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(rule.id());
        rule.apply(compilationUnit, rewrite, configuration);
        Document document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
