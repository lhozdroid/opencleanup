package io.github.lhozdroid.opencleanup.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Tests collision-safe conversion of fully qualified type names to imports.
 */
class UseSimpleTypeNamesRuleTest {

    /**
     * Verifies that qualified type and annotation names are shortened and imported.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void shortensQualifiedTypesAndAnnotations() throws Exception {
        String source = """
                package example;

                class Example {
                    java.util.List<String> values;
                    java.util.ArrayList<String> mutable = new java.util.ArrayList<>();
                    java.util.Map.Entry<String, java.lang.Integer> entry;

                    @java.lang.Deprecated
                    void oldMethod() {
                    }
                }
                """;

        String rewritten = rewrite(source);

        assertTrue(rewritten.contains("import java.util.List;"));
        assertTrue(rewritten.contains("import java.util.ArrayList;"));
        assertTrue(rewritten.contains("import java.util.Map.Entry;"));
        assertFalse(rewritten.contains("java.util.List<String>"));
        assertFalse(rewritten.contains("java.util.ArrayList<String>"));
        assertFalse(rewritten.contains("new java.util.ArrayList"));
        assertFalse(rewritten.contains("java.util.Map.Entry<String"));
        assertFalse(rewritten.contains("java.lang.Integer>"));
        assertTrue(rewritten.contains("@Deprecated"));
    }

    /**
     * Verifies that two qualified types with the same simple name remain qualified.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesConflictingQualifiedTypes() throws Exception {
        String source = """
                class Example {
                    java.util.List<String> standard;
                    com.example.List custom;
                    java.util.ArrayList<String> standardMutable;
                    com.example.ArrayList customMutable;
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Verifies that an existing explicit import selects the safe type during a collision.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void shortensOnlyTheTypeSelectedByAnExistingImport() throws Exception {
        String source = """
                import java.util.List;

                class Example {
                    java.util.List<String> standard;
                    com.example.List custom;
                }
                """;

        String rewritten = rewrite(source);

        assertTrue(rewritten.contains("List<String> standard;"));
        assertTrue(rewritten.contains("com.example.List custom;"));
        assertFalse(rewritten.contains("java.util.List<String> standard;"));
    }

    /**
     * Verifies that existing simple type uses prevent an uncertain new import.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesQualifiedTypeWhenSimpleNameAlreadyHasUnknownMeaning() throws Exception {
        String source = """
                class Example {
                    List<String> existing;
                    java.util.List<String> qualified;
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Verifies that static member references and string contents are not treated as type names.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesNonTypeQualifiedNamesAndStrings() throws Exception {
        String source = """
                class Example {
                    String value = "java.util.List";

                    Object create() {
                        return java.util.Collections.emptyList();
                    }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Applies the simple type-name rule to one source string.
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
        configuration.setId(UseSimpleTypeNamesRule.ID);
        boolean changed = new UseSimpleTypeNamesRule().apply(compilationUnit, rewrite, configuration);
        if (!changed) {
            return source;
        }

        Document document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
