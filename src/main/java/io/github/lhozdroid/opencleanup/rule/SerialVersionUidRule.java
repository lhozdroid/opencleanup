package io.github.lhozdroid.opencleanup.rule;

import java.util.Map;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/** Adds a deterministic {@code serialVersionUID} to source-local serializable classes. */
public final class SerialVersionUidRule implements CleanupRule {

    /** The stable identifier for serialVersionUID cleanup. */
    public static final String ID = "serialization.serial-version-uid";

    /** The option selecting a conventional value. */
    private static final String DEFAULT = "default";

    /** The option selecting a deterministic generated value. */
    private static final String GENERATED = "generated";

    /**
     * Returns the stable identifier for serialVersionUID cleanup.
     *
     * @return the serialVersionUID rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Adds a missing private static final serialVersionUID field.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when a field insertion is scheduled
     */
    @Override
    public boolean apply(CompilationUnit compilationUnit, ASTRewrite rewrite,
            RuleConfiguration configuration) {
        String mode = configuredMode(configuration);
        if (mode == null) {
            return false;
        }
        Map<String, AbstractTypeDeclaration> types = MissingCodeSupport.collectTypes(compilationUnit);
        boolean changed = false;
        for (AbstractTypeDeclaration type : types.values()) {
            if (!(type instanceof TypeDeclaration declaration) || declaration.isInterface()
                    || MissingCodeSupport.hasField(type, "serialVersionUID")
                    || !isSerializable(declaration, types)) {
                continue;
            }
            FieldDeclaration field = createField(declaration.getAST(), declaration, mode);
            ListRewrite body = rewrite.getListRewrite(
                    declaration, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
            body.insertLast(field, null);
            changed = true;
        }
        return changed;
    }

    /**
     * Resolves the supported serial UID mode.
     *
     * @param configuration the rule configuration
     * @return {@code default}, {@code generated}, or {@code null} for an unsupported value
     */
    private String configuredMode(RuleConfiguration configuration) {
        String mode = configuration == null ? null : configuration.optionValue(ID);
        if (mode == null && configuration != null) {
            mode = configuration.optionValue("mode");
        }
        if (mode == null || mode.isBlank()) {
            return DEFAULT;
        }
        String normalized = mode.trim().toLowerCase(java.util.Locale.ROOT);
        return DEFAULT.equals(normalized) || GENERATED.equals(normalized) ? normalized : null;
    }

    /**
     * Checks whether a class directly implements or source-locally inherits Serializable.
     *
     * @param declaration the class under consideration
     * @param types source-local type declarations
     * @return {@code true} when the class is provably serializable
     */
    private boolean isSerializable(TypeDeclaration declaration,
            Map<String, AbstractTypeDeclaration> types) {
        for (Object interfaceObject : declaration.superInterfaceTypes()) {
            if (MissingCodeSupport.isSerializable((org.eclipse.jdt.core.dom.Type) interfaceObject)) {
                return true;
            }
            String name = MissingCodeSupport.referencedTypeName(
                    (org.eclipse.jdt.core.dom.Type) interfaceObject);
            AbstractTypeDeclaration parent = name == null ? null : types.get(name);
            if (parent instanceof TypeDeclaration parentType && isSerializable(parentType, types)) {
                return true;
            }
        }
        if (declaration.getSuperclassType() != null) {
            String name = MissingCodeSupport.referencedTypeName(declaration.getSuperclassType());
            AbstractTypeDeclaration parent = name == null ? null : types.get(name);
            if (parent instanceof TypeDeclaration parentType && isSerializable(parentType, types)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Builds the generated serialVersionUID field.
     *
     * @param ast the AST receiving the new field
     * @param declaration the declaring class
     * @param mode the configured value mode
     * @return the generated field declaration
     */
    private FieldDeclaration createField(AST ast, TypeDeclaration declaration, String mode) {
        VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
        fragment.setName(ast.newSimpleName("serialVersionUID"));
        NumberLiteral value = ast.newNumberLiteral(
                GENERATED.equals(mode) ? generatedValue(declaration) : "1L");
        fragment.setInitializer(value);
        FieldDeclaration field = ast.newFieldDeclaration(fragment);
        field.setType(ast.newPrimitiveType(PrimitiveType.LONG));
        field.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));
        field.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD));
        field.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.FINAL_KEYWORD));
        return field;
    }

    /**
     * Produces a stable positive literal for generated serial UID mode.
     *
     * @param declaration the class receiving the generated field
     * @return a Java long literal
     */
    private String generatedValue(TypeDeclaration declaration) {
        long hash = 1125899906842597L;
        String source = declaration.getName().getIdentifier() + ':' + declaration.toString();
        for (int index = 0; index < source.length(); index++) {
            hash = 31 * hash + source.charAt(index);
        }
        long positive = hash == Long.MIN_VALUE ? 1L : Math.abs(hash);
        return (positive == 0L ? 1L : positive) + "L";
    }
}
