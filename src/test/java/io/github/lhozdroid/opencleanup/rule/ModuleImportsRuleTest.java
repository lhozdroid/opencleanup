package io.github.lhozdroid.opencleanup.rule;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.junit.jupiter.api.Test;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Tests Java 21 compatibility of the module-import cleanup.
 */
class ModuleImportsRuleTest {

    /**
     * Verifies that Java 21 source is preserved when module-import syntax is unavailable.
     */
    @Test
    void skipsJava21Source() {
        CompilationUnit compilationUnit = parse("import java.util.List; class Example { List<String> values; }");
        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(ModuleImportsRule.ID);

        assertFalse(new ModuleImportsRule().apply(
                compilationUnit,
                ASTRewrite.create(compilationUnit.getAST()),
                configuration));
    }

    /**
     * Parses one Java source string with the Java 21 AST.
     *
     * @param source the source text to parse
     * @return the parsed compilation unit
     */
    private CompilationUnit parse(String source) {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        return (CompilationUnit) parser.createAST(null);
    }
}
