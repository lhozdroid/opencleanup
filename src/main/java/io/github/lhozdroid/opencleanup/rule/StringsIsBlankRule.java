package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Replaces a conservative empty-string check with {@code String.isBlank()}.
 *
 * <p>The syntax-only implementation matches only {@code name.trim().isEmpty()}
 * where both calls have no arguments and {@code name} is a simple name. It does
 * not resolve the receiver type, so callers should enable this rule only when
 * the matching receiver is known to be a {@link String}. Complex receivers and
 * other call shapes are intentionally left unchanged.</p>
 */
public final class StringsIsBlankRule implements CleanupRule {

    /** The stable identifier used to select this cleanup rule. */
    public static final String ID = "strings.is-blank";

    /**
     * Returns the stable identifier for the string blank-check cleanup.
     *
     * @return the string blank-check rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Replaces matching {@code trim().isEmpty()} calls with {@code isBlank()}.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one matching call is scheduled for replacement
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Replaces one exact {@code name.trim().isEmpty()} call shape.
             *
             * @param node the visited method invocation
             * @return {@code false} when the invocation is replaced, otherwise {@code true}
             */
            @Override
            public boolean visit(MethodInvocation node) {
                if (!isTrimmedEmptyCall(node)) {
                    return true;
                }

                MethodInvocation trimCall = (MethodInvocation) node.getExpression();
                Expression receiver = (Expression) ASTNode.copySubtree(
                        node.getAST(), trimCall.getExpression());
                MethodInvocation replacement = node.getAST().newMethodInvocation();
                replacement.setExpression(receiver);
                replacement.setName(node.getAST().newSimpleName("isBlank"));
                rewrite.replace(node, replacement, null);
                changed[0] = true;
                return false;
            }
        });
        return changed[0];
    }

    /**
     * Checks the exact syntax-only shape supported by this rule.
     *
     * @param node the method invocation to inspect
     * @return {@code true} for {@code simpleName.trim().isEmpty()} with no arguments
     */
    private boolean isTrimmedEmptyCall(MethodInvocation node) {
        if (!"isEmpty".equals(node.getName().getIdentifier())
                || !node.arguments().isEmpty()
                || !(node.getExpression() instanceof MethodInvocation trimCall)) {
            return false;
        }
        return "trim".equals(trimCall.getName().getIdentifier())
                && trimCall.arguments().isEmpty()
                && trimCall.getExpression() instanceof org.eclipse.jdt.core.dom.SimpleName;
    }
}
