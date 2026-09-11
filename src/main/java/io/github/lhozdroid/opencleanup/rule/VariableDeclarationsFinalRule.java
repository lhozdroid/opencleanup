package io.github.lhozdroid.opencleanup.rule;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Adds {@code final} to selected declarations that are not written after initialization.
 *
 * <p>The implementation supports private instance fields with initializers, method and
 * constructor parameters, local declarations, enhanced-for variables, catch variables, and
 * variable declarations used by loops or resources. It intentionally skips declarations whose
 * write analysis is ambiguous.</p>
 */
public final class VariableDeclarationsFinalRule implements CleanupRule {

    /** The stable identifier for this cleanup rule. */
    public static final String ID = "variable-declarations.final";

    /** The option selecting private instance fields. */
    public static final String FIELDS = "fields";

    /** The option selecting method and constructor parameters. */
    public static final String PARAMETERS = "parameters";

    /** The option selecting local declarations. */
    public static final String LOCALS = "locals";

    /**
     * Creates a final declaration cleanup rule.
     */
    public VariableDeclarationsFinalRule() {
    }

    /**
     * Returns the stable identifier for final declaration cleanup.
     *
     * @return the final declaration rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records final modifiers for the selected declarations that pass conservative write checks.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one final modifier is scheduled
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        Selection selection = Selection.from(configuration);
        if (!selection.any()) {
            return false;
        }

        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Adds final to one eligible field declaration.
             *
             * @param node the visited field declaration
             * @return {@code true} to continue visiting field initializers
             */
            @Override
            public boolean visit(FieldDeclaration node) {
                if (selection.fields && canAddFinalToField(node)) {
                    addFinal(node, rewrite);
                    changed[0] = true;
                }
                return true;
            }

            /**
             * Adds final to one eligible local variable declaration statement.
             *
             * @param node the visited local declaration statement
             * @return {@code true} to continue visiting nested expressions
             */
            @Override
            public boolean visit(VariableDeclarationStatement node) {
                if (selection.locals && canAddFinalToLocal(node)) {
                    addFinal(node, rewrite);
                    changed[0] = true;
                }
                return true;
            }

            /**
             * Adds final to one eligible declaration expression.
             *
             * @param node the visited declaration expression
             * @return {@code false} because declaration expressions have no declarations below them
             */
            @Override
            public boolean visit(VariableDeclarationExpression node) {
                if (selection.locals && canAddFinalToLocal(node)) {
                    addFinal(node, rewrite);
                    changed[0] = true;
                }
                return false;
            }

            /**
             * Adds final to one eligible method or constructor parameter, or local binding.
             *
             * @param node the visited single-variable declaration
             * @return {@code false} because the declaration has no relevant child declarations
             */
            @Override
            public boolean visit(SingleVariableDeclaration node) {
                if (isMethodParameter(node)) {
                    if (selection.parameters && canAddFinalToParameter(node)) {
                        addFinal(node, rewrite);
                        changed[0] = true;
                    }
                } else if (selection.locals && isLocalSingleVariable(node)
                        && canAddFinalToLocal(node)) {
                    addFinal(node, rewrite);
                    changed[0] = true;
                }
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Checks whether a private instance field declaration can be made final.
     *
     * @param declaration the field declaration under consideration
     * @return {@code true} when every field fragment is initialized and never written
     */
    private boolean canAddFinalToField(FieldDeclaration declaration) {
        int modifiers = declaration.getModifiers();
        if (!Modifier.isPrivate(modifiers)
                || Modifier.isStatic(modifiers)
                || Modifier.isFinal(modifiers)
                || Modifier.isVolatile(modifiers)
                || Modifier.isTransient(modifiers)
                || declaration.fragments().isEmpty()
                || findEnclosingType(declaration) == null) {
            return false;
        }

        Set<String> names = fragmentNames(declaration.fragments());
        for (Object value : declaration.fragments()) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment) value;
            if (fragment.getInitializer() == null) {
                return false;
            }
        }
        return !hasWrite(findEnclosingType(declaration), names, declaration.getStartPosition(), true);
    }

    /**
     * Checks whether a local declaration can be made final.
     *
     * @param declaration the local declaration under consideration
     * @return {@code true} when every fragment is initialized and never written afterward
     */
    private boolean canAddFinalToLocal(ASTNode declaration) {
        Set<String> names = declarationFragmentNames(declaration);
        if (names.isEmpty() || !allFragmentsHaveInitializers(declaration)) {
            return false;
        }
        ASTNode executable = findEnclosingExecutable(declaration);
        return executable != null
                && !hasWrite(executable, names, declaration.getStartPosition(), false);
    }

    /**
     * Checks whether a method or constructor parameter can be made final.
     *
     * @param declaration the parameter under consideration
     * @return {@code true} when the parameter belongs to a concrete method and is never written
     */
    private boolean canAddFinalToParameter(SingleVariableDeclaration declaration) {
        if (Modifier.isFinal(declaration.getModifiers())
                || declaration.getParent() instanceof MethodDeclaration method
                    && method.getBody() == null) {
            return false;
        }
        ASTNode executable = findEnclosingExecutable(declaration);
        return executable != null
                && !hasWrite(executable, Set.of(declaration.getName().getIdentifier()),
                        declaration.getStartPosition(), false);
    }

    /**
     * Adds the final modifier at the end of a declaration's modifier list.
     *
     * @param declaration the declaration receiving final
     * @param rewrite the AST rewrite collecting source edits
     */
    private void addFinal(ASTNode declaration, ASTRewrite rewrite) {
        ListRewrite modifiers;
        if (declaration instanceof FieldDeclaration field) {
            modifiers = rewrite.getListRewrite(field, FieldDeclaration.MODIFIERS2_PROPERTY);
        } else if (declaration instanceof VariableDeclarationStatement statement) {
            modifiers = rewrite.getListRewrite(
                    statement, VariableDeclarationStatement.MODIFIERS2_PROPERTY);
        } else if (declaration instanceof VariableDeclarationExpression expression) {
            modifiers = rewrite.getListRewrite(
                    expression, VariableDeclarationExpression.MODIFIERS2_PROPERTY);
        } else if (declaration instanceof SingleVariableDeclaration variable) {
            modifiers = rewrite.getListRewrite(
                    variable, SingleVariableDeclaration.MODIFIERS2_PROPERTY);
        } else {
            return;
        }
        modifiers.insertLast(
                declaration.getAST().newModifier(Modifier.ModifierKeyword.FINAL_KEYWORD),
                null);
    }

    /**
     * Checks whether every fragment in a declaration has an initializer.
     *
     * @param declaration the declaration under consideration
     * @return {@code true} when all fragments have initializers
     */
    private boolean allFragmentsHaveInitializers(ASTNode declaration) {
        if (declaration instanceof VariableDeclarationStatement statement) {
            return allFragmentsHaveInitializers(statement.fragments());
        }
        if (declaration instanceof VariableDeclarationExpression expression) {
            return allFragmentsHaveInitializers(expression.fragments());
        }
        return declaration instanceof SingleVariableDeclaration;
    }

    /**
     * Checks whether all fragments in a list have initializers.
     *
     * @param fragments the declaration fragments to inspect
     * @return {@code true} when every fragment has an initializer
     */
    private boolean allFragmentsHaveInitializers(java.util.List<?> fragments) {
        if (fragments.isEmpty()) {
            return false;
        }
        for (Object value : fragments) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment) value;
            if (fragment.getInitializer() == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Collects fragment names from a field or local declaration.
     *
     * @param fragments the declaration fragments to inspect
     * @return the declared simple names
     */
    private Set<String> fragmentNames(java.util.List<?> fragments) {
        Set<String> names = new HashSet<>();
        for (Object value : fragments) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment) value;
            names.add(fragment.getName().getIdentifier());
        }
        return names;
    }

    /**
     * Collects fragment names from a supported declaration node.
     *
     * @param declaration the declaration under consideration
     * @return the declared simple names, or an empty set for unsupported nodes
     */
    private Set<String> declarationFragmentNames(ASTNode declaration) {
        if (declaration instanceof VariableDeclarationStatement statement) {
            return fragmentNames(statement.fragments());
        }
        if (declaration instanceof VariableDeclarationExpression expression) {
            return fragmentNames(expression.fragments());
        }
        if (declaration instanceof SingleVariableDeclaration variable) {
            return Set.of(variable.getName().getIdentifier());
        }
        return Set.of();
    }

    /**
     * Checks whether an AST subtree contains a write to one of the selected names.
     *
     * @param root the executable or type subtree to inspect
     * @param names the names whose writes are relevant
     * @param minimumPosition the declaration position used as the write lower bound
     * @param fieldTarget whether field-access writes should also be considered
     * @return {@code true} when a relevant write occurs after the declaration
     */
    private boolean hasWrite(
            ASTNode root,
            Set<String> names,
            int minimumPosition,
            boolean fieldTarget) {
        WriteFinder finder = new WriteFinder(names, minimumPosition, fieldTarget);
        root.accept(finder);
        return finder.found;
    }

    /**
     * Finds the nearest enclosing type for a field declaration.
     *
     * @param declaration the field declaration under consideration
     * @return the enclosing named or anonymous type, or {@code null}
     */
    private ASTNode findEnclosingType(ASTNode declaration) {
        ASTNode current = declaration.getParent();
        while (current != null) {
            if (current instanceof AbstractTypeDeclaration
                    || current instanceof AnonymousClassDeclaration) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * Finds the nearest executable scope for a declaration.
     *
     * @param declaration the declaration under consideration
     * @return the enclosing method, initializer, lambda, or anonymous class
     */
    private ASTNode findEnclosingExecutable(ASTNode declaration) {
        ASTNode current = declaration.getParent();
        while (current != null) {
            if (current instanceof MethodDeclaration
                    || current instanceof Initializer
                    || current instanceof LambdaExpression
                    || current instanceof AnonymousClassDeclaration) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * Checks whether a single-variable declaration is a method or constructor parameter.
     *
     * @param declaration the single-variable declaration under consideration
     * @return {@code true} when the declaration belongs directly to a method declaration
     */
    private boolean isMethodParameter(SingleVariableDeclaration declaration) {
        return declaration.getParent() instanceof MethodDeclaration;
    }

    /**
     * Checks whether a single-variable declaration is a supported local binding.
     *
     * @param declaration the single-variable declaration under consideration
     * @return {@code true} for enhanced-for and catch variables
     */
    private boolean isLocalSingleVariable(SingleVariableDeclaration declaration) {
        return declaration.getParent() instanceof org.eclipse.jdt.core.dom.EnhancedForStatement
                || declaration.getParent() instanceof CatchClause;
    }

    /**
     * Finds a stable operator write to one of the configured names.
     */
    private static final class WriteFinder extends ASTVisitor {

        /** The names whose writes are relevant. */
        private final Set<String> names;

        /** The source position after which writes are considered. */
        private final int minimumPosition;

        /** Whether field access forms should also be matched. */
        private final boolean fieldTarget;

        /** Whether a matching write has been found. */
        private boolean found;

        /**
         * Creates a write finder for one declaration scope.
         *
         * @param names the names whose writes should be found
         * @param minimumPosition the minimum source position for a write
         * @param fieldTarget whether field access forms should be matched
         */
        private WriteFinder(Set<String> names, int minimumPosition, boolean fieldTarget) {
            this.names = names;
            this.minimumPosition = minimumPosition;
            this.fieldTarget = fieldTarget;
        }

        /**
         * Checks one assignment expression for a write to a selected name.
         *
         * @param node the visited assignment expression
         * @return {@code false} after a matching write, otherwise {@code true}
         */
        @Override
        public boolean visit(Assignment node) {
            if (node.getStartPosition() > minimumPosition
                    && matches(node.getLeftHandSide())) {
                found = true;
                return false;
            }
            return true;
        }

        /**
         * Checks one prefix expression for an increment or decrement write.
         *
         * @param node the visited prefix expression
         * @return {@code false} after a matching write, otherwise {@code true}
         */
        @Override
        public boolean visit(PrefixExpression node) {
            if (node.getStartPosition() > minimumPosition
                    && isIncrementOrDecrement(node.getOperator())
                    && matches(node.getOperand())) {
                found = true;
                return false;
            }
            return true;
        }

        /**
         * Checks one postfix expression for an increment or decrement write.
         *
         * @param node the visited postfix expression
         * @return {@code false} after a matching write, otherwise {@code true}
         */
        @Override
        public boolean visit(PostfixExpression node) {
            if (node.getStartPosition() > minimumPosition
                    && matches(node.getOperand())) {
                found = true;
                return false;
            }
            return true;
        }

        /**
         * Checks whether an expression writes one of the selected names.
         *
         * @param expression the possible write target
         * @return {@code true} when the target matches a selected name
         */
        private boolean matches(org.eclipse.jdt.core.dom.Expression expression) {
            org.eclipse.jdt.core.dom.Expression unwrapped = expression;
            while (unwrapped instanceof ParenthesizedExpression parenthesized) {
                unwrapped = parenthesized.getExpression();
            }
            if (unwrapped instanceof org.eclipse.jdt.core.dom.SimpleName simpleName) {
                return names.contains(simpleName.getIdentifier());
            }
            if (!fieldTarget) {
                return false;
            }
            if (unwrapped instanceof org.eclipse.jdt.core.dom.FieldAccess fieldAccess) {
                return names.contains(fieldAccess.getName().getIdentifier());
            }
            if (unwrapped instanceof org.eclipse.jdt.core.dom.SuperFieldAccess superFieldAccess) {
                return names.contains(superFieldAccess.getName().getIdentifier());
            }
            return false;
        }

        /**
         * Checks whether a prefix operator writes its operand.
         *
         * @param operator the prefix operator to inspect
         * @return {@code true} for increment and decrement operators
         */
        private boolean isIncrementOrDecrement(PrefixExpression.Operator operator) {
            return operator == PrefixExpression.Operator.INCREMENT
                    || operator == PrefixExpression.Operator.DECREMENT;
        }
    }

    /**
     * Holds the selected declaration categories for one rule invocation.
     */
    private static final class Selection {

        /** Whether private instance fields are selected. */
        private final boolean fields;

        /** Whether method and constructor parameters are selected. */
        private final boolean parameters;

        /** Whether local declarations are selected. */
        private final boolean locals;

        /**
         * Creates one immutable declaration-category selection.
         *
         * @param fields whether fields are selected
         * @param parameters whether parameters are selected
         * @param locals whether locals are selected
         */
        private Selection(boolean fields, boolean parameters, boolean locals) {
            this.fields = fields;
            this.parameters = parameters;
            this.locals = locals;
        }

        /**
         * Creates a selection from the configured rule options.
         *
         * @param configuration the Maven rule configuration
         * @return the selected declaration categories
         */
        private static Selection from(RuleConfiguration configuration) {
            if (configuration == null || configuration.getOptions() == null
                    || configuration.getOptions().isEmpty()) {
                return new Selection(true, true, true);
            }
            return new Selection(
                    configuration.isOptionEnabled(FIELDS),
                    configuration.isOptionEnabled(PARAMETERS),
                    configuration.isOptionEnabled(LOCALS));
        }

        /**
         * Checks whether at least one declaration category is selected.
         *
         * @return {@code true} when a category is enabled
         */
        private boolean any() {
            return fields || parameters || locals;
        }
    }
}
