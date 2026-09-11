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
 * Tests conversion from class-literal instance checks to instanceof.
 */
class InstanceofRuleTest {

    /**
     * Verifies conversion of simple and qualified class literal checks.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void replacesClassLiteralInstanceChecks() throws Exception {
        String source = """
                class Example {
                    boolean check(Object value) {
                        return String.class.isInstance(value);
                    }

                    boolean checkList(Object value) {
                        return java.util.List.class.isInstance(value);
                    }
                }
                """;

        String rewritten = rewrite(source);

        assertTrue(rewritten.contains("return value instanceof String;"));
        assertTrue(rewritten.contains("return value instanceof java.util.List;"));
    }

    /**
     * Verifies that primitive and unresolved method arguments are preserved.
     *
     * @throws Exception if AST edits cannot be applied
     */
    @Test
    void preservesPrimitiveAndUncertainArguments() throws Exception {
        String source = """
                class Example {
                    boolean primitiveLiteral() {
                        return String.class.isInstance(1);
                    }

                    boolean primitiveVariable(int value) {
                        return String.class.isInstance(value);
                    }

                    boolean uncertain() {
                        return String.class.isInstance(value());
                    }

                    int value() {
                        return 1;
                    }
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Parses and rewrites one source string with the instanceof rule.
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
        configuration.setId(InstanceofRule.ID);
        boolean changed = new InstanceofRule().apply(compilationUnit, rewrite, configuration);
        if (!changed) {
            return source;
        }

        Document document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
