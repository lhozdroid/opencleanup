package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.UnionType;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import java.util.List;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Combines adjacent catch clauses that have the same parameter and body.
 */
public final class MultiCatchRule implements CleanupRule {

    /** The stable identifier used to select this cleanup rule. */
    public static final String ID = "multi-catch";

    /**
     * Returns the stable identifier for multi-catch cleanup.
     *
     * @return the multi-catch rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records combinations for eligible adjacent catch clauses.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least two catch clauses are combined
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Combines eligible catch clauses in one try statement.
             *
             * @param node the visited try statement
             * @return {@code true} to continue visiting nested statements
             */
            @Override
            public boolean visit(TryStatement node) {
                if (combine(node, rewrite)) {
                    changed[0] = true;
                }
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Combines each maximal run of compatible catch clauses in a try statement.
     *
     * @param node the try statement to inspect
     * @param rewrite the AST rewrite collecting source edits
     * @return {@code true} when at least one run was combined
     */
    private boolean combine(TryStatement node, ASTRewrite rewrite) {
        boolean changed = false;
        for (int index = 0; index < node.catchClauses().size() - 1; index++) {
            CatchClause first = (CatchClause) node.catchClauses().get(index);
            CatchClause second = (CatchClause) node.catchClauses().get(index + 1);
            if (!compatible(first, second)) {
                continue;
            }

            int end = index + 1;
            while (end + 1 < node.catchClauses().size()
                    && compatible(first, (CatchClause) node.catchClauses().get(end + 1))) {
                end++;
            }

            rewrite.replace(first.getException(), combinedException(first, end, node.getAST()), null);
            for (int removeIndex = index + 1; removeIndex <= end; removeIndex++) {
                rewrite.remove((ASTNode) node.catchClauses().get(removeIndex), null);
            }
            changed = true;
            index = end;
        }
        return changed;
    }

    /**
     * Checks whether two catch clauses can share one multi-catch parameter.
     *
     * @param first the first catch clause
     * @param second the second catch clause
     * @return {@code true} when parameter names, modifiers, and bodies match
     */
    private boolean compatible(CatchClause first, CatchClause second) {
        SingleVariableDeclaration firstException = first.getException();
        SingleVariableDeclaration secondException = second.getException();
        if (!firstException.modifiers().isEmpty()
                || !secondException.modifiers().isEmpty()
                || !firstException.extraDimensions().isEmpty()
                || !secondException.extraDimensions().isEmpty()
                || firstException.isVarargs()
                || secondException.isVarargs()
                || !firstException.getName().getIdentifier().equals(secondException.getName().getIdentifier())
                || !(first.getBody() instanceof Block firstBody)
                || !(second.getBody() instanceof Block secondBody)
                || !firstBody.subtreeMatch(new ASTMatcher(), secondBody)) {
            return false;
        }
        return !firstException.getType().subtreeMatch(
                new ASTMatcher(), secondException.getType());
    }

    /**
     * Builds a union catch parameter from the first catch clause through the supplied index.
     *
     * @param first the first catch clause in the run
     * @param end the inclusive final catch-clause index
     * @param ast the AST owning the replacement
     * @return the combined catch parameter
     */
    private SingleVariableDeclaration combinedException(CatchClause first, int end, AST ast) {
        SingleVariableDeclaration replacement = ast.newSingleVariableDeclaration();
        replacement.setName((org.eclipse.jdt.core.dom.SimpleName) ASTNode.copySubtree(
                ast, first.getException().getName()));
        UnionType union = ast.newUnionType();
        List<?> catches = (List<?>) first.getParent().getStructuralProperty(
                TryStatement.CATCH_CLAUSES_PROPERTY);
        for (int index = 0; index <= end; index++) {
            CatchClause clause = (CatchClause) catches.get(index);
            union.types().add(ASTNode.copySubtree(ast, clause.getException().getType()));
        }
        replacement.setType(union);
        return replacement;
    }
}
