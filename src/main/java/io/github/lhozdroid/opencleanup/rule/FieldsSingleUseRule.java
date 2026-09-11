package io.github.lhozdroid.opencleanup.rule;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Inlines a private final field that has one safe read in its declaring type.
 *
 * <p>The syntax-only implementation requires a single-fragment field with a side-effect-free
 * initializer. It deliberately leaves fields whose use cannot be distinguished from a qualified
 * or write access without resolved bindings.
 */
public final class FieldsSingleUseRule implements CleanupRule {

    /** The stable identifier for single-use field cleanup. */
    public static final String ID = "fields.single-use";

    /**
     * Returns the stable identifier for this cleanup rule.
     *
     * @return the single-use field rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records safe inlining edits for eligible private final fields.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one field is inlined
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Inspects one field declaration for a single safe read.
             *
             * @param node the visited field declaration
             * @return {@code true} to continue visiting the compilation unit
             */
            @Override
            public boolean visit(FieldDeclaration node) {
                if (node.fragments().size() != 1
                        || Modifier.isStatic(node.getModifiers())
                        || !Modifier.isPrivate(node.getModifiers())
                        || !Modifier.isFinal(node.getModifiers())) {
                    return true;
                }
                VariableDeclarationFragment fragment =
                        (VariableDeclarationFragment) node.fragments().get(0);
                if (fragment.getInitializer() == null || !isPure(fragment.getInitializer())) {
                    return true;
                }
                AbstractTypeDeclaration owner = enclosingType(node);
                if (owner == null) {
                    return true;
                }
                List<SimpleName> uses = findUses(owner, fragment.getName(), node);
                if (uses.size() != 1 || !isReadableUse(uses.get(0))) {
                    return true;
                }
                rewrite.replace(uses.get(0), ASTNode.copySubtree(node.getAST(), fragment.getInitializer()), null);
                rewrite.remove(node, null);
                changed[0] = true;
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Finds the nearest type declaration containing a node.
     *
     * @param node the node whose enclosing type is required
     * @return the enclosing type, or {@code null} when none exists
     */
    private AbstractTypeDeclaration enclosingType(ASTNode node) {
        ASTNode current = node.getParent();
        while (current != null && !(current instanceof AbstractTypeDeclaration)) {
            current = current.getParent();
        }
        return (AbstractTypeDeclaration) current;
    }

    /**
     * Collects simple-name reads belonging directly to the declaring type.
     *
     * @param owner the declaring type
     * @param declaration the field declaration name
     * @param field the field declaration to exclude from reference counting
     * @return candidate references to the field name
     */
    private List<SimpleName> findUses(
            AbstractTypeDeclaration owner,
            SimpleName declaration,
            FieldDeclaration field) {
        List<SimpleName> uses = new ArrayList<>();
        owner.accept(new ASTVisitor() {
            /**
             * Records a matching name that is not the declaration itself or a nested type use.
             *
             * @param node the visited simple name
             * @return {@code true} to continue visiting children
             */
            @Override
            public boolean visit(SimpleName node) {
                if (node != declaration
                        && declaration.getIdentifier().equals(node.getIdentifier())
                        && enclosingType(node) == owner
                        && enclosingField(node) != field) {
                    uses.add(node);
                }
                return true;
            }
        });
        return uses;
    }

    /**
     * Finds the nearest field declaration containing a node.
     *
     * @param node the node whose field context is required
     * @return the containing field, or {@code null} when the node is outside a field
     */
    private FieldDeclaration enclosingField(ASTNode node) {
        ASTNode current = node.getParent();
        while (current != null && !(current instanceof FieldDeclaration)) {
            current = current.getParent();
        }
        return (FieldDeclaration) current;
    }

    /**
     * Checks that a name is a plain read rather than a qualified or write access.
     *
     * @param name the candidate field reference
     * @return {@code true} when replacing the name is syntactically safe
     */
    private boolean isReadableUse(SimpleName name) {
        ASTNode parent = name.getParent();
        if (parent instanceof QualifiedName qualified && qualified.getName() == name
                || parent instanceof FieldAccess fieldAccess && fieldAccess.getName() == name
                || parent instanceof Assignment assignment && assignment.getLeftHandSide() == name
                || parent instanceof PostfixExpression
                || parent instanceof PrefixExpression) {
            return false;
        }
        return !(parent instanceof VariableDeclarationFragment fragment && fragment.getName() == name);
    }

    /**
     * Checks that an initializer has no method calls, assignments, or other obvious side effects.
     *
     * @param expression the initializer to inspect
     * @return {@code true} when its syntax is side-effect free
     */
    private boolean isPure(org.eclipse.jdt.core.dom.Expression expression) {
        final boolean[] pure = {true};
        expression.accept(new ASTVisitor() {
            /**
             * Rejects a method invocation in a field initializer.
             *
             * @param node the visited method invocation
             * @return {@code false} because the expression is not pure
             */
            @Override
            public boolean visit(org.eclipse.jdt.core.dom.MethodInvocation node) {
                pure[0] = false;
                return false;
            }

            /**
             * Rejects an object construction in a field initializer.
             *
             * @param node the visited class creation
             * @return {@code false} because the expression is not pure
             */
            @Override
            public boolean visit(org.eclipse.jdt.core.dom.ClassInstanceCreation node) {
                pure[0] = false;
                return false;
            }

            /**
             * Rejects an assignment in a field initializer.
             *
             * @param node the visited assignment
             * @return {@code false} because the expression is not pure
             */
            @Override
            public boolean visit(Assignment node) {
                pure[0] = false;
                return false;
            }

            /**
             * Rejects a postfix update in a field initializer.
             *
             * @param node the visited postfix expression
             * @return {@code false} because the expression is not pure
             */
            @Override
            public boolean visit(PostfixExpression node) {
                pure[0] = false;
                return false;
            }

            /**
             * Rejects a prefix update in a field initializer.
             *
             * @param node the visited prefix expression
             * @return {@code false} because the expression is not pure
             */
            @Override
            public boolean visit(PrefixExpression node) {
                if (node.getOperator() == PrefixExpression.Operator.INCREMENT
                        || node.getOperator() == PrefixExpression.Operator.DECREMENT) {
                    pure[0] = false;
                    return false;
                }
                return true;
            }
        });
        return pure[0];
    }
}
