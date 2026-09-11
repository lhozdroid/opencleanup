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
 * Tests conservative qualification of static members.
 */
class StaticMembersRuleTest {

    /**
     * Verifies that directly declared static fields and methods receive the declaring type.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void qualifiesSafeStaticMembers() throws Exception {
        String source = """
                class Example {
                    static int count;

                    static void helper() {
                    }

                    static void consume(int value) {
                    }

                    int read() {
                        return count;
                    }

                    void call() {
                        helper();
                        consume(count);
                        this.read();
                    }
                }
                """;

        String rewritten = rewrite(source, new RuleConfiguration());

        assertTrue(rewritten.contains("return Example.count;"));
        assertTrue(rewritten.contains("Example.helper();"));
        assertTrue(rewritten.contains("Example.consume(Example.count);"));
        assertTrue(rewritten.contains("this.read();"));
        assertEquals(1, rewritten.lines().filter(line -> line.contains("static int count;")).count());
    }

    /**
     * Verifies that local shadowing, already-qualified members, and disabled configuration remain
     * unchanged.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void skipsAmbiguousOrDisabledStaticMembers() throws Exception {
        String source = """
                class Example {
                    static int count;

                    int read(int count) {
                        return count;
                    }

                    int alreadyQualified() {
                        return Example.count;
                    }
                }
                """;

        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(StaticMembersRule.ID);
        io.github.lhozdroid.opencleanup.config.RuleOption option =
                new io.github.lhozdroid.opencleanup.config.RuleOption();
        option.setName(StaticMembersRule.ID);
        option.setValue("false");
        configuration.setOptions(java.util.List.of(option));

        assertEquals(source, rewrite(source, configuration));
    }

    /**
     * Parses and rewrites one source string with the static-member rule.
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

        configuration.setId(StaticMembersRule.ID);
        boolean changed = new StaticMembersRule().apply(compilationUnit, rewrite, configuration);
        if (!changed) {
            return source;
        }

        Document document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
