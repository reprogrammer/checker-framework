package checkers.nonnull;

import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

import checkers.compilermsgs.quals.CompilerMessageKey;
import checkers.initialization.InitializationVisitor;
import checkers.nonnull.quals.NonNull;
import checkers.source.Result;
import checkers.types.AnnotatedTypeMirror;
import checkers.types.AnnotatedTypeMirror.AnnotatedExecutableType;
import checkers.util.InternalUtils;
import checkers.util.TreeUtils;

import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.AssertTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;

// TODO/later: documentation
// Note: this code is originally based on NullnessVisitor
public class NonNullVisitor extends
        InitializationVisitor<AbstractNonNullChecker> {

    // Error message keys
    private static final String ASSIGNMENT_TYPE_INCOMPATIBLE = "assignment.type.incompatible";
    private static final String UNBOXING_OF_NULLABLE = "unboxing.of.nullable";
    private static final String KNOWN_NONNULL = "known.nonnull";
    private static final String LOCKING_NULLABLE = "locking.nullable";
    private static final String THROWING_NULLABLE = "throwing.nullable";
    private static final String ACCESSING_NULLABLE = "accessing.nullable";
    private static final String DEREFERENCE_OF_NULLABLE = "dereference.of.nullable";

    // Annotation and type constants
    private final AnnotationMirror NONNULL, NULLABLE, MONONONNULL;
    private final TypeMirror stringType;

    public NonNullVisitor(AbstractNonNullChecker checker,
            CompilationUnitTree root) {
        super(checker, root);

        NONNULL = checker.NONNULL;
        NULLABLE = checker.NULLABLE;
        MONONONNULL = checker.MONONONNULL;
        stringType = elements.getTypeElement("java.lang.String").asType();
        checkForAnnotatedJdk();
    }

    @Override
    protected void commonAssignmentCheck(Tree varTree, ExpressionTree valueExp,
            String errorKey) {

        // allow MonotonicNonNull to be initialized to null at declaration
        if (varTree.getKind() == Tree.Kind.VARIABLE) {
            Element elem = TreeUtils
                    .elementFromDeclaration((VariableTree) varTree);
            if (atypeFactory.fromElement(elem).hasAnnotation(MONONONNULL)
                    && !checker.getLintOption("strictmonoinit",
                            AbstractNonNullChecker.LINT_DEFAULT_STRICTMONOINIT)) {
                return;
            }
        }

        if (TreeUtils.isFieldAccess(varTree)) {
            AnnotatedTypeMirror valueType = atypeFactory
                    .getAnnotatedType(valueExp);
            // special case writing to NonNull field for free/unc receivers
            // cast is safe, because varTree is a field
            AnnotatedTypeMirror annos = getNonNullFactory()
                    .getDeclaredAndDefaultedAnnotatedType(varTree);
            // receiverType is null for static field accesses
            AnnotatedTypeMirror receiverType = atypeFactory
                    .getReceiverType((ExpressionTree) varTree);
            if (receiverType != null
                    && (checker.isFree(receiverType) || checker
                            .isUnclassified(receiverType))) {
                if (annos.hasAnnotation(NONNULL)
                        && !valueType.hasAnnotation(NONNULL)) {
                    checker.report(Result.failure(ASSIGNMENT_TYPE_INCOMPATIBLE,
                            atypeFactory.getAnnotatedType(valueExp).toString(),
                            annos.toString()), varTree);
                }
            }
        }
        super.commonAssignmentCheck(varTree, valueExp, errorKey);
    }

    private NonNullAnnotatedTypeFactory getNonNullFactory() {
        return (NonNullAnnotatedTypeFactory) atypeFactory;
    }

    /** Case 1: Check for null dereferencing */
    @Override
    public Void visitMemberSelect(MemberSelectTree node, Void p) {
        if (!TreeUtils.isSelfAccess(node))
            checkForNullability(node.getExpression(), DEREFERENCE_OF_NULLABLE);

        return super.visitMemberSelect(node, p);
    }

    /** Case 2: Check for implicit {@code .iterator} call */
    @Override
    public Void visitEnhancedForLoop(EnhancedForLoopTree node, Void p) {
        checkForNullability(node.getExpression(), DEREFERENCE_OF_NULLABLE);
        return super.visitEnhancedForLoop(node, p);
    }

    /** Case 3: Check for array dereferencing */
    @Override
    public Void visitArrayAccess(ArrayAccessTree node, Void p) {
        checkForNullability(node.getExpression(), ACCESSING_NULLABLE);
        return super.visitArrayAccess(node, p);
    }

    /** Case 4: Check for thrown exception nullness */
    @Override
    public Void visitThrow(ThrowTree node, Void p) {
        checkForNullability(node.getExpression(), THROWING_NULLABLE);
        return super.visitThrow(node, p);
    }

    /** Case 5: Check for synchronizing locks */
    @Override
    public Void visitSynchronized(SynchronizedTree node, Void p) {
        checkForNullability(node.getExpression(), LOCKING_NULLABLE);
        return super.visitSynchronized(node, p);
    }

    @Override
    public Void visitAssert(AssertTree node, Void p) {
        return super.visitAssert(node, p);
    }

    @Override
    public Void visitIf(IfTree node, Void p) {
        return super.visitIf(node, p);
    }

    /**
     * @return Reports an error if a comparison of a @NonNull expression with
     *         the null literal is performed.
     */
    protected void checkForRedundantTests(BinaryTree node) {

        final ExpressionTree leftOp = node.getLeftOperand();
        final ExpressionTree rightOp = node.getRightOperand();

        // equality tests
        if ((node.getKind() == Tree.Kind.EQUAL_TO || node.getKind() == Tree.Kind.NOT_EQUAL_TO)) {
            AnnotatedTypeMirror left = atypeFactory.getAnnotatedType(leftOp);
            AnnotatedTypeMirror right = atypeFactory.getAnnotatedType(rightOp);
            if (leftOp.getKind() == Tree.Kind.NULL_LITERAL
                    && right.hasAnnotation(NONNULL))
                checker.report(
                        Result.warning(KNOWN_NONNULL, rightOp.toString()), node);
            else if (rightOp.getKind() == Tree.Kind.NULL_LITERAL
                    && left.hasAnnotation(NONNULL))
                checker.report(
                        Result.warning(KNOWN_NONNULL, leftOp.toString()), node);
        }
    }

    /**
     * Case 6: Check for redundant nullness tests Case 7: unboxing case:
     * primitive operations
     */
    @Override
    public Void visitBinary(BinaryTree node, Void p) {
        final ExpressionTree leftOp = node.getLeftOperand();
        final ExpressionTree rightOp = node.getRightOperand();

        if (isUnboxingOperation(node)) {
            checkForNullability(leftOp, UNBOXING_OF_NULLABLE);
            checkForNullability(rightOp, UNBOXING_OF_NULLABLE);
        }

        checkForRedundantTests(node);

        return super.visitBinary(node, p);
    }

    /** Case 7: unboxing case: primitive operation */
    @Override
    public Void visitUnary(UnaryTree node, Void p) {
        checkForNullability(node.getExpression(), UNBOXING_OF_NULLABLE);
        return super.visitUnary(node, p);
    }

    /** Case 7: unboxing case: primitive operation */
    @Override
    public Void visitCompoundAssignment(CompoundAssignmentTree node, Void p) {
        // ignore String concatenation
        if (!isString(node)) {
            checkForNullability(node.getVariable(), UNBOXING_OF_NULLABLE);
            checkForNullability(node.getExpression(), UNBOXING_OF_NULLABLE);
        }
        return super.visitCompoundAssignment(node, p);
    }

    /** Case 7: unboxing case: casting to a primitive */
    @Override
    public Void visitTypeCast(TypeCastTree node, Void p) {
        if (isPrimitive(node) && !isPrimitive(node.getExpression()))
            checkForNullability(node.getExpression(), UNBOXING_OF_NULLABLE);
        return super.visitTypeCast(node, p);
    }

    // ///////////// Utility methods //////////////////////////////

    /**
     * Issues a 'dereference.of.nullable' if the type is not of a
     * {@link NonNull} type.
     *
     * @param type
     *            type to be checked nullability
     * @param errMsg
     *            the error message (must be {@link CompilerMessageKey})
     * @param tree
     *            the tree where the error is to reported
     */
    private void checkForNullability(ExpressionTree tree,
    /* @CompilerMessageKey */String errMsg) {
        AnnotatedTypeMirror type = atypeFactory.getAnnotatedType(tree);
        if (!type.hasAnnotation(NONNULL))
            checker.report(Result.failure(errMsg, tree), tree);
    }

    @Override
    protected boolean checkMethodInvocability(AnnotatedExecutableType method,
            MethodInvocationTree node) {
        if (!TreeUtils.isSelfAccess(node)) {
            Set<AnnotationMirror> recvAnnos = atypeFactory
                    .getReceiverType(node).getAnnotations();
            // If receiver is Nullable, then we don't want to issue a warning
            // about method invocability (we'd rather have only the
            // "dereference.of.nullable" message).
            if (recvAnnos.contains(NULLABLE) || recvAnnos.contains(MONONONNULL)) {
                return true;
            }
        }
        return super.checkMethodInvocability(method, node);
    }

    /** @return true if binary operation could cause an unboxing operation */
    private final boolean isUnboxingOperation(BinaryTree tree) {
        if (tree.getKind() == Tree.Kind.EQUAL_TO
                || tree.getKind() == Tree.Kind.NOT_EQUAL_TO) {
            // it is valid to check equality between two reference types, even
            // if one (or both) of them is null
            return isPrimitive(tree.getLeftOperand()) != isPrimitive(tree
                    .getRightOperand());
        } else {
            // All BinaryTree's are of type String, a primitive type or the
            // reference type equivalent of a primitive type. Furthermore,
            // Strings don't have a primitive type, and therefore only
            // BinaryTrees that aren't String can cause unboxing.
            return !isString(tree);
        }
    }

    /**
     * @return true if the type of the tree is a super of String
     * */
    private final boolean isString(ExpressionTree tree) {
        TypeMirror type = InternalUtils.typeOf(tree);
        return types.isAssignable(stringType, type);
    }

    /**
     * @return true if the type of the tree is a primitive
     */
    private static final boolean isPrimitive(ExpressionTree tree) {
        return InternalUtils.typeOf(tree).getKind().isPrimitive();
    }

}
