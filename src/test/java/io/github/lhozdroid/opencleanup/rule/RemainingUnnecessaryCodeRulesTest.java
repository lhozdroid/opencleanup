package io.github.lhozdroid.opencleanup.rule;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

class RemainingUnnecessaryCodeRulesTest {

    /**
     * Verifies that operations on map views become direct map operations.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void operatesOnMapsDirectly() throws Exception {
        String source = "class Example { void run(Map<String, Integer> map, String key) {"
                + " map.keySet().contains(key); map.values().size(); map.values().remove(1); } }";

        String result = rewrite(source, new MapMethodRule());

        assertTrue(result.contains("map.containsKey(key)"));
        assertTrue(result.contains("map.size()"));
        assertTrue(result.contains("map.values().remove(1)"));
    }

    /**
     * Verifies that a collection is initialized directly from the source of addAll.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void initializesCollectionAtCreation() throws Exception {
        String source = "class Example { void run(java.util.List<String> source) {"
                + " java.util.List<String> copy = new java.util.ArrayList<>(); copy.addAll(source); } }";

        String result = rewrite(source, new CollectionCloningRule());

        assertTrue(result.contains("new java.util.ArrayList<>(source)"));
        assertFalse(result.contains("copy.addAll(source)"));
    }

    /**
     * Verifies that a map is initialized directly from the source of putAll.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void initializesMapAtCreation() throws Exception {
        String source = "class Example { void run(java.util.Map<String, Integer> source) {"
                + " java.util.Map<String, Integer> copy = new java.util.HashMap<>(); copy.putAll(source); } }";

        String result = rewrite(source, new MapCloningRule());

        assertTrue(result.contains("new java.util.HashMap<>(source)"));
        assertFalse(result.contains("copy.putAll(source)"));
    }

    /**
     * Verifies that a passive assignment is removed when immediately overwritten.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void removesOverriddenAssignment() throws Exception {
        String source = "class Example { void run() { int value; value = 0; value = 1; } }";

        String result = rewrite(source, new OverriddenAssignmentRule());

        assertFalse(result.contains("value = 0"));
        assertTrue(result.contains("value = 1"));
    }

    /**
     * Verifies that the standard natural-order comparator is removed.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void removesRedundantComparator() throws Exception {
        String source = "class Example { void run(java.util.List<String> values) {"
                + " java.util.Collections.sort(values, java.util.Comparator.naturalOrder()); } }";

        String result = rewrite(source, new RedundantComparatorRule());

        assertTrue(result.contains("java.util.Collections.sort(values)"), result);
        assertFalse(result.contains("naturalOrder"));
    }

    /**
     * Verifies that an explicit array is removed from the Arrays.asList varargs call.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void removesUnnecessaryArrayCreation() throws Exception {
        String source = "class Example { void run() {"
                + " java.util.Arrays.asList(new String[] {\"a\", \"b\"}); } }";

        String result = rewrite(source, new ArrayCreationRule());

        assertTrue(result.contains("Arrays.asList(\"a\", \"b\")"), result);
        assertFalse(result.contains("new String[]"));
    }

    /**
     * Verifies that a while loop ending in an unconditional break becomes an if statement.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void convertsUnloopedWhile() throws Exception {
        String source = "class Example { void run(boolean active) {"
                + " while (active) { work(); break; } } void work() {} }";

        String result = rewrite(source, new UnloopedWhileRule());

        assertTrue(result.contains("if (active)"));
        assertTrue(result.contains("work();"));
        assertFalse(result.contains("while (active)"));
        assertFalse(result.contains("break;"));
    }

    /**
     * Applies one cleanup rule to a source string.
     *
     * @param source the Java source string to rewrite
     * @param rule the cleanup rule to apply
     * @return the rewritten Java source
     * @throws Exception if parsing or applying text edits fails
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
