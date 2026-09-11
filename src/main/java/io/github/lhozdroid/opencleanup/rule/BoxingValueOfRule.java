package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Replaces wrapper construction with the corresponding cached {@code valueOf} factory.
 */
public final class BoxingValueOfRule implements CleanupRule {

    /** The stable identifier for wrapper valueOf cleanup. */
    public static final String ID = "boxing.value-of";

    /**
     * Returns the stable identifier for this cleanup rule.
     *
     * @return the valueOf rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for exact one-argument wrapper constructors.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one constructor is replaced
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Replaces one exact wrapper construction with a valueOf invocation.
             *
             * @param node the visited class instance creation
             * @return {@code false} after replacement, otherwise {@code true}
             */
            @Override
            public boolean visit(ClassInstanceCreation node) {
                if (!(node.getType() instanceof org.eclipse.jdt.core.dom.SimpleType type)
                        || node.arguments().size() != 1
                        || !(type.getName() instanceof SimpleName name)
                        || !isWrapper(name.getIdentifier())) {
                    return true;
                }
                AST ast = node.getAST();
                MethodInvocation replacement = ast.newMethodInvocation();
                replacement.setExpression((Expression) ASTNode.copySubtree(ast, type.getName()));
                replacement.setName(ast.newSimpleName("valueOf"));
                replacement.arguments().add(ASTNode.copySubtree(
                        ast, (Expression) node.arguments().get(0)));
                rewrite.replace(node, replacement, null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Checks whether a simple type is one of Java's primitive wrapper classes.
     *
     * @param name the simple type name
     * @return {@code true} for a supported wrapper class
     */
    private boolean isWrapper(String name) {
        return switch (name) {
            case "Boolean", "Byte", "Character", "Short", "Integer", "Long", "Float", "Double" -> true;
            default -> false;
        };
    }
}
