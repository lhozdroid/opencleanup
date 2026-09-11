package io.github.lhozdroid.opencleanup.rewrite;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;
import io.github.lhozdroid.opencleanup.rule.CleanupRuleRegistry;

/**
 * Parses Java source files and applies the selected cleanup rules.
 */
public final class JavaSourceRewriter {

    private final CleanupRuleRegistry ruleRegistry;

    /**
     * Creates a source rewriter backed by a cleanup rule registry.
     *
     * @param ruleRegistry the registry used to resolve configured rules
     */
    public JavaSourceRewriter(CleanupRuleRegistry ruleRegistry) {
        this.ruleRegistry = ruleRegistry;
    }

    /**
     * Rewrites Java files below the supplied source roots.
     *
     * @param sourceRoots directories containing Java source files
     * @param charset charset used to read and write source files
     * @param configurations configured rule groups and rules
     * @return a summary of visited and changed files
     * @throws IOException if a source file cannot be read or written
     * @throws RewriteException if source parsing or AST rewriting fails
     */
    public RewriteReport rewrite(
            Collection<Path> sourceRoots,
            Charset charset,
            List<RuleConfiguration> configurations) throws IOException, RewriteException {
        List<CleanupRuleRegistry.ConfiguredRule> configuredRules = ruleRegistry.resolve(configurations);
        List<CleanupRuleRegistry.ConfiguredSourceRule> sourceRules = ruleRegistry.resolveSourceRules(configurations);
        RewriteState state = new RewriteState();

        for (Path sourceRoot : sourceRoots) {
            if (!Files.isDirectory(sourceRoot)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(sourceRoot)) {
                try {
                    paths.filter(this::isJavaSource)
                            .forEach(path -> rewriteFile(path, charset, configuredRules, sourceRules, state));
                } catch (UncheckedIOException exception) {
                    throw exception.getCause();
                }
            }
        }

        return state.toReport();
    }

    /**
     * Applies all resolved rules to one Java source file.
     *
     * @param path the Java source file
     * @param charset charset used to read and write the file
     * @param configuredRules rules selected for this execution
     * @param sourceRules source-level rules selected for this execution
     * @param state mutable execution counters and results
     */
    private void rewriteFile(
            Path path,
            Charset charset,
            List<CleanupRuleRegistry.ConfiguredRule> configuredRules,
            List<CleanupRuleRegistry.ConfiguredSourceRule> sourceRules,
            RewriteState state) {
        try {
            state.filesVisited++;
            String originalSource = Files.readString(path, charset);
            String source = originalSource;
            Set<String> appliedRules = new LinkedHashSet<>();
            for (CleanupRuleRegistry.ConfiguredSourceRule sourceRule : sourceRules) {
                String rewrittenSource = sourceRule.rule().apply(source, sourceRule.configuration());
                if (!source.equals(rewrittenSource)) {
                    source = rewrittenSource;
                    appliedRules.add(sourceRule.rule().id());
                }
            }
            CompilationUnit compilationUnit = parse(source);
            ASTRewrite rewrite = ASTRewrite.create(compilationUnit.getAST());

            for (CleanupRuleRegistry.ConfiguredRule configuredRule : configuredRules) {
                if (configuredRule.rule().apply(compilationUnit, rewrite, configuredRule.configuration())) {
                    appliedRules.add(configuredRule.rule().id());
                }
            }

            if (appliedRules.isEmpty()) {
                return;
            }

            IDocument document = new Document(source);
            TextEdit edits = rewrite.rewriteAST(document, Map.of());
            edits.apply(document);
            String rewrittenSource = document.get();
            if (originalSource.equals(rewrittenSource)) {
                return;
            }

            Files.writeString(path, rewrittenSource, charset);
            state.filesChanged++;
            state.changedFiles.add(path);
            state.appliedRules.addAll(appliedRules);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not rewrite " + path, exception);
        } catch (BadLocationException exception) {
            throw new UncheckedIOException(
                    "Could not rewrite " + path,
                    new IOException("Could not apply source edits", exception));
        } catch (RuntimeException exception) {
            throw new UncheckedIOException(new IOException("Could not rewrite " + path, exception));
        }
    }

    /**
     * Parses source text as a Java 21 compilation unit.
     *
     * @param source the Java source text
     * @return the parsed compilation unit
     */
    private CompilationUnit parse(String source) {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setStatementsRecovery(true);
        parser.setBindingsRecovery(true);
        return (CompilationUnit) parser.createAST(null);
    }

    /**
     * Checks whether a path is a regular Java source file.
     *
     * @param path the path to check
     * @return {@code true} when the path points to a file ending in {@code .java}
     */
    private boolean isJavaSource(Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().endsWith(".java");
    }

    private static final class RewriteState {

        private int filesVisited;
        private int filesChanged;
        private final List<Path> changedFiles = new java.util.ArrayList<>();
        private final Set<String> appliedRules = new LinkedHashSet<>();

        /**
         * Builds the immutable report for this execution.
         *
         * @return the rewrite execution report
         */
        private RewriteReport toReport() {
            return RewriteReport.builder()
                    .filesVisited(filesVisited)
                    .filesChanged(filesChanged)
                    .changedFiles(changedFiles)
                    .appliedRules(appliedRules)
                    .build();
        }
    }
}
