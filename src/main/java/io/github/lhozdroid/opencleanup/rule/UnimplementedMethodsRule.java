package io.github.lhozdroid.opencleanup.rule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import org.eclipse.jdt.core.dom.CompilationUnit;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/** Generates conservative method stubs for source-local abstract contracts. */
public final class UnimplementedMethodsRule implements CleanupRule {

    /** The stable identifier for generated unimplemented-method stubs. */
    public static final String ID = "methods.unimplemented";

    /**
     * Returns the stable identifier for unimplemented-method cleanup.
     *
     * @return the unimplemented-method rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Adds stubs for abstract methods required by source-local parents.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one method stub is scheduled
     */
    @Override
    public boolean apply(CompilationUnit compilationUnit, ASTRewrite rewrite,
            RuleConfiguration configuration) {
        Map<String, AbstractTypeDeclaration> types = MissingCodeSupport.collectTypes(compilationUnit);
        boolean changed = false;
        for (AbstractTypeDeclaration type : types.values()) {
            if (!(type instanceof TypeDeclaration declaration) || declaration.isInterface()
                    || MissingCodeSupport.isAbstractType(type)) {
                continue;
            }
            List<MethodDeclaration> required = requiredMethods(declaration, types);
            Set<String> existing = new HashSet<>();
            for (MethodDeclaration method : MissingCodeSupport.declaredMethods(type)) {
                existing.add(MissingCodeSupport.signature(method));
            }
            ListRewrite body = rewrite.getListRewrite(
                    declaration, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
            for (MethodDeclaration method : required) {
                if (!existing.add(MissingCodeSupport.signature(method))) {
                    continue;
                }
                body.insertLast(createStub(declaration.getAST(), method,
                        isInterfaceMethod(method)), null);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Finds abstract contracts inherited by one concrete class.
     *
     * @param type the concrete class under consideration
     * @param types source-local type declarations
     * @return required abstract methods in deterministic traversal order
     */
    private List<MethodDeclaration> requiredMethods(
            TypeDeclaration type,
            Map<String, AbstractTypeDeclaration> types) {
        List<MethodDeclaration> required = new ArrayList<>();
        for (AbstractTypeDeclaration parent : MissingCodeSupport.directParents(type, types)) {
            collectContracts(parent, types, required, new HashSet<>());
        }
        return required;
    }

    /**
     * Collects abstract methods from an ancestor and its ancestors.
     *
     * @param type the ancestor being inspected
     * @param types source-local type declarations
     * @param required destination list of abstract contracts
     * @param visited ancestor names already inspected
     */
    private void collectContracts(
            AbstractTypeDeclaration type,
            Map<String, AbstractTypeDeclaration> types,
            List<MethodDeclaration> required,
            Set<String> visited) {
        if (!visited.add(type.getName().getIdentifier())) {
            return;
        }
        for (MethodDeclaration method : MissingCodeSupport.declaredMethods(type)) {
            if (MissingCodeSupport.isAbstractContract(method, type)) {
                required.add(method);
            }
        }
        for (AbstractTypeDeclaration parent : MissingCodeSupport.directParents(type, types)) {
            collectContracts(parent, types, required, visited);
        }
    }

    /**
     * Creates a concrete copy of an abstract method with a minimal return stub.
     *
     * @param ast the AST receiving the generated method
     * @param source the abstract source method
     * @param interfaceMethod whether the source method belongs to an interface
     * @return the generated concrete method
     */
    private MethodDeclaration createStub(AST ast, MethodDeclaration source,
            boolean interfaceMethod) {
        MethodDeclaration stub = (MethodDeclaration) ASTNode.copySubtree(ast, source);
        removeModifier(stub, Modifier.ModifierKeyword.ABSTRACT_KEYWORD);
        removeModifier(stub, Modifier.ModifierKeyword.NATIVE_KEYWORD);
        removeModifier(stub, Modifier.ModifierKeyword.DEFAULT_KEYWORD);
        if (interfaceMethod && !hasVisibility(stub)) {
            stub.modifiers().add(0, ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
        }
        Block body = ast.newBlock();
        if (!isVoid(stub)) {
            ReturnStatement returnStatement = ast.newReturnStatement();
            returnStatement.setExpression(defaultValue(ast, stub));
            body.statements().add(returnStatement);
        }
        stub.setBody(body);
        return stub;
    }

    /**
     * Determines whether an inherited method originated in an interface.
     *
     * @param method the inherited method to inspect
     * @return {@code true} when the direct parent is an interface
     */
    private boolean isInterfaceMethod(MethodDeclaration method) {
        return method.getParent() instanceof TypeDeclaration declaration && declaration.isInterface();
    }

    /**
     * Removes one modifier keyword from a method copy.
     *
     * @param method the method copy to update
     * @param keyword the modifier keyword to remove
     */
    private void removeModifier(MethodDeclaration method, Modifier.ModifierKeyword keyword) {
        for (Object modifierObject : method.modifiers()) {
            if (modifierObject instanceof Modifier modifier && modifier.getKeyword() == keyword) {
                method.modifiers().remove(modifier);
                return;
            }
        }
    }

    /**
     * Checks whether a method has explicit visibility.
     *
     * @param method the method to inspect
     * @return {@code true} when public, protected, or private is present
     */
    private boolean hasVisibility(MethodDeclaration method) {
        return MissingCodeSupport.hasModifier(method, Modifier.ModifierKeyword.PUBLIC_KEYWORD)
                || MissingCodeSupport.hasModifier(method, Modifier.ModifierKeyword.PROTECTED_KEYWORD)
                || MissingCodeSupport.hasModifier(method, Modifier.ModifierKeyword.PRIVATE_KEYWORD);
    }

    /**
     * Checks whether a copied method returns void.
     *
     * @param method the method to inspect
     * @return {@code true} when its return type is void
     */
    private boolean isVoid(MethodDeclaration method) {
        return method.getReturnType2() instanceof PrimitiveType primitive
                && primitive.getPrimitiveTypeCode() == PrimitiveType.VOID;
    }

    /**
     * Creates a compile-safe default return expression.
     *
     * @param ast the AST receiving the expression
     * @param method the method whose return type is inspected
     * @return a default expression suitable for the return type
     */
    private Expression defaultValue(AST ast, MethodDeclaration method) {
        if (method.getReturnType2() instanceof PrimitiveType primitive
                && primitive.getPrimitiveTypeCode() != PrimitiveType.BOOLEAN) {
            return ast.newNumberLiteral("0");
        }
        if (method.getReturnType2() instanceof PrimitiveType primitive
                && primitive.getPrimitiveTypeCode() == PrimitiveType.BOOLEAN) {
            return ast.newBooleanLiteral(false);
        }
        return ast.newNullLiteral();
    }
}
