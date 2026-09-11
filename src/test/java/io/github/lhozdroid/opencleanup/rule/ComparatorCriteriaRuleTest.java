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
 * Tests comparator criteria construction.
 */
class ComparatorCriteriaRuleTest {

    /**
     * Verifies conversion of a direct compare-to comparator criterion.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void convertsCompareToCriterion() throws Exception {
        String source = """
                import java.util.Comparator;
                import java.util.List;

                class Example {
                    void sort(List<Person> people) {
                        people.sort(new Comparator<Person>() {
                            @Override
                            public int compare(Person left, Person right) {
                                return left.name().compareTo(right.name());
                            }
                        });
                    }
                }

                record Person(String name) {
                }
                """;

        String rewritten = rewrite(source);

        assertTrue(rewritten.contains("java.util.Comparator.comparing(value -> value.name())"));
        assertTrue(!rewritten.contains("new Comparator<Person>()"));
    }

    /**
     * Verifies that unrelated comparator bodies remain unchanged.
     *
     * @throws Exception if the source cannot be parsed or rewritten
     */
    @Test
    void preservesUnsupportedComparator() throws Exception {
        String source = """
                import java.util.Comparator;
                import java.util.List;

                class Example {
                    void sort(List<Person> people) {
                        people.sort(new Comparator<Person>() {
                            @Override
                            public int compare(Person left, Person right) {
                                return left.name().length() - right.name().length();
                            }
                        });
                    }
                }

                record Person(String name) {
                }
                """;

        assertEquals(source, rewrite(source));
    }

    /**
     * Parses and rewrites one source string with the comparator criteria rule.
     *
     * @param source the source string to rewrite
     * @return the rewritten source string
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
        configuration.setId(ComparatorCriteriaRule.ID);
        new ComparatorCriteriaRule().apply(compilationUnit, rewrite, configuration);
        IDocument document = new Document(source);
        TextEdit edits = rewrite.rewriteAST(document, Map.of());
        edits.apply(document);
        return document.get();
    }
}
