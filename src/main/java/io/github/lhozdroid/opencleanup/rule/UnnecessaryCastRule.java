package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Removes casts whose operand's type is provable from Java syntax alone.
 *
 * <p>Binding resolution is intentionally not assumed. Literal casts and casts directly around
 * an object construction of the same declared type are safe without a class path.</p>
 */
public final class UnnecessaryCastRule implements CleanupRule {

    /** The stable identifier for unnecessary-cast cleanup. */
    public static final String ID = "casts.unnecessary";

    /**
     * Returns the stable identifier for this rule.
     *
     * @return the rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records removals for syntactically provably redundant casts.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one cast is scheduled for removal
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Removes a cast around an expression with a known matching type.
             *
             * @param node the visited cast expression
             * @return {@code false} after a replacement, otherwise {@code true}
             */
            @Override
            public boolean visit(CastExpression node) {
                if (!isRedundant(node)) {
                    return true;
                }
                rewrite.replace(node, rewrite.createMoveTarget(node.getExpression()), null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Checks whether a cast has a syntax-known matching operand type.
     *
     * @param cast the cast expression to inspect
     * @return {@code true} when removing the cast preserves the expression type
     */
    private boolean isRedundant(CastExpression cast) {
        String type = cast.getType().toString();
        Expression expression = cast.getExpression();
        if (expression instanceof BooleanLiteral) {
            return "boolean".equals(type);
        }
        if (expression instanceof CharacterLiteral) {
            return "char".equals(type);
        }
        if (expression instanceof StringLiteral) {
            return "String".equals(type) || "java.lang.String".equals(type);
        }
        if (expression instanceof NumberLiteral numberLiteral) {
            return numberType(numberLiteral.getToken()).equals(type);
        }
        if (expression instanceof ClassInstanceCreation creation) {
            return type.equals(creation.getType().toString());
        }
        return false;
    }

    /**
     * Infers the declared type of a simple numeric literal.
     *
     * @param token the numeric literal token
     * @return the primitive type name, or an empty string for an unsupported token
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
        if (token.matches("[0-9]+|0[xX][0-9a-fA-F]+|0[bB][01]+|0[0-7]+")) {
            return "int";
        }
        return "";
    }
}
