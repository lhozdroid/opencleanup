package io.github.lhozdroid.opencleanup.rule;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TextBlock;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;
import io.github.lhozdroid.opencleanup.rewrite.SourceRewriteRule;
import io.github.lhozdroid.opencleanup.rewrite.SourceRewriteSupport;
import io.github.lhozdroid.opencleanup.rewrite.SourceRewriteSupport.SourceLine;

/**
 * Removes trailing spaces and tabs without changing line endings or text-block contents.
 */
public final class TrailingWhitespaceRule implements SourceRewriteRule {

    /** The stable identifier for trailing-whitespace cleanup. */
    public static final String ID = "format.trailing-whitespace";

    /** The option value that preserves whitespace on otherwise empty lines. */
    private static final String IGNORE_EMPTY_LINES = "ignore-empty-lines";

    /**
     * Returns the stable identifier for trailing-whitespace cleanup.
     *
     * @return the trailing-whitespace rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Removes trailing horizontal whitespace from ordinary source lines.
     *
     * @param source the complete Java source text
     * @param configuration the rule configuration; {@code ignore-empty-lines} is supported
     * @return source with eligible trailing whitespace removed
     */
    @Override
    public String apply(String source, RuleConfiguration configuration) {
        boolean ignoreEmptyLines = isIgnoreEmptyLinesMode(configuration);
        List<SourceLine> lines = SourceRewriteSupport.splitLines(source);
        Set<Integer> textBlockLines = textBlockLines(source);
        List<SourceLine> rewritten = new java.util.ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            SourceLine line = lines.get(index);
            String content = line.content();
            if (textBlockLines.contains(index + 1)
                    || (ignoreEmptyLines && content.trim().isEmpty())) {
                rewritten.add(line);
                continue;
            }
            int end = content.length();
            while (end > 0 && isHorizontalWhitespace(content.charAt(end - 1))) {
                end--;
            }
            rewritten.add(new SourceLine(content.substring(0, end), line.delimiter()));
        }
        return SourceRewriteSupport.joinLines(rewritten);
    }

    /**
     * Checks whether the configured mode preserves whitespace on empty lines.
     *
     * @param configuration the optional rule configuration
     * @return {@code true} when empty-line whitespace should be retained
     */
    private boolean isIgnoreEmptyLinesMode(RuleConfiguration configuration) {
        if (configuration == null) {
            return false;
        }
        String mode = configuration.optionValue("mode");
        if (mode == null) {
            mode = configuration.optionValue(ID);
        }
        return IGNORE_EMPTY_LINES.equalsIgnoreCase(mode)
                || configuration.isOptionEnabled(IGNORE_EMPTY_LINES);
    }

    /**
     * Finds source lines belonging to Java text blocks so their whitespace remains semantic data.
     *
     * @param source the source text to inspect
     * @return one-based source line numbers occupied by text blocks
     */
    private Set<Integer> textBlockLines(String source) {
        Set<Integer> lines = new HashSet<>();
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Records every source line touched by a text block.
             *
             * @param node the visited text block
             * @return {@code false} because text blocks have no relevant child nodes
             */
            @Override
            public boolean visit(TextBlock node) {
                int startLine = compilationUnit.getLineNumber(node.getStartPosition());
                int endPosition = node.getStartPosition() + Math.max(0, node.getLength() - 1);
                int endLine = compilationUnit.getLineNumber(endPosition);
                for (int line = startLine + 1; line < endLine; line++) {
                    lines.add(line);
                }
                return false;
            }
        });
        return lines;
    }

    /**
     * Checks whether a character is whitespace that can safely be removed at line end.
     *
     * @param character the character to inspect
     * @return {@code true} for spaces and tabs
     */
    private boolean isHorizontalWhitespace(char character) {
        return character == ' ' || character == '\t';
    }

}
