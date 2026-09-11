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
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import io.github.lhozdroid.opencleanup.config.RuleConfiguration;

/**
 * Initializes a new collection with the source collection used by a following {@code addAll} call.
 */
public final class CollectionCloningRule implements CleanupRule {

    /** The stable identifier for collection cloning cleanup. */
    public static final String ID = "collections.clone";

    private static final Set<String> COLLECTION_TYPES = Set.of(
            "ArrayList", "ArrayDeque", "HashSet", "LinkedHashSet", "LinkedList", "PriorityQueue",
            "TreeSet", "Vector", "ConcurrentLinkedDeque", "ConcurrentLinkedQueue",
            "ConcurrentSkipListSet", "CopyOnWriteArrayList", "CopyOnWriteArraySet",
            "DelayQueue", "LinkedBlockingDeque", "LinkedBlockingQueue", "LinkedTransferQueue",
            "PriorityBlockingQueue");

    /**
     * Returns the stable identifier for this rule.
     *
     * @return the collection cloning rule identifier
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Records collection constructor argument insertions and removes the following add-all call.
     *
     * @param compilationUnit the parsed Java compilation unit
     * @param rewrite the AST rewrite collecting source edits
     * @param configuration the Maven configuration for this rule
     * @return {@code true} when at least one collection is initialized from another collection
     */
    @Override
    public boolean apply(CompilationUnit compilationUnit, ASTRewrite rewrite,
            RuleConfiguration configuration) {
        boolean[] changed = {false};
        compilationUnit.accept(new ASTVisitor() {
            /**
             * Finds adjacent collection creation and add-all statements in one block.
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
     * Rewrites one adjacent creation and {@code addAll} pair.
     *
     * @param previous the statement creating or assigning the destination collection
     * @param current the candidate add-all statement
     * @param rewrite the AST rewrite collecting source edits
     * @return {@code true} when the pair is eligible
     */
    private boolean rewritePair(Statement previous, Statement current, ASTRewrite rewrite) {
        if (!(current instanceof ExpressionStatement expressionStatement)
                || !(expressionStatement.getExpression() instanceof MethodInvocation addAll)
                || !addAll.getName().getIdentifier().equals("addAll")
                || addAll.arguments().size() != 1 || addAll.getExpression() == null) {
            return false;
        }
        ClassInstanceCreation creation = creation(previous, addAll.getExpression());
        if (creation == null || !eligibleType(creation) || !validConstructor(creation)) {
            return false;
        }
        ListRewrite arguments = rewrite.getListRewrite(
                creation, ClassInstanceCreation.ARGUMENTS_PROPERTY);
        arguments.insertLast(rewrite.createMoveTarget((Expression) addAll.arguments().get(0)), null);
        rewrite.remove(current, null);
        return true;
    }

    /**
     * Finds the collection creation associated with an add-all receiver.
     *
     * @param previous the preceding statement
     * @param receiver the add-all receiver
     * @return the matching constructor, or {@code null} when it is not a local creation
     */
    private ClassInstanceCreation creation(Statement previous, Expression receiver) {
        String receiverName = receiver instanceof SimpleName name ? name.getIdentifier() : null;
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
     * Checks whether a constructor is one of the known collection implementations.
     *
     * @param creation the collection constructor
     * @return {@code true} for a supported concrete collection type
     */
    private boolean eligibleType(ClassInstanceCreation creation) {
        String text = creation.getType().toString();
        int genericStart = text.indexOf('<');
        String base = genericStart < 0 ? text : text.substring(0, genericStart);
        int nameSeparator = base.lastIndexOf('.');
        return COLLECTION_TYPES.contains(nameSeparator < 0 ? base : base.substring(nameSeparator + 1));
    }

    /**
     * Checks that no existing constructor argument would be overwritten.
     *
     * @param creation the collection constructor
     * @return {@code true} when the constructor has no arguments
     */
    private boolean validConstructor(ClassInstanceCreation creation) {
        return creation.getAnonymousClassDeclaration() == null && creation.arguments().isEmpty();
    }
}
