package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Converts simple index-based array and list loops into enhanced for loops.
 *
 * <p>The supported syntax-only shape starts an {@code int} index at zero, compares it with a
 * simple-name array's {@code length} or a simple-name list's {@code size()}, increments it once,
 * and uses the indexed element exactly once in the body. The generated loop uses {@code var} so
 * that type bindings are not required. Ambiguous or potentially mutating shapes are preserved.
 */
public final class EnhancedForRule implements CleanupRule {

    /** The stable identifier for enhanced-for loop conversion. */
    public static final String ID = "loops.enhanced-for";

    private static final String ELEMENT_NAME = "element";

    /**
     * Returns the stable identifier for enhanced-for loop conversion.
     *
     * @return the enhanced-for rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for eligible classic index-based loops.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one loop is scheduled for conversion
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Converts one eligible classic loop and skips its moved body.
             *
             * @param node the visited classic for statement
             * @return {@code false} after conversion, otherwise {@code true}
             */
            @Override
            public boolean visit(ForStatement node) {
                LoopShape shape = LoopShape.from(node);
                if (shape == null) {
                    return true;
                }

                Expression indexedElement = findIndexedElement(node.getBody(), shape);
                if (indexedElement == null) {
                    return true;
                }

                EnhancedForStatement replacement = createReplacement(node, shape, rewrite);
                rewrite.replace(indexedElement, node.getAST().newSimpleName(ELEMENT_NAME), null);
                rewrite.replace(node, replacement, null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Finds the sole read-only indexed element use in a loop body.
     *
     * @param body the classic loop body
     * @param shape the parsed loop shape
     * @return the indexed element expression, or {@code null} when the body is ambiguous
     */
    private Expression findIndexedElement(Statement body, LoopShape shape) {
        if (body instanceof Block block && block.statements().size() != 1) {
            return null;
        }

        IndexedElementFinder finder = new IndexedElementFinder(shape);
        body.accept(finder);
        return finder.result();
    }

    /**
     * Creates an enhanced-for replacement while moving the original loop body into it.
     *
     * @param node the classic for statement being replaced
     * @param shape the parsed loop shape
     * @param rewrite the rewrite collecting source edits
     * @return the enhanced-for replacement statement
     */
    private EnhancedForStatement createReplacement(
            ForStatement node,
            LoopShape shape,
            ASTRewrite rewrite) {
        AST ast = node.getAST();
        EnhancedForStatement replacement = ast.newEnhancedForStatement();
        SingleVariableDeclaration parameter = ast.newSingleVariableDeclaration();
        parameter.setType(ast.newSimpleType(ast.newSimpleName("var")));
        parameter.setName(ast.newSimpleName(ELEMENT_NAME));
        replacement.setParameter(parameter);
        replacement.setExpression(ast.newSimpleName(shape.collectionName()));
        replacement.setBody((Statement) rewrite.createMoveTarget(node.getBody()));
        return replacement;
    }

    /**
     * Describes the syntax required to convert one classic index-based loop.
     *
     * @param indexName the loop index variable name
     * @param collectionName the array or list variable name
     * @param listAccess whether the collection uses {@code get(index)} access
     */
    private record LoopShape(String indexName, String collectionName, boolean listAccess) {

        /**
         * Extracts a supported loop shape from a classic for statement.
         *
         * @param node the classic for statement to inspect
         * @return the supported loop shape, or {@code null} when the syntax is unsupported
         */
        private static LoopShape from(ForStatement node) {
            if (node.initializers().size() != 1
                    || !(node.initializers().get(0) instanceof VariableDeclarationExpression initializer)
                    || initializer.fragments().size() != 1
                    || !isIntType(initializer)
                    || node.getExpression() == null
                    || node.updaters().size() != 1
                    || node.getBody() == null) {
                return null;
            }

            VariableDeclarationFragment fragment =
                    (VariableDeclarationFragment) initializer.fragments().get(0);
            if (!(fragment.getInitializer() instanceof NumberLiteral literal)
                    || !"0".equals(literal.getToken())
                    || fragment.getExtraDimensions() != 0) {
                return null;
            }

            String indexName = fragment.getName().getIdentifier();
            if (!isIncrement((Expression) node.updaters().get(0), indexName)) {
                return null;
            }

            return fromCondition(node.getExpression(), indexName);
        }

        /**
         * Checks that the loop index is declared with the primitive {@code int} type.
         *
         * @param initializer the loop initializer
         * @return {@code true} when the initializer declares exactly primitive {@code int}
         */
        private static boolean isIntType(VariableDeclarationExpression initializer) {
            return initializer.getType().isPrimitiveType()
                    && "int".equals(initializer.getType().toString());
        }

        /**
         * Checks whether an updater increments the expected index by one.
         *
         * @param updater the loop updater expression
         * @param indexName the expected index variable name
         * @return {@code true} for prefix or postfix increment of that index
         */
        private static boolean isIncrement(Expression updater, String indexName) {
            if (updater instanceof PostfixExpression postfix) {
                return postfix.getOperator() == PostfixExpression.Operator.INCREMENT
                        && isSimpleName(postfix.getOperand(), indexName);
            }
            if (updater instanceof PrefixExpression prefix) {
                return prefix.getOperator() == PrefixExpression.Operator.INCREMENT
                        && isSimpleName(prefix.getOperand(), indexName);
            }
            return false;
        }

        /**
         * Extracts the collection and access style from the loop bound.
         *
         * @param expression the loop condition
         * @param indexName the loop index variable name
         * @return the supported loop shape, or {@code null} when the condition is unsupported
         */
        private static LoopShape fromCondition(Expression expression, String indexName) {
            if (!(expression instanceof InfixExpression condition)
                    || condition.getOperator() != InfixExpression.Operator.LESS
                    || !condition.extendedOperands().isEmpty()
                    || !isSimpleName(condition.getLeftOperand(), indexName)) {
                return null;
            }

            Expression bound = condition.getRightOperand();
            if (bound instanceof QualifiedName qualifiedName
                    && qualifiedName.getQualifier() instanceof SimpleName collection
                    && "length".equals(qualifiedName.getName().getIdentifier())) {
                return new LoopShape(indexName, collection.getIdentifier(), false);
            }
            if (bound instanceof MethodInvocation methodInvocation
                    && methodInvocation.getExpression() instanceof SimpleName collection
                    && "size".equals(methodInvocation.getName().getIdentifier())
                    && methodInvocation.arguments().isEmpty()
                    && methodInvocation.typeArguments().isEmpty()) {
                return new LoopShape(indexName, collection.getIdentifier(), true);
            }
            return null;
        }

        /**
         * Checks whether an expression is a simple name with the expected identifier.
         *
         * @param expression the expression to inspect
         * @param expectedName the expected simple-name identifier
         * @return {@code true} when the expression matches the expected simple name
         */
        private static boolean isSimpleName(Expression expression, String expectedName) {
            return expression instanceof SimpleName simpleName
                    && expectedName.equals(simpleName.getIdentifier());
        }
    }

    /**
     * Finds and validates one indexed element use in a loop body.
     */
    private static final class IndexedElementFinder extends ASTVisitor {

        private final LoopShape shape;
        private Expression result;
        private int indexUses;
        private boolean invalid;
        private boolean elementNameUsed;

        /**
         * Creates a finder for one parsed loop shape.
         *
         * @param shape the loop shape whose indexed use is required
         */
        private IndexedElementFinder(LoopShape shape) {
            this.shape = shape;
        }

        /**
         * Visits each simple name to reject additional index uses and name collisions.
         *
         * @param node the visited simple name
         * @return {@code true} to continue visiting child nodes
         */
        @Override
        public boolean visit(SimpleName node) {
            if (shape.indexName().equals(node.getIdentifier())) {
                indexUses++;
            }
            if (ELEMENT_NAME.equals(node.getIdentifier())) {
                elementNameUsed = true;
            }
            return true;
        }

        /**
         * Visits array access expressions and records one eligible indexed element.
         *
         * @param node the visited array access
         * @return {@code true} to continue visiting nested expressions
         */
        @Override
        public boolean visit(ArrayAccess node) {
            if (!shape.listAccess()
                    && node.getArray() instanceof SimpleName collection
                    && node.getIndex() instanceof SimpleName index
                    && shape.collectionName().equals(collection.getIdentifier())
                    && shape.indexName().equals(index.getIdentifier())) {
                record(node);
            }
            return true;
        }

        /**
         * Visits method invocations and records one eligible list element access.
         *
         * @param node the visited method invocation
         * @return {@code true} to continue visiting nested expressions
         */
        @Override
        public boolean visit(MethodInvocation node) {
            if (shape.listAccess()
                    && node.getExpression() instanceof SimpleName collection
                    && "get".equals(node.getName().getIdentifier())
                    && node.arguments().size() == 1
                    && node.typeArguments().isEmpty()
                    && node.arguments().get(0) instanceof SimpleName index
                    && shape.collectionName().equals(collection.getIdentifier())
                    && shape.indexName().equals(index.getIdentifier())) {
                record(node);
            }
            return true;
        }

        /**
         * Returns the sole validated indexed element expression.
         *
         * @return the indexed element expression, or {@code null} when validation failed
         */
        private Expression result() {
            return !invalid && result != null && indexUses == 1 && !elementNameUsed
                    && isReadOnly(result) ? result : null;
        }

        /**
         * Records an indexed expression and marks the body invalid if a second one exists.
         *
         * @param expression the indexed element expression
         */
        private void record(Expression expression) {
            if (result != null) {
                invalid = true;
                return;
            }
            result = expression;
        }

        /**
         * Checks that an indexed expression is not used as an assignment or increment target.
         *
         * @param expression the indexed element expression
         * @return {@code true} when the expression is read-only in its immediate syntax context
         */
        private boolean isReadOnly(Expression expression) {
            ASTNode parent = expression.getParent();
            if (parent instanceof Assignment assignment && assignment.getLeftHandSide() == expression) {
                return false;
            }
            if (parent instanceof PostfixExpression postfix && postfix.getOperand() == expression) {
                return false;
            }
            return !(parent instanceof PrefixExpression prefix
                    && prefix.getOperand() == expression
                    && (prefix.getOperator() == PrefixExpression.Operator.INCREMENT
                    || prefix.getOperator() == PrefixExpression.Operator.DECREMENT));
        }
    }
}
