package io.github.lhozdroid.opencleanup.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.List;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.junit.jupiter.api.Test;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;
import io.github.lhozdroid.opencleanup.config.RuleOption;

/**
 * Tests conservative declaration-category sorting.
 */
class MembersSortRuleTest {

    /**
     * Verifies fields, constructors, methods, and nested types follow the default member order.
     *
     * @throws Exception if the source edit cannot be applied
     */
    @Test
    void sortsMembersByDeclarationCategory() throws Exception {
        String source = "class Example {\n"
                + "    void run() {}\n"
                + "    class Nested {}\n"
                + "    Example() {}\n"
                + "    int value;\n"
                + "    static int count;\n"
                + "}\n";
        String expected = "class Example {\n"
                + "    static int count;\n"
                + "    int value;\n"
                + "    Example() {}\n"
                + "    void run() {}\n"
                + "    class Nested {}\n"
                + "}\n";

        assertEquals(
                expected.replaceAll("\\s+", " ").trim(),
                rewrite(source).replaceAll("\\s+", " ").trim());
    }

    /**
     * Verifies members already in category order are left byte-for-byte unchanged.
     *
     * @throws Exception if the source edit cannot be applied
     */
    @Test
    void preservesSortedMembers() throws Exception {
        String source = "class Example {\n"
                + "    static int count;\n"
                + "    int value;\n"
                + "    Example() {}\n"
                + "    void run() {}\n"
                + "}\n";

        assertEquals(source, rewrite(source));
    }

    /**
     * Verifies a configured category order takes precedence over the default order.
     *
     * @throws Exception if the source edit cannot be applied
     */
    @Test
    void honorsConfiguredMemberOrder() throws Exception {
        String source = "class Example {\n"
                + "    int value;\n"
                + "    void run() {}\n"
                + "}\n";
        RuleConfiguration configuration = new RuleConfiguration();
        RuleOption order = new RuleOption();
        order.setName("order");
        order.setValue("methods, fields");
        configuration.setOptions(List.of(order));

        String rewritten = rewrite(source, configuration);
        assertEquals("class Example { void run() {} int value; }", rewritten
                .replaceAll("\\s+", " ").trim());
    }

    /**
     * Parses and rewrites one source string with the member sorting rule.
     *
     * @param source the source to rewrite
     * @return the rewritten source
     * @throws Exception if the source edit cannot be applied
     */
    private String rewrite(String source) throws Exception {
        return rewrite(source, new RuleConfiguration());
    }

    /**
     * Parses and rewrites one source string with a supplied member-sort configuration.
     *
     * @param source the source to rewrite
     * @param configuration the member-sort configuration
     * @return the rewritten source
     * @throws Exception if the source edit cannot be applied
     */
    private String rewrite(String source, RuleConfiguration configuration) throws Exception {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
        ASTRewrite rewrite = ASTRewrite.create(compilationUnit.getAST());
        configuration.setId(MembersSortRule.ID);

        boolean changed = new MembersSortRule().apply(compilationUnit, rewrite, configuration);
        if (!changed) {
            return source;
        }

        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
