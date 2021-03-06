package org.checkerframework.framework.type;

/*>>>
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.interning.qual.*;
*/

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.NullType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.UnionType;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import org.checkerframework.dataflow.qual.Pure;
import org.checkerframework.dataflow.qual.SideEffectFree;
import org.checkerframework.framework.qual.InvisibleQualifier;
import org.checkerframework.framework.qual.TypeQualifier;
import org.checkerframework.framework.type.visitor.AnnotatedTypeScanner;
import org.checkerframework.framework.type.visitor.AnnotatedTypeVisitor;
import org.checkerframework.framework.type.visitor.SimpleAnnotatedTypeVisitor;
import org.checkerframework.framework.util.AnnotatedTypes;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.ErrorReporter;
import org.checkerframework.javacutil.TreeUtils;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
/**
 * Represents an annotated type in the Java programming language.
 * Types include primitive types, declared types (class and interface types),
 * array types, type variables, and the null type.
 * Also represented are wildcard type arguments,
 * the signature and return types of executables,
 * and pseudo-types corresponding to packages and to the keyword {@code void}.
 *
 * <p> Types should be compared using the utility methods in {@link
 * AnnotatedTypes}.  There is no guarantee that any particular type will always
 * be represented by the same object.
 *
 * <p> To implement operations based on the class of an {@code
 * AnnotatedTypeMirror} object, either
 * use a visitor or use the result of the {@link #getKind()} method.
 *
 * @see TypeMirror
 */
public abstract class AnnotatedTypeMirror {

    /**
     * Creates the appropriate AnnotatedTypeMirror specific wrapper for the
     * provided type
     *
     * @param type
     * @param atypeFactory
     * @return [to document]
     */
    public static AnnotatedTypeMirror createType(TypeMirror type,
        AnnotatedTypeFactory atypeFactory) {
        if (type == null) {
            ErrorReporter.errorAbort("AnnotatedTypeMirror.createType: input type must not be null!");
            return null;
        }

        com.sun.tools.javac.code.Type jctype = ((com.sun.tools.javac.code.Type)type);
        type = jctype.unannotatedType();

        AnnotatedTypeMirror result;
        switch (type.getKind()) {
            case ARRAY:
                result = new AnnotatedArrayType((ArrayType) type, atypeFactory);
                break;
            case DECLARED:
                result = new AnnotatedDeclaredType((DeclaredType) type, atypeFactory);
                break;
            case ERROR:
                ErrorReporter.errorAbort("AnnotatedTypeMirror.createType: input should type-check already! Found error type: " + type);
                return null; // dead code
            case EXECUTABLE:
                result = new AnnotatedExecutableType((ExecutableType) type, atypeFactory);
                break;
            case VOID:
            case PACKAGE:
            case NONE:
                result = new AnnotatedNoType((NoType) type, atypeFactory);
                break;
            case NULL:
                result = new AnnotatedNullType((NullType) type, atypeFactory);
                break;
            case TYPEVAR:
                result = new AnnotatedTypeVariable((TypeVariable) type, atypeFactory);
                break;
            case WILDCARD:
                result = new AnnotatedWildcardType((WildcardType) type, atypeFactory);
                break;
            case INTERSECTION:
                result = new AnnotatedIntersectionType((IntersectionType) type, atypeFactory);
                break;
            case UNION:
                result = new AnnotatedUnionType((UnionType) type, atypeFactory);
                break;
            default:
                if (type.getKind().isPrimitive()) {
                    result = new AnnotatedPrimitiveType((PrimitiveType) type, atypeFactory);
                    break;
                }
                ErrorReporter.errorAbort("AnnotatedTypeMirror.createType: unidentified type " +
                        type + " (" + type.getKind() + ")");
                return null; // dead code
        }
        /*if (jctype.isAnnotated()) {
            result.addAnnotations(jctype.getAnnotationMirrors());
        }*/
        return result;
    }

    /** The factory to use for lazily creating annotated types. */
    protected final AnnotatedTypeFactory atypeFactory;

    /** Actual type wrapped with this AnnotatedTypeMirror **/
    protected final TypeMirror actualType;

    /** The annotations on this type. */
    // AnnotationMirror doesn't override Object.hashCode, .equals, so we use
    // the class name of Annotation instead.
    // Caution: Assumes that a type can have at most one AnnotationMirror for
    // any Annotation type. JSR308 is pushing to have this change.
    protected final Set<AnnotationMirror> annotations = AnnotationUtils.createAnnotationSet();

    /** The explicitly written annotations on this type. */
    // TODO: use this to cache the result once computed? For generic types?
    // protected final Set<AnnotationMirror> explicitannotations = AnnotationUtils.createAnnotationSet();

    // If unique IDs are helpful, add these and the commented lines that use them.
    // private static int uidCounter = 0;
    // public int uid;

    /**
     * Constructor for AnnotatedTypeMirror.
     *
     * @param type  the underlying type
     * @param typeFactory used to create further types and to access
     *     global information (Types, Elements, ...)
     */
    private AnnotatedTypeMirror(TypeMirror type,
            AnnotatedTypeFactory atypeFactory) {
        this.actualType = type;
        assert atypeFactory != null;
        this.atypeFactory = atypeFactory;
        // uid = ++uidCounter;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AnnotatedTypeMirror))
            return false;
        AnnotatedTypeMirror t = (AnnotatedTypeMirror) o;
        if (atypeFactory.types.isSameType(this.actualType, t.actualType)
                && AnnotationUtils.areSame(getAnnotations(), t.getAnnotations()))
            return true;
        return false;
    }

    @Pure
    @Override
    public int hashCode() {
        return this.annotations.toString().hashCode() * 17
            + this.actualType.toString().hashCode() * 13;
    }

    /**
     * Applies a visitor to this type.
     *
     * @param <R>   the return type of the visitor's methods
     * @param <P>   the type of the additional parameter to the visitor's methods
     * @param v the visitor operating on this type
     * @param p additional parameter to the visitor
     * @return  a visitor-specified result
     */
    public abstract <R, P> R accept(AnnotatedTypeVisitor<R, P> v, P p);

    /**
     * Returns the {@code kind} of this type
     * @return the kind of this type
     */
    public TypeKind getKind() {
        return actualType.getKind();
    }

    /**
     * Returns the underlying unannotated Java type, which this wraps
     *
     * @return  the underlying type
     */
    public TypeMirror getUnderlyingType() {
        return actualType;
    }

    /**
     * Returns true if an annotation from the given sub-hierarchy targets this type.
     *
     * It doesn't account for annotations in deep types (type arguments,
     * array components, etc).
     *
     * @param p The qualifier hierarchy to check for.
     * @return True iff an annotation from the same hierarchy as p is present.
     */
    public boolean isAnnotatedInHierarchy(AnnotationMirror p) {
        return getAnnotationInHierarchy(p) != null;
    }

    /**
     * Returns an annotation from the given sub-hierarchy, if such
     * an annotation targets this type; otherwise returns null.
     *
     * It doesn't account for annotations in deep types (type arguments,
     * array components, etc).
     *
     * @param p The qualifier hierarchy to check for.
     * @return An annotation from the same hierarchy as p if present.
     */
    public AnnotationMirror getAnnotationInHierarchy(AnnotationMirror p) {
        AnnotationMirror aliased = p;
        if (!atypeFactory.isSupportedQualifier(aliased)) {
            aliased = atypeFactory.aliasedAnnotation(p);
        }
        if (atypeFactory.isSupportedQualifier(aliased)) {
            QualifierHierarchy qualHier = this.atypeFactory.getQualifierHierarchy();
            AnnotationMirror anno = qualHier.findCorrespondingAnnotation(aliased, annotations);
            if (anno != null) {
                return anno;
            }
        }
        return null;
    }

    /**
     * Returns an annotation from the given sub-hierarchy, if such
     * an annotation is present on this type or on its extends bounds;
     * otherwise returns null.
     *
     * It doesn't account for annotations in deep types (type arguments,
     * array components, etc).
     *
     * @param p The qualifier hierarchy to check for.
     * @return An annotation from the same hierarchy as p if present.
     */
    public AnnotationMirror getEffectiveAnnotationInHierarchy(AnnotationMirror p) {
        AnnotationMirror aliased = p;
        if (!atypeFactory.isSupportedQualifier(aliased)) {
            aliased = atypeFactory.aliasedAnnotation(p);
        }
        if (atypeFactory.isSupportedQualifier(aliased)) {
            QualifierHierarchy qualHier = this.atypeFactory.getQualifierHierarchy();
            AnnotationMirror anno = qualHier.findCorrespondingAnnotation(aliased,
                    getEffectiveAnnotations());
            if (anno != null) {
                return anno;
            }
        }
        return null;
    }

    /**
     * Returns the annotations on this type.
     *
     * It does not include annotations in deep types (type arguments, array
     * components, etc).
     *
     * @return  a set of the annotations on this
     */
    public Set<AnnotationMirror> getAnnotations() {
        return Collections.unmodifiableSet(annotations);
    }

    /**
     * Returns the "effective" annotations on this type, i.e. the annotations on
     * the type itself, or on the upper/extends bound of a type variable/wildcard
     * (recursively, until a class type is reached).
     *
     * @return  a set of the annotations on this
     */
    public Set<AnnotationMirror> getEffectiveAnnotations() {
        Set<AnnotationMirror> effectiveAnnotations = getErased().getAnnotations();
//        assert atypeFactory.qualHierarchy.getWidth() == effectiveAnnotations
//                .size() : "Invalid number of effective annotations ("
//                + effectiveAnnotations + "). Should be "
//                + atypeFactory.qualHierarchy.getWidth() + " but is "
//                + effectiveAnnotations.size() + ". Type: " + this.toString();
        return effectiveAnnotations;
    }

    /**
     * Returns the actual annotation mirror used to annotate this type,
     * whose name equals the passed annotationName if one exists, null otherwise.
     *
     * @param annotationName
     * @return the annotation mirror for annotationName
     */
    public AnnotationMirror getAnnotation(Name annotationName) {
        assert annotationName != null : "Null annotationName in getAnnotation";
        return getAnnotation(annotationName.toString().intern());
    }

    /**
     * Returns the actual annotation mirror used to annotate this type,
     * whose name equals the string argument if one exists, null otherwise.
     *
     * @param annotationStr
     * @return the annotation mirror for annotationStr
     */
    public AnnotationMirror getAnnotation(/*@Interned*/ String annotationStr) {
        assert annotationStr != null : "Null annotationName in getAnnotation";
        for (AnnotationMirror anno : getAnnotations())
            if (AnnotationUtils.areSameByName(anno, annotationStr))
                return anno;
        return null;
    }

    /**
     * Returns the actual annotation mirror used to annotate this type,
     * whose name equals the passed annotationName if one exists, null otherwise.
     *
     * @param annoClass annotation class
     * @return the annotation mirror for anno
     */
    public AnnotationMirror getAnnotation(Class<? extends Annotation> annoClass) {
        for (AnnotationMirror annoMirror : getAnnotations()) {
            if (AnnotationUtils.areSameByClass(annoMirror, annoClass)) {
                return annoMirror;
            }
        }
        return null;
    }

    /**
     * Returns the set of explicitly written annotations supported by this checker.
     * This is useful to check the validity of annotations explicitly present on a type,
     * as flow inference might add annotations that were not previously present.
     *
     * @return The set of explicitly written annotations supported by this checker.
     */
    public Set<AnnotationMirror> getExplicitAnnotations() {
        // TODO JSR 308: The explicit type annotations should be always present
        Set<AnnotationMirror> explicitAnnotations = AnnotationUtils.createAnnotationSet();
        List<? extends AnnotationMirror> typeAnnotations = this.getUnderlyingType().getAnnotationMirrors();

        Set<? extends AnnotationMirror> validAnnotations = atypeFactory.getQualifierHierarchy().getTypeQualifiers();
        for (AnnotationMirror explicitAnno : typeAnnotations) {
            for (AnnotationMirror validAnno : validAnnotations) {
                if (AnnotationUtils.areSameIgnoringValues(explicitAnno, validAnno)) {
                    explicitAnnotations.add(explicitAnno);
                }
            }
        }

        return explicitAnnotations;
    }

    /**
     * Determines whether this type contains the given annotation.
     * This method considers the annotation's values, that is,
     * if the type is "@A("s") @B(3) Object" a call with
     * "@A("t") or "@A" will return false, whereas a call with
     * "@B(3)" will return true.
     *
     * In contrast to {@link #hasAnnotationRelaxed(AnnotationMirror)}
     * this method also compares annotation values.
     *
     * @param a the annotation to check for
     * @return true iff the type contains the annotation {@code a}
     *
     * @see #hasAnnotationRelaxed(AnnotationMirror)
     */
    public boolean hasAnnotation(AnnotationMirror a) {
        return AnnotationUtils.containsSame(getAnnotations(), a);
    }

    /**
     * Determines whether this type contains the given annotation.
     *
     * @param a the annotation name to check for
     * @return true iff the type contains the annotation {@code a}
     *
     * @see #hasAnnotationRelaxed(AnnotationMirror)
     */
    public boolean hasAnnotation(Name a) {
        return getAnnotation(a) != null;
    }

    /**
     * Determines whether this type contains an annotation with the same
     * annotation type as a particular annotation. This method does not
     * consider an annotation's values.
     *
     * @param a the class of annotation to check for
     * @return true iff the type contains an annotation with the same type as
     * the annotation given by {@code a}
     */
    public boolean hasAnnotation(Class<? extends Annotation> a) {
        return getAnnotation(a) != null;
    }

    /**
     * A version of hasAnnotation that considers annotations on the
     * upper bound of wildcards and type variables.
     *
     * @see #hasAnnotation(Class)
     */
    public boolean hasEffectiveAnnotation(Class<? extends Annotation> a) {
        return AnnotationUtils.containsSameIgnoringValues(
                getEffectiveAnnotations(),
                AnnotationUtils.fromClass(atypeFactory.elements, a));
    }

    /**
     * A version of hasAnnotation that considers annotations on the
     * upper bound of wildcards and type variables.
     *
     * @see #hasAnnotation(AnnotationMirror)
     */
    public boolean hasEffectiveAnnotation(AnnotationMirror a) {
        return AnnotationUtils.containsSame(getEffectiveAnnotations(), a);
    }

    /**
     * Determines whether this type contains the given annotation
     * explicitly written at declaration. This method considers the
     * annotation's values, that is, if the type is
     * "@A("s") @B(3) Object" a call with "@A("t") or "@A" will
     * return false, whereas a call with "@B(3)" will return true.
     *
     * In contrast to {@link #hasExplicitAnnotationRelaxed(AnnotationMirror)}
     * this method also compares annotation values.
     *
     * @param a the annotation to check for
     * @return true iff the annotation {@code a} is explicitly written
     * on the type
     *
     * @see #hasExplicitAnnotationRelaxed(AnnotationMirror)
     */
    public boolean hasExplicitAnnotation(AnnotationMirror a) {
        return AnnotationUtils.containsSame(getExplicitAnnotations(), a);
    }

    /**
     * Determines whether this type contains an annotation with the same
     * annotation type as a particular annotation. This method does not
     * consider an annotation's values, that is,
     * if the type is "@A("s") @B(3) Object" a call with
     * "@A("t"), "@A", or "@B" will return true.
     *
     * @param a the annotation to check for
     * @return true iff the type contains an annotation with the same type as
     * the annotation given by {@code a}
     *
     * @see #hasAnnotation(AnnotationMirror)
     */
    public boolean hasAnnotationRelaxed(AnnotationMirror a) {
        return AnnotationUtils.containsSameIgnoringValues(getAnnotations(), a);
    }

    /**
     * A version of hasAnnotationRelaxed that considers annotations on the
     * upper bound of wildcards and type variables.
     *
     * @see #hasAnnotationRelaxed(AnnotationMirror)
     */
    public boolean hasEffectiveAnnotationRelaxed(AnnotationMirror a) {
        return AnnotationUtils.containsSameIgnoringValues(getEffectiveAnnotations(), a);
    }

    /**
     * A version of hasAnnotationRelaxed that only considers annotations that
     * are explicitly written on the type.
     *
     * @see #hasAnnotationRelaxed(AnnotationMirror)
     */
    public boolean hasExplicitAnnotationRelaxed(AnnotationMirror a) {
        return AnnotationUtils.containsSameIgnoringValues(getExplicitAnnotations(), a);
    }

    /**
     * Determines whether this type contains an explictly written annotation
     * with the same annotation type as a particular annotation. This method
     * does not consider an annotation's values.
     *
     * @param a the class of annotation to check for
     * @return true iff the type contains an explicitly written annotation
     * with the same type as the annotation given by {@code a}
     */
    public boolean hasExplicitAnnotation(Class<? extends Annotation> a) {
        return AnnotationUtils.containsSameIgnoringValues(getExplicitAnnotations(), getAnnotation(a));
    }

    /**
     * Adds an annotation to this type. If the annotation does not have the
     * {@link TypeQualifier} meta-annotation, this method has no effect.
     *
     * @param a the annotation to add
     */
    public void addAnnotation(AnnotationMirror a) {
        if (a == null) {
            ErrorReporter.errorAbort("AnnotatedTypeMirror.addAnnotation: null is not a valid annotation.");
        }
        if (atypeFactory.isSupportedQualifier(a)) {
            this.annotations.add(a);
        } else {
            AnnotationMirror aliased = atypeFactory.aliasedAnnotation(a);
            if (atypeFactory.isSupportedQualifier(aliased)) {
                addAnnotation(aliased);
            }
        }
    }

    /**
     * Adds an annotation to this type, removing any existing annotation from the
     * same qualifier hierarchy first.
     *
     * @param a the annotation to add
     */
    public void replaceAnnotation(AnnotationMirror a) {
        this.removeAnnotationInHierarchy(a);
        this.addAnnotation(a);
    }

    /**
     * Adds an annotation to this type. If the annotation does not have the
     * {@link TypeQualifier} meta-annotation, this method has no effect.
     *
     * @param a the class of the annotation to add
     */
    public void addAnnotation(Class<? extends Annotation> a) {
        AnnotationMirror anno = AnnotationUtils.fromClass(atypeFactory.elements, a);
        addAnnotation(anno);
    }

    /**
     * Adds multiple annotations to this type.
     *
     * @param annotations the annotations to add
     */
    public void addAnnotations(Iterable<? extends AnnotationMirror> annotations) {
        for (AnnotationMirror a : annotations) {
            this.addAnnotation(a);
        }
    }

    /**
     * Adds those annotations to the current type, for which no annotation
     * from the same qualifier hierarchy is present.
     *
     * @param annotations the annotations to add
     */
    public void addMissingAnnotations(Iterable<? extends AnnotationMirror> annotations) {
        for (AnnotationMirror a : annotations) {
            if (!this.isAnnotatedInHierarchy(a)) {
                this.addAnnotation(a);
            }
        }
    }

    /**
     * Adds multiple annotations to this type, removing any existing annotations from the
     * same qualifier hierarchy first.
     *
     * @param replAnnos the annotations to replace
     */
    public void replaceAnnotations(Iterable<? extends AnnotationMirror> replAnnos) {
        for (AnnotationMirror a : replAnnos) {
            this.removeAnnotationInHierarchy(a);
            this.addAnnotation(a);
        }
    }

    /**
     * Removes an annotation from the type.
     *
     * @param a the annotation to remove
     * @return true if the annotation was removed, false if the type's
     * annotations were unchanged
     */
    public boolean removeAnnotation(AnnotationMirror a) {
        // Going from the AnnotationMirror to its name and then calling
        // getAnnotation ensures that we get the canonical AnnotationMirror that can be
        // removed.
        // TODO: however, this also means that if we are annotated with "@I(1)" and
        // remove "@I(2)" it will be removed. Is this what we want?
        // It's currently necessary for the IGJ Checker and Lock Checker.
        AnnotationMirror anno = getAnnotation(AnnotationUtils.annotationName(a));
        if (anno != null) {
            return annotations.remove(anno);
        } else {
            return false;
        }
    }

    public boolean removeAnnotation(Class<? extends Annotation> a) {
        AnnotationMirror anno = AnnotationUtils.fromClass(atypeFactory.elements, a);
        if (anno == null || !atypeFactory.isSupportedQualifier(anno)) {
            ErrorReporter.errorAbort("AnnotatedTypeMirror.removeAnnotation called with un-supported class: " + a);
        }
        return removeAnnotation(anno);
    }

    /**
     * Remove any annotation that is in the same qualifier hierarchy as the parameter.
     *
     * @param a An annotation from the same qualifier hierarchy
     * @return If an annotation was removed
     */
    public boolean removeAnnotationInHierarchy(AnnotationMirror a) {
        AnnotationMirror prev = this.getAnnotationInHierarchy(a);
        if (prev != null) {
            return this.removeAnnotation(prev);
        }
        return false;
    }

    /**
     * Remove an annotation that is in the same qualifier hierarchy as the parameter,
     * unless it's the top annotation.
     *
     * @param a An annotation from the same qualifier hierarchy
     * @return If an annotation was removed
     */
    public boolean removeNonTopAnnotationInHierarchy(AnnotationMirror a) {
        AnnotationMirror prev = this.getAnnotationInHierarchy(a);
        QualifierHierarchy qualHier = this.atypeFactory.getQualifierHierarchy();
        if (prev != null && !prev.equals(qualHier.getTopAnnotation(a))) {
            return this.removeAnnotation(prev);
        }
        return false;
    }

    /**
     * Removes multiple annotations from the type.
     *
     * @param annotations
     *            the annotations to remove
     * @return true if at least one annotation was removed, false if the type's
     *         annotations were unchanged
     */
    public boolean removeAnnotations(Iterable<? extends AnnotationMirror> annotations) {
        boolean changed = false;
        for (AnnotationMirror a : annotations)
            changed |= this.removeAnnotation(a);
        return changed;
    }

    /**
     * Removes all annotations on this type.
     * Make sure to add an annotation again, e.g. Unqualified.
     *
     * This method should only be used in very specific situations.
     * For individual type systems, it is generally better to use
     * {@link #removeAnnotation(AnnotationMirror)}
     * and similar methods.
     */
    public void clearAnnotations() {
        annotations.clear();
    }

    private static boolean isInvisibleQualified(AnnotationMirror anno) {
        return ((TypeElement)anno.getAnnotationType().asElement()).getAnnotation(InvisibleQualifier.class) != null;
    }

    // A helper method to print annotations separated by a space.
    // Note a final space after a list of annotations to separate from the underlying type.
    @SideEffectFree
    protected final static String formatAnnotationString(
            Collection<? extends AnnotationMirror> lst,
            boolean printInvisible) {
        StringBuilder sb = new StringBuilder();
        for (AnnotationMirror obj : lst) {
            if (obj == null) {
                ErrorReporter.errorAbort("AnnotatedTypeMirror.formatAnnotationString: found null AnnotationMirror!");
            }
            if (isInvisibleQualified(obj) &&
                    !printInvisible) {
                continue;
            }
            formatAnnotationMirror(obj, sb);
            sb.append(" ");
        }
        return sb.toString();
    }

    // A helper method to output a single AnnotationMirror, without showing full package names.
    protected final static void formatAnnotationMirror(AnnotationMirror am, StringBuilder sb) {
        sb.append("@");
        sb.append(am.getAnnotationType().asElement().getSimpleName());
        Map<? extends ExecutableElement, ? extends AnnotationValue> args = am.getElementValues();
        if (!args.isEmpty()) {
            sb.append("(");
            boolean oneValue = false;
            if (args.size() == 1) {
                Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> first = args.entrySet().iterator().next();
                if (first.getKey().getSimpleName().contentEquals("value")) {
                    formatAnnotationMirrorArg(first.getValue(), sb);
                    oneValue = true;
                }
            }
            if (!oneValue) {
                boolean notfirst = false;
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> arg : args.entrySet()) {
                    if (notfirst) {
                        sb.append(", ");
                    }
                    notfirst = true;
                    sb.append(arg.getKey().getSimpleName() + "=");
                    formatAnnotationMirrorArg(arg.getValue(), sb);
                }
            }
            sb.append(")");
        }
    }

    /**
     * Returns the string representation of a single AnnotationMirror, without showing full package names.
     */
    public final static String formatAnnotationMirror(AnnotationMirror am) {
        StringBuilder sb = new StringBuilder();
        formatAnnotationMirror(am, sb);
        return sb.toString();
    }

    // A helper method to output a single AnnotationValue, without showing full package names.
    @SuppressWarnings("unchecked")
    protected final static void formatAnnotationMirrorArg(AnnotationValue av, StringBuilder sb) {
        Object val = av.getValue();
        if (List.class.isAssignableFrom(val.getClass())) {
            List<AnnotationValue> vallist = (List<AnnotationValue>) val;
            if (vallist.size() == 1) {
                formatAnnotationMirrorArg(vallist.get(0), sb);
            } else {
                sb.append('{');
                boolean notfirst = false;
                for (AnnotationValue nav : vallist) {
                    if (notfirst) {
                        sb.append(", ");
                    }
                    notfirst = true;
                    formatAnnotationMirrorArg(nav, sb);
                }
                sb.append('}');
            }
        } else if (VariableElement.class.isAssignableFrom(val.getClass())) {
            VariableElement ve = (VariableElement) val;
            sb.append(ve.getEnclosingElement().getSimpleName() + "." + ve.getSimpleName());
        } else {
            sb.append(av.toString());
        }
    }

    @SideEffectFree
    @Override
    public final String toString() {
        // Also see
        // org.checkerframework.common.basetype.BaseTypeVisitor.commonAssignmentCheck(AnnotatedTypeMirror, AnnotatedTypeMirror, Tree, String)
        // TODO the direct access to the 'checker' field is not clean
        return toString(atypeFactory.checker.hasOption("printAllQualifiers"));
    }

    /**
     * A version of toString() that optionally outputs all type qualifiers,
     * including @InvisibleQualifier's.
     *
     * @param invisible Whether to always output invisible qualifiers.
     * @return A string representation of the current type containing all qualifiers.
     */
    @SideEffectFree
    public String toString(boolean invisible) {
        return formatAnnotationString(getAnnotations(), invisible)
                + this.actualType;
    }

    @SideEffectFree
    public String toStringDebug() {
        return toString(true) + " " + getClass().getSimpleName(); // + "#" + uid;
    }

    /**
     * Returns the erasure type of the this type, according to JLS
     * specifications.
     *
     * @return  the erasure of this
     */
    public AnnotatedTypeMirror getErased() {
        return this;
    }

    /**
     * Returns a shallow copy of this type.
     *
     * @param copyAnnotations
     *            whether copy should have annotations, i.e. whether
     *            field {@code annotations} should be copied.
     */
    public abstract AnnotatedTypeMirror getCopy(boolean copyAnnotations);

    protected static AnnotatedDeclaredType createTypeOfObject(AnnotatedTypeFactory atypeFactory) {
        AnnotatedDeclaredType objectType =
        atypeFactory.fromElement(
                atypeFactory.elements.getTypeElement(
                        Object.class.getCanonicalName()));
        return objectType;
    }

    /**
     * Return a copy of this, with the given substitutions performed.
     *
     * @param mappings
     */
    public AnnotatedTypeMirror substitute(
            Map<? extends AnnotatedTypeMirror,
                    ? extends AnnotatedTypeMirror> mappings) {
        if (mappings.containsKey(this)) {
            return mappings.get(this).getCopy(true);
        }
        return this.getCopy(true);
    }

    public static interface AnnotatedReferenceType {
        // No members.
    }

    /**
     * Represents a declared type (whether class or interface).
     */
    public static class AnnotatedDeclaredType extends AnnotatedTypeMirror
    implements AnnotatedReferenceType {

        /** Parametrized Type Arguments **/
        protected List<AnnotatedTypeMirror> typeArgs;

        /**
         * Whether the type was initially raw, i.e. the user
         * did not provide the type arguments.
         * typeArgs will contain inferred type arguments, which
         * might be too conservative at the moment.
         * TODO: improve inference.
         *
         * Ideally, the field would be final. However, when
         * we determine the supertype of a raw type, we need
         * to set wasRaw for the supertype.
         */
        private boolean wasRaw;

        /** The enclosing Type **/
        protected AnnotatedDeclaredType enclosingType;

        protected List<AnnotatedDeclaredType> supertypes = null;

        /**
         * Constructor for this type
         *
         * @param type  underlying kind of this type
         * @param atypeFactory TODO
         */
        private AnnotatedDeclaredType(DeclaredType type,
                AnnotatedTypeFactory atypeFactory) {
            super(type, atypeFactory);
            TypeElement typeelem = (TypeElement) type.asElement();
            DeclaredType declty = (DeclaredType) typeelem.asType();
            wasRaw = !declty.getTypeArguments().isEmpty() &&
                    type.getTypeArguments().isEmpty();

            TypeMirror encl = type.getEnclosingType();
            if (encl.getKind() == TypeKind.DECLARED) {
                this.enclosingType = (AnnotatedDeclaredType) createType(encl, atypeFactory);
            } else if (encl.getKind() != TypeKind.NONE) {
                ErrorReporter.errorAbort("AnnotatedDeclaredType: unsupported enclosing type: " +
                        type.getEnclosingType() + " (" + encl.getKind() + ")");
            }
        }

        @SideEffectFree
        @Override
        public String toString(boolean printInvisible) {
            StringBuilder sb = new StringBuilder();
            final Element typeElt = this.getUnderlyingType().asElement();
            String smpl = typeElt.getSimpleName().toString();
            if (smpl.isEmpty()) {
                // For anonymous classes smpl is empty - toString
                // of the element is more useful.
                smpl = typeElt.toString();
            }
            sb.append(formatAnnotationString(getAnnotations(), printInvisible));
            sb.append(smpl);
            if (!this.getTypeArguments().isEmpty()) {
                sb.append("<");

                boolean isFirst = true;
                for (AnnotatedTypeMirror typeArg : getTypeArguments()) {
                    if (!isFirst) sb.append(", ");
                    sb.append(typeArg.toString(printInvisible));
                    isFirst = false;
                }
                sb.append(">");
            }
            return sb.toString();
        }

        @Override
        public <R, P> R accept(AnnotatedTypeVisitor<R, P> v, P p) {
            return v.visitDeclared(this, p);
        }

        /**
         * Sets the type arguments on this type
         * @param ts the type arguments
         */
        // WMD
        public
        void setTypeArguments(List<? extends AnnotatedTypeMirror> ts) {
            if (ts == null || ts.isEmpty()) {
                typeArgs = Collections.emptyList();
            } else {
                typeArgs = Collections.unmodifiableList(new ArrayList<AnnotatedTypeMirror>(ts));
            }
        }

        /**
         * @return the type argument for this type
         */
        public List<AnnotatedTypeMirror> getTypeArguments() {
            if (typeArgs == null) {
                typeArgs = new ArrayList<AnnotatedTypeMirror>();
                if (!((DeclaredType)actualType).getTypeArguments().isEmpty()) { // lazy init
                    for (TypeMirror t : ((DeclaredType)actualType).getTypeArguments()) {
                        typeArgs.add(createType(t, atypeFactory));
                    }
                }
                typeArgs = Collections.unmodifiableList(typeArgs);
            }
            return typeArgs;
        }

        /**
         * Returns true if the type was raw, that is, type arguments were not
         * provided but instead inferred.
         *
         * @return true iff the type was raw
         */
        public boolean wasRaw() {
            return wasRaw;
        }

        /**
         * Set the wasRaw flag to true.
         * This should only be necessary when determining
         * the supertypes of a raw type.
         */
        private void setWasRaw() {
            this.wasRaw = true;
        }

        @Override
        public DeclaredType getUnderlyingType() {
            return (DeclaredType) actualType;
        }

        void setDirectSuperTypes(List<AnnotatedDeclaredType> supertypes) {
            this.supertypes = new ArrayList<AnnotatedDeclaredType>(supertypes);
        }

        @Override
        public List<AnnotatedDeclaredType> directSuperTypes() {
            if (supertypes == null) {
                supertypes = Collections.unmodifiableList(directSuperTypes(this));
            }
            return supertypes;
        }

        /*
         * Return the direct super types field without lazy initialization,
         * to prevent infinite recursion in IGJATF.postDirectSuperTypes.
         * TODO: find a nicer way, see the single caller in QualifierDefaults
         * for comment.
         */
        public List<AnnotatedDeclaredType> directSuperTypesField() {
            return supertypes;
        }

        @Override
        public AnnotatedDeclaredType getCopy(boolean copyAnnotations) {
            AnnotatedDeclaredType type =
                new AnnotatedDeclaredType(getUnderlyingType(), atypeFactory);
            if (copyAnnotations)
                type.addAnnotations(annotations);
            type.setEnclosingType(getEnclosingType());
            type.setTypeArguments(getTypeArguments());
            return type;
        }

        @Override
        public AnnotatedTypeMirror substitute(
                Map<? extends AnnotatedTypeMirror,
                    ? extends AnnotatedTypeMirror> mappings) {
            if (mappings.containsKey(this))
                return mappings.get(this);

            AnnotatedDeclaredType type = getCopy(true);

            Map<AnnotatedTypeMirror, AnnotatedTypeMirror> newMappings =
                    new HashMap<AnnotatedTypeMirror, AnnotatedTypeMirror>(mappings);
            newMappings.put(this, type);

            List<AnnotatedTypeMirror> typeArgs = new ArrayList<AnnotatedTypeMirror>();
            for (AnnotatedTypeMirror t : getTypeArguments())
                typeArgs.add(t.substitute(newMappings));
            type.setTypeArguments(typeArgs);

            return type;
        }

        @Override
        public AnnotatedDeclaredType getErased() {
            // 1. |G<T_1, ..., T_n>| = |G|
            // 2. |T.C| = |T|.C
            if (!getTypeArguments().isEmpty()) {
                Types types = atypeFactory.types;
                // Handle case 1.
                AnnotatedDeclaredType rType =
                    (AnnotatedDeclaredType)AnnotatedTypeMirror.createType(
                            types.erasure(actualType),
                            atypeFactory);
                rType.addAnnotations(getAnnotations());
                rType.setTypeArguments(Collections.<AnnotatedTypeMirror> emptyList());
                return rType.getErased();
            } else if ((getEnclosingType() != null) &&
                       (getEnclosingType().getKind() != TypeKind.NONE)) {
                // Handle case 2
                // TODO: Test this
                AnnotatedDeclaredType rType = getCopy(true);
                AnnotatedDeclaredType et = getEnclosingType();
                rType.setEnclosingType(et.getErased());
                return rType;
            } else {
                return this;
            }
        }

        /* Using this equals method resulted in an infinite recursion
         * with type variables. TODO: Keep track of visited type variables?
        @Override
        public boolean equals(Object o) {
            boolean res = super.equals(o);

            if (res && (o instanceof AnnotatedDeclaredType)) {
                AnnotatedDeclaredType dt = (AnnotatedDeclaredType) o;

                List<AnnotatedTypeMirror> mytas = this.getTypeArguments();
                List<AnnotatedTypeMirror> othertas = dt.getTypeArguments();
                for (int i = 0; i < mytas.size(); ++i) {
                    if (!mytas.get(i).equals(othertas.get(i))) {
                        System.out.println("in AnnotatedDeclaredType; this: " + this + " and " + o);
                        res = false;
                        break;
                    }
                }
            }
            return res;
        }
        */

        /**
         * Sets the enclosing type
         *
         * @param enclosingType
         */
        /*default-visibility*/ void setEnclosingType(AnnotatedDeclaredType enclosingType) {
            this.enclosingType = enclosingType;
        }

        /**
         * Returns the enclosing type, as in the type of {@code A} in the type
         * {@code A.B}.
         *
         * @return enclosingType the enclosing type
         */
        public AnnotatedDeclaredType getEnclosingType() {
            return enclosingType;
        }
    }

    /**
     * Represents a type of an executable. An executable is a method, constructor, or initializer.
     */
    public static class AnnotatedExecutableType extends AnnotatedTypeMirror {

        private final ExecutableType actualType;

        private ExecutableElement element;

        private AnnotatedExecutableType(ExecutableType type,
                AnnotatedTypeFactory factory) {
            super(type, factory);
            this.actualType = type;
        }

        final private List<AnnotatedTypeMirror> paramTypes =
            new ArrayList<AnnotatedTypeMirror>();
        private AnnotatedDeclaredType receiverType;
        private AnnotatedTypeMirror returnType;
        final private List<AnnotatedTypeMirror> throwsTypes =
            new ArrayList<AnnotatedTypeMirror>();
        final private List<AnnotatedTypeVariable> typeVarTypes =
            new ArrayList<AnnotatedTypeVariable>();

        /**
         * @return true if this type represents a varargs method
         */
        public boolean isVarArgs() {
            return this.element.isVarArgs();
        }

        @Override
        public <R, P> R accept(AnnotatedTypeVisitor<R, P> v, P p) {
            return v.visitExecutable(this, p);
        }

        @Override
        public ExecutableType getUnderlyingType() {
            return this.actualType;
        }

        /* TODO: it never makes sense to add annotations to an executable type -
         * instead, they should be added to the right component.
         * For simpler, more regular use, we might want to allow querying for annotations.
         *
        @Override
        public void addAnnotations(Iterable<? extends AnnotationMirror> annotations) {
            //Thread.dumpStack();
            super.addAnnotations(annotations);
        }
        @Override
        public void addAnnotation(AnnotationMirror a) {
            //Thread.dumpStack();
            super.addAnnotation(a);
        }
        @Override
        public void addAnnotation(Class<? extends Annotation> a) {
            //Thread.dumpStack();
            super.addAnnotation(a);
        }

        @Override
        public Set<AnnotationMirror> getAnnotations() {
            Thread.dumpStack();
            return null;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof AnnotatedExecutableType))
                return false;
            // TODO compare components
            return true;
        }
        */

        /**
         * Sets the parameter types of this executable type
         * @param params the parameter types
         */
        void setParameterTypes(
                List<? extends AnnotatedTypeMirror> params) {
            paramTypes.clear();
            paramTypes.addAll(params);
        }

        /**
         * @return the parameter types of this executable type
         */
        public List<AnnotatedTypeMirror> getParameterTypes() {
            if (paramTypes.isEmpty()
                    && !actualType.getParameterTypes().isEmpty()) { // lazy init
                for (TypeMirror t : actualType.getParameterTypes())
                    paramTypes.add(createType(t, atypeFactory));
            }
            return Collections.unmodifiableList(paramTypes);
        }

        /**
         * Sets the return type of this executable type
         * @param returnType    the return type
         */
        void setReturnType(AnnotatedTypeMirror returnType) {
            this.returnType = returnType;
        }

        /**
         * The return type of a method or constructor.
         * For constructors, the return type is not VOID, but the type of
         * the enclosing class.
         *
         * @return the return type of this executable type
         */
        public AnnotatedTypeMirror getReturnType() {
            if (returnType == null
                    && element != null
                    && actualType.getReturnType() != null) {// lazy init
                TypeMirror aret = actualType.getReturnType();
                if (((MethodSymbol)element).isConstructor()) {
                    // For constructors, the underlying return type is void.
                    // Take the type of the enclosing class instead.
                    aret = element.getEnclosingElement().asType();
                }
                returnType = createType(aret, atypeFactory);
            }
            return returnType;
        }

        /**
         * Sets the receiver type on this executable type
         * @param receiverType the receiver type
         */
        void setReceiverType(AnnotatedDeclaredType receiverType) {
            this.receiverType = receiverType;
        }

        /**
         * @return the receiver type of this executable type;
         *   null for static methods and constructors of top-level classes
         */
        public /*@Nullable*/ AnnotatedDeclaredType getReceiverType() {
            if (receiverType == null &&
                    // Static methods don't have a receiver
                    !ElementUtils.isStatic(getElement()) &&
                    // Top-level constructors don't have a receiver
                    (getElement().getKind() != ElementKind.CONSTRUCTOR ||
                    getElement().getEnclosingElement().getEnclosingElement().getKind() != ElementKind.PACKAGE)) {
                TypeElement encl = ElementUtils.enclosingClass(getElement());
                if (getElement().getKind() == ElementKind.CONSTRUCTOR) {
                    // Can only reach this branch if we're the constructor of a nested class
                    encl =  ElementUtils.enclosingClass(encl.getEnclosingElement());
                }
                AnnotatedTypeMirror type = createType(encl.asType(), atypeFactory);
                assert type instanceof AnnotatedDeclaredType;
                receiverType = (AnnotatedDeclaredType)type;
            }
            return receiverType;
        }

        /**
         * Sets the thrown types of this executable type
         *
         * @param thrownTypes the thrown types
         */
        void setThrownTypes(
                List<? extends AnnotatedTypeMirror> thrownTypes) {
            this.throwsTypes.clear();
            this.throwsTypes.addAll(thrownTypes);
        }

        /**
         * @return the thrown types of this executable type
         */
        public List<AnnotatedTypeMirror> getThrownTypes() {
            if (throwsTypes.isEmpty()
                    && !actualType.getThrownTypes().isEmpty()) { // lazy init
                for (TypeMirror t : actualType.getThrownTypes())
                    throwsTypes.add(createType(t, atypeFactory));
            }
            return Collections.unmodifiableList(throwsTypes);
        }

        /**
         * Sets the type variables associated with this executable type
         *
         * @param types the type variables of this executable type
         */
        void setTypeVariables(List<AnnotatedTypeVariable> types) {
            typeVarTypes.clear();
            typeVarTypes.addAll(types);
        }

        /**
         * @return the type variables of this executable type, if any
         */
        public List<AnnotatedTypeVariable> getTypeVariables() {
            if (typeVarTypes.isEmpty()
                    && !actualType.getTypeVariables().isEmpty()) { // lazy init
                for (TypeMirror t : actualType.getTypeVariables()) {
                    typeVarTypes.add((AnnotatedTypeVariable)createType(
                            t, atypeFactory));
                }
            }
            return Collections.unmodifiableList(typeVarTypes);
        }

        @Override
        public AnnotatedExecutableType getCopy(boolean copyAnnotations) {
            AnnotatedExecutableType type =
                new AnnotatedExecutableType(getUnderlyingType(), atypeFactory);

            type.setElement(getElement());
            type.setParameterTypes(getParameterTypes());
            type.setReceiverType(getReceiverType());
            type.setReturnType(getReturnType());
            type.setThrownTypes(getThrownTypes());
            type.setTypeVariables(getTypeVariables());

            return type;
        }

        public /*@NonNull*/ ExecutableElement getElement() {
            return element;
        }

        public void setElement(/*@NonNull*/ ExecutableElement elem) {
            this.element = elem;
        }

        @Override
        public AnnotatedExecutableType getErased() {
            Types types = atypeFactory.types;
            AnnotatedExecutableType type =
                new AnnotatedExecutableType(
                        (ExecutableType) types.erasure(getUnderlyingType()),
                        atypeFactory);
            type.setElement(getElement());
            type.setParameterTypes(erasureList(getParameterTypes()));
            if (getReceiverType() != null) {
                type.setReceiverType(getReceiverType().getErased());
            } else {
                type.setReceiverType(null);
            }
            type.setReturnType(getReturnType().getErased());
            type.setThrownTypes(erasureList(getThrownTypes()));

            return type;
        }

        private List<AnnotatedTypeMirror> erasureList(List<? extends AnnotatedTypeMirror> lst) {
            List<AnnotatedTypeMirror> erased = new ArrayList<AnnotatedTypeMirror>();
            for (AnnotatedTypeMirror t : lst)
                erased.add(t.getErased());
            return erased;
        }

        @Override
        public AnnotatedExecutableType substitute(
                Map<? extends AnnotatedTypeMirror,
                        ? extends AnnotatedTypeMirror> mappings) {
            // Shouldn't substitute for methods!
            AnnotatedExecutableType type = getCopy(true);

            // Params
            {
                List<AnnotatedTypeMirror> params = new ArrayList<AnnotatedTypeMirror>();
                for (AnnotatedTypeMirror t : getParameterTypes()) {
                    params.add(t.substitute(mappings));
                }
                type.setParameterTypes(params);
            }

            if (getReceiverType() != null)
                type.setReceiverType((AnnotatedDeclaredType)getReceiverType().substitute(mappings));

            type.setReturnType(getReturnType().substitute(mappings));

            // Throws
            {
                List<AnnotatedTypeMirror> throwns = new ArrayList<AnnotatedTypeMirror>();
                for (AnnotatedTypeMirror t : getThrownTypes()) {
                    throwns.add(t.substitute(mappings));
                }
                type.setThrownTypes(throwns);
            }

            // Method type variables
            {
                List<AnnotatedTypeVariable> mtvs = new ArrayList<AnnotatedTypeVariable>();
                for (AnnotatedTypeVariable t : getTypeVariables()) {
                    // Substitute upper and lower bound of the type variable.
                    AnnotatedTypeVariable newtv = AnnotatedTypes.deepCopy(t);
                    AnnotatedTypeMirror bnd = newtv.getUpperBoundField();
                    if (bnd != null) {
                        bnd = bnd.substitute(mappings);
                        newtv.setUpperBound(bnd);
                    }
                    bnd = newtv.getLowerBoundField();
                    if (bnd != null) {
                        bnd = bnd.substitute(mappings);
                        newtv.setLowerBound(bnd);
                    }
                    mtvs.add(newtv);
                }
                type.setTypeVariables(mtvs);
            }

            return type;
        }

        @SideEffectFree
        @Override
        public String toString(boolean printInvisible) {
            StringBuilder sb = new StringBuilder();
            if (!getTypeVariables().isEmpty()) {
                sb.append('<');
                for (AnnotatedTypeVariable atv : getTypeVariables()) {
                    sb.append(atv.toString(printInvisible));
                }
                sb.append("> ");
            }
            if (getReturnType() != null) {
                sb.append(getReturnType().toString(printInvisible));
            } else {
                sb.append("<UNKNOWNRETURN>");
            }
            sb.append(' ');
            if (element != null) {
                sb.append(element.getSimpleName());
            } else {
                sb.append("METHOD");
            }
            sb.append('(');
            AnnotatedDeclaredType rcv = getReceiverType();
            if (rcv != null) {
                sb.append(rcv.toString(printInvisible));
                sb.append(" this");
            }
            if (!getParameterTypes().isEmpty()) {
                int p = 0;
                for (AnnotatedTypeMirror atm : getParameterTypes()) {
                    if (rcv != null ||
                            p > 0) {
                        sb.append(", ");
                    }
                    sb.append(atm.toString(printInvisible));
                    // Output some parameter names to make it look more like a method.
                    // TODO: go to the element and look up real parameter names, maybe.
                    sb.append(" p");
                    sb.append(p++);
                }
            }
            sb.append(')');
            if (!getThrownTypes().isEmpty()) {
                sb.append(" throws ");
                for (AnnotatedTypeMirror atm : getThrownTypes()) {
                    sb.append(atm.toString(printInvisible));
                }
            }
            return sb.toString();
        }
    }

    /**
     * Represents Array types in java. A multidimensional array type is
     * represented as an array type whose component type is also an
     * array type.
     */
    public static class AnnotatedArrayType extends AnnotatedTypeMirror
    implements AnnotatedReferenceType {

        private final ArrayType actualType;

        private AnnotatedArrayType(ArrayType type,
                AnnotatedTypeFactory factory) {
            super(type, factory);
            this.actualType = type;
        }

        /** The component type of this array type */
        private AnnotatedTypeMirror componentType;

        @Override
        public <R, P> R accept(AnnotatedTypeVisitor<R, P> v, P p) {
            return v.visitArray(this, p);
        }

        @Override
        public ArrayType getUnderlyingType() {
            return this.actualType;
        }

        /**
         * Sets the component type of this array
         *
         * @param type the component type
         */
        // WMD
        public
        void setComponentType(AnnotatedTypeMirror type) {
            this.componentType = type;
        }

        /**
         * @return the component type of this array
         */
        public AnnotatedTypeMirror getComponentType() {
            if (componentType == null) // lazy init
                setComponentType(createType(
                        actualType.getComponentType(), atypeFactory));
            return componentType;
        }


        @Override
        public AnnotatedArrayType getCopy(boolean copyAnnotations) {
            AnnotatedArrayType type = new AnnotatedArrayType(actualType, atypeFactory);
            if (copyAnnotations)
                type.addAnnotations(annotations);
            type.setComponentType(getComponentType());
            return type;
        }

        @Override
        public AnnotatedTypeMirror substitute(
                Map<? extends AnnotatedTypeMirror,
                        ? extends AnnotatedTypeMirror> mappings) {
            if (mappings.containsKey(this))
                return mappings.get(this);

            AnnotatedArrayType type = getCopy(true);
            AnnotatedTypeMirror c = getComponentType();
            AnnotatedTypeMirror cs = c.substitute(mappings);
            type.setComponentType(cs);
            return type;
        }

        @Override
        public AnnotatedArrayType getErased() {
            // | T[ ] | = |T| [ ]
            AnnotatedArrayType at = getCopy(true);
            AnnotatedTypeMirror ct = at.getComponentType().getErased();
            at.setComponentType(ct);
            return at;

        }

        public String toStringAsCanonical(boolean printInvisible) {
            StringBuilder sb = new StringBuilder();

            AnnotatedArrayType array = this;
            AnnotatedTypeMirror component;
            while (true) {
                component = array.getComponentType();
                if (array.getAnnotations().size() > 0) {
                    sb.append(' ');
                    sb.append(formatAnnotationString(array.getAnnotations(), printInvisible));
                }
                sb.append("[]");
                if (!(component instanceof AnnotatedArrayType)) {
                    sb.insert(0, component.toString(printInvisible));
                    break;
                }
                array = (AnnotatedArrayType) component;
            }
            return sb.toString();
        }

        @SideEffectFree
        @Override
        public String toString(boolean printInvisible) {
            return toStringAsCanonical(printInvisible);
        }
    }

    /**
     * Represents a type variable. A type variable may be explicitly declared by
     * a type parameter of a type, method, or constructor. A type variable may
     * also be declared implicitly, as by the capture conversion of a wildcard
     * type argument (see chapter 5 of The Java Language Specification, Third
     * Edition).
     *
     */
    public static class AnnotatedTypeVariable extends AnnotatedTypeMirror
    implements AnnotatedReferenceType {

        private AnnotatedTypeVariable(TypeVariable type,
                AnnotatedTypeFactory factory) {
            super(type, factory);
        }

        /** The lower bound of the type variable. **/
        private AnnotatedTypeMirror lowerBound;

        /** The upper bound of the type variable. **/
        private AnnotatedTypeMirror upperBound;

        @Override
        public <R, P> R accept(AnnotatedTypeVisitor<R, P> v, P p) {
            return v.visitTypeVariable(this, p);
        }

        @Override
        public TypeVariable getUnderlyingType() {
            return (TypeVariable) this.actualType;
        }

        /**
         * Set the lower bound of this variable type
         *
         * Returns the lower bound of this type variable. While a type
         * parameter cannot include an explicit lower bound declaration,
         * capture conversion can produce a type variable with a non-trivial
         * lower bound. Type variables otherwise have a lower bound of
         * NullType.
         *
         * @param type the lower bound type
         */
        void setLowerBound(AnnotatedTypeMirror type) {
            this.lowerBound = type;
        }

        /**
         * Get the lower bound field directly, bypassing any lazy initialization.
         * This method is necessary to prevent infinite recursions in initialization.
         * In general, prefer getLowerBound.
         *
         * @return the lower bound field.
         */
        public AnnotatedTypeMirror getLowerBoundField() {
            return lowerBound;
        }

        /**
         * @return the lower bound type of this type variable
         * @see #getEffectiveLowerBound
         */
        public AnnotatedTypeMirror getLowerBound() {
            if (lowerBound == null && ((TypeVariable)actualType).getLowerBound() != null) { // lazy init
                setLowerBound(createType(((TypeVariable)actualType).getLowerBound(), atypeFactory));
                fixupBoundAnnotations();
            }
            return lowerBound;
        }

        /**
         * @return the effective lower bound:  the lower bound,
         * with annotations on the type variable considered.
        */
        public AnnotatedTypeMirror getEffectiveLowerBound() {
            AnnotatedTypeMirror effbnd = AnnotatedTypes.deepCopy(getLowerBound());
            effbnd.replaceAnnotations(annotations);
            return effbnd;
        }


        // If the lower bound was not present in actualType, then its
        // annotation was defaulted from the AnnotatedTypeFactory.  If the
        // lower bound annotation is a supertype of the upper bound
        // annotation, then the type is ill-formed.  In that case, change
        // the defaulted lower bound to be consistent with the
        // explicitly-written upper bound.
        //
        // As a concrete example, if the default annotation is @Nullable,
        // then the type "X extends @NonNull Y" should not be converted
        // into "X extends @NonNull Y super @Nullable bottomtype" but be
        // converted into "X extends @NonNull Y super @NonNull bottomtype".
        //
        // In addition, ensure consistency of annotations on type variables
        // and the upper bound. Assume class C<X extends @Nullable Object>.
        // The type of "@Nullable X" has to be "@Nullable X extends @Nullable Object",
        // because otherwise the annotations are inconsistent.
        private void fixupBoundAnnotations() {
            if (!annotations.isEmpty() && upperBound != null) {
                // TODO: there seems to be some (for me) unexpected sharing
                // between upper bounds. Without the copying in the next line, test
                // case KeyForChecked fails, because the annotation on the return type
                // type variable changes the upper bound of the parameter type variable.
                // Should such a copy be made somewhere else and for more?
                upperBound = upperBound.getCopy(true);
                // TODO: this direct replacement forbids us to check well-formedness,
                // which is done in
                // org.checkerframework.common.basetype.BaseTypeVisitor.TypeValidator.visitTypeVariable(AnnotatedTypeVariable, Tree)
                // and assumed in nullness test Wellformed.
                // Which behavior do we want?
                upperBound.replaceAnnotations(annotations);
            }
            if (upperBound != null && upperBound.getAnnotations().isEmpty()) {
                // new Throwable().printStackTrace();
                // upperBound.addAnnotations(typeFactory.qualHierarchy.getRootAnnotations());
                // TODO: this should never happen.
            }
            if (((TypeVariable)actualType).getLowerBound() instanceof NullType &&
                    lowerBound != null && upperBound != null) {
                Set<AnnotationMirror> lAnnos = lowerBound.getEffectiveAnnotations();
                Set<AnnotationMirror> uAnnos = upperBound.getEffectiveAnnotations();
                QualifierHierarchy qualifierHierarchy = atypeFactory.getQualifierHierarchy();

                for (AnnotationMirror top : qualifierHierarchy.getTopAnnotations()) {
                    AnnotationMirror lAnno = qualifierHierarchy.getAnnotationInHierarchy(lAnnos, top);
                    AnnotationMirror uAnno = qualifierHierarchy.getAnnotationInHierarchy(uAnnos, top);
                    fixupBoundAnnotationsImpl(qualifierHierarchy,
                            lowerBound, upperBound, annotations,
                            top, lAnno, uAnno);
                }
            }
        }

        /**
         * Set the upper bound of this variable type
         * @param type the upper bound type
         */
        void setUpperBound(AnnotatedTypeMirror type) {
            // TODO: create a deepCopy?
            this.upperBound = type;
        }

        /**
         * Get the upper bound field directly, bypassing any lazy initialization.
         * This method is necessary to prevent infinite recursions in initialization.
         * In general, prefer getUpperBound.
         *
         * @return the upper bound field.
         */
        public AnnotatedTypeMirror getUpperBoundField() {
            return upperBound;
        }

        /**
         * Get the upper bound of the type variable, possibly lazily initializing it.
         * Attention: If the upper bound is lazily initialized, it will not contain
         * any annotations! Callers of the method have to make sure that an
         * AnnotatedTypeFactory first processed the bound.
         *
         * @return the upper bound type of this type variable
         * @see #getEffectiveUpperBound
         */
        public AnnotatedTypeMirror getUpperBound() {
            if (upperBound == null && ((TypeVariable)actualType).getUpperBound() != null) { // lazy init
                setUpperBound(createType(((TypeVariable)actualType).getUpperBound(), atypeFactory));
                fixupBoundAnnotations();
            }
            return upperBound;
        }

        /**
         * @return the effective upper bound:  the upper bound,
         * with annotations on the type variable considered.
        */
        public AnnotatedTypeMirror getEffectiveUpperBound() {
            AnnotatedTypeMirror effbnd = AnnotatedTypes.deepCopy(getUpperBound());
            effbnd.replaceAnnotations(annotations);
            return effbnd;
        }

        /**
         *  Used to terminate recursion into upper bounds.
         */
        private boolean inUpperBounds = false;

        @Override
        public AnnotatedTypeVariable getCopy(boolean copyAnnotations) {
            AnnotatedTypeVariable type =
                new AnnotatedTypeVariable(((TypeVariable)actualType), atypeFactory);
            if (copyAnnotations)
                type.addAnnotations(annotations);
            if (!inUpperBounds) {
                inUpperBounds = true;
                type.inUpperBounds = true;
                type.setUpperBound(getUpperBound());
                inUpperBounds = false;
                type.inUpperBounds = false;
            }
            return type;
        }

        @Override
        public AnnotatedTypeMirror getErased() {
            // |T extends A&B| = |A|
            return this.getEffectiveUpperBound().getErased();
        }

        /* TODO: If we use the stronger equals method below, we also
         * need this "canonical" version of the type variable.
         * This type variable will be used for hashmaps that keep track
         * of type arguments.
        private AnnotatedTypeVariable canonical;

        public AnnotatedTypeVariable getCanonical() {
            if (canonical == null) {
                canonical = new AnnotatedTypeVariable(this.actualType, env, atypeFactory);
            }
            return canonical;
        }
         */

        private static <K extends AnnotatedTypeMirror, V extends AnnotatedTypeMirror>
        V mapGetHelper(Map<K, V> mappings, AnnotatedTypeVariable key) {
            for (Map.Entry<K, V> entry : mappings.entrySet()) {
                K possible = entry.getKey();
                V possValue = entry.getValue();
                if (possible == key) return possValue;
                if (possible instanceof AnnotatedTypeVariable) {
                    AnnotatedTypeVariable other = (AnnotatedTypeVariable)possible;
                    Element oElt = other.getUnderlyingType().asElement();
                    if (key.getUnderlyingType().asElement().equals(oElt)) {
                        // Not identical AnnotatedTypeMirrors, but they wrap the same TypeMirror.
                        if (!key.annotations.isEmpty()
                                && !AnnotationUtils.areSame(key.annotations, other.annotations)) {
                            // An annotated type variable use means to override
                            // any annotations on the actual type argument.
                            @SuppressWarnings("unchecked")
                            V found = (V)possValue.getCopy(false);
                            found.addAnnotations(possValue.getAnnotations());
                            found.replaceAnnotations(key.annotations);
                            return found;
                        } else {
                            return possValue;
                        }
                    }
                }
            }
            return null;
        }

        @Override
        public AnnotatedTypeMirror substitute(
                Map<? extends AnnotatedTypeMirror,
                        ? extends AnnotatedTypeMirror> mappings) {
            AnnotatedTypeMirror found = mapGetHelper(mappings, this);
            if (found != null) {
                return found;
            }

            AnnotatedTypeVariable type = getCopy(true);
            /* TODO: the above call of getCopy results in calls of
             * getUpperBound, which lazily initializes the field.
             * This causes a modification of the data structure, when
             * all we want to do is copy it.
             * However, if we only do the first part of getCopy,
             * test cases fail. I spent a huge amount of time debugging
             * this and added the annotateImplicitHack above.
            AnnotatedTypeVariable type =
                    new AnnotatedTypeVariable(actualType, env, atypeFactory);
            copyFields(type, true);*/

            Map<AnnotatedTypeMirror, AnnotatedTypeMirror> newMappings =
                new HashMap<AnnotatedTypeMirror, AnnotatedTypeMirror>(mappings);
            newMappings.put(this, type);
            if (lowerBound != null) {
                type.setLowerBound(lowerBound.substitute(newMappings));
            }
            if (upperBound != null) {
                type.setUpperBound(upperBound.substitute(newMappings));
            }
            return type;
        }

        // Style taken from Type
        boolean isPrintingBound = false;

        @SideEffectFree
        @Override
        public String toString(boolean printInvisible) {
            StringBuilder sb = new StringBuilder();
            sb.append(formatAnnotationString(annotations, printInvisible));
            sb.append(actualType);
            if (!isPrintingBound) {
                try {
                    isPrintingBound = true;
                    if (getLowerBoundField() != null && getLowerBoundField().getKind() != TypeKind.NULL) {
                        sb.append(" super ");
                        sb.append(getLowerBoundField().toString(printInvisible));
                    }
                    // If the upper bound annotation is not the default, perhaps
                    // print the upper bound even if its kind is TypeKind.NULL.
                    if (getUpperBoundField() != null && getUpperBoundField().getKind() != TypeKind.NULL) {
                        sb.append(" extends ");
                        sb.append(getUpperBoundField().toString(printInvisible));
                    }
                } finally {
                    isPrintingBound = false;
                }
            }
            return sb.toString();
        }

        @Pure
        @Override
        public int hashCode() {
            return this.getUnderlyingType().hashCode();
        }

        /* TODO: provide strict equality comparison.
        @Override
        public boolean equals(Object o) {
            boolean isSame = super.equals(o);
            if (!isSame || !(o instanceof AnnotatedTypeVariable))
                return false;
            AnnotatedTypeVariable other = (AnnotatedTypeVariable) o;
            isSame = this.getUpperBound().equals(other.getUpperBound()) &&
                    this.getLowerBound().equals(other.getLowerBound());
            return isSame;
        }
        */
    }

    /**
     * A pseudo-type used where no actual type is appropriate. The kinds of
     * NoType are:
     *
     * <ul>
     *   <li>VOID - corresponds to the keyword void.</li>
     *   <li> PACKAGE - the pseudo-type of a package element.</li>
     *   <li> NONE - used in other cases where no actual type is appropriate;
     *        for example, the superclass of java.lang.Object. </li>
     * </ul>
     */
    public static class AnnotatedNoType extends AnnotatedTypeMirror {

        private AnnotatedNoType(NoType type, AnnotatedTypeFactory factory) {
            super(type, factory);
        }

        // No need for methods
        // Might like to override annotate(), include(), execlude()
        // AS NoType does not accept any annotations

        @Override
        public <R, P> R accept(AnnotatedTypeVisitor<R, P> v, P p) {
            return v.visitNoType(this, p);
        }

        @Override
        public NoType getUnderlyingType() {
            return (NoType) this.actualType;
        }

        @Override
        public AnnotatedNoType getCopy(boolean copyAnnotations) {
            AnnotatedNoType type = new AnnotatedNoType((NoType) actualType, atypeFactory);
            if (copyAnnotations)
                type.addAnnotations(annotations);
            return type;
        }

        @Override
        public AnnotatedTypeMirror substitute(
                Map<? extends AnnotatedTypeMirror,
                        ? extends AnnotatedTypeMirror> mappings) {
            // Cannot substitute
            return getCopy(true);
        }
    }

    /**
     * Represents the null type. This is the type of the expression {@code null}.
     */
    public static class AnnotatedNullType extends AnnotatedTypeMirror
    implements AnnotatedReferenceType {

        private AnnotatedNullType(NullType type, AnnotatedTypeFactory factory) {
            super(type, factory);
        }

        @Override
        public <R, P> R accept(AnnotatedTypeVisitor<R, P> v, P p) {
            return v.visitNull(this, p);
        }

        @Override
        public NullType getUnderlyingType() {
            return (NullType) this.actualType;
        }

        @Override
        public AnnotatedNullType getCopy(boolean copyAnnotations) {
            AnnotatedNullType type = new AnnotatedNullType((NullType) actualType, atypeFactory);
            if (copyAnnotations)
                type.addAnnotations(annotations);
            return type;
        }

        @Override
        public AnnotatedTypeMirror substitute(
                Map<? extends AnnotatedTypeMirror,
                        ? extends AnnotatedTypeMirror> mappings) {
            // cannot substitute
            return getCopy(true);
        }

        @SideEffectFree
        @Override
        public String toString(boolean printInvisible) {
            if (printInvisible) {
                return formatAnnotationString(getAnnotations(), printInvisible) + "null";
            } else {
                return "null";
            }
        }
    }

    /**
     * Represents a primitive type. These include {@code boolean},
     * {@code byte}, {@code short}, {@code int}, {@code long}, {@code char},
     * {@code float}, and {@code double}.
     */
    public static class AnnotatedPrimitiveType extends AnnotatedTypeMirror
    implements AnnotatedReferenceType {

        private AnnotatedPrimitiveType(PrimitiveType type,
                AnnotatedTypeFactory factory) {
            super(type, factory);
        }

        @Override
        public <R, P> R accept(AnnotatedTypeVisitor<R, P> v, P p) {
            return v.visitPrimitive(this, p);
        }

        @Override
        public PrimitiveType getUnderlyingType() {
            return (PrimitiveType) this.actualType;
        }

        @Override
        public AnnotatedPrimitiveType getCopy(boolean copyAnnotations) {
            AnnotatedPrimitiveType type =
                new AnnotatedPrimitiveType((PrimitiveType) actualType, atypeFactory);
            if (copyAnnotations)
                type.addAnnotations(annotations);
            return type;
        }

        @Override
        public AnnotatedTypeMirror substitute(
                Map<? extends AnnotatedTypeMirror,
                        ? extends AnnotatedTypeMirror> mappings) {
            if (mappings.containsKey(this))
                return mappings.get(this);
            return getCopy(true);
        }
    }

    /**
     * Represents a wildcard type argument. Examples include:
     *
     *    ?
     *    ? extends Number
     *    ? super T
     *
     * A wildcard may have its upper bound explicitly set by an extends
     * clause, its lower bound explicitly set by a super clause, or neither
     * (but not both).
     */
    public static class AnnotatedWildcardType extends AnnotatedTypeMirror {
        /** SuperBound **/
        private AnnotatedTypeMirror superBound;

        /** ExtendBound **/
        private AnnotatedTypeMirror extendsBound;

        private AnnotatedWildcardType(WildcardType type, AnnotatedTypeFactory factory) {
            super(type, factory);
        }

        /**
         * Sets the super bound of this wild card
         *
         * @param type  the type of the lower bound
         */
        void setSuperBound(AnnotatedTypeMirror type) {
            this.superBound = type;
        }

        public AnnotatedTypeMirror getSuperBoundField() {
            return superBound;
        }

        /**
         * @return the lower bound of this wildcard. If no lower bound is
         * explicitly declared, {@code null} is returned.
         */
        public AnnotatedTypeMirror getSuperBound() {
            if (superBound == null
                    && ((WildcardType)actualType).getSuperBound() != null) {
                // lazy init
                AnnotatedTypeMirror annosupertype = createType(((WildcardType)actualType).getSuperBound(), atypeFactory);
                setSuperBound(annosupertype);
                fixupBoundAnnotations();
            }
            return this.superBound;
        }

        public AnnotatedTypeMirror getEffectiveSuperBound() {
            AnnotatedTypeMirror spb = getSuperBound();
            if (spb == null) {
                return null;
            }
            AnnotatedTypeMirror effbnd = AnnotatedTypes.deepCopy(spb);
            effbnd.replaceAnnotations(annotations);
            return effbnd;
        }

        /**
         * Sets the upper bound of this wild card
         *
         * @param type  the type of the upper bound
         */
        void setExtendsBound(AnnotatedTypeMirror type) {
            this.extendsBound = type;
        }

        public AnnotatedTypeMirror getExtendsBoundField() {
            return extendsBound;
        }

        /**
         * @return the upper bound of this wildcard. If no upper bound is
         * explicitly declared, the upper bound of the type variable to which
         * the wildcard is bound is used.
         */
        public AnnotatedTypeMirror getExtendsBound() {
            if (extendsBound == null) {
                // lazy init
                TypeMirror extType = ((WildcardType)actualType).getExtendsBound();
                if (extType == null) {
                    // Take the upper bound of the type variable the wildcard is bound to.
                    com.sun.tools.javac.code.Type.WildcardType wct = (com.sun.tools.javac.code.Type.WildcardType) actualType;
                    com.sun.tools.javac.util.Context ctx = ((com.sun.tools.javac.processing.JavacProcessingEnvironment) atypeFactory.processingEnv).getContext();
                    extType = com.sun.tools.javac.code.Types.instance(ctx).upperBound(wct);
                }
                AnnotatedTypeMirror annoexttype = createType(extType, atypeFactory);
                // annoexttype.setElement(this.element);
                setExtendsBound(annoexttype);
                fixupBoundAnnotations();
            }
            return this.extendsBound;
        }

        private void fixupBoundAnnotations() {
            /*System.out.println("AWC.fix: " + this + " elem: " + this.element);
            atypeFactory.annotateImplicit(this.element, this.extendsBound);
            atypeFactory.annotateImplicit(this.element, this.superBound);*/
        }

        /**
         * @return the effective extends bound: the extends bound, with
         *         annotations on the type variable considered.
         */
        public AnnotatedTypeMirror getEffectiveExtendsBound() {
            AnnotatedTypeMirror effbnd = AnnotatedTypes.deepCopy(getExtendsBound());
            effbnd.replaceAnnotations(annotations);
            return effbnd;
        }

        /**
         * @return the effective upper bound annotations: the annotations on
         *         this, or if none, those on the upper bound.
         */
        public Set<AnnotationMirror> getEffectiveExtendsBoundAnnotations() {
            Set<AnnotationMirror> result = annotations;
            // If there are no annotations, return the upper bound.
            if (result.isEmpty()) {
                AnnotatedTypeMirror ub = getExtendsBound();
                if (ub != null) {
                    return ub.getEffectiveAnnotations();
                } else {
                    return Collections.unmodifiableSet(result);
                }
            }
            result = AnnotationUtils.createAnnotationSet();
            result.addAll(annotations);
            Set<AnnotationMirror> boundAnnotations = Collections.emptySet();
            AnnotatedTypeMirror ub = getExtendsBound();
            if (ub != null) {
                boundAnnotations = ub.getEffectiveAnnotations();
            }
            // Add all the annotation from the the upper bound, for which there
            // isn't already another annotation in the set from the same
            // hierarchy.
            for (AnnotationMirror boundAnnotation : boundAnnotations) {
                QualifierHierarchy qualHierarchy = atypeFactory.qualHierarchy;
                AnnotationMirror top = qualHierarchy.getTopAnnotation(boundAnnotation);
                if (qualHierarchy.getAnnotationInHierarchy(result, top) == null) {
                    result.add(boundAnnotation);
                }
            }
            return Collections.unmodifiableSet(result);
        }

        @Override
        public <R, P> R accept(AnnotatedTypeVisitor<R, P> v, P p) {
            return v.visitWildcard(this, p);
        }

        @Override
        public WildcardType getUnderlyingType() {
            return (WildcardType) this.actualType;
        }

        @Override
        public AnnotatedWildcardType getCopy(boolean copyAnnotations) {
            AnnotatedWildcardType type = new AnnotatedWildcardType((WildcardType) actualType, atypeFactory);
            if (copyAnnotations)
                type.addAnnotations(annotations);
            type.setExtendsBound(getExtendsBound());
            type.setSuperBound(getSuperBound());

            type.typeArgHack = typeArgHack;

            return type;
        }

        @Override
        public AnnotatedTypeMirror substitute(
                Map<? extends AnnotatedTypeMirror,
                        ? extends AnnotatedTypeMirror> mappings) {
            if (mappings.containsKey(this))
                return mappings.get(this);

            AnnotatedWildcardType type = getCopy(true);
            // Prevent looping
            Map<AnnotatedTypeMirror, AnnotatedTypeMirror> newMapping =
                new HashMap<AnnotatedTypeMirror, AnnotatedTypeMirror>(mappings);
            newMapping.put(this, type);

            // The extends and super bounds can be null because the underlying
            // type's extends and super bounds can be null.
            if (extendsBound != null)
                type.setExtendsBound(extendsBound.substitute(newMapping));
            if (superBound != null)
                type.setSuperBound(superBound.substitute(newMapping));

            if (type.getExtendsBound() != null &&
                    type.getSuperBound() != null &&
                    AnnotatedTypes.areSame(type.getExtendsBound(), type.getSuperBound())) {
                return type.getExtendsBound();
            } else {
                return type;
            }
        }

        @Override
        public AnnotatedTypeMirror getErased() {
            // |? extends A&B| = |A|
            return getEffectiveExtendsBound().getErased();
        }

        boolean isPrintingBound = false;

        @SideEffectFree
        @Override
        public String toString(boolean printInvisible) {
            StringBuilder sb = new StringBuilder();
            sb.append(formatAnnotationString(annotations, printInvisible));
            sb.append("?");
            if (!isPrintingBound) {
                try {
                    isPrintingBound = true;
                    if (getSuperBoundField() != null && getSuperBoundField().getKind() != TypeKind.NULL) {
                        sb.append(" super ");
                        sb.append(getSuperBoundField().toString(printInvisible));
                    }
                    if (getExtendsBoundField() != null && getExtendsBoundField().getKind() != TypeKind.NONE) {
                        sb.append(" extends ");
                        sb.append(getExtendsBoundField().toString(printInvisible));
                    }
                } finally {
                    isPrintingBound = false;
                }
            }
            return sb.toString();
        }

        // Remove the typeArgHack once method type
        // argument inference and raw type handling is improved.
        private boolean typeArgHack = false;

        /* package-scope */ void setTypeArgHack() {
            typeArgHack = true;
        }

        /* package-scope */ boolean isTypeArgHack() {
            return typeArgHack;
        }
    }

    public static class AnnotatedIntersectionType extends AnnotatedTypeMirror {

        /**
         * Constructor for this type
         *
         * @param type  underlying kind of this type
         * @param atypeFactory TODO
         */
        private AnnotatedIntersectionType(IntersectionType type,
                AnnotatedTypeFactory atypeFactory) {
            super(type, atypeFactory);
        }

        @SideEffectFree
        @Override
        public String toString(boolean printInvisible) {
            StringBuilder sb = new StringBuilder();
            // Prevent an infinite recursion that might happen when calling toString
            // within deepCopy, caused by postAsSuper in (at least) the IGJ Checker.
            // if (this.supertypes == null) { return; }

            boolean isFirst = true;
            for(AnnotatedDeclaredType adt : this.directSuperTypes()) {
                if (!isFirst) sb.append(" & ");
                sb.append(adt.toString(printInvisible));
                isFirst = false;
            }
            return sb.toString();
        }

        @Override
        public <R, P> R accept(AnnotatedTypeVisitor<R, P> v, P p) {
            return v.visitIntersection(this, p);
        }

        @Override
        public AnnotatedIntersectionType getCopy(boolean copyAnnotations) {
            AnnotatedIntersectionType type =
                    new AnnotatedIntersectionType((IntersectionType) actualType, atypeFactory);
            if (copyAnnotations)
                type.addAnnotations(annotations);
            type.supertypes = this.supertypes;
            return type;
        }

        protected List<AnnotatedDeclaredType> supertypes;

        @Override
        public List<AnnotatedDeclaredType> directSuperTypes() {
            if (supertypes == null) {
                List<? extends TypeMirror> ubounds = ((IntersectionType)actualType).getBounds();
                List<AnnotatedDeclaredType> res = new ArrayList<AnnotatedDeclaredType>(ubounds.size());
                for (TypeMirror bnd : ubounds) {
                    res.add((AnnotatedDeclaredType) createType(bnd, atypeFactory));
                }
                supertypes = Collections.unmodifiableList(res);
            }
            return supertypes;
        }

        public List<AnnotatedDeclaredType> directSuperTypesField() {
            return supertypes;
        }

        void setDirectSuperTypes(List<AnnotatedDeclaredType> supertypes) {
            this.supertypes = new ArrayList<AnnotatedDeclaredType>(supertypes);
        }

        @Override
        public AnnotatedTypeMirror substitute(
                Map<? extends AnnotatedTypeMirror,
                    ? extends AnnotatedTypeMirror> mappings) {
            if (mappings.containsKey(this))
                return mappings.get(this);

            AnnotatedIntersectionType type = getCopy(true);

            Map<AnnotatedTypeMirror, AnnotatedTypeMirror> newMappings =
                    new HashMap<AnnotatedTypeMirror, AnnotatedTypeMirror>(mappings);
            newMappings.put(this, type);

            if (this.supertypes != null) {
                // watch need to copy upper bound as well
                List<AnnotatedDeclaredType> supertypes = new ArrayList<AnnotatedDeclaredType>();
                for (AnnotatedDeclaredType t : directSuperTypes())
                    supertypes.add((AnnotatedDeclaredType)t.substitute(newMappings));
                type.supertypes = supertypes;
            }
            return type;
        }
    }


    // TODO: Ensure union types are handled everywhere.
    // TODO: Should field "annotations" contain anything?
    public static class AnnotatedUnionType extends AnnotatedTypeMirror {

        /**
         * Constructor for this type
         *
         * @param type  underlying kind of this type
         * @param atypeFactory TODO
         */
        private AnnotatedUnionType(UnionType type,
                AnnotatedTypeFactory atypeFactory) {
            super(type, atypeFactory);
        }

        @SideEffectFree
        @Override
        public String toString(boolean printInvisible) {
            StringBuilder sb = new StringBuilder();

            boolean isFirst = true;
            for(AnnotatedDeclaredType adt : this.getAlternatives()) {
                if (!isFirst) sb.append(" | ");
                sb.append(adt.toString(printInvisible));
                isFirst = false;
            }
            return sb.toString();
        }

        @Override
        public <R, P> R accept(AnnotatedTypeVisitor<R, P> v, P p) {
            return v.visitUnion(this, p);
        }

        @Override
        public AnnotatedUnionType getCopy(boolean copyAnnotations) {
            AnnotatedUnionType type =
                    new AnnotatedUnionType((UnionType) actualType, atypeFactory);
            if (copyAnnotations)
                type.addAnnotations(annotations);
            type.alternatives = this.alternatives;
            return type;
        }

        protected List<AnnotatedDeclaredType> alternatives;

        public List<AnnotatedDeclaredType> getAlternatives() {
            if (alternatives == null) {
                List<? extends TypeMirror> ualts = ((UnionType)actualType).getAlternatives();
                List<AnnotatedDeclaredType> res = new ArrayList<AnnotatedDeclaredType>(ualts.size());
                for (TypeMirror alt : ualts) {
                    res.add((AnnotatedDeclaredType) createType(alt, atypeFactory));
                }
                alternatives = Collections.unmodifiableList(res);
            }
            return alternatives;
        }

        @Override
        public AnnotatedTypeMirror substitute(
                Map<? extends AnnotatedTypeMirror,
                    ? extends AnnotatedTypeMirror> mappings) {
            if (mappings.containsKey(this))
                return mappings.get(this);

            AnnotatedUnionType type = getCopy(true);

            Map<AnnotatedTypeMirror, AnnotatedTypeMirror> newMappings =
                    new HashMap<AnnotatedTypeMirror, AnnotatedTypeMirror>(mappings);
            newMappings.put(this, type);

            if (this.alternatives != null) {
                // watch need to copy alternatives as well
                List<AnnotatedDeclaredType> alternatives = new ArrayList<AnnotatedDeclaredType>();
                for (AnnotatedDeclaredType t : getAlternatives())
                    alternatives.add((AnnotatedDeclaredType)t.substitute(newMappings));
                type.alternatives = alternatives;
            }
            return type;
        }
    }



    public List<? extends AnnotatedTypeMirror> directSuperTypes() {
        return directSuperTypes(this);
    }

    // Version of method below for declared types
    protected final List<AnnotatedDeclaredType> directSuperTypes(
            AnnotatedDeclaredType type) {
        setSuperTypeFinder(type.atypeFactory);
        List<AnnotatedDeclaredType> supertypes =
            superTypeFinder.visitDeclared(type, null);
        atypeFactory.postDirectSuperTypes(type, supertypes);
        return supertypes;
    }

    // Version of method above for all types
    private final List<? extends AnnotatedTypeMirror> directSuperTypes(
            AnnotatedTypeMirror type) {
        setSuperTypeFinder(type.atypeFactory);
        List<? extends AnnotatedTypeMirror> supertypes =
            superTypeFinder.visit(type, null);
        atypeFactory.postDirectSuperTypes(type, supertypes);
        return supertypes;
    }

    private static void setSuperTypeFinder(AnnotatedTypeFactory factory) {
        if (superTypeFinder == null || superTypeFinder.atypeFactory != factory) {
            superTypeFinder = new SuperTypeFinder(factory);
        }
        if (replacer == null) {
            replacer = new Replacer(factory.types);
        }
    }

    private static SuperTypeFinder superTypeFinder;

    private static class SuperTypeFinder extends
    SimpleAnnotatedTypeVisitor<List<? extends AnnotatedTypeMirror>, Void> {
        private final Types types;
        private final AnnotatedTypeFactory atypeFactory;

        SuperTypeFinder(AnnotatedTypeFactory atypeFactory) {
            this.atypeFactory = atypeFactory;
            this.types = atypeFactory.types;
        }

        @Override
        public List<AnnotatedTypeMirror> defaultAction(AnnotatedTypeMirror t, Void p) {
            return new ArrayList<AnnotatedTypeMirror>();
        }


        /**
         * Primitive Rules:
         *
         * double >1 float
         * float >1 long
         * long >1 int
         * int >1 char
         * int >1 short
         * short >1 byte
         *
         * For easiness:
         * boxed(primitiveType) >: primitiveType
         */
        @Override
        public List<AnnotatedTypeMirror> visitPrimitive(AnnotatedPrimitiveType type, Void p) {
            List<AnnotatedTypeMirror> superTypes =
                new ArrayList<AnnotatedTypeMirror>();
            Set<AnnotationMirror> annotations = type.getAnnotations();

            // Find Boxed type
            TypeElement boxed = types.boxedClass(type.getUnderlyingType());
            AnnotatedDeclaredType boxedType = atypeFactory.getAnnotatedType(boxed);
            boxedType.replaceAnnotations(annotations);
            superTypes.add(boxedType);

            TypeKind superPrimitiveType = null;

            if (type.getKind() == TypeKind.BOOLEAN) {
                // Nothing
            } else if (type.getKind() == TypeKind.BYTE) {
                superPrimitiveType = TypeKind.SHORT;
            } else if (type.getKind() == TypeKind.CHAR) {
                superPrimitiveType = TypeKind.INT;
            } else if (type.getKind() == TypeKind.DOUBLE) {
                // Nothing
            } else if (type.getKind() == TypeKind.FLOAT) {
                superPrimitiveType = TypeKind.DOUBLE;
            } else if (type.getKind() == TypeKind.INT) {
                superPrimitiveType = TypeKind.LONG;
            } else if (type.getKind() == TypeKind.LONG) {
                superPrimitiveType = TypeKind.FLOAT;
            } else if (type.getKind() == TypeKind.SHORT) {
                superPrimitiveType = TypeKind.INT;
            } else
                assert false: "Forgot the primitive " + type;

            if (superPrimitiveType != null) {
                AnnotatedPrimitiveType superPrimitive = (AnnotatedPrimitiveType)
                    atypeFactory.toAnnotatedType(types.getPrimitiveType(superPrimitiveType));
                superPrimitive.addAnnotations(annotations);
                superTypes.add(superPrimitive);
            }

            return superTypes;
        }

        @Override
        public List<AnnotatedDeclaredType> visitDeclared(AnnotatedDeclaredType type, Void p) {
            List<AnnotatedDeclaredType> supertypes =
                new ArrayList<AnnotatedDeclaredType>();
            // Set<AnnotationMirror> annotations = type.getAnnotations();

            TypeElement typeElement =
                (TypeElement) type.getUnderlyingType().asElement();
            // Mapping of type variable to actual types
            Map<TypeParameterElement, AnnotatedTypeMirror> mapping =
                new HashMap<TypeParameterElement, AnnotatedTypeMirror>();

            for (int i = 0; i < typeElement.getTypeParameters().size() &&
                            i < type.getTypeArguments().size(); ++i) {
                mapping.put(typeElement.getTypeParameters().get(i),
                        type.getTypeArguments().get(i));
            }

            ClassTree classTree = atypeFactory.trees.getTree(typeElement);
            // Testing against enum and annotation. Ideally we can simply use element!
            if (classTree != null) {
                supertypes.addAll(supertypesFromTree(type, classTree));
            } else {
                supertypes.addAll(supertypesFromElement(type, typeElement));
                // final Element elem = type.getElement() == null ? typeElement : type.getElement();
            }

            for (AnnotatedDeclaredType dt : supertypes) {
                replacer.visit(dt, mapping);
            }

            return supertypes;
        }

        private List<AnnotatedDeclaredType> supertypesFromElement(AnnotatedDeclaredType type, TypeElement typeElement) {
            List<AnnotatedDeclaredType> supertypes = new ArrayList<AnnotatedDeclaredType>();
            // Find the super types: Start with enums and superclass
            if (typeElement.getKind() == ElementKind.ENUM) {
                DeclaredType dt = (DeclaredType) typeElement.getSuperclass();
                AnnotatedDeclaredType adt = (AnnotatedDeclaredType) atypeFactory.toAnnotatedType(dt);
                List<AnnotatedTypeMirror> tas = adt.getTypeArguments();
                List<AnnotatedTypeMirror> newtas = new ArrayList<AnnotatedTypeMirror>();
                for (AnnotatedTypeMirror t : tas) {
                    // If the type argument of super is the same as the input type
                    if (atypeFactory.types.isSameType(t.getUnderlyingType(), type.getUnderlyingType())) {
                        t.addAnnotations(type.getAnnotations());
                        newtas.add(t);
                    }
                }
                adt.setTypeArguments(newtas);
                supertypes.add(adt);
            } else if (typeElement.getSuperclass().getKind() != TypeKind.NONE) {
                DeclaredType superClass = (DeclaredType) typeElement.getSuperclass();
                AnnotatedDeclaredType dt =
                    (AnnotatedDeclaredType) atypeFactory.toAnnotatedType(superClass);
                supertypes.add(dt);
            } else if (!ElementUtils.isObject(typeElement)) {
                supertypes.add(createTypeOfObject(atypeFactory));
            }
            for (TypeMirror st : typeElement.getInterfaces()) {
                AnnotatedDeclaredType ast =
                    (AnnotatedDeclaredType) atypeFactory.toAnnotatedType(st);
                supertypes.add(ast);
            }
            TypeFromElement.annotateSupers(supertypes, typeElement);

            if (type.wasRaw()) {
                for (AnnotatedDeclaredType adt : supertypes) {
                    adt.setWasRaw();
                }
            }
            return supertypes;
        }

        private List<AnnotatedDeclaredType> supertypesFromTree(AnnotatedDeclaredType type, ClassTree classTree) {
            List<AnnotatedDeclaredType> supertypes = new ArrayList<AnnotatedDeclaredType>();
            if (classTree.getExtendsClause() != null) {
                AnnotatedDeclaredType adt = (AnnotatedDeclaredType)
                    atypeFactory.fromTypeTree(classTree.getExtendsClause());
                supertypes.add(adt);
            } else if (!ElementUtils.isObject(TreeUtils.elementFromDeclaration(classTree))) {
                supertypes.add(createTypeOfObject(atypeFactory));
            }

            for (Tree implemented : classTree.getImplementsClause()) {
                AnnotatedDeclaredType adt = (AnnotatedDeclaredType)
                    atypeFactory.getAnnotatedTypeFromTypeTree(implemented);
                supertypes.add(adt);
            }

            TypeElement elem = TreeUtils.elementFromDeclaration(classTree);
            if (elem.getKind() == ElementKind.ENUM) {
                DeclaredType dt = (DeclaredType) elem.getSuperclass();
                AnnotatedDeclaredType adt = (AnnotatedDeclaredType) atypeFactory.toAnnotatedType(dt);
                List<AnnotatedTypeMirror> tas = adt.getTypeArguments();
                List<AnnotatedTypeMirror> newtas = new ArrayList<AnnotatedTypeMirror>();
                for (AnnotatedTypeMirror t : tas) {
                    // If the type argument of super is the same as the input type
                    if (atypeFactory.types.isSameType(t.getUnderlyingType(), type.getUnderlyingType())) {
                        t.addAnnotations(type.getAnnotations());
                        newtas.add(t);
                    }
                }
                adt.setTypeArguments(newtas);
                supertypes.add(adt);
            }
            if (type.wasRaw()) {
                for (AnnotatedDeclaredType adt : supertypes) {
                    adt.setWasRaw();
                }
            }
            return supertypes;
        }

        /**
         * For type = A[ ] ==>
         *  Object >: A[ ]
         *  Clonable >: A[ ]
         *  java.io.Serializable >: A[ ]
         *
         * if A is reference type, then also
         *  B[ ] >: A[ ] for any B[ ] >: A[ ]
         */
        @Override
        public List<AnnotatedTypeMirror> visitArray(AnnotatedArrayType type, Void p) {
            List<AnnotatedTypeMirror> superTypes = new ArrayList<AnnotatedTypeMirror>();
            Set<AnnotationMirror> annotations = type.getAnnotations();
            Elements elements = atypeFactory.elements;
            final AnnotatedTypeMirror objectType =
                atypeFactory.getAnnotatedType(elements.getTypeElement("java.lang.Object"));
            objectType.addAnnotations(annotations);
            superTypes.add(objectType);

            final AnnotatedTypeMirror cloneableType =
                atypeFactory.getAnnotatedType(elements.getTypeElement("java.lang.Cloneable"));
            cloneableType.addAnnotations(annotations);
            superTypes.add(cloneableType);

            final AnnotatedTypeMirror serializableType =
                atypeFactory.getAnnotatedType(elements.getTypeElement("java.io.Serializable"));
            serializableType.addAnnotations(annotations);
            superTypes.add(serializableType);

            if (type.getComponentType() instanceof AnnotatedReferenceType) {
                for (AnnotatedTypeMirror sup : type.getComponentType().directSuperTypes()) {
                    ArrayType arrType = atypeFactory.types.getArrayType(sup.getUnderlyingType());
                    AnnotatedArrayType aarrType = (AnnotatedArrayType)
                        atypeFactory.toAnnotatedType(arrType);
                    aarrType.setComponentType(sup);
                    aarrType.addAnnotations(annotations);
                    superTypes.add(aarrType);
                }
            }

            return superTypes;
        }

        @Override
        public List<AnnotatedTypeMirror> visitTypeVariable(AnnotatedTypeVariable type, Void p) {
            List<AnnotatedTypeMirror> superTypes = new ArrayList<AnnotatedTypeMirror>();
            if (type.getEffectiveUpperBound() != null)
                superTypes.add(AnnotatedTypes.deepCopy(type.getEffectiveUpperBound()));
            return superTypes;
        }

        @Override
        public List<AnnotatedTypeMirror> visitWildcard(AnnotatedWildcardType type, Void p) {
            List<AnnotatedTypeMirror> superTypes = new ArrayList<AnnotatedTypeMirror>();
            if (type.getEffectiveExtendsBound() != null)
                superTypes.add(AnnotatedTypes.deepCopy(type.getEffectiveExtendsBound()));
            return superTypes;
        }
    };

    private static Replacer replacer;

    private static class Replacer extends AnnotatedTypeScanner<Void, Map<TypeParameterElement, AnnotatedTypeMirror>> {
        final Types types;

        public Replacer(Types types) {
            this.types = types;
        }

        @Override
        public Void visitDeclared(AnnotatedDeclaredType type, Map<TypeParameterElement, AnnotatedTypeMirror> mapping) {
            List<AnnotatedTypeMirror> args = new ArrayList<AnnotatedTypeMirror>();
            for (AnnotatedTypeMirror arg : type.getTypeArguments()) {
                Element elem = types.asElement(arg.getUnderlyingType());
                if ((elem != null) &&
                        (elem.getKind() == ElementKind.TYPE_PARAMETER) &&
                        (mapping.containsKey(elem))) {
                    AnnotatedTypeMirror other = mapping.get(elem);
                    other.replaceAnnotations(arg.annotations);
                    args.add(other);
                } else {
                    args.add(arg);
                }
            }
            type.setTypeArguments(args);
            return super.visitDeclared(type, mapping);
        }

        @Override
        public Void visitArray(AnnotatedArrayType type, Map<TypeParameterElement, AnnotatedTypeMirror> mapping) {
            AnnotatedTypeMirror comptype = type.getComponentType();
            Element elem = types.asElement(comptype.getUnderlyingType());
            AnnotatedTypeMirror other;
            if ((elem != null) &&
                    (elem.getKind() == ElementKind.TYPE_PARAMETER) &&
                    (mapping.containsKey(elem))) {
                other = mapping.get(elem);
                other.replaceAnnotations(comptype.annotations);
                type.setComponentType(other);
            }
            return super.visitArray(type, mapping);
        }
    };

    /**
     * Implementation that handles a single hierarchy (identified by top).
     */
    private static void fixupBoundAnnotationsImpl(QualifierHierarchy qualifierHierarchy,
            AnnotatedTypeMirror lowerBound, AnnotatedTypeMirror upperBound,
            Collection<AnnotationMirror> allAnnotations,
            AnnotationMirror top,
            AnnotationMirror lAnno, AnnotationMirror uAnno) {
        if (lAnno == null) {
            AnnotationMirror a = qualifierHierarchy.getAnnotationInHierarchy(allAnnotations, top);
            if (a != null) {
                lowerBound.replaceAnnotation(a);
                return;
            } else {
                lAnno = qualifierHierarchy.getBottomAnnotation(top);
                lowerBound.replaceAnnotation(lAnno);
            }
        }

        if (uAnno == null) {
            // TODO: The subtype tests below fail with empty annotations.
            // Is there anything better to do here?
        } else if (qualifierHierarchy.isSubtype(lAnno, uAnno)) {
            // Nothing to do if lAnnos is a subtype of uAnnos.
        } else if (qualifierHierarchy.isSubtype(uAnno, lAnno)) {
            lowerBound.replaceAnnotation(uAnno);
        } else {
            ErrorReporter.errorAbort("AnnotatedTypeMirror.fixupBoundAnnotations: default annotation on lower bound ( " + lAnno + ") is inconsistent with explicit upper bound: " + upperBound);
        }
    }

}
