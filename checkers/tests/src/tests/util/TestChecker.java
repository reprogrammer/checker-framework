package tests.util;

import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.util.Elements;

import javacutils.AnnotationUtils;

import checkers.basetype.BaseTypeChecker;
import checkers.quals.Bottom;
import checkers.quals.TypeQualifiers;
import checkers.quals.Unqualified;
import checkers.types.SubtypingAnnotatedTypeFactory;
import checkers.types.QualifierHierarchy;
import checkers.util.GraphQualifierHierarchy;
import checkers.util.MultiGraphQualifierHierarchy.MultiGraphFactory;

import com.sun.source.tree.CompilationUnitTree;

/**
 * A simple checker used for testing the Checker Framework. It treats the
 * {@code @Odd} and {@code @Even} annotations as a subtype-style qualifiers with
 * no special semantics.
 *
 * <p>
 * This checker should only be used for testing the framework.
 */
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@TypeQualifiers({ Odd.class, MonotonicOdd.class, Even.class, Unqualified.class,
        Bottom.class })
public final class TestChecker extends BaseTypeChecker<tests.util.TestChecker.FrameworkTestAnnotatedTypeFactory> {

    protected AnnotationMirror BOTTOM;

    @Override
    public void initChecker() {
        Elements elements = processingEnv.getElementUtils();
        BOTTOM = AnnotationUtils.fromClass(elements, Bottom.class);
        super.initChecker();
    }

    @Override
    public FrameworkTestAnnotatedTypeFactory createFactory(CompilationUnitTree tree) {
        return new FrameworkTestAnnotatedTypeFactory(this, tree);
    }

    class FrameworkTestAnnotatedTypeFactory extends SubtypingAnnotatedTypeFactory<TestChecker> {
        public FrameworkTestAnnotatedTypeFactory(TestChecker checker, CompilationUnitTree root) {
            super(checker, root, true);
            this.typeAnnotator.addTypeName(java.lang.Void.class, BOTTOM);
            this.treeAnnotator.addTreeKind(com.sun.source.tree.Tree.Kind.NULL_LITERAL, BOTTOM);
            this.postInit();
        }
    }

    @Override
    public QualifierHierarchy createQualifierHierarchy(MultiGraphFactory factory) {
        return new GraphQualifierHierarchy(factory, BOTTOM);
    }
}
