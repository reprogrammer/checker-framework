package checkers.util;

import checkers.quals.DefaultLocation;
import checkers.quals.DefaultQualifier;
import checkers.quals.DefaultQualifiers;
import checkers.types.AnnotatedTypeFactory;
import checkers.types.AnnotatedTypeMirror;
import checkers.types.AnnotatedTypeMirror.AnnotatedDeclaredType;
import checkers.types.AnnotatedTypeMirror.AnnotatedExecutableType;
import checkers.types.AnnotatedTypeMirror.AnnotatedIntersectionType;
import checkers.types.AnnotatedTypeMirror.AnnotatedNoType;
import checkers.types.AnnotatedTypeMirror.AnnotatedTypeVariable;
import checkers.types.AnnotatedTypeMirror.AnnotatedWildcardType;
import checkers.types.QualifierHierarchy;
import checkers.types.visitors.AnnotatedTypeScanner;

import javacutils.AnnotationUtils;
import javacutils.ErrorReporter;
import javacutils.InternalUtils;
import javacutils.Pair;
import javacutils.TreeUtils;

import java.lang.annotation.Annotation;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Elements;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type.WildcardType;

/**
 * Determines the default qualifiers on a type.
 * Default qualifiers are specified via the {@link DefaultQualifier} annotation.
 *
 * @see DefaultQualifier
 */
public class QualifierDefaults {

    // TODO add visitor state to get the default annotations from the top down?
    // TODO apply from package elements also
    // TODO try to remove some dependencies (e.g. on factory)

    private final Elements elements;
    private final AnnotatedTypeFactory atypeFactory;

    @SuppressWarnings("serial")
    private static class AMLocTreeSet extends TreeSet<Pair<AnnotationMirror, DefaultLocation>> {
        public AMLocTreeSet() {
            super(new AMLocComparator());
        }

        static class AMLocComparator implements Comparator<Pair<AnnotationMirror, DefaultLocation>> {
            @Override
            public int compare(Pair<AnnotationMirror, DefaultLocation> o1,
                    Pair<AnnotationMirror, DefaultLocation> o2) {
                int snd = o1.second.compareTo(o2.second);
                if (snd == 0) {
                    return AnnotationUtils.annotationOrdering().compare(o1.first, o2.first);
                } else {
                    return snd;
                }
            }
        }

        // Cannot wrap into unmodifiable set :-(
        // TODO cleaner solution?
        public static final AMLocTreeSet EMPTY_SET = new AMLocTreeSet();
    }

    /** Defaults that apply, if nothing else applies. */
    private final AMLocTreeSet absoluteDefaults = new AMLocTreeSet();

    /** Defaults that apply for a certain Element.
     * On the one hand this is used for caching (an earlier name for the field was
     * "qualifierCache". It can also be used by type systems to set defaults for
     * certain Elements.
     */
    private final Map<Element, AMLocTreeSet> elementDefaults =
            new IdentityHashMap<Element, AMLocTreeSet>();

    /**
     * @param elements interface to Element data in the current processing environment
     * @param atypeFactory an annotation factory, used to get annotations by name
     */
    public QualifierDefaults(Elements elements, AnnotatedTypeFactory atypeFactory) {
        this.elements = elements;
        this.atypeFactory = atypeFactory;
    }

    /**
     * Sets the default annotations.  A programmer may override this by
     * writing the @DefaultQualifier annotation on an element.
     */
    public void addAbsoluteDefault(AnnotationMirror absoluteDefaultAnno, DefaultLocation location) {
        checkDuplicates(absoluteDefaults, absoluteDefaultAnno, location);
        absoluteDefaults.add(Pair.of(absoluteDefaultAnno, location));
    }

    public void addAbsoluteDefaults(AnnotationMirror absoluteDefaultAnno, DefaultLocation[] locations) {
        for (DefaultLocation location : locations) {
            addAbsoluteDefault(absoluteDefaultAnno, location);
        }
    }

    /**
     * Sets the default annotations for a certain Element.
     */
    public void addElementDefault(Element elem, AnnotationMirror elementDefaultAnno, DefaultLocation location) {
        AMLocTreeSet prevset = elementDefaults.get(elem);
        if (prevset != null) {
            checkDuplicates(prevset, elementDefaultAnno, location);
        } else {
            prevset = new AMLocTreeSet();
        }
        prevset.add(Pair.of(elementDefaultAnno, location));
        elementDefaults.put(elem, prevset);
    }

    private void checkDuplicates(Set<Pair<AnnotationMirror, DefaultLocation>> prevset,
            AnnotationMirror newanno, DefaultLocation newloc) {
        for (Pair<AnnotationMirror, DefaultLocation> def : prevset) {
            AnnotationMirror anno = def.first;
            QualifierHierarchy qh = atypeFactory.getQualifierHierarchy();
            if (!newanno.equals(anno) &&
                    qh.isSubtype(newanno, qh.getTopAnnotation(anno))) {
                if (newloc == def.second) {
                    ErrorReporter.errorAbort("Only one qualifier from a hierarchy can be the default! Existing: "
                            + prevset + " and new: " + newanno);
                }
            }
        }
    }

    /**
     * Applies default annotations to a type given an {@link Element}.
     *
     * @param elt the element from which the type was obtained
     * @param type the type to annotate
     */
    public void annotate(Element elt, AnnotatedTypeMirror type) {
        applyDefaultsElement(elt, type);
    }

    /**
     * Applies default annotations to a type given a {@link Tree}.
     *
     * @param tree the tree from which the type was obtained
     * @param type the type to annotate
     */
    public void annotate(Tree tree, AnnotatedTypeMirror type) {
        applyDefaults(tree, type);
    }

    /**
     * Determines the nearest enclosing element for a tree by climbing the tree
     * toward the root and obtaining the element for the first declaration
     * (variable, method, or class) that encloses the tree.
     * Initializers of local variables are handled in a special way:
     * within an initializer we look for the DefaultQualifier(s) annotation and
     * keep track of the previously visited tree.
     * TODO: explain the behavior better.
     *
     * @param tree the tree
     * @return the nearest enclosing element for a tree
     */
    private Element nearestEnclosingExceptLocal(Tree tree) {
        TreePath path = atypeFactory.getPath(tree);
        if (path == null) {
            Element method = atypeFactory.getEnclosingMethod(tree);
            if (method != null) {
                return method;
            } else {
                return InternalUtils.symbol(tree);
            }
        }

        Tree prev = null;

        for (Tree t : path) {
            switch (t.getKind()) {
            case VARIABLE:
                VariableTree vtree = (VariableTree)t;
                ExpressionTree vtreeInit = vtree.getInitializer();
                if (vtreeInit != null && prev == vtreeInit) {
                    Element elt = TreeUtils.elementFromDeclaration((VariableTree)t);
                    DefaultQualifier d = elt.getAnnotation(DefaultQualifier.class);
                    DefaultQualifiers ds = elt.getAnnotation(DefaultQualifiers.class);

                    if (d == null && ds == null)
                        break;
                }
                if (prev!=null && prev.getKind() == Tree.Kind.MODIFIERS) {
                    // Annotations are modifiers. We do not want to apply the local variable default to
                    // annotations. Without this, test fenum/TestSwitch failed, because the default for
                    // an argument became incompatible with the declared type.
                    break;
                }
                return TreeUtils.elementFromDeclaration((VariableTree)t);
            case METHOD:
                return TreeUtils.elementFromDeclaration((MethodTree)t);
            case CLASS:
            case ENUM:
            case INTERFACE:
            case ANNOTATION_TYPE:
                return TreeUtils.elementFromDeclaration((ClassTree)t);
            default: // Do nothing.
            }
            prev = t;
        }

        return null;
    }

    /**
     * Applies default annotations to a type.
     * A {@link Tree} that determines the appropriate scope for defaults.
     * <p>
     *
     * For instance, if the tree is associated with a declaration (e.g., it's
     * the use of a field, or a method invocation), defaults in the scope of the
     * <i>declaration</i> are used; if the tree is not associated with a
     * declaration (e.g., a typecast), defaults in the scope of the tree are
     * used.
     *
     * @param tree the tree associated with the type
     * @param type the type to which defaults will be applied
     *
     * @see #applyDefaultsElement(Element, AnnotatedTypeMirror)
     */
    private void applyDefaults(Tree tree, AnnotatedTypeMirror type) {

        // The location to take defaults from.
        Element elt = null;
        switch (tree.getKind()) {
            case MEMBER_SELECT:
                elt = TreeUtils.elementFromUse((MemberSelectTree)tree);
                break;

            case IDENTIFIER:
                elt = TreeUtils.elementFromUse((IdentifierTree)tree);
                break;

            case METHOD_INVOCATION:
                elt = TreeUtils.elementFromUse((MethodInvocationTree)tree);
                break;

            // TODO cases for array access, etc. -- every expression tree
            // (The above probably means that we should use defaults in the
            // scope of the declaration of the array.  Is that right?  -MDE)

            default:
                // If no associated symbol was found, use the tree's (lexical)
                // scope.
                elt = nearestEnclosingExceptLocal(tree);
                // elt = nearestEnclosing(tree);
        }
        // System.out.println("applyDefaults on tree " + tree +
        //        " gives elt: " + elt + "(" + elt.getKind() + ")");

        if (elt != null) {
            applyDefaultsElement(elt, type);
        }
    }

    private Set<Pair<AnnotationMirror, DefaultLocation>> fromDefaultQualifier(DefaultQualifier dq) {
        // TODO: I want to simply write d.value(), but that doesn't work.
        // It works in other places, e.g. see handling of @SubtypeOf.
        // The hack below should probably be added to:
        // Class<? extends Annotation> cls = AnnotationUtils.parseTypeValue(dq, "value");
        Class<? extends Annotation> cls;
        try {
            cls = dq.value();
        } catch( MirroredTypeException mte ) {
            try {
                @SuppressWarnings("unchecked")
                Class<? extends Annotation> clscast = (Class<? extends Annotation>) Class.forName(mte.getTypeMirror().toString());
                cls = clscast;
            } catch (ClassNotFoundException e) {
                ErrorReporter.errorAbort("Could not load qualifier: " + e.getMessage(), e);
                cls = null;
            }
        }

        AnnotationMirror anno = AnnotationUtils.fromClass(elements, cls);

        if (anno == null) {
            return null;
        }

        if (!atypeFactory.isSupportedQualifier(anno)) {
            anno = atypeFactory.aliasedAnnotation(anno);
        }

        if (atypeFactory.isSupportedQualifier(anno)) {
            EnumSet<DefaultLocation> locations = EnumSet.of(dq.locations()[0], dq.locations());
            Set<Pair<AnnotationMirror, DefaultLocation>> ret = new HashSet<Pair<AnnotationMirror, DefaultLocation>>(locations.size());
            for (DefaultLocation loc : locations) {
                ret.add(Pair.of(anno, loc));
            }
            return ret;
        } else {
            return null;
        }
    }

    private AMLocTreeSet defaultsAt(final Element elt) {
        if (elt == null) {
            return AMLocTreeSet.EMPTY_SET;
        }

        if (elementDefaults.containsKey(elt)) {
            return elementDefaults.get(elt);
        }

        AMLocTreeSet qualifiers = null;

        {
            DefaultQualifier d = elt.getAnnotation(DefaultQualifier.class);

            if (d != null) {
                qualifiers = new AMLocTreeSet();
                Set<Pair<AnnotationMirror, DefaultLocation>> p = fromDefaultQualifier(d);

                if (p != null) {
                    qualifiers.addAll(p);
                }
            }
        }

        {
            DefaultQualifiers ds = elt.getAnnotation(DefaultQualifiers.class);
            if (ds != null) {
                if (qualifiers == null) {
                    qualifiers = new AMLocTreeSet();
                }
                for (DefaultQualifier d : ds.value()) {
                    Set<Pair<AnnotationMirror, DefaultLocation>> p = fromDefaultQualifier(d);
                    if (p != null) {
                        qualifiers.addAll(p);
                    }
                }
            }
        }

        Element parent;
        if (elt.getKind() == ElementKind.PACKAGE)
            parent = ((Symbol) elt).owner;
        else
            parent = elt.getEnclosingElement();

        AMLocTreeSet parentDefaults = defaultsAt(parent);
        if (qualifiers == null || qualifiers.isEmpty())
            qualifiers = parentDefaults;
        else
            qualifiers.addAll(parentDefaults);

        if (qualifiers != null && !qualifiers.isEmpty()) {
            elementDefaults.put(elt, qualifiers);
            return qualifiers;
        } else {
            return AMLocTreeSet.EMPTY_SET;
        }
    }

    /**
     * Applies default annotations to a type.
     * The defaults are taken from an {@link Element} by using the
     * {@link DefaultQualifier} annotation present on the element
     * or any of its enclosing elements.
     *
     * @param annotationScope the element representing the nearest enclosing
     *        default annotation scope for the type
     * @param type the type to which defaults will be applied
     */
    private void applyDefaultsElement(final Element annotationScope, final AnnotatedTypeMirror type) {
        AMLocTreeSet defaults = defaultsAt(annotationScope);
        DefaultApplierElement applier = new DefaultApplierElement(atypeFactory, annotationScope, type);

        for (Pair<AnnotationMirror, DefaultLocation> def : defaults) {
            applier.apply(def.first, def.second);
        }

        for (Pair<AnnotationMirror, DefaultLocation> def : absoluteDefaults) {
            applier.apply(def.first, def.second);
        }
    }

    public static class DefaultApplierElement {

        private final AnnotatedTypeFactory atypeFactory;
        private final Element scope;
        private final AnnotatedTypeMirror type;

        // Should only be set by {@link apply}
        private DefaultLocation location;

        private final DefaultApplierElementImpl impl;

        public DefaultApplierElement(AnnotatedTypeFactory atypeFactory, Element scope, AnnotatedTypeMirror type) {
            this.atypeFactory = atypeFactory;
            this.scope = scope;
            this.type = type;
            this.impl = new DefaultApplierElementImpl();
        }

        public void apply(AnnotationMirror toApply, DefaultLocation location) {
            this.location = location;
            impl.visit(type, toApply);
        }

        /**
         * Returns true if the given qualifier should be applied to the given type.  Currently we do not
         * apply defaults to void types, packages, wildcards, and type variables
         * @param type Type to which qual would be applied
         * @param qual A default qualifier to apply
         * @return true if this application should proceed
         */
        private static boolean shouldBeAnnotated(final AnnotatedTypeMirror type,
                final AnnotationMirror qual) {

            return !( type == null ||
                    type.getKind() == TypeKind.NONE ||
                    type.getKind() == TypeKind.WILDCARD ||
                    type.getKind() == TypeKind.TYPEVAR  ||
                    type instanceof AnnotatedNoType );

        }

        private static void doApply(AnnotatedTypeMirror type, AnnotationMirror qual) {

            if (!shouldBeAnnotated(type, qual)) {
                return;
            }

            // Add the default annotation, but only if no other
            // annotation is present.
            if (!type.isAnnotatedInHierarchy(qual)) {
                type.addAnnotation(qual);
            }

            /* Intersection types, list the types in the direct supertypes.
             * Make sure to apply the default there too.
             * Use the direct supertypes field to prevent an infinite recursion
             * with the IGJATF.postDirectSuperTypes. TODO: investigate better way.
             */
            if (type.getKind() == TypeKind.INTERSECTION) {
                List<AnnotatedDeclaredType> sups = ((AnnotatedIntersectionType)type).directSuperTypesField();
                if (sups != null) {
                    for (AnnotatedTypeMirror sup : sups) {
                        if (!sup.isAnnotatedInHierarchy(qual)) {
                            sup.addAnnotation(qual);
                        }
                    }
                }
            }
        }


        private class DefaultApplierElementImpl extends AnnotatedTypeScanner<Void, AnnotationMirror> {

            @Override
            public Void scan(AnnotatedTypeMirror t, AnnotationMirror qual) {
                if (!shouldBeAnnotated(t, qual)) {
                    return super.scan(t, qual);
                }

                switch (location) {
                case FIELD: {
                    if (scope.getKind() == ElementKind.FIELD &&
                            t == type) {
                        doApply(t, qual);
                    }
                    break;
                }
                case LOCAL_VARIABLE: {
                    if (scope.getKind() == ElementKind.LOCAL_VARIABLE &&
                            t == type) {
                        // TODO: how do we determine that we are in a cast or instanceof type?
                        doApply(t, qual);
                    }
                    break;
                }
                case RESOURCE_VARIABLE: {
                    if (scope.getKind() == ElementKind.RESOURCE_VARIABLE &&
                            t == type) {
                        doApply(t, qual);
                    }
                    break;
                }
                case EXCEPTION_PARAMETER: {
                    if (scope.getKind() == ElementKind.EXCEPTION_PARAMETER &&
                            t == type) {
                        doApply(t, qual);
                    }
                    break;
                }
                case PARAMETERS: {
                    if (scope.getKind() == ElementKind.PARAMETER &&
                            t == type) {
                        doApply(t, qual);
                    } else if ((scope.getKind() == ElementKind.METHOD || scope.getKind() == ElementKind.CONSTRUCTOR) &&
                            t.getKind() == TypeKind.EXECUTABLE &&
                            t == type) {

                        for (AnnotatedTypeMirror atm : ((AnnotatedExecutableType)t).getParameterTypes()) {
                            doApply(atm, qual);
                        }
                    }
                    break;
                }
                case RECEIVERS: {
                    if (scope.getKind() == ElementKind.PARAMETER &&
                            t == type && "this".equals(scope.getSimpleName())) {
                        // TODO: comparison against "this" is ugly, won't work
                        // for all possible names for receiver parameter.
                        doApply(t, qual);
                    } else if ((scope.getKind() == ElementKind.METHOD) &&
                            t.getKind() == TypeKind.EXECUTABLE &&
                            t == type) {
                        doApply(((AnnotatedExecutableType)t).getReceiverType(), qual);
                    }
                    break;
                }
                case RETURNS: {
                    if (scope.getKind() == ElementKind.METHOD &&
                            t.getKind() == TypeKind.EXECUTABLE &&
                            t == type) {
                        doApply(((AnnotatedExecutableType)t).getReturnType(), qual);
                    }
                    break;
                }
                case IMPLICIT_UPPER_BOUNDS: {
                    if (this.isTypeVarExtendsImplicit) {
                        doApply(t, qual);
                    }
                    break;
                }
                case EXPLICIT_UPPER_BOUNDS: {
                    if (this.isTypeVarExtendsExplicit) {
                        doApply(t, qual);
                    }
                    break;
                }
                case UPPER_BOUNDS: {
                    if (this.isTypeVarExtendsImplicit || this.isTypeVarExtendsExplicit) {
                        doApply(t, qual);
                    }
                    break;
                }
                case OTHERWISE:
                case ALL: {
                    // TODO: forbid ALL if anything else was given.
                    doApply(t, qual);
                    break;
                }
                default: {
                    ErrorReporter
                            .errorAbort("QualifierDefaults.DefaultApplierElement: unhandled location: " +
                                    location);
                    return null;
                }
                }

                return super.scan(t, qual);
            }

            @Override
            public void reset() {
                super.reset();
                impl.isTypeVarExtendsImplicit = false;
                impl.isTypeVarExtendsExplicit = false;
            }

            private boolean isTypeVarExtendsImplicit = false;
            private boolean isTypeVarExtendsExplicit = false;

            @Override
            public Void visitTypeVariable(AnnotatedTypeVariable type,
                    AnnotationMirror qual) {
                if (visitedNodes.containsKey(type)) {
                    return visitedNodes.get(type);
                }

                Void r = scan(type.getLowerBoundField(), qual);
                visitedNodes.put(type, r);
                Element tvel = type.getUnderlyingType().asElement();
                // TODO: find a better way to do this
                TreePath treepath = atypeFactory.getTreeUtils().getPath(tvel);
                Tree tree = treepath == null ? null : treepath.getLeaf();

                boolean prevIsTypeVarExtendsImplicit = isTypeVarExtendsImplicit;
                boolean prevIsTypeVarExtendsExplicit = isTypeVarExtendsExplicit;

                if (tree == null) {
                    // This is not only for elements from binaries, but also
                    // when the compilation unit is no-longer available.
                    isTypeVarExtendsImplicit = false;
                    isTypeVarExtendsExplicit = true;
                } else {
                    if (tree.getKind() == Tree.Kind.TYPE_PARAMETER) {
                        TypeParameterTree tptree = (TypeParameterTree) tree;

                        List<? extends Tree> bnds = tptree.getBounds();
                        if (bnds != null && !bnds.isEmpty()) {
                            isTypeVarExtendsImplicit = false;
                            isTypeVarExtendsExplicit = true;
                        } else {
                            isTypeVarExtendsImplicit = true;
                            isTypeVarExtendsExplicit = false;
                        }
                    }
                }
                try {
                    r = scanAndReduce(type.getUpperBoundField(), qual, r);
                } finally {
                    isTypeVarExtendsImplicit = prevIsTypeVarExtendsImplicit;
                    isTypeVarExtendsExplicit = prevIsTypeVarExtendsExplicit;
                }
                visitedNodes.put(type, r);
                return r;
            }

            @Override
            public Void visitWildcard(AnnotatedWildcardType type,
                    AnnotationMirror qual) {
                if (visitedNodes.containsKey(type)) {
                    return visitedNodes.get(type);
                }
                Void r;
                boolean prevIsTypeVarExtendsImplicit = isTypeVarExtendsImplicit;
                boolean prevIsTypeVarExtendsExplicit = isTypeVarExtendsExplicit;

                WildcardType wc = (WildcardType) type.getUnderlyingType();

                if (wc.isUnbound() &&
                        wc.bound != null) {
                    // If the wildcard bound is implicit, look what
                    // the type variable bound would be.
                    Element tvel = wc.bound.asElement();
                    TreePath treepath = atypeFactory.getTreeUtils().getPath(tvel);
                    Tree tree = treepath == null ? null : treepath.getLeaf();

                    if (tree != null &&
                            tree.getKind() == Tree.Kind.TYPE_PARAMETER) {
                        TypeParameterTree tptree = (TypeParameterTree) tree;

                        List<? extends Tree> bnds = tptree.getBounds();
                        if (bnds != null && !bnds.isEmpty()) {
                            isTypeVarExtendsImplicit = false;
                            isTypeVarExtendsExplicit = true;
                        } else {
                            isTypeVarExtendsImplicit = true;
                            isTypeVarExtendsExplicit = false;
                        }
                    } else {
                        isTypeVarExtendsImplicit = false;
                        isTypeVarExtendsExplicit = true;
                    }
                } else {
                    isTypeVarExtendsImplicit = false;
                    isTypeVarExtendsExplicit = true;
                }
                try {
                    r = scan(type.getExtendsBoundField(), qual);
                } finally {
                    isTypeVarExtendsImplicit = prevIsTypeVarExtendsImplicit;
                    isTypeVarExtendsExplicit = prevIsTypeVarExtendsExplicit;
                }
                visitedNodes.put(type, r);
                r = scanAndReduce(type.getSuperBoundField(), qual, r);
                visitedNodes.put(type, r);
                return r;
            }
        }
    }
}
