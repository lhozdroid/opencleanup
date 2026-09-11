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

/**
 * Tests syntax-only simplification of direct conditional return expressions.
 */
class ConditionalReturnRuleTest {

    /**
     * Verifies both boolean literal conditional return directions are simplified.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void simplifiesBooleanConditionalReturns() throws Exception {
        String source = """
                class Example {
                    boolean same(boolean condition) {
                        return condition ? true : false;
                    }

                    boolean inverted(boolean condition) {
                        return condition ? false : true;
                    }
                }
                """;

        String rewritten = rewrite(source);

        assertTrue(rewritten.contains("return condition;"));
        assertTrue(rewritten.contains("return !condition;"));
        assertEquals(0, rewritten.lines().filter(line -> line.contains("? true : false")
                || line.contains("? false : true")).count());
    }

    /**
     * Verifies that only direct returns with two boolean literal branches change.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesUnsupportedReturnShapes() throws Exception {
        String source = """
                class Example {
                    boolean nonLiteralBranch(boolean condition, boolean value) {
                        return condition ? value : false;
                    }

                    boolean parenthesized(boolean condition) {
                        return (condition ? true : false);
                    }

                    boolean assignedBeforeReturn(boolean condition) {
                        boolean value = condition ? true : false;
                        return value;
                    }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Parses and rewrites one source string with the conditional-return rule.
     *
     * @param source the Java source to rewrite
     * @return the rewritten Java source
     * @throws Exception if parsing or applying AST edits fails
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
        configuration.setId(ConditionalReturnRule.ID);
        boolean changed = new ConditionalReturnRule().apply(compilationUnit, rewrite, configuration);
        if (!changed) {
            return source;
        }

        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
