package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Replaces a class literal's {@code isInstance} call with an {@code instanceof} expression.
 *
 * <p>Only class literals with reference-compatible, syntax-identifiable arguments are changed.
 * Primitive literals, primitive variables, unresolved method results, and generic class literal
 * forms are retained because the original call can accept boxed values while {@code instanceof}
 * requires a reference operand.</p>
 */
public final class InstanceofRule implements CleanupRule {

    /** The stable identifier for this cleanup rule. */
    public static final String ID = "expressions.instanceof";

    /**
     * Creates an instanceof cleanup rule.
     */
    public InstanceofRule() {
    }

    /**
     * Returns the stable identifier for instanceof cleanup.
     *
     * @return the instanceof rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for eligible {@code Class.isInstance} method calls.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one call is replaced
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Replaces one eligible class-literal instance check.
             *
             * @param node the visited method invocation
             * @return {@code false} after replacement, otherwise {@code true}
             */
            @Override
            public boolean visit(MethodInvocation node) {
                if (!(node.getExpression() instanceof TypeLiteral typeLiteral)
                        || !"isInstance".equals(node.getName().getIdentifier())
                        || !node.typeArguments().isEmpty()
                        || node.arguments().size() != 1
                        || !isReferenceType(typeLiteral.getType())
                        || !(node.arguments().get(0) instanceof Expression argument)
                        || !isReferenceCompatible(argument)) {
                    return true;
                }

                org.eclipse.jdt.core.dom.InstanceofExpression replacement =
                        node.getAST().newInstanceofExpression();
                replacement.setLeftOperand((Expression) rewrite.createMoveTarget(argument));
                replacement.setRightOperand((Type) rewrite.createMoveTarget(typeLiteral.getType()));
                rewrite.replace(node, replacement, null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Checks whether a class literal names a reference type.
     *
     * @param type the type used by the class literal
     * @return {@code true} when the type is valid on the right side of instanceof
     */
    private boolean isReferenceType(Type type) {
        return !(type instanceof PrimitiveType)
                && !(type instanceof org.eclipse.jdt.core.dom.ParameterizedType);
    }

    /**
     * Checks whether an argument can be used as the left operand of instanceof.
     *
     * @param expression the argument passed to isInstance
     * @return {@code true} when the argument is known or conservatively assumed to be a reference
     */
    private boolean isReferenceCompatible(Expression expression) {
        if (expression instanceof ParenthesizedExpression parenthesized) {
            return isReferenceCompatible(parenthesized.getExpression());
        }
        ITypeBinding binding = expression.resolveTypeBinding();
        if (binding != null) {
            return !binding.isPrimitive();
        }
        if (expression instanceof SimpleName simpleName) {
            return !isDeclaredPrimitive(simpleName);
        }
        return expression instanceof StringLiteral
                || expression instanceof TextBlock
                || expression instanceof NullLiteral
                || expression instanceof ClassInstanceCreation
                || expression instanceof ArrayCreation
                || expression instanceof ThisExpression
                || expression instanceof CastExpression cast
                    && isReferenceType(cast.getType());
    }

    /**
     * Checks whether an unresolved simple name is declared with a primitive type in its scope.
     *
     * @param name the simple name used as the isInstance argument
     * @return {@code true} when a matching primitive declaration is found
     */
    private boolean isDeclaredPrimitive(SimpleName name) {
        MethodDeclaration method = findEnclosingMethod(name);
        if (method != null) {
            PrimitiveNameFinder finder = new PrimitiveNameFinder(name.getIdentifier());
            method.accept(finder);
            return finder.found;
        }
        return false;
    }

    /**
     * Finds the executable method containing a name.
     *
     * @param name the name whose enclosing method is needed
     * @return the enclosing method, or {@code null} when the name is outside a method
     */
    private MethodDeclaration findEnclosingMethod(SimpleName name) {
        ASTNode current = name.getParent();
        while (current != null) {
            if (current instanceof MethodDeclaration method) {
                return method;
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * Finds primitive declarations matching one unresolved simple name.
     *
     * <p>The visitor is intentionally limited to method parameters and local declarations. A
     * matching primitive declaration anywhere in the method causes the rewrite to be skipped.</p>
     */
    private static final class PrimitiveNameFinder extends ASTVisitor {

        /** The unresolved variable name being searched. */
        private final String identifier;

        /** Whether a matching primitive declaration has been found. */
        private boolean found;

        /**
         * Creates a primitive declaration finder.
         *
         * @param identifier the variable name to find
         */
        private PrimitiveNameFinder(String identifier) {
            this.identifier = identifier;
        }

        /**
         * Checks one local variable declaration for a matching primitive name.
         *
         * @param node the visited local declaration
         * @return {@code false} after a match, otherwise {@code true}
         */
        @Override
        public boolean visit(VariableDeclarationStatement node) {
            if (node.getType() instanceof PrimitiveType) {
                for (Object value : node.fragments()) {
                    VariableDeclarationFragment fragment = (VariableDeclarationFragment) value;
                    if (identifier.equals(fragment.getName().getIdentifier())) {
                        found = true;
                        return false;
                    }
                }
            }
            return true;
        }

        /**
         * Checks one parameter declaration for a matching primitive name.
         *
         * @param node the visited parameter declaration
         * @return {@code false} after a match, otherwise {@code true}
         */
        @Override
        public boolean visit(SingleVariableDeclaration node) {
            if (node.getType() instanceof PrimitiveType
                    && identifier.equals(node.getName().getIdentifier())) {
                found = true;
                return false;
            }
            return true;
        }
    }
}
