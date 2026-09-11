package io.github.lhozdroid.opencleanup.rule;

import java.util.Set;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Initializes a new map with the map source used by a following {@code putAll} call.
 */
public final class MapCloningRule implements CleanupRule {

    /** The stable identifier for map cloning cleanup. */
    public static final String ID = "maps.clone";

    private static final Set<String> MAP_TYPES = Set.of(
            "ConcurrentHashMap", "ConcurrentSkipListMap", "Hashtable", "HashMap",
            "IdentityHashMap", "LinkedHashMap", "TreeMap", "WeakHashMap");

    /**
     * Returns the stable identifier for this rule.
     *
     * @return the map cloning rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records map constructor argument insertions and removes the following put-all call.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one map is initialized from another map
     */
    @Override
    public boolean apply(CompilationUnit compilationUnit, ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Finds adjacent map creation and put-all statements in one block.
             *
             * @param node the visited block
             * @return {@code true} to continue visiting nested blocks
             */
            @Override
            public boolean visit(Block node) {
                for (int index = 1; index < node.statements().size(); index++) {
                    if (rewritePair((Statement) node.statements().get(index - 1),
                            (Statement) node.statements().get(index), rewrite)) {
                        changed[0] = true;
                    }
                }
                return true;
            }
        });
        return changed[0];
    }

    /**
     * Rewrites one adjacent creation and {@code putAll} pair.
     *
     * @param previous the statement creating or assigning the destination map
     * @param current the candidate put-all statement
     * @param rewrite the AST rewrite collecting source edits
     * @return {@code true} when the pair is eligible
     */
    private boolean rewritePair(Statement previous, Statement current, ASTRewrite rewrite) {
        if (!(current instanceof ExpressionStatement expressionStatement)
                || !(expressionStatement.getExpression() instanceof MethodInvocation putAll)
                || !putAll.getName().getIdentifier().equals("putAll")
                || putAll.arguments().size() != 1 || putAll.getExpression() == null) {
            return false;
        }
        String receiverName = putAll.getExpression() instanceof SimpleName name ? name.getIdentifier() : null;
        ClassInstanceCreation creation = creation(previous, receiverName);
        if (creation == null || !eligibleType(creation) || !creation.arguments().isEmpty()
                || creation.getAnonymousClassDeclaration() != null) {
            return false;
        }
        ListRewrite arguments = rewrite.getListRewrite(
                creation, ClassInstanceCreation.ARGUMENTS_PROPERTY);
        arguments.insertLast(rewrite.createMoveTarget((Expression) putAll.arguments().get(0)), null);
        rewrite.remove(current, null);
        return true;
    }

    /**
     * Finds the map constructor associated with a put-all receiver.
     *
     * @param previous the preceding statement
     * @param receiverName the simple receiver name
     * @return the matching constructor, or {@code null} when unsupported
     */
    private ClassInstanceCreation creation(Statement previous, String receiverName) {
        if (receiverName == null) {
            return null;
        }
        if (previous instanceof VariableDeclarationStatement declaration && declaration.fragments().size() == 1) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment) declaration.fragments().get(0);
            return fragment.getName().getIdentifier().equals(receiverName)
                    && fragment.getInitializer() instanceof ClassInstanceCreation value ? value : null;
        }
        if (previous instanceof ExpressionStatement statement
                && statement.getExpression() instanceof Assignment assignment
                && assignment.getOperator() == Assignment.Operator.ASSIGN
                && assignment.getLeftHandSide() instanceof SimpleName left
                && left.getIdentifier().equals(receiverName)
                && assignment.getRightHandSide() instanceof ClassInstanceCreation value) {
            return value;
        }
        return null;
    }

    /**
     * Checks whether a constructor names a supported concrete map implementation.
     *
     * @param creation the map constructor
     * @return {@code true} for a supported map type
     */
    private boolean eligibleType(ClassInstanceCreation creation) {
        String text = creation.getType().toString();
        int genericStart = text.indexOf('<');
        String base = genericStart < 0 ? text : text.substring(0, genericStart);
        int nameSeparator = base.lastIndexOf('.');
        return MAP_TYPES.contains(nameSeparator < 0 ? base : base.substring(nameSeparator + 1));
    }
}
