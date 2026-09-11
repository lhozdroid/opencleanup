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

/**
 * Tests simplification of if/else statements returning opposite booleans.
 */
class SimplifyBooleanIfElseRuleTest {

    /**
     * Verifies both truth directions, including single-return branch blocks.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void simplifiesOppositeBooleanReturns() throws Exception {
        String source = """
                class Example {
                    boolean same(boolean value) {
                        if (value) {
                            return true;
                        } else {
                            return false;
                        }
                    }

                    boolean inverted(boolean value)
                    {
                        if (value)
                            return false;
                        else
                            return true;
                    }
                }
                """;

        String rewrittenSource = rewrite(source);

        assertTrue(rewrittenSource.contains("boolean same(boolean value) {\n        return value;\n"));
        assertTrue(rewrittenSource.contains("boolean inverted(boolean value)\n    {\n        return !value;\n"));
        assertEquals(0, rewrittenSource.lines().filter(line -> line.trim().startsWith("if ("))
                .count());
    }

    /**
     * Verifies unsupported branch shapes remain byte-for-byte unchanged.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesUnsupportedBooleanIfElseShapes() throws Exception {
        String source = """
                class Example {
                    boolean multipleStatements(boolean value) {
                        if (value) {
                            record(value);
                            return true;
                        } else {
                            return false;
                        }
                    }

                    boolean missingElse(boolean value) {
                        if (value) {
                            return true;
                        }
                        return false;
                    }

                    boolean sameResults(boolean value) {
                        if (value) {
                            return true;
                        } else {
                            return true;
                        }
                    }

                    boolean expressionResults(boolean value) {
                        if (value) {
                            return value;
                        } else {
                            return false;
                        }
                    }

                    void record(boolean value) {
                    }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Parses source, applies the rule, and renders the collected AST edits.
     *
     * @param source the Java source to rewrite
     * @return the rewritten Java source
     * @throws Exception if parsing or applying AST edits fails
     */
    private String rewrite(String source) throws Exception {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
        ASTRewrite rewrite = ASTRewrite.create(compilationUnit.getAST());
        new SimplifyBooleanIfElseRule().apply(compilationUnit, rewrite, null);

        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
