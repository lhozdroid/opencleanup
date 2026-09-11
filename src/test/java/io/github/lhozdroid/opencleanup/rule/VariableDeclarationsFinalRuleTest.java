package io.github.lhozdroid.opencleanup.rule;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.junit.jupiter.api.Test;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;
import io.github.lhozdroid.opencleanup.config.RuleOption;

/**
 * Tests conservative addition of final modifiers.
 */
class VariableDeclarationsFinalRuleTest {

    /**
     * Verifies that selected stable fields, parameters, and locals receive final.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void addsFinalToStableSelectedDeclarations() throws Exception {
        String source = """
                class Example {
                    private int stableField = 1;
                    private int changedField = 2;
                    int publicField = 3;

                    void check(boolean stableParameter, boolean changedParameter) {
                        int stableLocal = 1;
                        int changedLocal = 2;
                        changedField = 4;
                        changedParameter = false;
                        changedLocal = 3;
                        if (stableParameter) {
                            use(stableLocal);
                        }
                    }

                    void use(int value) {
                    }
                }
                """;

        String rewritten = rewrite(
                source,
                VariableDeclarationsFinalRule.FIELDS,
                VariableDeclarationsFinalRule.PARAMETERS,
                VariableDeclarationsFinalRule.LOCALS);

        assertTrue(rewritten.contains("private final int stableField = 1;"));
        assertTrue(rewritten.contains("private int changedField = 2;"));
        assertTrue(rewritten.contains("int publicField = 3;"));
        assertTrue(rewritten.contains("void check(final boolean stableParameter, boolean changedParameter)"));
        assertTrue(rewritten.contains("final int stableLocal = 1;"));
        assertTrue(rewritten.contains("int changedLocal = 2;"));
    }

    /**
     * Verifies that category options select only the requested declaration kinds.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void honorsDeclarationCategoryOptions() throws Exception {
        String source = """
                class Example {
                    private int field = 1;

                    void check(boolean parameter) {
                        int local = 1;
                        use(parameter, local);
                    }

                    void use(boolean parameter, int local) {
                    }
                }
                """;

        String rewritten = rewrite(source, VariableDeclarationsFinalRule.FIELDS);

        assertTrue(rewritten.contains("private final int field = 1;"));
        assertFalse(rewritten.contains("final boolean parameter"));
        assertFalse(rewritten.contains("final int local"));
    }

    /**
     * Verifies that written declarations and unsupported field shapes remain unchanged.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesWrittenAndUnsupportedDeclarations() throws Exception {
        String source = """
                class Example {
                    private int writtenField = 1;
                    private int uninitializedField;
                    private static int staticField = 1;
                    private volatile int volatileField = 1;

                    void check(boolean parameter) {
                        int writtenLocal = 1;
                        writtenField = 2;
                        writtenLocal++;
                        parameter = false;
                        for (int index = 0; index < 1; index++) {
                            use(index);
                        }
                        use(writtenField);
                    }

                    void use(int value) {
                    }
                }
                """;

        String rewritten = rewrite(
                source,
                VariableDeclarationsFinalRule.FIELDS,
                VariableDeclarationsFinalRule.PARAMETERS,
                VariableDeclarationsFinalRule.LOCALS);

        assertFalse(rewritten.contains("final int writtenField"));
        assertFalse(rewritten.contains("final int uninitializedField"));
        assertFalse(rewritten.contains("final int staticField"));
        assertFalse(rewritten.contains("final int volatileField"));
        assertFalse(rewritten.contains("final int writtenLocal"));
        assertFalse(rewritten.contains("final boolean parameter"));
        assertFalse(rewritten.contains("final int index"));
    }

    /**
     * Rewrites one source string with selected final declaration options.
     *
     * @param source the Java source to rewrite
     * @param options the selected declaration categories
     * @return the rewritten Java source
     * @throws Exception if parsing or applying AST edits fails
     */
    private String rewrite(String source, String... options) throws Exception {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setStatementsRecovery(true);
        parser.setBindingsRecovery(true);
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
        ASTRewrite rewrite = ASTRewrite.create(compilationUnit.getAST());

        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(VariableDeclarationsFinalRule.ID);
        configuration.setOptions(List.of(options).stream().map(this::option).toList());
        boolean changed = new VariableDeclarationsFinalRule().apply(
                compilationUnit, rewrite, configuration);
        if (!changed) {
            return source;
        }

        Document document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }

    /**
     * Creates one enabled declaration-category option.
     *
     * @param name the option name
     * @return the enabled rule option
     */
    private RuleOption option(String name) {
        RuleOption option = new RuleOption();
        option.setName(name);
        option.setValue("true");
        return option;
    }
}
