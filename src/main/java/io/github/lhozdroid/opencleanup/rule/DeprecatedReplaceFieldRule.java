package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Inlines deprecated fields only when JDT exposes their compile-time constant value.
 */
public final class DeprecatedReplaceFieldRule implements CleanupRule {

    /** The stable identifier used to select this cleanup rule. */
    public static final String ID = "deprecated.replace-field";

    /**
     * Returns the stable identifier for deprecated-field replacement cleanup.
     *
     * @return the deprecated-field replacement rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records literal replacements for eligible deprecated constant fields.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one field reference is replaced
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Replaces one reference to a deprecated compile-time constant.
             *
             * @param node the visited simple name
             * @return {@code true} to continue visiting nested nodes
             */
            @Override
            public boolean visit(SimpleName node) {
                if (node.isDeclaration() || !(node.resolveBinding() instanceof IVariableBinding binding)
                        || !binding.isField() || !binding.isDeprecated()
                        || binding.getConstantValue() == null) {
                    return true;
                }
                Expression replacement = literal(node.getAST(), binding);
                if (replacement == null) {
                    return true;
                }
                ASTNode target = qualifiedReference(node);
                rewrite.replace(target, replacement, null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Selects the complete member reference when the field is qualified.
     *
     * @param name the resolved field name
     * @return the rewrite target for the field reference
     */
    private ASTNode qualifiedReference(SimpleName name) {
        ASTNode parent = name.getParent();
        if (parent instanceof QualifiedName qualifiedName && qualifiedName.getName() == name) {
            return qualifiedName;
        }
        if (parent instanceof FieldAccess fieldAccess && fieldAccess.getName() == name) {
            return fieldAccess;
        }
        if (parent instanceof SuperFieldAccess superFieldAccess
                && superFieldAccess.getName() == name) {
            return superFieldAccess;
        }
        return name;
    }

    /**
     * Creates a source literal for a JDT compile-time constant.
     *
     * @param ast the AST owning the replacement
     * @param binding the resolved deprecated field binding
     * @return the replacement literal, or {@code null} when unsupported
     */
    private Expression literal(AST ast, IVariableBinding binding) {
        Object value = binding.getConstantValue();
        if (value instanceof Boolean booleanValue) {
            BooleanLiteral literal = ast.newBooleanLiteral(booleanValue);
            return literal;
        }
        if (value instanceof Character characterValue) {
            CharacterLiteral literal = ast.newCharacterLiteral();
            literal.setCharValue(characterValue);
            return literal;
        }
        if (value instanceof String stringValue) {
            StringLiteral literal = ast.newStringLiteral();
            literal.setLiteralValue(stringValue);
            return literal;
        }
        if (value instanceof Number numberValue) {
            String token = numberToken(numberValue, binding);
            return token == null ? null : ast.newNumberLiteral(token);
        }
        return null;
    }

    /**
     * Creates a Java numeric literal token for a constant value.
     *
     * @param value the resolved numeric value
     * @param binding the field binding providing the declared type
     * @return a valid Java numeric literal token, or {@code null} when unsupported
     */
    private String numberToken(Number value, IVariableBinding binding) {
        String type = binding.getType() == null ? "" : binding.getType().getName();
        if (value instanceof Double doubleValue && !Double.isFinite(doubleValue)
                || value instanceof Float floatValue && !Float.isFinite(floatValue)) {
            return null;
        }
        String token = value.toString();
        return switch (type) {
            case "long" -> token + "L";
            case "float" -> token + "F";
            case "double" -> token.contains(".") ? token : token + ".0";
            default -> token;
        };
    }
}
