package io.github.lhozdroid.opencleanup.rule;

import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Converts recognized functional-interface anonymous classes and lambdas in either direction.
 *
 * <p>The syntax-only implementation recognizes standard Java functional
 * interfaces and requires an explicit {@code @Override} implementation for
 * anonymous classes. Lambda-to-anonymous conversion is limited to variable
 * declarations whose target type is one of the recognized interfaces.</p>
 */
public final class FunctionalInterfacesConvertRule implements CleanupRule {

    /** The stable identifier for functional-interface conversion. */
    public static final String ID = "functional-interfaces.convert";

    /**
     * Returns the stable identifier for functional-interface conversion.
     *
     * @return the functional-interface conversion rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Converts anonymous classes to lambdas or lambdas to anonymous classes according to the option.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one functional implementation is converted
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        String mode = configuration.optionValue(ID);
        if ("lambda".equalsIgnoreCase(mode)) {
            return convertAnonymousClasses(compilationUnit, rewrite);
        }
        if ("anonymous".equalsIgnoreCase(mode)) {
            return convertLambdas(compilationUnit, rewrite);
        }
        return false;
    }

    /**
     * Converts recognized functional-interface anonymous classes to lambdas.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @return {@code true} when at least one anonymous class is converted
     */
    private boolean convertAnonymousClasses(CompilationUnit compilationUnit, ASTRewrite rewrite) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Converts one recognized anonymous functional implementation.
             *
             * @param node the visited class instance creation
             * @return {@code false} after conversion, otherwise {@code true}
             */
            @Override
            public boolean visit(ClassInstanceCreation node) {
                if (node.getAnonymousClassDeclaration() == null
                        || !node.arguments().isEmpty()
                        || FunctionalShape.from(node.getType()) == null) {
                    return true;
                }

                MethodDeclaration method = soleOverriddenMethod(node);
                if (method == null || method.getBody() == null || containsThisOrSuper(method.getBody())) {
                    return true;
                }

                LambdaExpression lambda = toLambda(node.getAST(), method);
                rewrite.replace(node, lambda, null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Converts recognized target-typed lambdas in local variable declarations.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @return {@code true} when at least one lambda is converted
     */
    private boolean convertLambdas(CompilationUnit compilationUnit, ASTRewrite rewrite) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Converts one lambda with a recognized variable-declaration target type.
             *
             * @param node the visited lambda expression
             * @return {@code true} to continue visiting nested lambdas
             */
            @Override
            public boolean visit(LambdaExpression node) {
                if (!(node.getParent() instanceof VariableDeclarationFragment fragment)
                        || fragment.getInitializer() != node
                        || !(fragment.getParent() instanceof VariableDeclarationStatement declaration)
                        || declaration.fragments().size() != 1) {
                    return true;
                }

                FunctionalShape shape = FunctionalShape.from(declaration.getType());
                if (shape == null || !shape.supports(node)) {
                    return true;
                }

                rewrite.replace(node, toAnonymousClass(node.getAST(), declaration.getType(), node, shape), null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Finds the single explicitly overridden method in an anonymous class.
     *
     * @param creation the anonymous functional implementation
     * @return the sole overridden method, or {@code null} when the body is ambiguous
     */
    private MethodDeclaration soleOverriddenMethod(ClassInstanceCreation creation) {
        if (creation.getAnonymousClassDeclaration().bodyDeclarations().size() != 1
                || !(creation.getAnonymousClassDeclaration().bodyDeclarations().get(0)
                        instanceof MethodDeclaration method)
                || !hasOverrideAnnotation(method)
                || !method.typeParameters().isEmpty()
                || method.isConstructor()) {
            return null;
        }
        for (Object parameter : method.parameters()) {
            SingleVariableDeclaration variable = (SingleVariableDeclaration) parameter;
            if (variable.isVarargs() || !variable.extraDimensions().isEmpty()) {
                return null;
            }
        }
        return method;
    }

    /**
     * Checks whether a method carries an {@code @Override} annotation.
     *
     * @param method the method to inspect
     * @return {@code true} when an override marker is present
     */
    private boolean hasOverrideAnnotation(MethodDeclaration method) {
        for (Object modifier : method.modifiers()) {
            if (modifier instanceof Annotation annotation
                    && "Override".equals(annotation.getTypeName().getFullyQualifiedName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Avoids conversion when anonymous-class receiver semantics are observable.
     *
     * @param body the anonymous method body
     * @return {@code true} when the body mentions {@code this} or {@code super}
     */
    private boolean containsThisOrSuper(Block body) {
        String source = body.toString();
        return source.matches("(?s).*\\b(this|super)\\b.*");
    }

    /**
     * Builds a lambda with copied parameters and method body.
     *
     * @param ast the AST receiving the lambda
     * @param method the anonymous implementation method
     * @return the generated lambda expression
     */
    private LambdaExpression toLambda(AST ast, MethodDeclaration method) {
        LambdaExpression lambda = ast.newLambdaExpression();
        lambda.setParentheses(true);
        for (Object parameter : method.parameters()) {
            lambda.parameters().add(ASTNode.copySubtree(ast, (ASTNode) parameter));
        }
        lambda.setBody(ASTNode.copySubtree(ast, method.getBody()));
        return lambda;
    }

    /**
     * Builds an anonymous implementation for a recognized target-typed lambda.
     *
     * @param ast the AST receiving the anonymous class
     * @param type the functional interface type
     * @param lambda the source lambda
     * @param shape the recognized functional method shape
     * @return the generated anonymous class creation
     */
    private ClassInstanceCreation toAnonymousClass(
            AST ast,
            Type type,
            LambdaExpression lambda,
            FunctionalShape shape) {
        ClassInstanceCreation creation = ast.newClassInstanceCreation();
        creation.setType((Type) ASTNode.copySubtree(ast, type));

        MethodDeclaration method = ast.newMethodDeclaration();
        method.modifiers().add(ast.newMarkerAnnotation());
        MarkerAnnotation override = (MarkerAnnotation) method.modifiers().get(0);
        override.setTypeName(ast.newSimpleName("Override"));
        method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
        method.setName(ast.newSimpleName(shape.methodName()));
        method.setReturnType2(shape.returnType(ast));
        shape.addParameters(ast, lambda, method);
        method.setBody(lambdaBody(ast, lambda, shape));

        org.eclipse.jdt.core.dom.AnonymousClassDeclaration anonymous = ast.newAnonymousClassDeclaration();
        anonymous.bodyDeclarations().add(method);
        creation.setAnonymousClassDeclaration(anonymous);
        return creation;
    }

    /**
     * Converts a lambda body to the block required by an anonymous method.
     *
     * @param ast the AST receiving the body
     * @param lambda the source lambda
     * @param shape the recognized functional method shape
     * @return the generated method body
     */
    private Block lambdaBody(AST ast, LambdaExpression lambda, FunctionalShape shape) {
        if (lambda.getBody() instanceof Block block) {
            return (Block) ASTNode.copySubtree(ast, block);
        }

        Block body = ast.newBlock();
        Expression expression = (Expression) lambda.getBody();
        if (shape.voidReturn()) {
            ExpressionStatement statement = ast.newExpressionStatement(
                    (Expression) ASTNode.copySubtree(ast, expression));
            body.statements().add(statement);
        } else {
            ReturnStatement statement = ast.newReturnStatement();
            statement.setExpression((Expression) ASTNode.copySubtree(ast, expression));
            body.statements().add(statement);
        }
        return body;
    }

    /**
     * Describes one recognized standard functional interface.
     *
     * @param methodName the abstract method name
     * @param returnTypeArgument the generic return-type argument index, or {@code -1}
     * @param parameterTypeArguments the generic parameter-type argument indexes
     * @param primitiveReturn the primitive return type name, or {@code null}
     */
    private record FunctionalShape(
            String methodName,
            int returnTypeArgument,
            List<Integer> parameterTypeArguments,
            String primitiveReturn,
            List<Type> typeArguments) {

        /**
         * Resolves a standard functional interface from its source type.
         *
         * @param type the functional interface type
         * @return the functional shape, or {@code null} when it is not recognized
         */
        private static FunctionalShape from(Type type) {
            String text = type.toString();
            int genericStart = text.indexOf('<');
            String base = genericStart < 0 ? text : text.substring(0, genericStart);
            int packageSeparator = base.lastIndexOf('.');
            String simpleBase = packageSeparator < 0 ? base : base.substring(packageSeparator + 1);
            List<Type> typeArguments = genericArguments(type);
            int argumentCount = typeArguments.size();
            return switch (simpleBase) {
                case "Runnable" -> argumentCount == 0
                        ? new FunctionalShape("run", -1, List.of(), "void", typeArguments) : null;
                case "Consumer" -> argumentCount == 1
                        ? new FunctionalShape("accept", -1, List.of(0), "void", typeArguments) : null;
                case "Predicate" -> argumentCount == 1
                        ? new FunctionalShape("test", -1, List.of(0), "boolean", typeArguments) : null;
                case "Function" -> argumentCount == 2
                        ? new FunctionalShape("apply", 1, List.of(0), null, typeArguments) : null;
                case "Supplier" -> argumentCount == 1
                        ? new FunctionalShape("get", 0, List.of(), null, typeArguments) : null;
                case "UnaryOperator" -> argumentCount == 1
                        ? new FunctionalShape("apply", 0, List.of(0), null, typeArguments) : null;
                case "BinaryOperator" -> argumentCount == 1
                        ? new FunctionalShape("apply", 0, List.of(0, 0), null, typeArguments) : null;
                case "Comparator" -> argumentCount == 1
                        ? new FunctionalShape("compare", -1, List.of(0, 0), "int", typeArguments) : null;
                default -> null;
            };
        }

        /**
         * Extracts explicit generic arguments from a functional interface type.
         *
         * @param type the interface type
         * @return the generic argument types
         */
        private static List<Type> genericArguments(Type type) {
            if (!(type instanceof ParameterizedType parameterized)) {
                return List.of();
            }
            List<Type> arguments = new java.util.ArrayList<>();
            for (Object argument : parameterized.typeArguments()) {
                Type genericType = (Type) argument;
                if (genericType.toString().contains("?")) {
                    return List.of();
                }
                arguments.add(genericType);
            }
            return List.copyOf(arguments);
        }

        /**
         * Checks whether a lambda has the parameter count required by this shape.
         *
         * @param lambda the lambda to inspect
         * @return {@code true} when the lambda can be represented by this method
         */
        private boolean supports(LambdaExpression lambda) {
            return lambda.parameters().size() == parameterTypeArguments.size()
                    && (lambda.getBody() instanceof Block || lambda.getBody() instanceof Expression);
        }

        /**
         * Creates the method return type for an anonymous implementation.
         *
         * @param ast the AST receiving the type
         * @return the generated return type
         */
        private Type returnType(AST ast) {
            if ("void".equals(primitiveReturn)) {
                return ast.newPrimitiveType(PrimitiveType.VOID);
            }
            if ("boolean".equals(primitiveReturn)) {
                return ast.newPrimitiveType(PrimitiveType.BOOLEAN);
            }
            if ("int".equals(primitiveReturn)) {
                return ast.newPrimitiveType(PrimitiveType.INT);
            }
            return (Type) ASTNode.copySubtree(ast, typeArguments.get(returnTypeArgument));
        }

        /**
         * Adds copied lambda parameters to an anonymous method.
         *
         * @param ast the AST receiving parameters
         * @param lambda the source lambda
         * @param method the generated method
         */
        private void addParameters(AST ast, LambdaExpression lambda, MethodDeclaration method) {
            for (Object value : lambda.parameters()) {
                VariableDeclaration parameter = (VariableDeclaration) value;
                if (parameter instanceof SingleVariableDeclaration single) {
                    method.parameters().add(ASTNode.copySubtree(ast, single));
                } else {
                    SingleVariableDeclaration generated = ast.newSingleVariableDeclaration();
                    generated.setName((SimpleName) ASTNode.copySubtree(ast, parameter.getName()));
                    int parameterIndex = method.parameters().size();
                    int typeArgumentIndex = parameterTypeArguments.get(parameterIndex);
                    generated.setType((Type) ASTNode.copySubtree(
                            ast, typeArguments.get(typeArgumentIndex)));
                    method.parameters().add(generated);
                }
            }
        }

        /**
         * Checks whether this functional shape returns void.
         *
         * @return {@code true} when the abstract method has a void return type
         */
        private boolean voidReturn() {
            return "void".equals(primitiveReturn);
        }
    }
}
