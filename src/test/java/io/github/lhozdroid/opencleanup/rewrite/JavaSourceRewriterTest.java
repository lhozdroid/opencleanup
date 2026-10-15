package io.github.lhozdroid.opencleanup.rewrite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;
import io.github.lhozdroid.opencleanup.rule.CleanupRuleRegistry;
import io.github.lhozdroid.opencleanup.rule.TrailingWhitespaceRule;
import io.github.lhozdroid.opencleanup.rule.UnusedPrivateMembersRule;

/** Tests the complete source-level rewrite pipeline. */
class JavaSourceRewriterTest {

    /** A temporary source root used by the pipeline tests. */
    @TempDir
    Path temporaryDirectory;

    /**
     * Verifies that a source-level rule is resolved, applied, and reported by the rewriter.
     *
     * @throws Exception if the temporary source cannot be read or written
     */
    @Test
    void appliesAndReportsSourceRule() throws Exception {
        Path sourceFile = temporaryDirectory.resolve("Example.java");
        Files.writeString(sourceFile, "class Example {  \n}\n", StandardCharsets.UTF_8);

        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(TrailingWhitespaceRule.ID);

        RewriteReport report = new JavaSourceRewriter(new CleanupRuleRegistry())
                .rewrite(List.of(temporaryDirectory), StandardCharsets.UTF_8, List.of(configuration));

        assertEquals("class Example {\n}\n", Files.readString(sourceFile, StandardCharsets.UTF_8));
        assertEquals(1, report.getFilesChanged());
        assertTrue(report.getAppliedRules().contains(TrailingWhitespaceRule.ID));
    }

    /**
     * Verifies atomic replacement preserves POSIX permissions on rewritten source files.
     *
     * @throws Exception if the temporary source cannot be read or written
     */
    @Test
    void preservesPosixPermissionsOnRewrite() throws Exception {
        Path sourceFile = temporaryDirectory.resolve("Permissions.java");
        Files.writeString(sourceFile, "class Permissions {  \n}\n", StandardCharsets.UTF_8);
        java.util.Set<PosixFilePermission> permissions;
        try {
            permissions = Files.getPosixFilePermissions(sourceFile);
        } catch (UnsupportedOperationException exception) {
            return;
        }

        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(TrailingWhitespaceRule.ID);
        new JavaSourceRewriter(new CleanupRuleRegistry())
                .rewrite(List.of(temporaryDirectory), StandardCharsets.UTF_8, List.of(configuration));

        assertEquals(permissions, Files.getPosixFilePermissions(sourceFile));
    }

    /**
     * Verifies malformed source is not destructively rewritten through recovery AST nodes.
     *
     * @throws Exception if the temporary source cannot be read or written
     */
    @Test
    void skipsMalformedSource() throws Exception {
        Path sourceFile = temporaryDirectory.resolve("Malformed.java");
        String source = "class Malformed { private int unused;";
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(UnusedPrivateMembersRule.ID);

        RewriteReport report = new JavaSourceRewriter(new CleanupRuleRegistry())
                .rewrite(List.of(temporaryDirectory), StandardCharsets.UTF_8, List.of(configuration));

        assertEquals(source, Files.readString(sourceFile, StandardCharsets.UTF_8));
        assertEquals(0, report.getFilesChanged());
    }

    /**
     * Verifies that a rewritten Lombok source still compiles against its generated setter API.
     *
     * @throws Exception if the temporary source cannot be rewritten or compiled
     */
    @Test
    void preservesCompilableLombokGeneratedApi() throws Exception {
        Path example = temporaryDirectory.resolve("Example.java");
        Path usage = temporaryDirectory.resolve("Usage.java");
        Files.writeString(example, "import lombok.Data; @Data class Example {"
                + " private String name; }", StandardCharsets.UTF_8);
        Files.writeString(usage, "class Usage { void update(Example example) {"
                + " example.setName(\"updated\"); } }", StandardCharsets.UTF_8);

        RuleConfiguration configuration = new RuleConfiguration();
        configuration.setId(UnusedPrivateMembersRule.ID);
        new JavaSourceRewriter(new CleanupRuleRegistry())
                .rewrite(List.of(temporaryDirectory), StandardCharsets.UTF_8, List.of(configuration));

        assertTrue(Files.readString(example, StandardCharsets.UTF_8)
                .contains("private String name;"));
        assertCompiles(List.of(example, usage), temporaryDirectory.resolve("compiled"));
    }

    /**
     * Compiles source files with the current test class path and annotation processors.
     *
     * @param sourceFiles Java source files to compile
     * @param outputDirectory destination for compiled classes
     * @throws Exception if the compiler is unavailable or compilation fails
     */
    private void assertCompiles(List<Path> sourceFiles, Path outputDirectory) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("A JDK compiler is required for this test");
        }
        Files.createDirectories(outputDirectory);
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            Iterable compilationUnits = fileManager.getJavaFileObjectsFromFiles(
                    sourceFiles.stream().map(Path::toFile).toList());
            String compilerClassPath = classPathWithLombok();
            List<String> options = List.of(
                    "-d", outputDirectory.toString(),
                    "-classpath", compilerClassPath,
                    "-processorpath", compilerClassPath);
            boolean compiled = compiler.getTask(null, fileManager, null, options, null, compilationUnits)
                    .call();
            assertTrue(compiled, "Lombok-generated API source did not compile");
        }
    }

    /**
     * Resolves a compiler class path that includes the provided Lombok jar.
     *
     * @return the current test class path plus Lombok
     * @throws Exception if Lombok cannot be located
     */
    private String classPathWithLombok() throws Exception {
        Path lombokLocation = Path.of(Class.forName("lombok.Data")
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        return System.getProperty("java.class.path")
                + System.getProperty("path.separator")
                + lombokLocation;
    }

    /**
     * Verifies the full rule catalog reaches a byte-identical fixpoint in a second pass.
     *
     * @throws Exception if the temporary sources cannot be read or written
     */
    @Test
    void fullCatalogRewriteIsIdempotent() throws Exception {
        // Write representative sources covering records, imports, final locals, loops, and catches.
        Path records = temporaryDirectory.resolve("Records.java");
        Files.writeString(records, RECORDS_SOURCE, StandardCharsets.UTF_8);
        Path legacy = temporaryDirectory.resolve("Legacy.java");
        Files.writeString(legacy, LEGACY_SOURCE, StandardCharsets.UTF_8);

        List<RuleConfiguration> configurations = everyRuleConfiguration();
        JavaSourceRewriter rewriter = new JavaSourceRewriter(new CleanupRuleRegistry());

        // Apply every rule once and snapshot the written sources.
        RewriteReport firstPass = rewriter.rewrite(
                List.of(temporaryDirectory), StandardCharsets.UTF_8, configurations);
        Map<String, String> afterFirstPass = readSources();

        // A second pass must report no changes and leave every source byte-identical.
        RewriteReport secondPass = rewriter.rewrite(
                List.of(temporaryDirectory), StandardCharsets.UTF_8, configurations);

        assertEquals(2, firstPass.getFilesVisited());
        assertTrue(firstPass.getFilesChanged() > 0);
        assertEquals(0, secondPass.getFilesChanged());
        assertEquals(afterFirstPass, readSources());
    }

    /**
     * Builds one configuration per registered rule so the whole catalog runs.
     *
     * @return configurations selecting every AST and source rule by identifier
     * @throws ReflectiveOperationException if the registry fields cannot be inspected
     */
    private List<RuleConfiguration> everyRuleConfiguration() throws ReflectiveOperationException {
        CleanupRuleRegistry registry = new CleanupRuleRegistry();
        TreeSet<String> ruleIds = new TreeSet<>();
        for (String fieldName : List.of("rules", "sourceRules")) {
            Field field = CleanupRuleRegistry.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            ruleIds.addAll(((Map<?, ?>) field.get(registry)).keySet().stream()
                    .map(Object::toString)
                    .toList());
        }

        List<RuleConfiguration> configurations = new ArrayList<>();
        for (String ruleId : ruleIds) {
            RuleConfiguration configuration = new RuleConfiguration();
            configuration.setId(ruleId);
            configurations.add(configuration);
        }
        return configurations;
    }

    /**
     * Reads every Java source below the temporary root into an ordered map.
     *
     * @return file names mapped to their UTF-8 content
     * @throws Exception if the temporary sources cannot be read
     */
    private Map<String, String> readSources() throws Exception {
        Map<String, String> sources = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(temporaryDirectory)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                sources.put(path.getFileName().toString(), Files.readString(path, StandardCharsets.UTF_8));
            }
        }
        return sources;
    }

    /** A record source exercising modern constructs and header imports. */
    private static final String RECORDS_SOURCE = "package example;\n"
            + "\n"
            + "import java.time.Clock;\n"
            + "import java.util.List;\n"
            + "\n"
            + "public sealed interface Shape permits Shape.Circle, Shape.Square {\n"
            + "    record Circle(@Deprecated List<String>[] names, Clock clock, int... extra)\n"
            + "            implements Shape {\n"
            + "        Circle {\n"
            + "            if (names.length == 0) {\n"
            + "                throw new IllegalArgumentException();\n"
            + "            }\n"
            + "        }\n"
            + "\n"
            + "        String text() {\n"
            + "            return \"\"\"\n"
            + "                keep semantic spaces  \n"
            + "                \"\"\";\n"
            + "        }\n"
            + "\n"
            + "        void nested() {\n"
            + "            record Local(int value) {\n"
            + "            }\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    record Square(int side) implements Shape {\n"
            + "    }\n"
            + "}\n";

    /** A legacy class exercising unused imports, final locals, loops, and catches. */
    private static final String LEGACY_SOURCE = "package example;\n"
            + "\n"
            + "import java.util.List;\n"
            + "import java.util.Set;\n"
            + "\n"
            + "class Legacy {\n"
            + "    private List<String> values;\n"
            + "\n"
            + "    void run(boolean flag) {\n"
            + "        int total = 0;\n"
            + "        for (int index = 0; index < 1;) {\n"
            + "            total += index;\n"
            + "            break;\n"
            + "        }\n"
            + "        try {\n"
            + "            use(values, flag);\n"
            + "        } catch (RuntimeException failure) {\n"
            + "            total++;\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    void use(List<String> values, boolean flag) {\n"
            + "    }\n"
            + "}\n";
}
