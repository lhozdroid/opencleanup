package io.github.lhozdroid.opencleanup.rule;

import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeMethodReference;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Converts unambiguous lambda expressions to equivalent method references.
 *
 * <p>The rule intentionally does not resolve bindings. It therefore requires
 * an explicit receiver type for unbound instance references and only uses
 * {@code this} or {@code super} for bound references. Lambda arguments must be
 * passed directly, in order, without additional expressions.</p>
 */
public final class LambdaMethodReferenceRule implements CleanupRule {

    /** The stable identifier for lambda-to-method-reference conversion. */
    public static final String ID = "functional-interfaces.lambda-method-reference";

    /**
     * Returns the stable identifier for this cleanup rule.
     *
     * @return the lambda method-reference rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for lambda expressions that have a safe method-reference form.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one lambda is replaced
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Replaces one eligible lambda expression with a method reference.
             *
             * @param node the visited lambda expression
             * @return {@code false} when replaced, otherwise {@code true}
             */
            @Override
            public boolean visit(LambdaExpression node) {
                MethodReference reference = methodReference(node);
                if (reference == null) {
                    return true;
                }
                rewrite.replace(node, reference, null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Finds a method-reference equivalent for a lambda's body.
     *
     * @param lambda the lambda to inspect
     * @return the equivalent method reference, or {@code null} when unsupported
     */
    private MethodReference methodReference(LambdaExpression lambda) {
        Expression body = expressionBody(lambda.getBody());
        if (body instanceof MethodInvocation invocation) {
            return invocationReference(lambda, invocation);
        }
        if (body instanceof SuperMethodInvocation invocation) {
            return superInvocationReference(lambda, invocation);
        }
        if (body instanceof ClassInstanceCreation creation) {
            return creationReference(lambda, creation);
        }
        return null;
    }

    /**
     * Extracts an expression body from either an expression lambda or a single return statement.
     *
     * @param body the lambda body node
     * @return the expression body, or {@code null} when the body has other statements
     */
    private Expression expressionBody(ASTNode body) {
        if (body instanceof Expression expression) {
            return expression;
        }
        if (!(body instanceof Block block) || block.statements().size() != 1) {
            return null;
        }
        Statement statement = (Statement) block.statements().get(0);
        return statement instanceof ReturnStatement returnStatement
                ? returnStatement.getExpression() : null;
    }

    /**
     * Builds a method reference for a method invocation body when its arguments map exactly.
     *
     * @param lambda the source lambda
     * @param invocation the invocation in the lambda body
     * @return the equivalent method reference, or {@code null} when the mapping is ambiguous
     */
    private MethodReference invocationReference(
            LambdaExpression lambda,
            MethodInvocation invocation) {
        if (!invocation.typeArguments().isEmpty()) {
            return null;
        }
        Expression receiver = invocation.getExpression();
        if (receiver instanceof ThisExpression thisExpression
                && thisExpression.getQualifier() == null
                && argumentsMatch(lambda.parameters(), invocation.arguments(), 0)) {
            return expressionReference(lambda.getAST(), thisExpression, invocation.getName());
        }
        if (receiver instanceof SimpleName receiverName) {
            MethodReference unbound = typedReceiverReference(lambda, receiverName, invocation);
            if (unbound != null) {
                return unbound;
            }
        }
        if (receiver == null
                && !inStaticContext(lambda)
                && argumentsMatch(lambda.parameters(), invocation.arguments(), 0)) {
            return expressionReference(lambda.getAST(), lambda.getAST().newThisExpression(), invocation.getName());
        }
        return null;
    }

    /**
     * Builds an unbound instance method reference from an explicitly typed first parameter.
     *
     * @param lambda the source lambda
     * @param receiverName the invocation receiver name
     * @param invocation the invocation in the lambda body
     * @return the type method reference, or {@code null} when the receiver is not explicit and typed
     */
    private MethodReference typedReceiverReference(
            LambdaExpression lambda,
            SimpleName receiverName,
            MethodInvocation invocation) {
        List<?> parameters = lambda.parameters();
        if (parameters.isEmpty()
                || parameters.size() != invocation.arguments().size() + 1
                || !allExplicitParameters(parameters)
                || !parameterMatches(parameters.get(0), receiverName)
                || !argumentsMatch(parameters, invocation.arguments(), 1)) {
            return null;
        }
        SingleVariableDeclaration receiver = (SingleVariableDeclaration) parameters.get(0);
        if (!receiver.modifiers().isEmpty()
                || receiver.isVarargs()
                || !receiver.extraDimensions().isEmpty()
                || receiver.getType() == null) {
            return null;
        }
        TypeMethodReference reference = lambda.getAST().newTypeMethodReference();
        reference.setType((Type) ASTNode.copySubtree(lambda.getAST(), receiver.getType()));
        reference.setName((SimpleName) ASTNode.copySubtree(lambda.getAST(), invocation.getName()));
        return reference;
    }

    /**
     * Builds a method reference for a {@code super.method(arguments)} body.
     *
     * @param lambda the source lambda
     * @param invocation the super method invocation
     * @return the equivalent super method reference, or {@code null} when arguments do not map
     */
    private MethodReference superInvocationReference(
            LambdaExpression lambda,
            SuperMethodInvocation invocation) {
        if (!invocation.typeArguments().isEmpty()
                || !argumentsMatch(lambda.parameters(), invocation.arguments(), 0)) {
            return null;
        }
        SuperMethodReference reference = lambda.getAST().newSuperMethodReference();
        if (invocation.getQualifier() != null) {
            reference.setQualifier((org.eclipse.jdt.core.dom.Name) ASTNode.copySubtree(
                    lambda.getAST(), invocation.getQualifier()));
        }
        reference.setName((SimpleName) ASTNode.copySubtree(lambda.getAST(), invocation.getName()));
        return reference;
    }

    /**
     * Builds a constructor reference when every constructor argument is a lambda parameter.
     *
     * @param lambda the source lambda
     * @param creation the constructor invocation in the lambda body
     * @return the equivalent creation reference, or {@code null} when unsupported
     */
    private MethodReference creationReference(
            LambdaExpression lambda,
            ClassInstanceCreation creation) {
        if (creation.getAnonymousClassDeclaration() != null
                || creation.getExpression() != null
                || !argumentsMatch(lambda.parameters(), creation.arguments(), 0)) {
            return null;
        }
        org.eclipse.jdt.core.dom.CreationReference reference = lambda.getAST().newCreationReference();
        reference.setType((Type) ASTNode.copySubtree(lambda.getAST(), creation.getType()));
        return reference;
    }

    /**
     * Checks whether all lambda parameters are explicitly declared.
     *
     * @param parameters the lambda parameter nodes
     * @return {@code true} when every parameter is a single-variable declaration
     */
    private boolean allExplicitParameters(List<?> parameters) {
        return parameters.stream().allMatch(SingleVariableDeclaration.class::isInstance);
    }

    /**
     * Checks whether a parameter node has the requested name.
     *
     * @param parameter the lambda parameter node
     * @param name the name to compare
     * @return {@code true} when the parameter name matches
     */
    private boolean parameterMatches(Object parameter, SimpleName name) {
        if (parameter instanceof SingleVariableDeclaration variable) {
            return variable.getName().getIdentifier().equals(name.getIdentifier());
        }
        return parameter instanceof VariableDeclarationFragment fragment
                && fragment.getName().getIdentifier().equals(name.getIdentifier());
    }

    /**
     * Checks whether invocation arguments are direct references to lambda parameters in order.
     *
     * @param parameters the lambda parameters
     * @param arguments the invocation arguments
     * @param parameterOffset index of the first parameter represented by the arguments
     * @return {@code true} when every argument maps directly and in order
     */
    private boolean argumentsMatch(List<?> parameters, List<?> arguments, int parameterOffset) {
        if (arguments.size() != parameters.size() - parameterOffset) {
            return false;
        }
        for (int index = 0; index < arguments.size(); index++) {
            if (!(arguments.get(index) instanceof SimpleName argument)
                    || !parameterMatches(parameters.get(index + parameterOffset), argument)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates a bound expression method reference using copied AST children.
     *
     * @param ast the AST receiving the new method reference
     * @param expression the receiver expression
     * @param name the invoked method name
     * @return the new expression method reference
     */
    private ExpressionMethodReference expressionReference(
            AST ast,
            Expression expression,
            SimpleName name) {
        ExpressionMethodReference reference = ast.newExpressionMethodReference();
        reference.setExpression((Expression) ASTNode.copySubtree(ast, expression));
        reference.setName((SimpleName) ASTNode.copySubtree(ast, name));
        return reference;
    }

    /**
     * Checks whether a lambda is nested in a static declaration where {@code this} is unavailable.
     *
     * @param lambda the lambda to inspect
     * @return {@code true} when the nearest enclosing declaration is static
     */
    private boolean inStaticContext(LambdaExpression lambda) {
        ASTNode current = lambda.getParent();
        while (current != null) {
            if (current instanceof MethodDeclaration method) {
                return Modifier.isStatic(method.getModifiers());
            }
            if (current instanceof Initializer initializer) {
                return Modifier.isStatic(initializer.getModifiers());
            }
            if (current instanceof FieldDeclaration field) {
                return Modifier.isStatic(field.getModifiers());
            }
            if (current instanceof AbstractTypeDeclaration || current instanceof AnonymousClassDeclaration) {
                return false;
            }
            current = current.getParent();
        }
        return true;
    }
}
