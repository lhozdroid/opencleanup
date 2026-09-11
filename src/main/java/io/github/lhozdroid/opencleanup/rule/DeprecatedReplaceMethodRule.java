package io.github.lhozdroid.opencleanup.rule;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodRef;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Inlines local deprecated methods that explicitly document a replacement method.
 *
 * <p>The rewrite deliberately requires resolved bindings, a matching local method
 * declaration, and a one-statement forwarding body. This keeps incomplete ASTs and
 * replacements whose visibility or side effects cannot be proven unchanged.</p>
 */
public final class DeprecatedReplaceMethodRule implements CleanupRule {

    /** The stable identifier used to select this cleanup rule. */
    public static final String ID = "deprecated.replace-method";

    /**
     * Returns the stable identifier for deprecated-method replacement cleanup.
     *
     * @return the deprecated-method replacement rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records inlining edits for eligible deprecated method invocations.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one invocation is inlined
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        Map<String, MethodDeclaration> declarations = declarations(compilationUnit);
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Inlines one invocation whose resolved deprecated declaration is eligible.
             *
             * @param node the visited method invocation
             * @return {@code true} to continue visiting nested expressions
             */
            @Override
            public boolean visit(MethodInvocation node) {
                if (insideDeprecatedDeclaration(node)) {
                    return true;
                }
                MethodDeclaration declaration = declarationFor(node, declarations);
                MethodInvocation replacement = replacement(node, declaration);
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
     * Collects local method declarations by their name and parameter count.
     *
     * @param compilationUnit the compilation unit to inspect
     * @return the local method declaration index
     */
    private Map<String, MethodDeclaration> declarations(CompilationUnit compilationUnit) {
        Map<String, MethodDeclaration> result = new HashMap<>();
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Indexes one method declaration.
             *
             * @param node the visited declaration
             * @return {@code true} to continue visiting nested declarations
             */
            @Override
            public boolean visit(MethodDeclaration node) {
                if (node.resolveBinding() != null) {
                    result.putIfAbsent(key(node.getName().getIdentifier(), node.parameters().size()), node);
                }
                return true;
            }
        });
        return result;
    }

    /**
     * Finds the local declaration corresponding to a bound invocation.
     *
     * @param invocation the invocation to resolve
     * @param declarations indexed local declarations
     * @return the matching declaration, or {@code null}
     */
    private MethodDeclaration declarationFor(
            MethodInvocation invocation,
            Map<String, MethodDeclaration> declarations) {
        if (invocation.resolveMethodBinding() == null
                || !invocation.resolveMethodBinding().isDeprecated()) {
            return null;
        }
        MethodDeclaration declaration = declarations.get(
                key(invocation.getName().getIdentifier(), invocation.arguments().size()));
        if (declaration == null || declaration.resolveBinding() == null) {
            return null;
        }
        String invocationKey = invocation.resolveMethodBinding().getMethodDeclaration().getKey();
        String declarationKey = declaration.resolveBinding().getMethodDeclaration().getKey();
        return invocationKey.equals(declarationKey) && hasReplacementLink(declaration)
                ? declaration : null;
    }

    /**
     * Builds a forwarding invocation with call arguments substituted for parameters.
     *
     * @param invocation the deprecated invocation
     * @param declaration the local deprecated declaration
     * @return the replacement invocation, or {@code null} when unsupported
     */
    private MethodInvocation replacement(
            MethodInvocation invocation,
            MethodDeclaration declaration) {
        if (declaration == null || declaration.getBody() == null
                || declaration.getBody().statements().size() != 1) {
            return null;
        }
        Statement statement = (Statement) declaration.getBody().statements().get(0);
        MethodInvocation forwarded = forwardedInvocation(statement);
        if (forwarded == null || forwarded.resolveMethodBinding() == null
                || !sameLinkedMethod(declaration, forwarded)
                || forwarded.arguments().size() != declaration.parameters().size()
                || invocation.arguments().size() != declaration.parameters().size()) {
            return null;
        }
        MethodInvocation copy = (MethodInvocation) ASTNode.copySubtree(invocation.getAST(), forwarded);
        if (!substituteArguments(copy, forwarded, declaration, invocation)) {
            return null;
        }
        if (forwarded.getExpression() == null
                && invocation.getExpression() != null) {
            copy.setExpression((Expression) ASTNode.copySubtree(
                    invocation.getAST(), invocation.getExpression()));
        }
        return copy;
    }

    /**
     * Extracts a method invocation from the only supported forwarding statement.
     *
     * @param statement the deprecated method body statement
     * @return the forwarded invocation, or {@code null}
     */
    private MethodInvocation forwardedInvocation(Statement statement) {
        if (statement instanceof ReturnStatement returnStatement
                && returnStatement.getExpression() instanceof MethodInvocation invocation) {
            return invocation;
        }
        if (statement instanceof ExpressionStatement expressionStatement
                && expressionStatement.getExpression() instanceof MethodInvocation invocation) {
            return invocation;
        }
        return null;
    }

    /**
     * Replaces direct parameter references in a copied forwarding invocation.
     *
     * @param invocation the copied replacement invocation
     * @param substitutions parameter names and their call arguments
     */
    private boolean substituteArguments(
            MethodInvocation invocation,
            MethodInvocation forwarded,
            MethodDeclaration declaration,
            MethodInvocation original) {
        for (int index = 0; index < forwarded.arguments().size(); index++) {
            Object argument = forwarded.arguments().get(index);
            if (!(argument instanceof SimpleName parameterReference)) {
                return false;
            }
            int parameterIndex = parameterIndex(declaration, parameterReference.getIdentifier());
            if (parameterIndex < 0) {
                return false;
            }
            invocation.arguments().set(index, ASTNode.copySubtree(
                    invocation.getAST(), (ASTNode) original.arguments().get(parameterIndex)));
        }
        return true;
    }

    /**
     * Finds a formal parameter index by name.
     *
     * @param declaration the method declaration
     * @param name the parameter name
     * @return the parameter index, or {@code -1}
     */
    private int parameterIndex(MethodDeclaration declaration, String name) {
        for (int index = 0; index < declaration.parameters().size(); index++) {
            org.eclipse.jdt.core.dom.SingleVariableDeclaration parameter =
                    (org.eclipse.jdt.core.dom.SingleVariableDeclaration) declaration.parameters().get(index);
            if (name.equals(parameter.getName().getIdentifier())) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Checks that the forwarding body invokes the method named by the deprecation link.
     *
     * @param declaration the deprecated declaration
     * @param forwarded the forwarding invocation
     * @return {@code true} when the Javadoc link identifies the forwarded method
     */
    private boolean sameLinkedMethod(MethodDeclaration declaration, MethodInvocation forwarded) {
        String linkedName = replacementName(declaration.getJavadoc());
        return linkedName != null && linkedName.equals(forwarded.getName().getIdentifier());
    }

    /**
     * Checks that a deprecated Javadoc tag contains a method reference.
     *
     * @param declaration the method declaration to inspect
     * @return {@code true} when an explicit replacement method is documented
     */
    private boolean hasReplacementLink(MethodDeclaration declaration) {
        return replacementName(declaration.getJavadoc()) != null;
    }

    /**
     * Extracts the first method reference from a deprecated Javadoc tag.
     *
     * @param javadoc the declaration Javadoc
     * @return the linked method name, or {@code null}
     */
    private String replacementName(Javadoc javadoc) {
        if (javadoc == null) {
            return null;
        }
        for (Object tagObject : javadoc.tags()) {
            TagElement tag = (TagElement) tagObject;
            if (!"@deprecated".equals(tag.getTagName())) {
                continue;
            }
            String[] name = {null};
            tag.accept(new ASTVisitor() {
                /**
                 * Records the first linked method reference in a deprecated tag.
                 *
                 * @param methodRef the visited Javadoc method reference
                 * @return {@code false} after finding a reference, otherwise {@code true}
                 */
                @Override
                public boolean visit(MethodRef methodRef) {
                    name[0] = methodRef.getName().getIdentifier();
                    return false;
                }
            });
            if (name[0] != null) {
                return name[0];
            }
        }
        return null;
    }

    /**
     * Checks whether an invocation occurs inside a method declaration.
     *
     * @param invocation the invocation to inspect
     * @return {@code true} when it is part of a declaration body
     */
    private boolean insideDeprecatedDeclaration(MethodInvocation invocation) {
        if (invocation.resolveMethodBinding() == null) {
            return false;
        }
        String invocationKey = invocation.resolveMethodBinding().getMethodDeclaration().getKey();
        ASTNode current = invocation.getParent();
        while (current != null) {
            if (current instanceof MethodDeclaration declaration
                    && declaration.resolveBinding() != null
                    && declaration.resolveBinding().isDeprecated()) {
                return invocationKey.equals(
                        declaration.resolveBinding().getMethodDeclaration().getKey());
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * Creates an index key for a method name and arity.
     *
     * @param name the method name
     * @param arity the number of parameters
     * @return the declaration index key
     */
    private String key(String name, int arity) {
        return name + '/' + arity;
    }
}
