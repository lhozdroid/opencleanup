package io.github.lhozdroid.opencleanup.rule;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.TypeMethodReference;
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
                        || !Modifier.isFinal(node.getModifiers())
                        || LombokSupport.hasAnyAnnotation(node)
                        || LombokSupport.hasAnyEnclosingTypeAnnotation(node)
                        || LombokSupport.hasAnyAnnotatedMember(node)
                        || LombokSupport.isManagedField(node)) {
                    return true;
                }
                VariableDeclarationFragment fragment =
                        (VariableDeclarationFragment) node.fragments().get(0);
                if (fragment.getInitializer() == null
                        || !isCompileTimeConstant(
                                node.getType(),
                                fragment.getInitializer())) {
                    return true;
                }
                AbstractTypeDeclaration owner = enclosingType(node);
                if (owner == null) {
                    return true;
                }
                if (hasShadowingDeclaration(owner, fragment.getName().getIdentifier(), node)) {
                    return true;
                }
                List<SimpleName> uses = findUses(owner, fragment.getName(), node);
                if (uses.size() != 1 || !isReadableUse(uses.get(0))) {
                    return true;
                }
                if (enclosingType(uses.get(0)) != owner) {
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
        while (current != null
                && !(current instanceof AbstractTypeDeclaration)
                && !(current instanceof AnonymousClassDeclaration)) {
            current = current.getParent();
        }
        if (current instanceof AnonymousClassDeclaration) {
            return null;
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
                        && enclosingField(node) != field) {
                    uses.add(node);
                }
                return true;
            }
        });
        return uses;
    }

    /**
     * Checks whether a declaration inside the owner shadows the candidate field name.
     *
     * @param owner the field's declaring type
     * @param fieldName the candidate field name
     * @param field the field declaration to exclude
     * @return {@code true} when a parameter, local, or catch variable has the same name
     */
    private boolean hasShadowingDeclaration(
            AbstractTypeDeclaration owner,
            String fieldName,
            FieldDeclaration field) {
        boolean[] shadowed = {false};
        owner.accept(new ASTVisitor() {
            /**
             * Finds a declaration that can shadow the field.
             *
             * @param node the visited single-variable declaration
             * @return {@code false} because no child declaration is relevant
             */
            @Override
            public boolean visit(org.eclipse.jdt.core.dom.SingleVariableDeclaration node) {
                if (fieldName.equals(node.getName().getIdentifier())) {
                    shadowed[0] = true;
                }
                return false;
            }

            /**
             * Finds a local variable that can shadow the field.
             *
             * @param node the visited variable fragment
             * @return {@code true} to inspect the initializer for other declarations
             */
            @Override
            public boolean visit(VariableDeclarationFragment node) {
                if (enclosingField(node) != field
                        && fieldName.equals(node.getName().getIdentifier())) {
                    shadowed[0] = true;
                }
                return true;
            }
        });
        return shadowed[0];
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
        if (parent instanceof QualifiedName qualified
                && (qualified.getQualifier() == name || qualified.getName() == name)
                || parent instanceof FieldAccess fieldAccess && fieldAccess.getName() == name
                || parent instanceof org.eclipse.jdt.core.dom.SuperFieldAccess fieldAccess
                        && fieldAccess.getName() == name
                || parent instanceof Assignment assignment && assignment.getLeftHandSide() == name
                || parent instanceof PostfixExpression
                || parent instanceof PrefixExpression
                || parent instanceof MethodDeclaration method && method.getName() == name
                || parent instanceof org.eclipse.jdt.core.dom.MethodInvocation invocation
                        && invocation.getName() == name
                || parent instanceof org.eclipse.jdt.core.dom.SuperMethodInvocation invocation
                        && invocation.getName() == name
                || parent instanceof MethodReference && methodReferenceName((MethodReference) parent) == name
                || parent instanceof Annotation annotation && annotation.getTypeName() == name
                || parent instanceof MemberValuePair pair && pair.getName() == name
                || parent instanceof SimpleType
                || parent instanceof NameQualifiedType
                || parent instanceof QualifiedType
                || parent instanceof AbstractTypeDeclaration declaration && declaration.getName() == name
                || parent instanceof EnumConstantDeclaration constant && constant.getName() == name
                || parent instanceof ImportDeclaration
                || parent instanceof PackageDeclaration) {
            return false;
        }
        return !(parent instanceof VariableDeclarationFragment fragment && fragment.getName() == name);
    }

    /**
     * Returns the target name of a method reference.
     *
     * @param reference the method reference to inspect
     * @return the target method name, or {@code null} for an unsupported reference shape
     */
    private SimpleName methodReferenceName(MethodReference reference) {
        if (reference instanceof ExpressionMethodReference expressionReference) {
            return expressionReference.getName();
        }
        if (reference instanceof TypeMethodReference typeReference) {
            return typeReference.getName();
        }
        if (reference instanceof org.eclipse.jdt.core.dom.SuperMethodReference superReference) {
            return superReference.getName();
        }
        return null;
    }

    /**
     * Checks whether an initializer is a compile-time constant expression.
     *
     * @param type the declared field type
     * @param expression the field initializer
     * @return {@code true} when inlining it cannot change evaluation timing or identity
     */
    private boolean isCompileTimeConstant(
            org.eclipse.jdt.core.dom.Type type,
            org.eclipse.jdt.core.dom.Expression expression) {
        if (expression instanceof org.eclipse.jdt.core.dom.NumberLiteral numberLiteral) {
            String token = numberLiteral.getToken();
            return switch (type.toString()) {
                case "int" -> !token.matches(".*[lLfFdD]$");
                case "long" -> token.matches(".*[lL]$");
                case "float" -> token.matches(".*[fF]$");
                case "double" -> token.matches(".*(?:[dD]|[.]|[eE]).*");
                default -> false;
            };
        }
        return expression instanceof org.eclipse.jdt.core.dom.NullLiteral
                || expression instanceof org.eclipse.jdt.core.dom.StringLiteral
                || expression instanceof org.eclipse.jdt.core.dom.BooleanLiteral
                || expression instanceof org.eclipse.jdt.core.dom.CharacterLiteral;
    }

}
