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
 * Tests conservative conversion to instanceof pattern matching.
 */
class InstanceofPatternMatchingRuleTest {

    /**
     * Verifies conversion of a first local cast declaration in an if block.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void convertsMatchingCastDeclaration() throws Exception {
        String source = """
                class Example {
                    boolean check(Object value) {
                        if (value instanceof String) {
                            String text = (String) value;
                            return text.isBlank();
                        }
                        return false;
                    }
                }
                """;

        String rewritten = rewrite(source);

        assertTrue(rewritten.contains("if (value instanceof String text) {"));
        assertTrue(rewritten.contains("return text.isBlank();"));
        assertTrue(!rewritten.contains("String text = (String) value;"));
    }

    /**
     * Verifies that uncertain control-flow and evaluation shapes remain
     * unchanged.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesUnsupportedShapes() throws Exception {
        String source = """
                class Example {
                    void withElse(Object value) {
                        if (value instanceof String) {
                            String text = (String) value;
                            use(text);
                        } else {
                            use(value);
                        }
                    }

                    void withInterveningStatement(Object value) {
                        if (value instanceof String) {
                            use(value);
                            String text = (String) value;
                            use(text);
                        }
                    }

                    void withSideEffect() {
                        if (nextValue() instanceof String) {
                            String text = (String) nextValue();
                            use(text);
                        }
                    }

                    void withDifferentCast(Object value) {
                        if (value instanceof String) {
                            CharSequence text = (String) value;
                            use(text);
                        }
                    }

                    Object nextValue() {
                        return "value";
                    }

                    void use(Object value) {
                    }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Parses and rewrites one source string with the pattern matching rule.
     *
     * @param source the Java source to rewrite
     * @return the rewritten Java source
     * @throws Exception if parsing or applying AST edits fails
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
        configuration.setId(InstanceofPatternMatchingRule.ID);
        boolean changed = new InstanceofPatternMatchingRule().apply(
                compilationUnit, rewrite, configuration);
        if (!changed) {
            return source;
        }

        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
