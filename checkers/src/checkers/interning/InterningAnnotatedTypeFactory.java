package checkers.interning;

import checkers.basetype.BaseAnnotatedTypeFactory;
import checkers.basetype.BaseTypeChecker;
import checkers.interning.quals.Interned;
import checkers.interning.quals.PolyInterned;
import checkers.quals.DefaultQualifier;
import checkers.quals.ImplicitFor;
import checkers.quals.Unqualified;
import checkers.types.AnnotatedTypeFactory;
import checkers.types.AnnotatedTypeMirror;
import checkers.types.AnnotatedTypeMirror.AnnotatedDeclaredType;
import checkers.types.AnnotatedTypeMirror.AnnotatedPrimitiveType;
import checkers.types.TreeAnnotator;
import checkers.types.TypeAnnotator;

import javacutils.AnnotationUtils;
import javacutils.ElementUtils;
import javacutils.TreeUtils;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.Tree;

/**
 * An {@link AnnotatedTypeFactory} that accounts for the properties of the
 * Interned type system. This type factory will add the {@link Interned}
 * annotation to a type if the input:
 *
 * <ol>
 * <li value="1">is a String literal
 * <li value="2">is a class literal
 * <li value="3">has an enum type
 * <li value="4">has a primitive type
 * <li value="5">has the type java.lang.Class
 * </ol>
 *
 * This factory extends {@link BaseAnnotatedTypeFactory} and inherits its
 * functionality, including: flow-sensitive qualifier inference, qualifier
 * polymorphism (of {@link PolyInterned}), implicit annotations via
 * {@link ImplicitFor} on {@link Interned} (to handle cases 1, 2, 4), and
 * user-specified defaults via {@link DefaultQualifier}.
 * Case 5 is handled by the stub library.
 */
public class InterningAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

    /** The {@link Interned} annotation. */
    final AnnotationMirror INTERNED, UNQUALIFIED;

    /**
     * Creates a new {@link InterningAnnotatedTypeFactory} that operates on a
     * particular AST.
     *
     * @param checker the checker to use
     */
    public InterningAnnotatedTypeFactory(BaseTypeChecker checker) {
        super(checker);
        this.INTERNED = AnnotationUtils.fromClass(elements, Interned.class);
        this.UNQUALIFIED = AnnotationUtils.fromClass(elements, Unqualified.class);

        // If you update the following, also update ../../../manual/interning-checker.tex .
        addAliasedAnnotation(com.sun.istack.Interned.class, INTERNED);

        this.postInit();

        // The null literal is interned -> make Void interned also.
        typeAnnotator.addTypeName(java.lang.Void.class, INTERNED);
    }

    @Override
    protected TreeAnnotator createTreeAnnotator() {
        return new InterningTreeAnnotator(this);
    }

    @Override
    protected TypeAnnotator createTypeAnnotator() {
        return new InterningTypeAnnotator(this);
    }

    @Override
    public void annotateImplicit(Element element, AnnotatedTypeMirror type) {
        if (!type.isAnnotatedInHierarchy(INTERNED) && ElementUtils.isCompileTimeConstant(element))
            type.addAnnotation(INTERNED);
        super.annotateImplicit(element, type);
    }

    /**
     * A class for adding annotations based on tree
     */
    private class InterningTreeAnnotator  extends TreeAnnotator {

        InterningTreeAnnotator(InterningAnnotatedTypeFactory atypeFactory) {
            super(atypeFactory);
        }

        @Override
        public Void visitBinary(BinaryTree node, AnnotatedTypeMirror type) {
            if (TreeUtils.isCompileTimeString(node)) {
                type.replaceAnnotation(INTERNED);
            } else if (TreeUtils.isStringConcatenation(node)) {
                type.replaceAnnotation(UNQUALIFIED);
            } else if ((type.getKind().isPrimitive()) ||
                    node.getKind() == Tree.Kind.EQUAL_TO ||
                    node.getKind() == Tree.Kind.NOT_EQUAL_TO) {
                type.replaceAnnotation(INTERNED);
            } else {
                type.replaceAnnotation(UNQUALIFIED);
            }
            return super.visitBinary(node, type);
        }

        /* Compound assignments never result in an interned result.
         */
        @Override
        public Void visitCompoundAssignment(CompoundAssignmentTree node, AnnotatedTypeMirror type) {
          type.replaceAnnotation(UNQUALIFIED);
          return super.visitCompoundAssignment(node, type);
        }
    }

    /**
     * A class for adding annotations to a type after initial type resolution.
     */
    private class InterningTypeAnnotator extends TypeAnnotator {

        /** Creates an {@link InterningTypeAnnotator} for the given checker. */
        InterningTypeAnnotator(InterningAnnotatedTypeFactory atypeFactory) {
            super(atypeFactory);
        }

        @Override
        public Void visitDeclared(AnnotatedDeclaredType t, Element elem) {

            // case 3: Enum types, and the Enum class itself, are interned
            Element elt = t.getUnderlyingType().asElement();
            assert elt != null;
            if (elt.getKind() == ElementKind.ENUM) {
                t.replaceAnnotation(INTERNED);
            }

            return super.visitDeclared(t, elem);
        }
    }

    @Override
    public AnnotatedPrimitiveType getUnboxedType(AnnotatedDeclaredType type) {
        AnnotatedPrimitiveType primitive = super.getUnboxedType(type);
        primitive.replaceAnnotation(INTERNED);
        return primitive;
    }
}
