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

    public JavaSourceRewriter(CleanupRuleRegistry ruleRegistry) {
        this.ruleRegistry = ruleRegistry;
    }

    public RewriteReport rewrite(
            Collection<Path> sourceRoots,
            Charset charset,
            List<RuleConfiguration> configurations) throws IOException, RewriteException {
        List<CleanupRuleRegistry.ConfiguredRule> configuredRules = ruleRegistry.resolve(configurations);
        RewriteState state = new RewriteState();

        for (Path sourceRoot : sourceRoots) {
            if (!Files.isDirectory(sourceRoot)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(sourceRoot)) {
                try {
                    paths.filter(this::isJavaSource).forEach(path -> rewriteFile(path, charset, configuredRules, state));
                } catch (UncheckedIOException exception) {
                    throw exception.getCause();
                }
            }
        }

        return state.toReport();
    }

    private void rewriteFile(
            Path path,
            Charset charset,
            List<CleanupRuleRegistry.ConfiguredRule> configuredRules,
            RewriteState state) {
        try {
            state.filesVisited++;
            String source = Files.readString(path, charset);
            CompilationUnit compilationUnit = parse(source);
            ASTRewrite rewrite = ASTRewrite.create(compilationUnit.getAST());
            Set<String> appliedRules = new LinkedHashSet<>();

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
            if (source.equals(rewrittenSource)) {
                return;
            }

            Files.writeString(path, rewrittenSource, charset);
            state.filesChanged++;
            state.changedFiles.add(path);
            state.appliedRules.addAll(appliedRules);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not rewrite " + path, exception);
        } catch (Exception exception) {
            throw new UncheckedIOException(new IOException("Could not rewrite " + path, exception));
        }
    }

    private CompilationUnit parse(String source) {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setStatementsRecovery(true);
        parser.setBindingsRecovery(true);
        return (CompilationUnit) parser.createAST(null);
    }

    private boolean isJavaSource(Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().endsWith(".java");
    }

    private static final class RewriteState {

        private int filesVisited;
        private int filesChanged;
        private final List<Path> changedFiles = new java.util.ArrayList<>();
        private final Set<String> appliedRules = new LinkedHashSet<>();

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
