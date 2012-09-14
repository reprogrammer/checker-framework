package checkers.nonnull;

import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.VariableElement;

import checkers.basetype.BaseTypeChecker;
import checkers.initialization.InitializationAnnotatedTypeFactory;
import checkers.nonnull.quals.MonotonicNonNull;
import checkers.nonnull.quals.NonNull;
import checkers.nonnull.quals.Nullable;
import checkers.types.AnnotatedTypeMirror;
import checkers.types.AnnotatedTypeMirror.AnnotatedExecutableType;
import checkers.types.GeneralAnnotatedTypeFactory;
import checkers.types.TreeAnnotator;
import checkers.types.TypeAnnotator;
import checkers.util.ElementUtils;
import checkers.util.Pair;
import checkers.util.TreeUtils;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.util.TreePath;

public class NonNullAnnotatedTypeFactory
        extends
        InitializationAnnotatedTypeFactory<AbstractNonNullChecker, NonNullTransfer, NonNullAnalysis> {

    /** Annotation constants */
    protected final AnnotationMirror NONNULL, NULLABLE;

    protected final MapGetHeuristics mapGetHeuristics;
    protected final SystemGetPropertyHandler systemGetPropertyHandler;
    protected final CollectionToArrayHeuristics collectionToArrayHeuristics;

    /** Factory for arbitrary qualifiers, used for declarations and "unused" qualifier. */
    protected final GeneralAnnotatedTypeFactory generalFactory;

    public NonNullAnnotatedTypeFactory(AbstractNonNullChecker checker,
            CompilationUnitTree root) {
        super(checker, root);

        NONNULL = this.annotations.fromClass(NonNull.class);
        NULLABLE = this.annotations.fromClass(Nullable.class);

        // aliases with checkers.nullness.quals qualifiers
        addAliasedAnnotation(checkers.nullness.quals.NonNull.class, NONNULL);
        addAliasedAnnotation(checkers.nullness.quals.Nullable.class, NULLABLE);

        addAliasedAnnotation(checkers.nullness.quals.LazyNonNull.class,
                annotations.fromClass(MonotonicNonNull.class));

        // aliases borrowed from NullnessAnnotatedTypeFactory
        addAliasedAnnotation(com.sun.istack.NotNull.class, NONNULL);
        addAliasedAnnotation(edu.umd.cs.findbugs.annotations.NonNull.class,
                NONNULL);
        addAliasedAnnotation(javax.annotation.Nonnull.class, NONNULL);
        addAliasedAnnotation(javax.validation.constraints.NotNull.class,
                NONNULL);
        addAliasedAnnotation(org.jetbrains.annotations.NotNull.class, NONNULL);
        addAliasedAnnotation(org.netbeans.api.annotations.common.NonNull.class,
                NONNULL);
        addAliasedAnnotation(org.jmlspecs.annotation.NonNull.class, NONNULL);
        addAliasedAnnotation(com.sun.istack.Nullable.class, NULLABLE);
        addAliasedAnnotation(
                edu.umd.cs.findbugs.annotations.CheckForNull.class, NULLABLE);
        addAliasedAnnotation(edu.umd.cs.findbugs.annotations.Nullable.class,
                NULLABLE);
        addAliasedAnnotation(
                edu.umd.cs.findbugs.annotations.UnknownNullness.class, NULLABLE);
        addAliasedAnnotation(javax.annotation.CheckForNull.class, NULLABLE);
        addAliasedAnnotation(javax.annotation.Nullable.class, NULLABLE);
        addAliasedAnnotation(org.jetbrains.annotations.Nullable.class, NULLABLE);
        addAliasedAnnotation(
                org.netbeans.api.annotations.common.CheckForNull.class,
                NULLABLE);
        addAliasedAnnotation(
                org.netbeans.api.annotations.common.NullAllowed.class, NULLABLE);
        addAliasedAnnotation(
                org.netbeans.api.annotations.common.NullUnknown.class, NULLABLE);
        addAliasedAnnotation(org.jmlspecs.annotation.Nullable.class, NULLABLE);

        // TODO: These heuristics are just here temporarily. They all either
        // need to be replaced, or carefully checked for correctness.
        generalFactory = new GeneralAnnotatedTypeFactory(checker, root);
        mapGetHeuristics = new MapGetHeuristics(env, this, generalFactory);
        systemGetPropertyHandler = new SystemGetPropertyHandler(env, this);
        // do this last, as it might use the factory again.
        this.collectionToArrayHeuristics = new CollectionToArrayHeuristics(env,
                this);

        postInit();
    }

    @Override
    public Pair<AnnotatedExecutableType, List<AnnotatedTypeMirror>> methodFromUse(
            MethodInvocationTree tree) {
        Pair<AnnotatedExecutableType, List<AnnotatedTypeMirror>> mfuPair = super
                .methodFromUse(tree);
        AnnotatedExecutableType method = mfuPair.first;

        TreePath path = this.getPath(tree);
        if (path != null) {
            /*
             * The above check for null ensures that Issue 109 does not arise.
             * TODO: I'm a bit concerned about one aspect: it looks like the
             * field initializer is used to determine the type of a read field.
             * Why is this not just using the declared type of the field? Could
             * this lead to confusion for programmers? I think skipping the
             * mapGetHeuristics is always a safe option.
             */
            mapGetHeuristics.handle(path, method);
        }
        systemGetPropertyHandler.handle(tree, method);
        collectionToArrayHeuristics.handle(tree, method);
        return mfuPair;
    }

    protected AnnotatedTypeMirror getDeclaredAndDefaultedAnnotatedType(Tree tree) {
        HACK_DONT_CALL_POST_AS_MEMBER = true;
        shouldCache = false;

        AnnotatedTypeMirror type = getAnnotatedType(tree);

        shouldCache = true;
        HACK_DONT_CALL_POST_AS_MEMBER = false;

        return type;
    }

    @Override
    protected TypeAnnotator createTypeAnnotator(AbstractNonNullChecker checker) {
        return new NonNullTypeAnnotator(checker);
    }

    @Override
    protected TreeAnnotator createTreeAnnotator(AbstractNonNullChecker checker) {
        return new NonNullTreeAnnotator(checker);
    }

    /**
     * If the element is {@link NonNull} when used in a static member access,
     * modifies the element's type (by adding {@link NonNull}).
     *
     * @param elt
     *            the element being accessed
     * @param type
     *            the type of the element {@code elt}
     */
    private void annotateIfStatic(Element elt, AnnotatedTypeMirror type) {
        if (elt == null)
            return;

        if (elt.getKind().isClass() || elt.getKind().isInterface()
        // Workaround for System.{out,in,err} issue: assume all static
        // fields in java.lang.System are nonnull.
                || isSystemField(elt)) {
            type.replaceAnnotation(NONNULL);
        }
    }

    private static boolean isSystemField(Element elt) {
        if (!elt.getKind().isField())
            return false;

        if (!ElementUtils.isStatic(elt) || !ElementUtils.isFinal(elt))
            return false;

        VariableElement var = (VariableElement) elt;

        // Heuristic: if we have a static final field in a system package,
        // treat it as NonNull (many like Boolean.TYPE and System.out
        // have constant value null but are set by the VM).
        boolean inJavaPackage = ElementUtils.getQualifiedClassName(var)
                .toString().startsWith("java.");

        return (var.getConstantValue() != null
                || var.getSimpleName().contentEquals("class") || inJavaPackage);
    }

    private static boolean isExceptionParameter(IdentifierTree node) {
        Element elt = TreeUtils.elementFromUse(node);
        assert elt != null;
        return elt.getKind() == ElementKind.EXCEPTION_PARAMETER;
    }

    protected class NonNullTreeAnnotator
            extends
            InitializationAnnotatedTypeFactory<AbstractNonNullChecker, NonNullTransfer, NonNullAnalysis>.CommitmentTreeAnnotator {
        public NonNullTreeAnnotator(BaseTypeChecker checker) {
            super(checker);
        }

        @Override
        public Void visitMemberSelect(MemberSelectTree node,
                AnnotatedTypeMirror type) {

            Element elt = TreeUtils.elementFromUse(node);
            assert elt != null;
            // case 8: class in static member access
            annotateIfStatic(elt, type);
            return super.visitMemberSelect(node, type);
        }

        @Override
        public Void visitIdentifier(IdentifierTree node,
                AnnotatedTypeMirror type) {

            Element elt = TreeUtils.elementFromUse(node);
            assert elt != null;

            // case 8. static method access
            annotateIfStatic(elt, type);

            // Workaround: exception parameters should be implicitly
            // NonNull, but due to a compiler bug they have
            // kind PARAMETER instead of EXCEPTION_PARAMETER. Until it's
            // fixed we manually inspect enclosing catch blocks.
            // case 9. exception parameter
            if (isExceptionParameter(node))
                type.addAnnotation(NONNULL);

            return super.visitIdentifier(node, type);
        }

        // The result of a binary operation is always non-null.
        @Override
        public Void visitBinary(BinaryTree node, AnnotatedTypeMirror type) {
            type.replaceAnnotation(NONNULL);
            return null; // super.visitBinary(node, type);
        }

        // The result of a compound operation is always non-null.
        @Override
        public Void visitCompoundAssignment(CompoundAssignmentTree node, AnnotatedTypeMirror type) {
            type.replaceAnnotation(NONNULL);
            return null; // super.visitCompoundAssignment(node, type);
        }

        // The result of a unary operation is always non-null.
        @Override
        public Void visitUnary(UnaryTree node, AnnotatedTypeMirror type) {
            type.replaceAnnotation(NONNULL);
            return null; // super.visitUnary(node, type);
        }
    }

    protected class NonNullTypeAnnotator
            extends
            InitializationAnnotatedTypeFactory<AbstractNonNullChecker, NonNullTransfer, NonNullAnalysis>.CommitmentTypeAnnotator {
        public NonNullTypeAnnotator(BaseTypeChecker checker) {
            super(checker);
        }
    }
}
