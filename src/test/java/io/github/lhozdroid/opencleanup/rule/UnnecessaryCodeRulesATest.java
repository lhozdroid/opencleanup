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

/**
 * Focused tests for the unnecessary-code workstream A rules.
 */
class UnnecessaryCodeRulesATest {

    /**
     * Verifies removal of an unreferenced private field and method.
     *
     * @throws Exception if parsing or applying an AST edit fails
     */
    @Test
    void removesUnusedPrivateMembers() throws Exception {
        String source = """
                class Example {
                    private int unused;
                    private int used;
                    private void dead(int unusedParameter) { }
                    int value() { return used; }
                }
                """;

        String rewritten = rewrite(source, UnusedPrivateMembersRule.ID);

        assertFalse(rewritten.contains("unused;"));
        assertFalse(rewritten.contains("dead"));
        assertTrue(rewritten.contains("private int used;"));
    }

    /**
     * Verifies executable field initializers are retained when the field itself is unused.
     *
     * @throws Exception if parsing or applying an AST edit fails
     */
    @Test
    void preservesUnusedFieldsWithExecutableInitializers() throws Exception {
        String source = "class Example { private static int value = initialize();"
                + " private int first, second = initialize();"
                + " private static int initialize() { return 1; } }";

        String rewritten = rewrite(source, UnusedPrivateMembersRule.ID);

        assertTrue(rewritten.contains("private static int value = initialize();"));
        assertTrue(rewritten.contains("private int second = initialize();"));
        assertFalse(rewritten.contains("private int;"));
    }

    /**
     * Verifies fields used by Lombok-generated accessors are not removed as unused.
     *
     * @throws Exception if parsing or applying an AST edit fails
     */
    @Test
    void preservesLombokManagedFields() throws Exception {
        String source = """
                import lombok.Data;

                @Data
                class Example {
                    private int value;
                }
                """;

        String rewritten = rewrite(source, UnusedPrivateMembersRule.ID);

        assertTrue(rewritten.contains("private int value;"));
    }

    /**
     * Verifies a fully qualified Lombok annotation is recognized without bindings.
     *
     * @throws Exception if parsing or applying an AST edit fails
     */
    @Test
    void preservesFullyQualifiedLombokFields() throws Exception {
        String source = "@lombok.Data class Example { private int value; }";

        String rewritten = rewrite(source, UnusedPrivateMembersRule.ID);

        assertTrue(rewritten.contains("private int value;"));
    }

    /**
     * Verifies unknown type-level annotations are treated as framework contracts.
     *
     * @throws Exception if parsing or applying an AST edit fails
     */
    @Test
    void preservesFieldsUnderUnknownTypeAnnotations() throws Exception {
        String source = "@custom.UsesFields class Example { private int value; }";

        String rewritten = rewrite(source, UnusedPrivateMembersRule.ID);

        assertTrue(rewritten.contains("private int value;"));
    }

    /**
     * Verifies a private method annotated for Lombok builder generation is preserved.
     *
     * @throws Exception if parsing or applying an AST edit fails
     */
    @Test
    void preservesLombokManagedMethods() throws Exception {
        String source = "import lombok.Builder; class Example {"
                + " @Builder private static Example create(String value) { return new Example(); } }";

        String rewritten = rewrite(source, UnusedPrivateMembersRule.ID);

        assertTrue(rewritten.contains("private static Example create(String value)"));
    }

    /**
     * Verifies method references prevent removal of parameters required by the target method.
     *
     * @throws Exception if parsing or applying an AST edit fails
     */
    @Test
    void preservesParametersReferencedThroughMethodReferences() throws Exception {
        String source = "class Example { private void consume(String value) { }"
                + " java.util.function.Consumer<String> consumer() { return this::consume; } }";

        String rewritten = rewrite(source, UnusedPrivateMembersRule.ID);

        assertTrue(rewritten.contains("consume(String value)"));
    }

    /**
     * Verifies that only provably unnecessary suppression tokens are removed.
     *
     * @throws Exception if parsing or applying an AST edit fails
     */
    @Test
    void removesUnnecessarySuppressWarningsTokens() throws Exception {
        String source = """
                @SuppressWarnings({"unused", "deprecation", "unchecked"})
                class Example { }
                """;

        String rewritten = rewrite(source, SuppressWarningsRule.ID);

        assertFalse(rewritten.contains("\"unused\""));
        assertFalse(rewritten.contains("\"deprecation\""));
        assertTrue(rewritten.contains("\"unchecked\""));
    }

    /**
     * Verifies removal of casts around syntax-known values.
     *
     * @throws Exception if parsing or applying an AST edit fails
     */
    @Test
    void removesUnnecessaryCasts() throws Exception {
        String source = """
                class Example {
                    String text() { return (String) "text"; }
                    int number() { return (int) 1; }
                    Object preserved(Object value) { return (String) value; }
                }
                """;

        String rewritten = rewrite(source, UnnecessaryCastRule.ID);

        assertTrue(rewritten.contains("return \"text\";"));
        assertTrue(rewritten.contains("return 1;"));
        assertTrue(rewritten.contains("return (String) value;"));
    }

    /**
     * Verifies replacement of a simple zero-based array loop with Arrays.fill.
     *
     * @throws Exception if parsing or applying an AST edit fails
     */
    @Test
    void replacesSimpleArrayFillLoop() throws Exception {
        String source = """
                class Example {
                    void fill(int[] values) {
                        for (int index = 0; index < values.length; index++) {
                            values[index] = 0;
                        }
                    }
                }
                """;

        String rewritten = rewrite(source, ArraysFillRule.ID);

        assertTrue(rewritten.contains("java.util.Arrays.fill(values, 0);"));
        assertFalse(rewritten.contains("for (int index"));
    }

    /**
     * Verifies evaluation of null comparisons involving expressions known to be non-null.
     *
     * @throws Exception if parsing or applying an AST edit fails
     */
    @Test
    void evaluatesKnownNonNullComparisons() throws Exception {
        String source = """
                class Example {
                    boolean created() { return new Object() != null; }
                    boolean literal() { return null == "text"; }
                    boolean unknown(Object value) { return value != null; }
                }
                """;

        String rewritten = rewrite(source, EvaluateNullableRule.ID);

        assertTrue(rewritten.contains("return true;"));
        assertTrue(rewritten.contains("return false;"));
        assertTrue(rewritten.contains("return value != null;"));
    }

    /**
     * Verifies removal of a side-effect-free standalone comparison.
     *
     * @throws Exception if parsing or applying an AST edit fails
     */
    @Test
    void removesRedundantComparisonStatements() throws Exception {
        String source = """
                class Example {
                    boolean compare(boolean first) {
                        if (first == false) {
                            return false;
                        } else {
                            return first;
                        }
                    }
                }
                """;

        String rewritten = rewrite(source, RedundantComparisonRule.ID);

        assertFalse(rewritten.contains("first == false"));
        assertTrue(rewritten.contains("return first;"));
    }

    /**
     * Verifies removal of statements after a return in the same block.
     *
     * @throws Exception if parsing or applying an AST edit fails
     */
    @Test
    void removesUnreachableBlockStatements() throws Exception {
        String source = """
                class Example {
                    int value() {
                        return 1;
                        int unreachable = 2;
                    }
                }
                """;

        String rewritten = rewrite(source, UnreachableBlockRule.ID);

        assertFalse(rewritten.contains("unreachable"));
        assertTrue(rewritten.contains("return 1;"));
    }

    /**
     * Applies one rule to a source string and renders its text edit.
     *
     * @param source the Java source string
     * @param ruleId the rule identifier
     * @return the rewritten source string
     * @throws Exception if parsing or applying the AST edit fails
     */
    private String rewrite(String source, String ruleId) throws Exception {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setStatementsRecovery(true);
        parser.setBindingsRecovery(true);
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
        ASTRewrite rewrite = ASTRewrite.create(compilationUnit.getAST());
        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(ruleId);
        CleanupRule rule = switch (ruleId) {
            case UnusedPrivateMembersRule.ID -> new UnusedPrivateMembersRule();
            case SuppressWarningsRule.ID -> new SuppressWarningsRule();
            case UnnecessaryCastRule.ID -> new UnnecessaryCastRule();
            case ArraysFillRule.ID -> new ArraysFillRule();
            case EvaluateNullableRule.ID -> new EvaluateNullableRule();
            case RedundantComparisonRule.ID -> new RedundantComparisonRule();
            case UnreachableBlockRule.ID -> new UnreachableBlockRule();
            default -> throw new IllegalArgumentException("Unknown test rule: " + ruleId);
        };
        if (!rule.apply(compilationUnit, rewrite, configuration)) {
            return source;
        }
        Document document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
