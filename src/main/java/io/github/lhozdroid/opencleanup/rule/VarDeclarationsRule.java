package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Replaces local declarations with {@code var} when the initializer's type is
 * provably identical to the declared type from syntax alone.
 */
public final class VarDeclarationsRule implements CleanupRule {

    /** The stable identifier for local {@code var} declarations. */
    public static final String ID = "variable-declarations.var";

    /**
     * Returns the stable identifier for local {@code var} declarations.
     *
     * @return the var-declarations rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records replacements for local declarations with syntax-identical inferred types.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one declaration is changed
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Replaces one eligible local declaration type with {@code var}.
             *
             * @param node the visited local declaration statement
             * @return {@code true} to continue visiting nested declarations
             */
            @Override
            public boolean visit(VariableDeclarationStatement node) {
                if (!isEligible(node)) {
                    return true;
                }

                Type varType = node.getAST().newSimpleType(node.getAST().newSimpleName("var"));
                rewrite.replace(node.getType(), varType, null);
                changed[0] = true;
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Checks all fragments in one local declaration for a safe inferred type.
     *
     * @param declaration the declaration to inspect
     * @return {@code true} when every fragment has a supported exact initializer
     */
    private boolean isEligible(VariableDeclarationStatement declaration) {
        if (declaration.getType().toString().equals("var")
                || declaration.fragments().isEmpty()
                || declaration.getType().toString().contains("@")) {
            return false;
        }
        for (Object value : declaration.fragments()) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment) value;
            if (fragment.getExtraDimensions() != 0
                    || fragment.getInitializer() == null
                    || !initializerHasExactType(declaration.getType(), fragment.getInitializer())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Determines an initializer type only for literal, exact construction, and exact array shapes.
     *
     * @param declaredType the declared local type
     * @param initializer the local initializer
     * @return {@code true} when the initializer's inferred type is syntax-identical
     */
    private boolean initializerHasExactType(Type declaredType, Expression initializer) {
        String declared = declaredType.toString();
        if (initializer instanceof StringLiteral) {
            return "String".equals(declared);
        }
        if (initializer instanceof BooleanLiteral) {
            return "boolean".equals(declared);
        }
        if (initializer instanceof CharacterLiteral) {
            return "char".equals(declared);
        }
        if (initializer instanceof NumberLiteral number) {
            return numberType(number.getToken()).equals(declared);
        }
        if (initializer instanceof ClassInstanceCreation creation) {
            return creation.getAnonymousClassDeclaration() == null
                    && creation.typeArguments().isEmpty()
                    && sameType(declaredType, creation.getType());
        }
        if (initializer instanceof ArrayCreation creation) {
            return sameType(declaredType, creation.getType())
                    && creation.getInitializer() == null;
        }
        return false;
    }

    /**
     * Maps a numeric literal token to the primitive type inferred by Java syntax.
     *
     * @param token the numeric literal token
     * @return the inferred primitive type name
     */
    private String numberType(String token) {
        if (token.endsWith("L") || token.endsWith("l")) {
            return "long";
        }
        if (token.endsWith("F") || token.endsWith("f")) {
            return "float";
        }
        if (token.endsWith("D") || token.endsWith("d")
                || token.contains(".") || token.contains("e") || token.contains("E")) {
            return "double";
        }
        return "int";
    }

    /**
     * Compares two types structurally without requiring resolved bindings.
     *
     * @param first the declared type
     * @param second the initializer type
     * @return {@code true} when both type trees are equal
     */
    private boolean sameType(Type first, Type second) {
        return first.subtreeMatch(new ASTMatcher(), second);
    }
}
