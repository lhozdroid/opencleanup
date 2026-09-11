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
 * Tests conservative qualification of non-static field references.
 */
class NonStaticFieldsRuleTest {

    /**
     * Verifies that directly declared instance fields are qualified while declarations and static
     * contexts are preserved.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void qualifiesSafeInstanceFieldReferences() throws Exception {
        String source = """
                class Example {
                    int count;
                    static int shared;

                    int read() {
                        return count;
                    }

                    void write(int count) {
                        count++;
                        this.count = count;
                    }

                    void pass() {
                        consume(count);
                    }

                    void consume(int value) {
                    }

                    static int staticRead() {
                        return count;
                    }
                }
                """;

        String rewritten = rewrite(source, new RuleConfiguration());

        assertTrue(rewritten.contains("return this.count;"));
        assertTrue(rewritten.contains("int count;"));
        assertTrue(rewritten.contains("return count;"));
        assertTrue(rewritten.contains("consume(this.count);"));
        assertEquals(1, rewritten.lines().filter(line -> line.contains("this.count = count;")).count());
    }

    /**
     * Verifies that the binding-dependent mode and nested-type references remain unchanged.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void skipsAmbiguousFieldReferences() throws Exception {
        String source = """
                class Example {
                    int count;

                    int read(boolean condition) {
                        if (condition) {
                            int count = 1;
                            return count;
                        }
                        return count;
                    }

                    class Nested {
                        int read() {
                            return count;
                        }
                    }
                }
                """;

        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(NonStaticFieldsRule.ID);
        io.github.lhozdroid.opencleanup.config.RuleOption option =
                new io.github.lhozdroid.opencleanup.config.RuleOption();
        option.setName(NonStaticFieldsRule.ID);
        option.setValue("when-necessary");
        configuration.setOptions(java.util.List.of(option));

        assertEquals(source, rewrite(source, configuration));
    }

    /**
     * Parses and rewrites one source string with the non-static field rule.
     *
     * @param source the Java source to rewrite
     * @param configuration the rule configuration to apply
     * @return the rewritten Java source
     * @throws Exception if the source cannot be parsed or rewritten
     */
    private String rewrite(String source, RuleConfiguration configuration) throws Exception {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setSource(source.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setStatementsRecovery(true);
        parser.setBindingsRecovery(true);
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
        ASTRewrite rewrite = ASTRewrite.create(compilationUnit.getAST());

        configuration.setId(NonStaticFieldsRule.ID);
        boolean changed = new NonStaticFieldsRule().apply(compilationUnit, rewrite, configuration);
        if (!changed) {
            return source;
        }

        Document document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
