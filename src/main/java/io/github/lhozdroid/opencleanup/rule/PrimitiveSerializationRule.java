package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Uses static primitive-wrapper serialization methods for explicit wrapper values.
 */
public final class PrimitiveSerializationRule implements CleanupRule {

    /** The stable identifier used to select this cleanup rule. */
    public static final String ID = "serialization.primitive";

    /**
     * Returns the stable identifier for primitive serialization cleanup.
     *
     * @return the primitive serialization rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for exact wrapper-to-string expressions.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one serialization is replaced
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Replaces one explicit wrapper serialization expression.
             *
             * @param node the visited method invocation
             * @return {@code false} after replacement, otherwise {@code true}
             */
            @Override
            public boolean visit(MethodInvocation node) {
                MethodInvocation replacement = replacement(node);
                if (replacement != null) {
                    rewrite.replace(node, replacement, null);
                    changed[0] = true;
                    return false;
                }
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Builds a static wrapper {@code toString} invocation.
     *
     * @param node the candidate instance {@code toString} invocation
     * @return the replacement invocation, or {@code null} when unsupported
     */
    private MethodInvocation replacement(MethodInvocation node) {
        if (!"toString".equals(node.getName().getIdentifier())
                || !node.arguments().isEmpty()) {
            return null;
        }
        String wrapper;
        Expression value;
        if (node.getExpression() instanceof ClassInstanceCreation creation) {
            wrapper = wrapperName(creation);
            if (wrapper == null || creation.arguments().size() != 1) {
                return null;
            }
            value = (Expression) creation.arguments().get(0);
        } else if (node.getExpression() instanceof MethodInvocation factory) {
            wrapper = factoryName(factory);
            if (wrapper == null || factory.arguments().size() != 1) {
                return null;
            }
            value = (Expression) factory.arguments().get(0);
        } else {
            return null;
        }
        AST ast = node.getAST();
        MethodInvocation replacement = ast.newMethodInvocation();
        replacement.setExpression(ast.newSimpleName(wrapper));
        replacement.setName(ast.newSimpleName("toString"));
        replacement.arguments().add(ASTNode.copySubtree(ast, value));
        return replacement;
    }

    /**
     * Returns the supported wrapper name of a constructor expression.
     *
     * @param creation the candidate wrapper construction
     * @return the wrapper name, or {@code null} when unsupported
     */
    private String wrapperName(ClassInstanceCreation creation) {
        if (!(creation.getType() instanceof SimpleType type)
                || creation.getAnonymousClassDeclaration() != null) {
            return null;
        }
        return supportedWrapper(type.getName().getFullyQualifiedName());
    }

    /**
     * Returns the supported wrapper name of a value factory.
     *
     * @param factory the candidate wrapper value factory
     * @return the wrapper name, or {@code null} when unsupported
     */
    private String factoryName(MethodInvocation factory) {
        if (!(factory.getExpression() instanceof SimpleName receiver)
                || !"valueOf".equals(factory.getName().getIdentifier())) {
            return null;
        }
        return supportedWrapper(receiver.getIdentifier());
    }

    /**
     * Checks whether a name is one of Java's primitive wrapper types.
     *
     * @param name the type name to inspect
     * @return the same name when supported, otherwise {@code null}
     */
    private String supportedWrapper(String name) {
        return switch (name) {
            case "Boolean", "Byte", "Short", "Integer", "Long", "Float", "Double", "Character" -> name;
            default -> null;
        };
    }
}
