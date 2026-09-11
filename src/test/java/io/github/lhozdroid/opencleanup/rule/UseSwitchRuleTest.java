package io.github.lhozdroid.opencleanup.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.junit.jupiter.api.Test;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/** Tests conservative conditional-chain to switch rewrites. */
class UseSwitchRuleTest {

    /**
     * Verifies a primitive equality chain becomes a switch with isolated cases.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void replacesPrimitiveEqualityChain() throws Exception {
        String source = """
                class Example {
                    void check(int value) {
                        if (value == 1) {
                            first();
                        } else if (value == 2) {
                            second();
                        } else {
                            other();
                        }
                    }

                    void first() {
                    }

                    void second() {
                    }

                    void other() {
                    }
                }
                """;

        String normalized = rewrite(source).replaceAll("\\s+", " ").trim();
        assertTrue(normalized.contains("switch (value)"));
        assertTrue(normalized.contains("case 1 : { first(); } break;"));
        assertTrue(normalized.contains("case 2 : { second(); } break;"));
        assertTrue(normalized.contains("default : { other(); }"));
    }

    /**
     * Verifies a chain without an else branch preserves the fall-through path.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void preservesNoElseFallThrough() throws Exception {
        String source = """
                class Example {
                    void check(int value) {
                        if (value == 1) first();
                        else if (value == 2) second();
                        after();
                    }

                    void first() {
                    }

                    void second() {
                    }

                    void after() {
                    }
                }
                """;

        String normalized = rewrite(source).replaceAll("\\s+", " ").trim();
        assertTrue(normalized.contains("switch (value)"));
        assertTrue(normalized.contains("case 1 : { first(); } break;"));
        assertTrue(normalized.contains("case 2 : { second(); } break;"));
        assertTrue(normalized.contains("} after();"));
    }

    /**
     * Verifies reference equality and unresolved boxed types are not converted.
     *
     * @throws Exception if the source cannot be parsed or inspected
     */
    @Test
    void preservesReferenceAndUnresolvedSelectorTypes() throws Exception {
        String source = """
                class Example {
                    void stringCheck(String value) {
                        if (value == "one") first();
                        else if (value == "two") second();
                    }

                    void boxedCheck(Integer value) {
                        if (value == 1) first();
                        else if (value == 2) second();
                    }

                    void first() {
                    }

                    void second() {
                    }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Applies the conditional-switch rule to source text.
     *
     * @param source the Java source text
     * @return the rewritten source text
     * @throws Exception if the source cannot be parsed or rewritten
     */
    private String rewrite(String source) throws Exception {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setSource(source.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setStatementsRecovery(true);
        parser.setBindingsRecovery(true);
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
        ASTRewrite rewrite = ASTRewrite.create(compilationUnit.getAST());

        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(UseSwitchRule.ID);
        boolean changed = new UseSwitchRule().apply(compilationUnit, rewrite, configuration);
        if (!changed) {
            return source;
        }

        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
