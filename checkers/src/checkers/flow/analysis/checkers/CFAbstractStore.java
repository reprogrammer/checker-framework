package checkers.flow.analysis.checkers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import checkers.flow.analysis.FlowExpressions;
import checkers.flow.analysis.FlowExpressions.PureMethodCall;
import checkers.flow.analysis.FlowExpressions.Receiver;
import checkers.flow.analysis.Store;
import checkers.flow.analysis.checkers.CFAbstractValue.InferredAnnotation;
import checkers.flow.cfg.node.FieldAccessNode;
import checkers.flow.cfg.node.LocalVariableNode;
import checkers.flow.cfg.node.MethodInvocationNode;
import checkers.flow.cfg.node.Node;
import checkers.quals.MonotonicAnnotation;
import checkers.types.AnnotatedTypeFactory;
import checkers.util.AnnotationUtils;
import checkers.util.Pair;
import checkers.util.PurityUtils;

/**
 * A store for the checker framework analysis tracks the annotations of memory
 * locations such as local variables and fields.
 *
 * @author Charlie Garrett
 * @author Stefan Heule
 */
// TODO: this class should be split into parts that are reusable generally, and
// parts specific to the checker framework
public abstract class CFAbstractStore<V extends CFAbstractValue<V>, S extends CFAbstractStore<V, S>>
        implements Store<S> {

    /**
     * The analysis class this store belongs to.
     */
    protected final CFAbstractAnalysis<V, S, ?> analysis;

    /**
     * Information collected about local variables, which are identified by the
     * corresponding element.
     */
    protected final Map<Element, V> localVariableValues;

    /**
     * Information collected about fields, using the internal representation
     * {@link FlowExpressions.FieldAccess}.
     */
    protected Map<FlowExpressions.FieldAccess, V> fieldValues;

    /**
     * Information collected about pure method calls, using the internal
     * representation {@link FlowExpressions.PureMethodCall}.
     */
    protected Map<FlowExpressions.PureMethodCall, V> methodValues;

    /**
     * Should the analysis use sequential Java semantics (i.e., assume that only
     * one thread is running at all times)?
     */
    protected final boolean sequentialSemantics;

    /* --------------------------------------------------------- */
    /* Initialization */
    /* --------------------------------------------------------- */

    public CFAbstractStore(CFAbstractAnalysis<V, S, ?> analysis,
            boolean sequentialSemantics) {
        this.analysis = analysis;
        localVariableValues = new HashMap<>();
        fieldValues = new HashMap<>();
        methodValues = new HashMap<>();
        this.sequentialSemantics = sequentialSemantics;
    }

    /** Copy constructor. */
    protected CFAbstractStore(CFAbstractStore<V, S> other) {
        this.analysis = other.analysis;
        localVariableValues = new HashMap<>(other.localVariableValues);
        fieldValues = new HashMap<>(other.fieldValues);
        methodValues = new HashMap<>(other.methodValues);
        sequentialSemantics = other.sequentialSemantics;
    }

    /**
     * Set the abstract value of a method parameter (only adds the information
     * to the store, does not remove any other knowledge). Any previous
     * information is erased; this method should only be used to initialize the
     * abstract value.
     */
    public void initializeMethodParameter(LocalVariableNode p, /* @Nullable */
            V value) {
        if (value != null) {
            localVariableValues.put(p.getElement(), value);
        }
    }

    /* --------------------------------------------------------- */
    /* Handling of fields */
    /* --------------------------------------------------------- */

    /**
     * Remove any information that might not be valid any more after a method
     * call, and add information guaranteed by the method.
     *
     * <ol>
     * <li>If the method is side-effect free (as indicated by
     * {@link checkers.quals.Pure}), then no information needs to be removed.
     * <li>Otherwise, all information about field accesses {@code a.f} needs to
     * be removed, except if the method {@code n} cannot modify {@code a.f}
     * (e.g., if {@code a} is a local variable or {@code this}, and {@code f} is
     * final).
     * <li>Furthermore, if the field has a monotonic annotation, then its
     * information can also be kept.
     * </ol>
     *
     * Furthermore, if the method is deterministic, we store its result
     * {@code val} in the store.
     */
    static int i;
    public void updateForMethodCall(MethodInvocationNode n,
            AnnotatedTypeFactory factory, V val) {
        ExecutableElement method = n.getTarget().getMethod();

        // case 1: remove information if necessary
        if (!PurityUtils.isSideEffectFree(factory, method)) {
            // update field values
            Map<FlowExpressions.FieldAccess, V> newFieldValues = new HashMap<>();
            for (Entry<FlowExpressions.FieldAccess, V> e : fieldValues
                    .entrySet()) {
                FlowExpressions.FieldAccess fieldAccess = e.getKey();
                V otherVal = e.getValue();

                // case 3:
                List<Pair<AnnotationMirror, AnnotationMirror>> fieldAnnotations = factory
                        .getAnnotationWithMetaAnnotation(
                                fieldAccess.getField(),
                                MonotonicAnnotation.class);
                V newOtherVal = null;
                for (Pair<AnnotationMirror, AnnotationMirror> fieldAnnotation : fieldAnnotations) {
                    AnnotationMirror monotonicAnnotation = fieldAnnotation.second;
                    String annotation = AnnotationUtils.elementValueClassName(
                            monotonicAnnotation, "value");
                    AnnotationMirror target = factory
                            .annotationFromName(annotation);
                    InferredAnnotation anno = otherVal
                            .getAnnotationInHierarchy(target);
                    // Make sure the 'target' annotation is present.
                    if (!anno.isNoInferredAnnotation()
                            && AnnotationUtils.areSame(anno.getAnnotation(),
                                    target)) {
                        newOtherVal = analysis.createAbstractValue(
                                CFAbstractValue.createInferredAnnotationArray(
                                        analysis, target)).mostSpecific(
                                newOtherVal);
                    }
                }
                if (newOtherVal != null) {
                    // keep information for all hierarchies where we had a
                    // monotone annotation.
                    newFieldValues.put(fieldAccess, newOtherVal);
                    continue;
                }

                // case 2:
                if (!fieldAccess.isUnmodifiableByOtherCode()) {
                    continue; // remove information completely
                }

                // keep information
                newFieldValues.put(fieldAccess, otherVal);
            }
            fieldValues = newFieldValues;

            // update method values
            methodValues = new HashMap<>();
        }

        // store information about method call if possible
        Receiver methodCall = FlowExpressions.internalReprOf(
                analysis.getFactory(), n);
        replaceValue(methodCall, val);
    }

    /**
     * Add the annotation {@code a} for the expression {@code r} (correctly
     * deciding where to store the information depending on the type of the
     * expression {@code r}).
     *
     * <p>
     * This method does not take care of removing other information that might
     * be influenced by changes to certain parts of the state.
     *
     * <p>
     * If there is already a value {@code v} present for {@code r}, then the
     * stronger of the new and old value are taken (according to the lattice).
     * Note that this happens per hierarchy, and if the store already contains
     * information about a hierarchy other than {@code a}s hierarchy, that
     * information is preserved.
     */
    public void insertValue(FlowExpressions.Receiver r, AnnotationMirror a) {
        InferredAnnotation[] annotations = CFAbstractValue
                .createInferredAnnotationArray(analysis, a);
        V value = analysis.createAbstractValue(annotations);
        insertValue(r, value);
    }

    /**
     * Returns true if the receiver {@code r} can be stored in this store.
     */
    public static boolean canInsertReceiver(Receiver r) {
        if (r instanceof FlowExpressions.FieldAccess
                || r instanceof FlowExpressions.LocalVariable
                || r instanceof FlowExpressions.PureMethodCall) {
            return !r.containsUnknown();
        }
        return false;
    }

    /**
     * Add the abstract value {@code value} for the expression {@code r}
     * (correctly deciding where to store the information depending on the type
     * of the expression {@code r}).
     *
     * <p>
     * This method does not take care of removing other information that might
     * be influenced by changes to certain parts of the state.
     *
     * <p>
     * If there is already a value {@code v} present for {@code r}, then the
     * stronger of the new and old value are taken (according to the lattice).
     * Note that this happens per hierarchy, and if the store already contains
     * information about a hierarchy for which {@code value} does not contain
     * information, then that information is preserved.
     */
    public void insertValue(FlowExpressions.Receiver r, /* @Nullable */
            V value) {
        if (value == null) {
            // No need to insert a null abstract value because it represents
            // top and top is also the default value.
            return;
        }
        if (r.containsUnknown()) {
            // Expressions containing unknown expressions are not stored.
            return;
        }
        if (r instanceof FlowExpressions.LocalVariable) {
            Element localVar = ((FlowExpressions.LocalVariable) r).getElement();
            V oldValue = localVariableValues.get(localVar);
            localVariableValues.put(localVar, value.mostSpecific(oldValue));
        } else if (r instanceof FlowExpressions.FieldAccess) {
            FlowExpressions.FieldAccess fieldAcc = (FlowExpressions.FieldAccess) r;
            // Only store information about final fields (where the receiver is
            // also fixed) if concurrent semantics are enabled.
            if (sequentialSemantics || fieldAcc.isUnmodifiableByOtherCode()) {
                V oldValue = fieldValues.get(fieldAcc);
                fieldValues.put(fieldAcc, value.mostSpecific(oldValue));
            }
        } else if (r instanceof FlowExpressions.PureMethodCall) {
            FlowExpressions.PureMethodCall method = (FlowExpressions.PureMethodCall) r;
            // Don't store any information if concurrent semantics are enabled.
            if (sequentialSemantics) {
                V oldValue = methodValues.get(method);
                methodValues.put(method, value.mostSpecific(oldValue));
            }
        } else {
            // No other types of expressions need to be stored.
        }
    }

    /**
     * Completely replaces the abstract value {@code value} for the expression
     * {@code r} (correctly deciding where to store the information depending on
     * the type of the expression {@code r}). Any previous information is
     * discarded.
     *
     * <p>
     * This method does not take care of removing other information that might
     * be influenced by changes to certain parts of the state.
     */
    public void replaceValue(FlowExpressions.Receiver r, /* @Nullable */
            V value) {
        clearValue(r);
        insertValue(r, value);
    }

    /**
     * Remove any knowledge about the expression {@code r} (correctly deciding
     * where to remove the information depending on the type of the expression
     * {@code r}).
     */
    public void clearValue(FlowExpressions.Receiver r) {
        if (r.containsUnknown()) {
            // Expressions containing unknown expressions are not stored.
            return;
        }
        if (r instanceof FlowExpressions.LocalVariable) {
            Element localVar = ((FlowExpressions.LocalVariable) r).getElement();
            localVariableValues.remove(localVar);
        } else if (r instanceof FlowExpressions.FieldAccess) {
            FlowExpressions.FieldAccess fieldAcc = (FlowExpressions.FieldAccess) r;
            fieldValues.remove(fieldAcc);
        } else if (r instanceof FlowExpressions.PureMethodCall) {
            PureMethodCall method = (PureMethodCall) r;
            methodValues.remove(method);
        } else {
            // No other types of expressions are stored.
        }
    }

    /**
     * @return Current abstract value of a flow expression, or {@code null} if
     *         no information is available.
     */
    public/* @Nullable */V getValue(FlowExpressions.Receiver expr) {
        if (expr instanceof FlowExpressions.LocalVariable) {
            Element localVar = ((FlowExpressions.LocalVariable) expr)
                    .getElement();
            return localVariableValues.get(localVar);
        } else if (expr instanceof FlowExpressions.FieldAccess) {
            FlowExpressions.FieldAccess fieldAcc = (FlowExpressions.FieldAccess) expr;
            return fieldValues.get(fieldAcc);
        } else if (expr instanceof FlowExpressions.PureMethodCall) {
            FlowExpressions.PureMethodCall method = (FlowExpressions.PureMethodCall) expr;
            return methodValues.get(method);
        } else {
            assert false;
            return null;
        }
    }

    /**
     * @return Current abstract value of a field access, or {@code null} if no
     *         information is available.
     */
    public/* @Nullable */V getValue(FieldAccessNode n) {
        FlowExpressions.FieldAccess fieldAccess = FlowExpressions
                .internalReprOfFieldAccess(analysis.getFactory(), n);
        return fieldValues.get(fieldAccess);
    }

    /**
     * @return Current abstract value of a method call, or {@code null} if no
     *         information is available.
     */
    public/* @Nullable */V getValue(MethodInvocationNode n) {
        Receiver method = FlowExpressions.internalReprOf(analysis.getFactory(),
                n);
        if (method == null) {
            return null;
        }
        return methodValues.get(method);
    }

    /**
     * Update the information in the store by considering a field assignment
     * with target {@code n}, where the right hand side has the abstract value
     * {@code val}.
     *
     * @param val
     *            The abstract value of the value assigned to {@code n} (or
     *            {@code null} if the abstract value is not known).
     */
    public void updateForAssignment(FieldAccessNode n, /* @Nullable */V val) {
        FlowExpressions.FieldAccess fieldAccess = FlowExpressions
                .internalReprOfFieldAccess(analysis.getFactory(), n);
        removeConflicting(fieldAccess, val);
        if (!fieldAccess.containsUnknown() && val != null) {
            // Only store information about final fields (where the receiver is
            // also fixed) if concurrent semantics are enabled.
            if (sequentialSemantics || fieldAccess.isUnmodifiableByOtherCode()) {
                fieldValues.put(fieldAccess, val);
            }
        }
    }

    /**
     * Update the information in the store by considering an assignment with
     * target {@code n}, where the target is neither a local variable nor a
     * field access. This includes the following steps:
     *
     * <ol>
     * <li value="1">Remove any abstract values for field accesses <em>b.g</em>
     * where {@code n} might alias any expression in the receiver <em>b</em>.
     * <li value="2">Remove any information about pure method calls.
     * </ol>
     */
    public void updateForUnknownAssignment(Node n) {
        FlowExpressions.Unknown unknown = new FlowExpressions.Unknown(
                n.getType());
        Map<FlowExpressions.FieldAccess, V> newFieldValues = new HashMap<>();
        for (Entry<FlowExpressions.FieldAccess, V> e : fieldValues.entrySet()) {
            FlowExpressions.FieldAccess otherFieldAccess = e.getKey();
            V otherVal = e.getValue();
            // case 1:
            if (otherFieldAccess.getReceiver().containsAliasOf(this, unknown)) {
                continue; // remove information completely
            }
            newFieldValues.put(otherFieldAccess, otherVal);
        }
        fieldValues = newFieldValues;

        // case 2:
        methodValues = new HashMap<>();
    }

    /**
     * Remove any information in {@code fieldValues} that might not be true any
     * more after {@code fieldAccess} has been assigned a new value (with the
     * abstract value {@code val}). This includes the following steps (assume
     * that {@code fieldAccess} is of the form <em>a.f</em> for some <em>a</em>.
     *
     * <ol>
     * <li value="1">Update the abstract value of other field accesses
     * <em>b.g</em> where the field is equal (that is, <em>f=g</em>), and the
     * receiver <em>b</em> might alias the receiver of {@code fieldAccess},
     * <em>a</em>. This update will raise the abstract value for such field
     * accesses to at least {@code val} (or the old value, if that was less
     * precise). However, this is only necessary if the field <em>g</em> is not
     * final.
     * <li value="2">Remove any abstract values for field accesses <em>b.g</em>
     * where {@code fieldAccess} is the same (i.e., <em>a=b</em> and
     * <em>f=g</em>), or where {@code fieldAccess} might alias any expression in
     * the receiver <em>b</em>.
     * <li value="3">Remove any information about pure method calls.
     * </ol>
     *
     * @param val
     *            The abstract value of the value assigned to {@code n} (or
     *            {@code null} if the abstract value is not known).
     */
    protected void removeConflicting(FlowExpressions.FieldAccess fieldAccess, /*
                                                                               * @
                                                                               * Nullable
                                                                               */
            V val) {
        Map<FlowExpressions.FieldAccess, V> newFieldValues = new HashMap<>();
        for (Entry<FlowExpressions.FieldAccess, V> e : fieldValues.entrySet()) {
            FlowExpressions.FieldAccess otherFieldAccess = e.getKey();
            V otherVal = e.getValue();
            // case 2:
            if (otherFieldAccess.getReceiver().containsAliasOf(this,
                    fieldAccess)
                    || otherFieldAccess.equals(fieldAccess)) {
                continue; // remove information completely
            }
            // case 1:
            if (fieldAccess.getField().equals(otherFieldAccess.getField())) {
                if (canAlias(fieldAccess.getReceiver(),
                        otherFieldAccess.getReceiver())) {
                    if (!otherFieldAccess.isFinal()) {
                        if (val != null) {
                            newFieldValues.put(otherFieldAccess,
                                    val.leastUpperBound(otherVal));
                        } else {
                            // remove information completely
                        }
                        continue;
                    }
                }
            }
            // information is save to be carried over
            newFieldValues.put(otherFieldAccess, otherVal);
        }
        fieldValues = newFieldValues;

        // case 3:
        methodValues = new HashMap<>();
    }

    /**
     * Remove any information in {@code fieldValues} that might not be true any
     * more after {@code localVar} has been assigned a new value. This includes
     * the following steps:
     *
     * <ol>
     * <li value="1">Remove any abstract values for field accesses <em>b.g</em>
     * where {@code localVar} might alias any expression in the receiver
     * <em>b</em>.
     * <li value="2">Remove any information about pure method calls where the
     * receiver or any of the parameters contains {@code localVar}.
     * </ol>
     */
    protected void removeConflicting(LocalVariableNode localVar) {
        Map<FlowExpressions.FieldAccess, V> newFieldValues = new HashMap<>();
        FlowExpressions.LocalVariable var = new FlowExpressions.LocalVariable(
                localVar);
        for (Entry<FlowExpressions.FieldAccess, V> e : fieldValues.entrySet()) {
            FlowExpressions.FieldAccess otherFieldAccess = e.getKey();
            // case 1:
            if (otherFieldAccess.containsSyntacticEqualReceiver(var)) {
                continue;
            }
            newFieldValues.put(otherFieldAccess, e.getValue());
        }
        fieldValues = newFieldValues;

        Map<FlowExpressions.PureMethodCall, V> newMethodValues = new HashMap<>();
        for (Entry<FlowExpressions.PureMethodCall, V> e : methodValues
                .entrySet()) {
            FlowExpressions.PureMethodCall otherMethodAccess = e.getKey();
            // case 1:
            if (otherMethodAccess.containsSyntacticEqualReceiver(var)
                    || otherMethodAccess.containsSyntacticEqualParameter(var)) {
                continue;
            }
            newMethodValues.put(otherMethodAccess, e.getValue());
        }
        methodValues = newMethodValues;
    }

    /**
     * Can the objects {@code a} and {@code b} be aliases? Returns a
     * conservative answer (i.e., returns {@code true} if not enough information
     * is available to determine aliasing).
     */
    public boolean canAlias(FlowExpressions.Receiver a,
            FlowExpressions.Receiver b) {
        TypeMirror tb = b.getType();
        TypeMirror ta = a.getType();
        Types types = analysis.getTypes();
        return types.isSubtype(ta, tb) || types.isSubtype(tb, ta);
    }

    /* --------------------------------------------------------- */
    /* Handling of local variables */
    /* --------------------------------------------------------- */

    /**
     * @return Current abstract value of a local variable, or {@code null} if no
     *         information is available.
     */
    public/* @Nullable */V getValue(LocalVariableNode n) {
        Element el = n.getElement();
        return localVariableValues.get(el);
    }

    /**
     * Set the abstract value of a local variable in the store. Overwrites any
     * value that might have been available previously.
     *
     * @param val
     *            The abstract value of the value assigned to {@code n} (or
     *            {@code null} if the abstract value is not known).
     */
    public void updateForAssignment(LocalVariableNode n, /* @Nullable */V val) {
        removeConflicting(n);
        if (val != null) {
            localVariableValues.put(n.getElement(), val);
        }
    }

    /* --------------------------------------------------------- */
    /* Helper and miscellaneous methods */
    /* --------------------------------------------------------- */

    @SuppressWarnings("unchecked")
    @Override
    public S copy() {
        return analysis.createCopiedStore((S) this);
    }

    @Override
    public S leastUpperBound(S other) {
        S newStore = analysis.createEmptyStore(sequentialSemantics);

        for (Entry<Element, V> e : other.localVariableValues.entrySet()) {
            // local variables that are only part of one store, but not the
            // other are discarded, as one of store implicitly contains 'top'
            // for that variable.
            Element el = e.getKey();
            if (localVariableValues.containsKey(el)) {
                V otherVal = e.getValue();
                V thisVal = localVariableValues.get(el);
                V mergedVal = thisVal.leastUpperBound(otherVal);
                if (mergedVal != null) {
                    newStore.localVariableValues.put(el, mergedVal);
                }
            }
        }
        for (Entry<FlowExpressions.FieldAccess, V> e : other.fieldValues
                .entrySet()) {
            // information about fields that are only part of one store, but not
            // the other are discarded, as one store implicitly contains 'top'
            // for that field.
            FlowExpressions.FieldAccess el = e.getKey();
            if (fieldValues.containsKey(el)) {
                V otherVal = e.getValue();
                V thisVal = fieldValues.get(el);
                V mergedVal = thisVal.leastUpperBound(otherVal);
                if (mergedVal != null) {
                    newStore.fieldValues.put(el, mergedVal);
                }
            }
        }
        for (Entry<PureMethodCall, V> e : other.methodValues.entrySet()) {
            // information about methods that are only part of one store, but
            // not the other are discarded, as one store implicitly contains
            // 'top' for that field.
            FlowExpressions.PureMethodCall el = e.getKey();
            if (methodValues.containsKey(el)) {
                V otherVal = e.getValue();
                V thisVal = methodValues.get(el);
                V mergedVal = thisVal.leastUpperBound(otherVal);
                if (mergedVal != null) {
                    newStore.methodValues.put(el, mergedVal);
                }
            }
        }

        return newStore;
    }

    /**
     * Returns true iff this {@link CFAbstractStore} contains a superset of the
     * map entries of the argument {@link CFAbstractStore}. Note that we test
     * the entry keys and values by Java equality, not by any subtype
     * relationship. This method is used primarily to simplify the equals
     * predicate.
     */
    protected boolean supersetOf(CFAbstractStore<V, S> other) {
        for (Entry<Element, V> e : other.localVariableValues.entrySet()) {
            Element key = e.getKey();
            if (!localVariableValues.containsKey(key)
                    || !localVariableValues.get(key).equals(e.getValue())) {
                return false;
            }
        }
        for (Entry<FlowExpressions.FieldAccess, V> e : other.fieldValues
                .entrySet()) {
            FlowExpressions.FieldAccess key = e.getKey();
            if (!fieldValues.containsKey(key)
                    || !fieldValues.get(key).equals(e.getValue())) {
                return false;
            }
        }
        for (Entry<PureMethodCall, V> e : other.methodValues.entrySet()) {
            FlowExpressions.PureMethodCall key = e.getKey();
            if (!methodValues.containsKey(key)
                    || !methodValues.get(key).equals(e.getValue())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (o != null && o instanceof CFAbstractStore) {
            @SuppressWarnings("unchecked")
            CFAbstractStore<V, S> other = (CFAbstractStore<V, S>) o;
            return this.supersetOf(other) && other.supersetOf(this);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return toDOToutput().replace("\\n", "\n");
    }

    /**
     * @return DOT representation of the store (may contain control characters
     *         such as "\n").
     */
    public String toDOToutput() {
        StringBuilder result = new StringBuilder(this.getClass()
                .getCanonicalName() + " (\\n");
        internalDotOutput(result);
        result.append(")");
        return result.toString();
    }

    /**
     * Adds a DOT representation of the internal information of this store to
     * {@code result}.
     */
    protected void internalDotOutput(StringBuilder result) {
        for (Entry<Element, V> entry : localVariableValues.entrySet()) {
            result.append("  " + entry.getKey() + " > " + entry.getValue()
                    + "\\n");
        }
        for (Entry<FlowExpressions.FieldAccess, V> entry : fieldValues
                .entrySet()) {
            result.append("  " + entry.getKey() + " > " + entry.getValue()
                    + "\\n");
        }
        for (Entry<PureMethodCall, V> entry : methodValues.entrySet()) {
            result.append("  " + entry.getKey() + " > " + entry.getValue()
                    + "\\n");
        }
    }
}