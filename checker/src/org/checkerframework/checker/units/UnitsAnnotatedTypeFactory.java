package org.checkerframework.checker.units;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;

import org.checkerframework.checker.units.qual.A;
import org.checkerframework.checker.units.qual.Acceleration;
import org.checkerframework.checker.units.qual.Angle;
import org.checkerframework.checker.units.qual.Area;
import org.checkerframework.checker.units.qual.C;
import org.checkerframework.checker.units.qual.Current;
import org.checkerframework.checker.units.qual.K;
import org.checkerframework.checker.units.qual.Length;
import org.checkerframework.checker.units.qual.Luminance;
import org.checkerframework.checker.units.qual.Mass;
import org.checkerframework.checker.units.qual.MixedUnits;
import org.checkerframework.checker.units.qual.Prefix;
import org.checkerframework.checker.units.qual.Speed;
import org.checkerframework.checker.units.qual.Substance;
import org.checkerframework.checker.units.qual.Temperature;
import org.checkerframework.checker.units.qual.Time;
import org.checkerframework.checker.units.qual.UnitsBottom;
import org.checkerframework.checker.units.qual.UnitsMultiple;
import org.checkerframework.checker.units.qual.UnknownUnits;
import org.checkerframework.checker.units.qual.cd;
import org.checkerframework.checker.units.qual.degrees;
import org.checkerframework.checker.units.qual.g;
import org.checkerframework.checker.units.qual.h;
import org.checkerframework.checker.units.qual.km2;
import org.checkerframework.checker.units.qual.kmPERh;
import org.checkerframework.checker.units.qual.m;
import org.checkerframework.checker.units.qual.m2;
import org.checkerframework.checker.units.qual.mPERs;
import org.checkerframework.checker.units.qual.mPERs2;
import org.checkerframework.checker.units.qual.min;
import org.checkerframework.checker.units.qual.mm2;
import org.checkerframework.checker.units.qual.mol;
import org.checkerframework.checker.units.qual.radians;
import org.checkerframework.checker.units.qual.s;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.framework.type.TreeAnnotator;
import org.checkerframework.framework.util.AnnotationBuilder;
import org.checkerframework.framework.util.GraphQualifierHierarchy;
import org.checkerframework.framework.util.MultiGraphQualifierHierarchy.MultiGraphFactory;
import org.checkerframework.javacutil.AnnotationUtils;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.Tree;

/**
 * Annotated type factory for the Units Checker.
 *
 * Handles multiple names for the same unit, with different prefixes,
 * e.g. @kg is the same as @g(Prefix.kilo).
 *
 * Supports relations between units, e.g. if "m" is a variable of type "@m" and
 * "s" is a variable of type "@s", the division "m/s" is automatically annotated
 * as "mPERs", the correct unit for the result.
 */
public class UnitsAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

    protected final AnnotationMirror mixedUnits = AnnotationUtils.fromClass(elements, MixedUnits.class);

    // Map from canonical class name to the corresponding UnitsRelations instance.
    // We use the string to prevent instantiating the UnitsRelations multiple times.
    private Map<String, UnitsRelations> unitsRel;

    private final Map<String, AnnotationMirror> aliasMap = new HashMap<String, AnnotationMirror>();

    public UnitsAnnotatedTypeFactory(BaseTypeChecker checker) {
        // use true for flow inference
        super(checker, false);

        AnnotationMirror BOTTOM = AnnotationUtils.fromClass(elements, UnitsBottom.class);

        this.postInit();

        this.treeAnnotator.addTreeKind(Tree.Kind.NULL_LITERAL, BOTTOM);
        this.typeAnnotator.addTypeName(java.lang.Void.class, BOTTOM);
    }

    protected Map<String, UnitsRelations> getUnitsRel() {
        if (unitsRel == null) {
            unitsRel = new HashMap<String, UnitsRelations>();
        }
        return unitsRel;
    }

    @Override
    public AnnotationMirror aliasedAnnotation(AnnotationMirror a) {
        String aname = a.getAnnotationType().toString();
        if (aliasMap.containsKey(aname)) {
            return aliasMap.get(aname);
        }
        for (AnnotationMirror aa : a.getAnnotationType().asElement().getAnnotationMirrors() ) {
            // TODO: Is using toString the best way to go?
            if (aa.getAnnotationType().toString().equals(UnitsMultiple.class.getCanonicalName())) {
                @SuppressWarnings("unchecked")
                Class<? extends Annotation> theclass = (Class<? extends Annotation>)
                                                    AnnotationUtils.getElementValueClass(aa, "quantity", true);
                Prefix prefix = AnnotationUtils.getElementValueEnum(aa, "prefix", Prefix.class, true);
                AnnotationBuilder builder = new AnnotationBuilder(processingEnv, theclass);
                builder.setValue("value", prefix);
                AnnotationMirror res = builder.build();
                aliasMap.put(aname, res);
                return res;
            }
        }
        return super.aliasedAnnotation(a);
    }

    /** Copied from SubtypingChecker and adapted "quals" to "units".
     */
    @Override
    @SuppressWarnings("unchecked")
    protected Set<Class<? extends Annotation>> createSupportedTypeQualifiers() {
        Set<Class<? extends Annotation>> qualSet =
                new HashSet<Class<? extends Annotation>>();

        String qualNames = checker.getOption("units");
        if (qualNames == null) {
        } else {
            for (String qualName : qualNames.split(",")) {
                try {
                    final Class<? extends Annotation> q =
                            (Class<? extends Annotation>) Class.forName(qualName);

                    qualSet.add(q);
                    addUnitsRelations(q);
                } catch (ClassNotFoundException e) {
                    // TODO: use a proper error message key
                    @SuppressWarnings("CompilerMessages")
                    /*@org.checkerframework.checker.compilermsgs.qual.CompilerMessageKey*/ String msg = "Could not find class for unit: " + qualName + ". Ignoring unit.";
                    checker.message(javax.tools.Diagnostic.Kind.WARNING, root, msg);
                }
            }
        }

        // Always add the default units relations.
        // TODO: we assume that all the standard units only use this. For absolute correctness,
        // go through each and look for a UnitsRelations annotation.
        getUnitsRel().put("org.checkerframework.checker.units.UnitsRelationsDefault",
                new UnitsRelationsDefault().init(processingEnv));

        // Explicitly add the top type.
        qualSet.add(UnknownUnits.class);

        // Only add the directly supported units. Shorthands like kg are
        // handled automatically by aliases.

        qualSet.add(Length.class);
        // qualSet.add(mm.class);
        // qualSet.add(Meter.class);
        qualSet.add(m.class);
        // qualSet.add(km.class);

        qualSet.add(Time.class);
        // qualSet.add(Second.class);
        qualSet.add(s.class);
        qualSet.add(min.class);
        qualSet.add(h.class);

        qualSet.add(Speed.class);
        qualSet.add(mPERs.class);
        qualSet.add(kmPERh.class);

        qualSet.add(Area.class);
        qualSet.add(mm2.class);
        qualSet.add(m2.class);
        qualSet.add(km2.class);

        qualSet.add(Current.class);
        qualSet.add(A.class);

        qualSet.add(Mass.class);
        qualSet.add(g.class);
        // qualSet.add(kg.class);

        qualSet.add(Substance.class);
        qualSet.add(mol.class);

        qualSet.add(Luminance.class);
        qualSet.add(cd.class);

        qualSet.add(Temperature.class);
        qualSet.add(C.class);
        qualSet.add(K.class);

        qualSet.add(Acceleration.class);
        qualSet.add(mPERs2.class);

        qualSet.add(Angle.class);
        qualSet.add(degrees.class);
        qualSet.add(radians.class);

        // Use the framework-provided bottom qualifier. It will automatically be
        // at the bottom of the qualifier hierarchy.
        qualSet.add(UnitsBottom.class);

        return Collections.unmodifiableSet(qualSet);
    }

    /**
     * Look for an @UnitsRelations annotation on the qualifier and
     * add it to the list of UnitsRelations.
     *
     * @param annoUtils The AnnotationUtils instance to use.
     * @param qual The qualifier to investigate.
     */
    private void addUnitsRelations(Class<? extends Annotation> qual) {
        AnnotationMirror am = AnnotationUtils.fromClass(elements, qual);

        for (AnnotationMirror ama : am.getAnnotationType().asElement().getAnnotationMirrors() ) {
            if (ama.getAnnotationType().toString().equals(UnitsRelations.class.getCanonicalName())) {
                @SuppressWarnings("unchecked")
                Class<? extends UnitsRelations> theclass = (Class<? extends UnitsRelations>)
                    AnnotationUtils.getElementValueClass(ama, "value", true);
                String classname = theclass.getCanonicalName();

                if (!getUnitsRel().containsKey(classname)) {
                    try {
                        unitsRel.put(classname, ((UnitsRelations) theclass.newInstance()).init(processingEnv));
                    } catch (InstantiationException e) {
                        // TODO
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        // TODO
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    @Override
    protected TreeAnnotator createTreeAnnotator() {
        return new UnitsTreeAnnotator(this);
    }

    /**
     * A class for adding annotations based on tree
     */
    private class UnitsTreeAnnotator extends TreeAnnotator {

        UnitsTreeAnnotator(UnitsAnnotatedTypeFactory atypeFactory) {
            super(atypeFactory);
        }

        @Override
        public Void visitBinary(BinaryTree node, AnnotatedTypeMirror type) {
            AnnotatedTypeMirror lht = getAnnotatedType(node.getLeftOperand());
            AnnotatedTypeMirror rht = getAnnotatedType(node.getRightOperand());
            Tree.Kind kind = node.getKind();

            AnnotationMirror bestres = null;
            for (UnitsRelations ur : unitsRel.values()) {
                AnnotationMirror res = useUnitsRelation(kind, ur, lht, rht);

                if (bestres != null && res != null && !bestres.equals(res)) {
                    // TODO: warning
                    System.out.println("UnitsRelation mismatch, taking neither! Previous: "
                                    + bestres + " and current: " + res);
                    return null; // super.visitBinary(node, type);
                }

                if (res!=null) {
                    bestres = res;
                }
            }

            if (bestres!=null) {
                type.addAnnotation(bestres);
            } else {
                // Handle the binary operations that do not produce a UnitsRelation.

                switch(kind) {
                case MINUS:
                case PLUS:
                    if (lht.getAnnotations().equals(rht.getAnnotations())) {
                        // The sum or difference has the same units as both
                        // operands.
                        type.addAnnotations(lht.getAnnotations());
                        break;
                    } else {
                        type.addAnnotation(mixedUnits);
                        break;
                    }
                case DIVIDE:
                    if (lht.getAnnotations().equals(rht.getAnnotations())) {
                        // If the units of the division match,
                        // do not add an annotation to the result type, keep it
                        // unqualified.
                        break;
                    }
                    break;
                case MULTIPLY:
                    if (noUnits(lht)) {
                        type.addAnnotations(rht.getAnnotations());
                        break;
                    }
                    if (noUnits(rht)) {
                        type.addAnnotations(lht.getAnnotations());
                        break;
                    }
                    type.addAnnotation(mixedUnits);
                    break;

                    // Placeholders for unhandled binary operations
                case REMAINDER:
                    // The checker disallows the following:
                    //     @Length int q = 10 * UnitTools.m;
                    //     @Length int r = q % 3;
                    // This seems wrong because it allows this:
                    //     @Length int r = q - (q / 3) * 3;
                    // TODO: We agreed to treat remainder like division.
                    break;
                default:
                    // Do nothing
                }
            }

            return null; // super.visitBinary(node, type);
        }

        private boolean noUnits(AnnotatedTypeMirror t) {
            Set<AnnotationMirror> annos = t.getAnnotations();
            return annos.isEmpty() ||
                    (annos.size() == 1 &&
                    AnnotationUtils.areSameByClass(annos.iterator().next(), UnknownUnits.class));
        }

        @Override
        public Void visitCompoundAssignment(CompoundAssignmentTree node, AnnotatedTypeMirror type) {
            ExpressionTree var = node.getVariable();
            AnnotatedTypeMirror varType = getAnnotatedType(var);

            type.replaceAnnotations(varType.getAnnotations());
            return super.visitCompoundAssignment(node, type);
        }

        private AnnotationMirror useUnitsRelation(Tree.Kind kind, UnitsRelations ur,
                AnnotatedTypeMirror lht, AnnotatedTypeMirror rht) {

            AnnotationMirror res = null;
            if (ur!=null) {
                switch(kind) {
                case DIVIDE:
                    res = ur.division(lht, rht);
                    break;
                case MULTIPLY:
                    res = ur.multiplication(lht, rht);
                    break;
                default:
                    // Do nothing
                }
            }
            return res;
        }

    }


    /* Set the Bottom qualifier as the bottom of the hierarchy.
     */
    @Override
    public QualifierHierarchy createQualifierHierarchy(MultiGraphFactory factory) {
        return new UnitsQualifierHierarchy(factory, AnnotationUtils.fromClass(elements, UnitsBottom.class));
    }

    protected class UnitsQualifierHierarchy extends GraphQualifierHierarchy {

        public UnitsQualifierHierarchy(MultiGraphFactory f,
                AnnotationMirror bottom) {
            super(f, bottom);
        }

        @Override
        public boolean isSubtype(AnnotationMirror rhs, AnnotationMirror lhs) {
            if (AnnotationUtils.areSameIgnoringValues(lhs, rhs)) {
                return AnnotationUtils.areSame(lhs, rhs);
            }
            lhs = stripValues(lhs);
            rhs = stripValues(rhs);

            return super.isSubtype(rhs, lhs);
        }
    }

    private AnnotationMirror stripValues(AnnotationMirror anno) {
        return AnnotationUtils.fromName(elements, anno.getAnnotationType().toString());
    }

}
