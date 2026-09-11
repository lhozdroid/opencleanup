package io.github.lhozdroid.opencleanup.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;
import io.github.lhozdroid.opencleanup.rewrite.JavaSourceRewriter;

class OrganizeImportsRuleTest {

    @TempDir
    Path tempDirectory;

    /**
     * Verifies normal imports are sorted before static imports.
     *
     * @throws Exception if the temporary source file cannot be created or rewritten
     */
    @Test
    void sortsNormalAndStaticImports() throws Exception {
        Path sourceFile = tempDirectory.resolve("Example.java");
        Files.writeString(sourceFile, """
                import static java.util.Collections.sort;
                import java.util.Set;
                import static java.util.Collections.emptyList;
                import java.util.List;

                class Example {
                }
                """, StandardCharsets.UTF_8);

        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(OrganizeImportsRule.ID);

        new JavaSourceRewriter(new CleanupRuleRegistry())
                .rewrite(List.of(tempDirectory), StandardCharsets.UTF_8, List.of(configuration));

        List<String> imports = Files.readAllLines(sourceFile).stream()
                .filter(line -> line.startsWith("import "))
                .toList();
        assertEquals(List.of(
                "import java.util.List;",
                "import java.util.Set;",
                "import static java.util.Collections.emptyList;",
                "import static java.util.Collections.sort;"), imports);
    }
}
