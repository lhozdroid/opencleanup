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
 * Tests conservative qualification of non-static method invocations.
 */
class NonStaticMethodsRuleTest {

    /**
     * Verifies that safe instance-method calls receive a {@code this} qualifier.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void qualifiesSafeInstanceMethodCalls() throws Exception {
        String source = """
                class Example {
                    void helper() {
                    }

                    static void staticHelper() {
                    }

                    void call() {
                        helper();
                        this.helper();
                        staticHelper();
                    }
                }
                """;

        String rewritten = rewrite(source, new RuleConfiguration());

        assertTrue(rewritten.contains("this.helper();"));
        assertTrue(rewritten.contains("staticHelper();"));
        assertEquals(2, rewritten.lines().filter(line -> line.contains("this.helper();")).count());
    }

    /**
     * Verifies that local shadowing and the binding-dependent mode are preserved.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void skipsAmbiguousMethodCalls() throws Exception {
        String source = """
                class Example {
                    void helper() {
                    }

                    void call() {
                        Runnable helper = () -> { };
                        helper.run();
                    }
                }
                """;

        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(NonStaticMethodsRule.ID);
        io.github.lhozdroid.opencleanup.config.RuleOption option =
                new io.github.lhozdroid.opencleanup.config.RuleOption();
        option.setName(NonStaticMethodsRule.ID);
        option.setValue("when-necessary");
        configuration.setOptions(java.util.List.of(option));

        assertEquals(source, rewrite(source, configuration));
    }

    /**
     * Parses and rewrites one source string with the non-static method rule.
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

        configuration.setId(NonStaticMethodsRule.ID);
        boolean changed = new NonStaticMethodsRule().apply(compilationUnit, rewrite, configuration);
        if (!changed) {
            return source;
        }

        Document document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
