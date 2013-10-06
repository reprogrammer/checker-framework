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

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@TypeQualifiers( { Value.class, Odd.class, MonotonicOdd.class, Unqualified.class, Bottom.class } )
public final class FlowTestChecker extends BaseTypeChecker<tests.util.FlowTestChecker.FlowAnnotatedTypeFactory> {

    protected AnnotationMirror VALUE, BOTTOM;

    @Override
    public void initChecker() {
        Elements elements = processingEnv.getElementUtils();
        VALUE = AnnotationUtils.fromClass(elements, Value.class);
        BOTTOM = AnnotationUtils.fromClass(elements, Bottom.class);

        super.initChecker();
    }

    @Override
    public FlowAnnotatedTypeFactory createFactory(CompilationUnitTree tree) {
        return new FlowAnnotatedTypeFactory(this, tree);
    }

    class FlowAnnotatedTypeFactory extends SubtypingAnnotatedTypeFactory<FlowTestChecker> {
        public FlowAnnotatedTypeFactory(FlowTestChecker checker, CompilationUnitTree root) {
            super(checker, root, true);
            this.typeAnnotator.addTypeName(java.lang.Void.class, BOTTOM);
            this.treeAnnotator.addTreeKind(com.sun.source.tree.Tree.Kind.NULL_LITERAL, BOTTOM);
            this.postInit();
        }
    }

    @Override
    public QualifierHierarchy createQualifierHierarchy(MultiGraphFactory factory) {
        return new FlowQualifierHierarchy(factory);
    }

    private final class FlowQualifierHierarchy extends GraphQualifierHierarchy {

        public FlowQualifierHierarchy(MultiGraphFactory f) {
            super(f, BOTTOM);
        }

        @Override
        public boolean isSubtype(AnnotationMirror rhs, AnnotationMirror lhs) {
            if (AnnotationUtils.areSameIgnoringValues(lhs, VALUE) &&
                    AnnotationUtils.areSameIgnoringValues(rhs, VALUE)) {
                return AnnotationUtils.areSame(lhs, rhs);
            }
            if (AnnotationUtils.areSameIgnoringValues(lhs, VALUE)) {
                lhs = VALUE;
            }
            if (AnnotationUtils.areSameIgnoringValues(rhs, VALUE)) {
                rhs = VALUE;
            }
            return super.isSubtype(rhs, lhs);
        }
    }
}
