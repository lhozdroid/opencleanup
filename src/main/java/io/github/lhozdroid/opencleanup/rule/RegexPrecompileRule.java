package io.github.lhozdroid.opencleanup.rule;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Precompiles a literal regular expression reused by several simple-name matches in one method.
 */
public final class RegexPrecompileRule implements CleanupRule {

    /** The stable identifier used to select this cleanup rule. */
    public static final String ID = "regular-expressions.precompile";

    /**
     * Returns the stable identifier for regular-expression precompilation cleanup.
     *
     * @return the regular-expression precompile rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records pattern declarations and matcher calls for repeated literal regex variables.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when a regex is precompiled
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Precompiles eligible regex variables in one method body.
             *
             * @param node the visited method declaration
             * @return {@code false} because nested methods are independent scopes
             */
            @Override
            public boolean visit(MethodDeclaration node) {
                if (node.getBody() != null && precompile(node.getBody(), rewrite)) {
                    changed[0] = true;
                }
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Finds and precompiles each eligible string regex declaration in a block.
     *
     * @param body the method body to inspect
     * @param rewrite the AST rewrite collecting source edits
     * @return {@code true} when at least one declaration is inserted
     */
    private boolean precompile(Block body, ASTRewrite rewrite) {
        List<VariableDeclarationStatement> declarations = new ArrayList<>();
        body.accept(new ASTVisitor() {
            /**
             * Collects one local string declaration as a possible regex source.
             *
             * @param node the visited local declaration
             * @return {@code true} to continue collecting nested declarations
             */
            @Override
            public boolean visit(VariableDeclarationStatement node) {
                if (node.getType() instanceof SimpleType type
                        && "String".equals(type.getName().getFullyQualifiedName())
                        && node.fragments().size() == 1) {
                    VariableDeclarationFragment fragment =
                            (VariableDeclarationFragment) node.fragments().get(0);
                    if (fragment.getInitializer() instanceof StringLiteral) {
                        declarations.add(node);
                    }
                }
                return true;
            }
        });
        boolean changed = false;
        for (VariableDeclarationStatement declaration : declarations) {
            VariableDeclarationFragment fragment =
                    (VariableDeclarationFragment) declaration.fragments().get(0);
            String regexName = fragment.getName().getIdentifier();
            List<MethodInvocation> matches = matchesUsing(body, regexName);
            if (matches.size() < 2 || !(declaration.getParent() instanceof Block parent)) {
                continue;
            }
            String patternName = uniquePatternName(parent, regexName);
            VariableDeclarationStatement patternDeclaration = patternDeclaration(
                    declaration.getAST(), regexName, patternName);
            ListRewrite statements = rewrite.getListRewrite(parent, Block.STATEMENTS_PROPERTY);
            statements.insertAfter(patternDeclaration, declaration, null);
            for (MethodInvocation match : matches) {
                rewrite.replace(match, matcherInvocation(match, patternName), null);
            }
            changed = true;
        }
        return changed;
    }

    /**
     * Collects simple {@code String.matches(regex)} calls in a method body.
     *
     * @param body the method body to inspect
     * @param regexName the regex variable name
     * @return matching invocations
     */
    private List<MethodInvocation> matchesUsing(Block body, String regexName) {
        List<MethodInvocation> matches = new ArrayList<>();
        body.accept(new ASTVisitor() {
            /**
             * Collects one exact string matches invocation.
             *
             * @param node the visited method invocation
             * @return {@code true} to continue visiting nested expressions
             */
            @Override
            public boolean visit(MethodInvocation node) {
                if ("matches".equals(node.getName().getIdentifier())
                        && node.arguments().size() == 1
                        && node.getExpression() instanceof SimpleName
                        && node.arguments().get(0) instanceof SimpleName argument
                        && regexName.equals(argument.getIdentifier())) {
                    matches.add(node);
                }
                return true;
            }
        });
        return matches;
    }

    /**
     * Creates a pattern declaration immediately after its source string declaration.
     *
     * @param ast the owning AST
     * @param regexName the source string name
     * @param patternName the generated pattern name
     * @param initializer the literal regex initializer
     * @return the generated pattern declaration
     */
    private VariableDeclarationStatement patternDeclaration(
            AST ast, String regexName, String patternName) {
        VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
        fragment.setName(ast.newSimpleName(patternName));
        MethodInvocation compile = ast.newMethodInvocation();
        compile.setExpression(ast.newName("java.util.regex.Pattern"));
        compile.setName(ast.newSimpleName("compile"));
        compile.arguments().add(ast.newSimpleName(regexName));
        fragment.setInitializer(compile);
        VariableDeclarationStatement declaration = ast.newVariableDeclarationStatement(fragment);
        declaration.setType(ast.newSimpleType(ast.newName("java.util.regex.Pattern")));
        return declaration;
    }

    /**
     * Creates a matcher call that preserves the original receiver and regex argument.
     *
     * @param match the original matches invocation
     * @param patternName the generated pattern variable name
     * @return the matcher invocation
     */
    private MethodInvocation matcherInvocation(MethodInvocation match, String patternName) {
        AST ast = match.getAST();
        MethodInvocation matcher = ast.newMethodInvocation();
        matcher.setExpression(ast.newSimpleName(patternName));
        matcher.setName(ast.newSimpleName("matcher"));
        matcher.arguments().add(ASTNode.copySubtree(ast, (ASTNode) match.getExpression()));
        MethodInvocation result = ast.newMethodInvocation();
        result.setExpression(matcher);
        result.setName(ast.newSimpleName("matches"));
        return result;
    }

    /**
     * Chooses a pattern variable name that does not collide with a block declaration.
     *
     * @param block the containing block
     * @param regexName the source regex variable name
     * @return a unique generated name
     */
    private String uniquePatternName(Block block, String regexName) {
        String candidate = regexName + "Pattern";
        boolean[] present = {false};
        block.accept(new ASTVisitor() {
            /**
             * Checks one variable declaration for a generated-name collision.
             *
             * @param node the visited local declaration
             * @return {@code true} to continue scanning
             */
            @Override
            public boolean visit(VariableDeclarationFragment node) {
                if (candidate.equals(node.getName().getIdentifier())) {
                    present[0] = true;
                }
                return true;
            }
        });
        return present[0] ? candidate + "2" : candidate;
    }
}
