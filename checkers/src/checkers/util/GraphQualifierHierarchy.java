package checkers.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;

import checkers.quals.PolymorphicQualifier;
import checkers.nullness.quals.*;
import checkers.types.QualifierHierarchy;

// It's functional, but requires optimization and better documentation
//
/**
 * Represents the type qualifier hierarchy of a type system.
 *
 * This class is immutable and can be only created through {@link Factory}.
 */
public class GraphQualifierHierarchy extends QualifierHierarchy {

    /**
     * Factory used to create an instance of {@link GraphQualifierHierarchy}.
     * A factory can be used to create at most one {@link GraphQualifierHierarchy}.
     *
     * To create a hierarchy, a client may do so in three steps:
     * 1. add qualifiers using {@link #addQualifier(AnnotationMirror)};
     * 2. add subtype relations using {@link #addSubtype(AnnotationMirror, AnnotationMirror)}
     * 3. build the hierarchy and gets using {@link #build()}.
     *
     * Notice that {@link #addSubtype(AnnotationMirror, AnnotationMirror)} adds
     * the two qualifiers to the hierarchy if they are not already in.
     *
     * Also, once the client builds a hierarchy through {@link #build()},
     * no further modifications are allowed nor can it making a new instance.
     *
     * Clients build the hierarchy using {@link #addQualifier(AnnotationMirror)}
     * and {@link #addSubtype(AnnotationMirror, AnnotationMirror)}, then get
     * the instance with calling {@link #build()}
     */
    public static class Factory {
        /** map: qualifier --> supertypesMap of the qualifier */
        // supertypesMap is immutable once GraphQualifierHierarchy is built
        private Map<AnnotationMirror, Set<AnnotationMirror>> supertypes;

        private AnnotationMirror polyQualifier;

        private boolean wasBuilt = false;

        public Factory() {
            supertypes = AnnotationUtils.createAnnotationMap();
        }

        /**
         * Adds the passed qualifier to the hierarchy.  Clients need to specify
         * its super qualifiers in subsequent calls to
         * {@link #addSubtype(AnnotationMirror, AnnotationMirror)}.
         */
        public void addQualifier(AnnotationMirror qual) {
            assertNotBuilt();
            if (supertypes.containsKey(qual))
                return;
            supertypes.put(qual, AnnotationUtils.createAnnotationSet());

            if (isPolymorphic(qual))
                this.polyQualifier = qual;
        }

        private boolean isPolymorphic(AnnotationMirror qual) {
            if (qual == null)
                return false;
            Element qualElt = qual.getAnnotationType().asElement();
            return qualElt.getAnnotation(PolymorphicQualifier.class) != null;
        }

        /**
         * Adds a subtype relationship between the two type qualifiers.
         *
         * @param sub   the sub type qualifier
         * @param sup   the super type qualifier
         */
        public void addSubtype(AnnotationMirror sub, AnnotationMirror sup) {
            assertNotBuilt();
            addQualifier(sub);
            addQualifier(sup);
            supertypes.get(sub).add(sup);
        }

        /**
         * Returns an instance of {@link GraphQualifierHierarchy} that
         * represents the hierarchy built so far
         */
        public GraphQualifierHierarchy build() {
            assertNotBuilt();
            addPolyRelations();
            wasBuilt = true;
            return new GraphQualifierHierarchy(this);
        }

        private void assertNotBuilt() {
            if (wasBuilt)
                throw new IllegalStateException("qualifier hierarchy already built");
        }

        /**
         * add the relationships for polymorphic qualifiers.
         *
         * A polymorphic qualifier needs to be (take {@link PolyNull} for example)
         * 1. a subtype of the root qualifier (e.g. {@link Nullable})
         * 2. a supertype of all the bottom qualifiers  (e.g. {@link NonNull})
         */
        private void addPolyRelations() {
            if (polyQualifier == null)
                return;

            // find its supertypesMap
            if (supertypes.get(polyQualifier).isEmpty()) {
                AnnotationMirror root = findRoot(supertypes, polyQualifier);
                addSubtype(polyQualifier, root);
            }

            Set<AnnotationMirror> bottoms = findBottoms(supertypes, polyQualifier);
            for (AnnotationMirror bottom : bottoms)
                addSubtype(bottom, polyQualifier);
        }
    }

    private final Map<AnnotationMirror, Set<AnnotationMirror>> supertypesGraph;

    /** immutable map: qualifier --> supertypesMap of the qualifier**/
    // Contains all supertypes, not just the direct supertypes of the qualifier
    private final Map<AnnotationMirror, Set<AnnotationMirror>> supertypesMap;
    /** the root of all the qualifiers **/
    private final AnnotationMirror root;
    private final AnnotationMirror bottom;

    private GraphQualifierHierarchy(Factory f) {
        // // no need for copying as f.supertypes has no mutable references to it
        this.supertypesGraph = f.supertypes;
        this.supertypesMap = buildFullMap(f.supertypes);
        this.root = findRoot(this.supertypesMap, null);
        this.bottom = findBottom(this.supertypesMap, null);
    }

    protected GraphQualifierHierarchy(GraphQualifierHierarchy h) {
        this.supertypesGraph = h.supertypesGraph;
        this.supertypesMap = h.supertypesMap;
        this.root = h.root;
        this.lubs = h.lubs;
        this.bottom = h.bottom;
    }

    /**
     * Returns the root qualifier for this hierarchy.
     *
     * The root qualifier is inferred from the hierarchy, as being the only
     * one without any super qualifiers
     */
    @Override
    public AnnotationMirror getRootAnnotation() {
        return root;
    }

    @Override
    public AnnotationMirror getBottomQualifier() {
        return this.bottom;
    }

    private Set<Name> typeQualifiers = null;
    @Override
    public Set<Name> getTypeQualifiers() {
        if (typeQualifiers != null)
            return typeQualifiers;
        Set<Name> names = new HashSet<Name>();
        for (AnnotationMirror anno: supertypesMap.keySet())
            names.add(AnnotationUtils.annotationName(anno));
        typeQualifiers = names;
        return typeQualifiers;
    }

    // For caching results of lubs
    Map<AnnotationPair, AnnotationMirror> lubs = null;
    @Override
    public AnnotationMirror leastUpperBound(AnnotationMirror a1, AnnotationMirror a2) {
        if (AnnotationUtils.areSameIgnoringValues(a1, a2))
            return AnnotationUtils.areSame(a1, a2) ? a1 : root;
        if (lubs == null) {
            // WMD TODO: this does not seem to be needed
            // lubs = new HashMap<AnnotationPair, AnnotationMirror>();
            lubs = calculateLubs();
        }
        AnnotationPair pair = new AnnotationPair(a1, a2);
        return lubs.get(pair);
    }


    // For caching results of glbs
    Map<AnnotationPair, AnnotationMirror> glbs = null;
    @Override
    public AnnotationMirror greatestLowerBound(AnnotationMirror a1, AnnotationMirror a2) {
        if (AnnotationUtils.areSameIgnoringValues(a1, a2))
            return AnnotationUtils.areSame(a1, a2) ? a1 : bottom;
        if (glbs == null) {
            glbs = calculateGlbs();
        }
        AnnotationPair pair = new AnnotationPair(a1, a2);
        return glbs.get(pair);
    }

    /**
     * Most qualifiers have no value fields.  However, two annotations with
     * values are subtype of each other only if they have the same values.
     * i.e. I(m) is a subtype of I(n) iff m = n
     *
     * When client specifies an annotation, a1, to be a subtype of annotation
     * with values, a2, then a1 is a subtype of all instances of a2 regardless
     * of a2 values.  i.e. IGJBottom is a subtype of all instances of
     * {@code @I}.
     *
     */
    @Override
    public boolean isSubtype(AnnotationMirror anno1, AnnotationMirror anno2) {
        if (AnnotationUtils.areSame(root, anno2))
            return true;
        if (AnnotationUtils.areSameIgnoringValues(anno1, anno2))
            return AnnotationUtils.areSame(anno1, anno2);
        checkAnnoInGraph(anno1);
        checkAnnoInGraph(anno2);
        return this.supertypesMap.get(anno1).contains(anno2);
    }

    private final void checkAnnoInGraph(AnnotationMirror a) {
        if (supertypesMap.containsKey(a))
            return;

        if (a == null)
            throw new IllegalArgumentException(
                    "Found an unqualified type.  Please ensure that " +
                    "your implicit rules cover all cases and/or " +
                    "use a @DefaulQualifierInHierarchy annotation");
        else
            throw new IllegalArgumentException("Unrecognized qualifier: " + a);
    }

    /**
     * Infer the root for the subtype hierarchy.  Simply finds the one (and only
     * one) qualifier that is not a subtype of any other qualifier
     *
     * @param ignore
     *      a qualifier that cannot be a root candidate, like polymorphic
     *      qualifier
     */
    private static AnnotationMirror
    findRoot(Map<AnnotationMirror, Set<AnnotationMirror>> supertypes, AnnotationMirror ignore) {
        List<AnnotationMirror> possibleRoots = new LinkedList<AnnotationMirror>();
        for (AnnotationMirror anno : supertypes.keySet()) {
            if (supertypes.get(anno).isEmpty())
                possibleRoots.add(anno);
        }

        if (ignore != null)
            possibleRoots.remove(ignore);

        assert possibleRoots.size() == 1 : "Other than one possible root: "
                + possibleRoots + "\n"
                + "Does the checker know about all type qualifiers?";
        return possibleRoots.get(0);
    }

    private static AnnotationMirror
    findBottom(Map<AnnotationMirror, Set<AnnotationMirror>> supertypes, AnnotationMirror ignore) {
        Set<AnnotationMirror> bottoms = findBottoms(supertypes, ignore);
        if (bottoms.size() == 1)
            return bottoms.iterator().next();
        else
            return null;
    }

    /**
     * Infer the bottoms of the subtype hierarchy.  Simple finds the qualifiers
     * are not supertypesMap of other qualifiers.
     *
     * @param ignore
     *      a qualifier that cannot be a bottom candidate, like polymorphic
     *      qualifier
     */
    private static Set<AnnotationMirror>
    findBottoms(Map<AnnotationMirror, Set<AnnotationMirror>> supertypes, AnnotationMirror ignore) {
        Set<AnnotationMirror> bottoms = AnnotationUtils.createAnnotationSet();
        bottoms.addAll(supertypes.keySet());
        for (Set<AnnotationMirror> supers : supertypes.values()) {
            bottoms.removeAll(supers);
        }
        if (ignore != null)
            bottoms.remove(ignore);
        return bottoms;
    }

    private static Map<AnnotationMirror, Set<AnnotationMirror>>
    buildFullMap(Map<AnnotationMirror, Set<AnnotationMirror>> supertypes) {
        Map<AnnotationMirror, Set<AnnotationMirror>> fullMap = AnnotationUtils.createAnnotationMap();
        for (AnnotationMirror anno : supertypes.keySet()) {
            findAllSupers(anno, supertypes, fullMap);
        }
        return fullMap;
    }

    private Map<AnnotationPair, AnnotationMirror>  calculateLubs() {
        Map<AnnotationPair, AnnotationMirror> newlubs = new HashMap<AnnotationPair, AnnotationMirror>();
        for (AnnotationMirror a1 : supertypesGraph.keySet())
            for (AnnotationMirror a2 : supertypesGraph.keySet()) {
                if (AnnotationUtils.areSameIgnoringValues(a1, a2))
                    continue;
                AnnotationPair pair = new AnnotationPair(a1, a2);
                if (newlubs.containsKey(pair))
                    continue;
                AnnotationMirror lub = findLub(a1, a2);
                newlubs.put(pair, lub);
            }
        return newlubs;
    }

    private AnnotationMirror findLub(AnnotationMirror a1, AnnotationMirror a2) {
        if (isSubtype(a1, a2))
            return a2;
        if (isSubtype(a2, a1))
            return a1;

        Set<AnnotationMirror> outset = new HashSet<AnnotationMirror>();
        for (AnnotationMirror a1Super : findSmallestTypes(supertypesMap.get(a1))) {
            // TODO: we take the first of the smallest supertypes, maybe we would
            // get a different LUB if we used a different one?
            AnnotationMirror a1Lub = findLub(a1Super, a2);
            if (a1Lub != null) {
                outset.add(a1Lub);
            }
            if (a1Lub==null && a1Super==null) {
                // null is also used for Unqualified! If two qualifiers are separate
                // subtypes of unqualifed, this might happen.
                // I ran into this when KeyFor <: Unqualified and Covariant <: Unqualified.
                // I think it would be much nicer if Unqualified would not be optimized away...
                outset.add(null);
            }
        }
        if (outset.size()==1) {
            return outset.iterator().next();
        }
        if (outset.size()>1) {
            outset = findSmallestTypes(outset);
            // TODO: more than one, incomparable supertypes. Just pick the first one.
            // if (outset.size()>1) { System.out.println("Still more than one LUB!"); }
            return outset.iterator().next();
        }
        throw new AssertionError("Could not determine LUB for " + a1 + " and " + a2 + "\n" +
                                 "Does the checker know about all type qualifiers?");
    }

    // remove all supertypes of elements contained in the set
    private Set<AnnotationMirror> findSmallestTypes(Set<AnnotationMirror> inset) {
        Set<AnnotationMirror> outset = new HashSet<AnnotationMirror>(inset);

        for( AnnotationMirror a1 : inset ) {
            Iterator<AnnotationMirror> outit = outset.iterator();
            while( outit.hasNext() ) {
                AnnotationMirror a2 = outit.next();
                if( a1!=a2 && isSubtype(a1, a2) ) {
                    outit.remove();
                }
            }
        }
        return outset;
    }

    /**
     * Finds all the super qualifiers for an qualifier
     *
     * @param anno
     * @param supertypesMap
     * @return
     */
    private static Set<AnnotationMirror>
    findAllSupers(AnnotationMirror anno,
            Map<AnnotationMirror, Set<AnnotationMirror>> supertypes,
            Map<AnnotationMirror, Set<AnnotationMirror>> allSupersSoFar) {
        Set<AnnotationMirror> supers = AnnotationUtils.createAnnotationSet();
        if (allSupersSoFar.containsKey(anno))
            return Collections.unmodifiableSet(allSupersSoFar.get(anno));

        for (AnnotationMirror superAnno : supertypes.get(anno)) {
            supers.add(superAnno);
            supers.addAll(findAllSupers(superAnno, supertypes, allSupersSoFar));
        }
        allSupersSoFar.put(anno, Collections.unmodifiableSet(supers));
        return supers;
    }


    private Map<AnnotationPair, AnnotationMirror>  calculateGlbs() {
        Map<AnnotationPair, AnnotationMirror> newglbs = new HashMap<AnnotationPair, AnnotationMirror>();
        for (AnnotationMirror a1 : supertypesGraph.keySet())
            for (AnnotationMirror a2 : supertypesGraph.keySet()) {
                if (AnnotationUtils.areSameIgnoringValues(a1, a2))
                    continue;
                AnnotationPair pair = new AnnotationPair(a1, a2);
                if (newglbs.containsKey(pair))
                    continue;
                AnnotationMirror glb = findGlb(a1, a2);
                newglbs.put(pair, glb);
            }
        return newglbs;
    }

    private AnnotationMirror findGlb(AnnotationMirror a1, AnnotationMirror a2) {
        if (isSubtype(a1, a2))
            return a1;
        if (isSubtype(a2, a1))
            return a2;

        Set<AnnotationMirror> outset = new HashSet<AnnotationMirror>();
        for (AnnotationMirror a1Sub : supertypesGraph.keySet()) {
            if (isSubtype(a1Sub, a1) && !a1Sub.equals(a1)) {
                AnnotationMirror a1lb = findGlb(a1Sub, a2);
                if (a1lb != null)
                    outset.add(a1lb);
            }
        }
        if (outset.size()==1) {
            return outset.iterator().next();
        }
        if (outset.size()>1) {
            outset = findGreatestTypes(outset);
            // TODO: more than one, incomparable subtypes. Pick the first one.
            // if (outset.size()>1) { System.out.println("Still more than one GLB!"); }
            return outset.iterator().next();
        }
        throw new AssertionError("Could not determine GLB for " + a1 + " and " + a2 + "\n" +
                "Does the checker know about all type qualifiers?");
    }

    // remove all subtypes of elements contained in the set
    private Set<AnnotationMirror> findGreatestTypes(Set<AnnotationMirror> inset) {
        Set<AnnotationMirror> outset = new HashSet<AnnotationMirror>(inset);

        for( AnnotationMirror a1 : inset ) {
            Iterator<AnnotationMirror> outit = outset.iterator();
            while( outit.hasNext() ) {
                AnnotationMirror a2 = outit.next();
                if( a1!=a2 && isSubtype(a2, a1) ) {
                    outit.remove();
                }
            }
        }
        return outset;
    }


    private static class AnnotationPair {
        public final AnnotationMirror a1;
        public final AnnotationMirror a2;
        private int hashCode = -1;

        public AnnotationPair(AnnotationMirror a1, AnnotationMirror a2) {
            this.a1 = a1;
            this.a2 = a2;
        }

        public int hashCode() {
            if (hashCode == -1) {
                hashCode = 31;
                if (a1 != null)
                    hashCode += 17 * a1.toString().hashCode();
                if (a2 != null)
                    hashCode += 17 * a2.toString().hashCode();
            }
            return hashCode;
        }

        public boolean equals(Object o) {
            if (!(o instanceof AnnotationPair))
                return false;
            AnnotationPair other = (AnnotationPair)o;
            if (AnnotationUtils.areSameIgnoringValues(a1, other.a1)
                    && AnnotationUtils.areSameIgnoringValues(a2, other.a2))
                return true;
            if (AnnotationUtils.areSameIgnoringValues(a2, other.a1)
                    && AnnotationUtils.areSameIgnoringValues(a1, other.a2))
                return true;
            return false;
        }
    }

}
