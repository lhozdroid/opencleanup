package io.github.lhozdroid.opencleanup.rule;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Converts a conservative resource declaration and closing {@code finally}
 * block into a try-with-resources statement.
 */
public final class TryWithResourcesRule implements CleanupRule {

    /** The stable identifier used to select this cleanup rule. */
    public static final String ID = "try-with-resources";

    /**
     * Returns the stable identifier for try-with-resources cleanup.
     *
     * @return the try-with-resources rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records conversions for resource declarations followed by eligible try statements.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one try statement is converted
     */
    @Override
    public boolean apply(
            CompilationUnit compilationUnit,
            ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Converts one eligible try-finally statement.
             *
             * @param node the visited try statement
             * @return {@code false} after conversion, otherwise {@code true}
             */
            @Override
            public boolean visit(TryStatement node) {
                if (convert(node, rewrite)) {
                    changed[0] = true;
                    return false;
                }
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Converts a try statement when its immediately preceding declaration is closed in finally.
     *
     * @param node the try statement to inspect
     * @param rewrite the AST rewrite collecting source edits
     * @return {@code true} when the statement was scheduled for conversion
     */
    private boolean convert(TryStatement node, ASTRewrite rewrite) {
        if (!node.catchClauses().isEmpty()
                || node.getFinally() == null
                || !(node.getFinally() instanceof Block finallyBlock)
                || finallyBlock.statements().size() != 1
                || !(node.getParent() instanceof Block parent)) {
            return false;
        }

        int tryIndex = parent.statements().indexOf(node);
        if (tryIndex <= 0
                || !(parent.statements().get(tryIndex - 1) instanceof VariableDeclarationStatement declaration)
                || declaration.fragments().size() != 1) {
            return false;
        }

        VariableDeclarationFragment fragment =
                (VariableDeclarationFragment) declaration.fragments().get(0);
        if (fragment.getInitializer() == null
                || !fragment.extraDimensions().isEmpty()
                || !isCloseStatement((Statement) finallyBlock.statements().get(0), fragment.getName())) {
            return false;
        }

        org.eclipse.jdt.core.dom.AST ast = node.getAST();
        VariableDeclarationExpression resource = ast.newVariableDeclarationExpression(
                (VariableDeclarationFragment) ASTNode.copySubtree(ast, fragment));
        resource.setType((org.eclipse.jdt.core.dom.Type) ASTNode.copySubtree(ast, declaration.getType()));
        for (Object modifier : declaration.modifiers()) {
            resource.modifiers().add(ASTNode.copySubtree(ast, (ASTNode) modifier));
        }

        TryStatement replacement = ast.newTryStatement();
        replacement.resources().add(resource);
        replacement.setBody((Block) ASTNode.copySubtree(ast, node.getBody()));
        rewrite.replace(node, replacement, null);
        rewrite.remove(declaration, null);
        return true;
    }

    /**
     * Checks whether a statement is a no-argument close call on the declared resource.
     *
     * @param statement the statement in the finally block
     * @param resourceName the declared resource name
     * @return {@code true} when the statement closes exactly that resource
     */
    private boolean isCloseStatement(Statement statement, SimpleName resourceName) {
        if (!(statement instanceof ExpressionStatement expressionStatement)
                || !(expressionStatement.getExpression() instanceof MethodInvocation invocation)
                || !"close".equals(invocation.getName().getIdentifier())
                || !invocation.arguments().isEmpty()
                || !(invocation.getExpression() instanceof SimpleName receiver)) {
            return false;
        }
        return resourceName.getIdentifier().equals(receiver.getIdentifier());
    }
}
