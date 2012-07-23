package checkers.flow.analysis.checkers;

import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import checkers.flow.analysis.ConditionalTransferResult;
import checkers.flow.analysis.FlowExpressions;
import checkers.flow.analysis.FlowExpressions.FieldAccess;
import checkers.flow.analysis.FlowExpressions.Receiver;
import checkers.flow.analysis.FlowExpressions.ThisReference;
import checkers.flow.analysis.RegularTransferResult;
import checkers.flow.analysis.TransferFunction;
import checkers.flow.analysis.TransferInput;
import checkers.flow.analysis.TransferResult;
import checkers.flow.cfg.UnderlyingAST;
import checkers.flow.cfg.UnderlyingAST.CFGMethod;
import checkers.flow.cfg.UnderlyingAST.Kind;
import checkers.flow.cfg.node.AbstractNodeVisitor;
import checkers.flow.cfg.node.AssertNode;
import checkers.flow.cfg.node.AssignmentNode;
import checkers.flow.cfg.node.CaseNode;
import checkers.flow.cfg.node.CompoundAssignmentNode;
import checkers.flow.cfg.node.ConditionalNotNode;
import checkers.flow.cfg.node.EqualToNode;
import checkers.flow.cfg.node.FieldAccessNode;
import checkers.flow.cfg.node.InstanceOfNode;
import checkers.flow.cfg.node.LocalVariableNode;
import checkers.flow.cfg.node.MethodInvocationNode;
import checkers.flow.cfg.node.Node;
import checkers.flow.cfg.node.NotEqualNode;
import checkers.flow.cfg.node.TernaryExpressionNode;
import checkers.flow.cfg.node.TypeCastNode;
import checkers.flow.cfg.node.VariableDeclarationNode;
import checkers.flow.util.FlowExpressionParseUtil;
import checkers.flow.util.FlowExpressionParseUtil.FlowExpressionContext;
import checkers.flow.util.FlowExpressionParseUtil.FlowExpressionParseException;
import checkers.quals.ConditionalPostconditionAnnotation;
import checkers.quals.EnsuresAnnotation;
import checkers.quals.EnsuresAnnotationIf;
import checkers.quals.EnsuresAnnotations;
import checkers.quals.EnsuresAnnotationsIf;
import checkers.quals.PostconditionAnnotation;
import checkers.quals.PreconditionAnnotation;
import checkers.quals.RequiresAnnotation;
import checkers.quals.RequiresAnnotations;
import checkers.types.AnnotatedTypeFactory;
import checkers.types.AnnotatedTypeMirror;
import checkers.util.AnnotationUtils;
import checkers.util.ElementUtils;
import checkers.util.InternalUtils;
import checkers.util.Pair;
import checkers.util.TreeUtils;

import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;

/**
 * The default analysis transfer function for the Checker Framework propagates
 * information through assignments and uses the {@link AnnotatedTypeFactory} to
 * provide checker-specific logic how to combine types (e.g., what is the type
 * of a string concatenation, given the types of the two operands) and as an
 * abstraction function (e.g., determine the annotations on literals)..
 *
 * @author Charlie Garrett
 * @author Stefan Heule
 */
public abstract class CFAbstractTransfer<V extends CFAbstractValue<V>, S extends CFAbstractStore<V, S>, T extends CFAbstractTransfer<V, S, T>>
        extends AbstractNodeVisitor<TransferResult<V, S>, TransferInput<V, S>>
        implements TransferFunction<V, S> {

    /**
     * The analysis class this store belongs to.
     */
    protected CFAbstractAnalysis<V, S, T> analysis;

    /**
     * Should the analysis use sequential Java semantics (i.e., assume that only
     * one thread is running at all times)?
     */
    protected final boolean sequentialSemantics;

    public CFAbstractTransfer(CFAbstractAnalysis<V, S, T> analysis) {
        this.analysis = analysis;
        this.sequentialSemantics = !analysis.factory.getEnv().getOptions()
                .containsKey("concurrentSemantics");
    }

    /**
     * @return The abstract value of a non-leaf tree {@code tree}, as computed
     *         by the {@link AnnotatedTypeFactory}.
     */
    protected V getValueFromFactory(Tree tree) {
        analysis.setCurrentTree(tree);
        AnnotatedTypeMirror at = analysis.factory.getAnnotatedType(tree);
        analysis.setCurrentTree(null);
        return analysis.createAbstractValue(at.getAnnotations());
    }

    /**
     * @return The abstract value of a non-leaf tree {@code tree}, as computed
     *         by the {@link AnnotatedTypeFactory}, but without considering any
     *         flow-sensitive refinements.
     */
    protected V getEffectiveValueFromFactory(Tree tree) {
        analysis.setCurrentTree(tree);
        AnnotatedTypeMirror at = analysis.factory.getAnnotatedType(tree);
        analysis.setCurrentTree(null);
        return analysis.createAbstractValue(at.getEffectiveAnnotations());
    }

    /**
     * The initial store maps method formal parameters to their currently most
     * refined type.
     */
    @Override
    public S initialStore(UnderlyingAST underlyingAST, /* @Nullable */
            List<LocalVariableNode> parameters) {
        S info = analysis.createEmptyStore(sequentialSemantics);

        if (underlyingAST.getKind() == Kind.METHOD) {
            AnnotatedTypeFactory factory = analysis.getFactory();
            for (LocalVariableNode p : parameters) {
                AnnotatedTypeMirror anno = factory.getAnnotatedType(p
                        .getElement());
                info.initializeMethodParameter(p,
                        analysis.createAbstractValue(anno.getAnnotations()));
            }

            // add properties known through precondition
            CFGMethod method = (CFGMethod) underlyingAST;
            MethodTree methodTree = method.getMethod();
            Element methodElem = TreeUtils.elementFromDeclaration(methodTree);
            addInformationFromPreconditions(info, factory, method, methodTree,
                    methodElem);

            // Add knowledge about final fields, or values of non-final fields
            // if we are inside a constructor.
            List<Pair<VariableElement, V>> fieldValues = analysis
                    .getFieldValues();
            boolean isConstructor = TreeUtils.isConstructor(methodTree);
            for (Pair<VariableElement, V> p : fieldValues) {
                VariableElement element = p.first;
                V value = p.second;
                if (ElementUtils.isFinal(element) || isConstructor) {
                    TypeMirror type = InternalUtils.typeOf(method
                            .getClassTree());
                    Receiver field = new FieldAccess(new ThisReference(type),
                            type, element);
                    info.insertValue(field, value);
                }
            }
        }

        return info;
    }

    /**
     * Add the information from all the preconditions of the method
     * {@code method} with corresponding tree {@code methodTree} to the store
     * {@code info}.
     */
    protected void addInformationFromPreconditions(S info,
            AnnotatedTypeFactory factory, CFGMethod method,
            MethodTree methodTree, Element methodElem) {

        // Process single preconditions.
        AnnotationMirror requiresAnnotation = factory.getDeclAnnotation(
                methodElem, RequiresAnnotation.class);
        addInformationFromPrecondition(info, factory, method, methodTree,
                requiresAnnotation);

        // Process multiple preconditions.
        AnnotationMirror requiresAnnotations = factory.getDeclAnnotation(
                methodElem, RequiresAnnotations.class);
        if (requiresAnnotations != null) {
            List<AnnotationMirror> annotations = AnnotationUtils
                    .elementValueArray(requiresAnnotations, "value");
            for (AnnotationMirror a : annotations) {
                addInformationFromPrecondition(info, factory, method,
                        methodTree, a);
            }
        }

        // Process type-system specific annotations.
        Class<PreconditionAnnotation> metaAnnotation = PreconditionAnnotation.class;
        List<Pair<AnnotationMirror, AnnotationMirror>> result = factory
                .getDeclAnnotationWithMetaAnnotation(methodElem, metaAnnotation);
        for (Pair<AnnotationMirror, AnnotationMirror> r : result) {
            AnnotationMirror anno = r.first;
            AnnotationMirror metaAnno = r.second;
            List<String> expressions = AnnotationUtils.elementValueArray(anno,
                    "value");
            String annotation = AnnotationUtils.elementValueClassName(metaAnno,
                    "annotation");
            AnnotationMirror requiredAnnotation = factory
                    .annotationFromName(annotation);
            addInformationFromPrecondition(info, methodTree, expressions,
                    requiredAnnotation, method);
        }
    }

    /**
     * Add the information from the precondition {@code requiresAnnotation} of
     * the method {@code method} with corresponding tree {@code methodTree} to
     * the store {@code info}.
     */
    protected void addInformationFromPrecondition(S info,
            AnnotatedTypeFactory factory, CFGMethod method,
            MethodTree methodTree, AnnotationMirror requiresAnnotation) {
        if (requiresAnnotation != null) {
            List<String> expressions = AnnotationUtils.elementValueArray(
                    requiresAnnotation, "expression");
            String annotation = AnnotationUtils.elementValueClassName(
                    requiresAnnotation, "annotation");
            AnnotationMirror anno = factory.annotationFromName(annotation);

            addInformationFromPrecondition(info, methodTree, expressions, anno,
                    method);
        }
    }

    /**
     * Add the information from the precondition with annotation {@code anno}
     * and expressions {@code expressions} of the method {@code method} with
     * corresponding tree {@code methodTree} to the store {@code info}.
     */
    protected void addInformationFromPrecondition(S info,
            MethodTree methodTree, List<String> expressions,
            AnnotationMirror anno, CFGMethod method) {

        FlowExpressionContext flowExprContext = FlowExpressionParseUtil
                .buildFlowExprContextForDeclaration(methodTree,
                        method.getClassTree(), analysis.getFactory());

        // store all expressions in the store
        for (String stringExpr : expressions) {
            FlowExpressions.Receiver expr = null;
            try {
                // TODO: currently, these expressions are parsed at the
                // declaration (i.e. here) and for every use. this could
                // be optimized to store the result the first time.
                // (same for other annotations)
                expr = FlowExpressionParseUtil.parse(stringExpr,
                        flowExprContext, analysis.factory.getPath(methodTree));
                info.insertValue(expr, anno);
            } catch (FlowExpressionParseException e) {
                // report errors here
                analysis.checker.report(e.getResult(), methodTree);
            }
        }
    }

    /**
     * The default visitor returns the input information unchanged, or in the
     * case of conditional input information, merged.
     */
    @Override
    public TransferResult<V, S> visitNode(Node n, TransferInput<V, S> in) {
        V value = null;

        // TODO: handle implicit/explicit this and go to correct factory method
        Tree tree = n.getTree();
        if (tree != null) {
            if (TreeUtils.canHaveTypeAnnotation(tree)) {
                value = getValueFromFactory(tree);
            }
        }

        if (in.containsTwoStores()) {
            S thenStore = in.getThenStore();
            S elseStore = in.getElseStore();
            return new ConditionalTransferResult<>(value, thenStore, elseStore);
        } else {
            S info = in.getRegularStore();
            return new RegularTransferResult<>(value, info);
        }
    }

    @Override
    public TransferResult<V, S> visitFieldAccess(FieldAccessNode n,
            TransferInput<V, S> p) {
        S store = p.getRegularStore();
        V storeValue = store.getValue(n);
        // look up value in factory, and take the more specific one
        // TODO: handle cases, where this is not allowed (e.g. contructors in
        // non-null type systems)
        V factoryValue = getValueFromFactory(n.getTree());
        V value = moreSpecificValue(factoryValue, storeValue);
        return new RegularTransferResult<>(value, store);
    }

    /**
     * Use the most specific type information available according to the store.
     */
    @Override
    public TransferResult<V, S> visitLocalVariable(LocalVariableNode n,
            TransferInput<V, S> in) {
        S store = in.getRegularStore();
        V valueFromStore = store.getValue(n);
        V valueFromFactory = getValueFromFactory(n.getTree());
        V value = moreSpecificValue(valueFromFactory, valueFromStore);
        return new RegularTransferResult<>(value, store);
    }

    /**
     * The resulting abstract value is the merge of the 'then' and 'else'
     * branch.
     */
    @Override
    public TransferResult<V, S> visitTernaryExpression(TernaryExpressionNode n,
            TransferInput<V, S> p) {
        TransferResult<V, S> result = super.visitTernaryExpression(n, p);
        S store = result.getRegularStore();
        V thenValue = p.getValueOfSubNode(n.getThenOperand());
        V elseValue = p.getValueOfSubNode(n.getElseOperand());
        V resultValue = null;
        if (thenValue != null && elseValue != null) {
            resultValue = thenValue.leastUpperBound(elseValue);
        }
        return new RegularTransferResult<>(resultValue, store);
    }

    /**
     * Revert the role of the 'thenStore' and 'elseStore'.
     */
    @Override
    public TransferResult<V, S> visitConditionalNot(ConditionalNotNode n,
            TransferInput<V, S> p) {
        TransferResult<V, S> result = super.visitConditionalNot(n, p);
        S thenStore = result.getThenStore();
        S elseStore = result.getElseStore();
        return new ConditionalTransferResult<>(result.getResultValue(),
                elseStore, thenStore);
    }

    @Override
    public TransferResult<V, S> visitEqualTo(EqualToNode n,
            TransferInput<V, S> p) {
        TransferResult<V, S> res = super.visitEqualTo(n, p);

        Node leftN = n.getLeftOperand();
        Node rightN = n.getRightOperand();
        V leftV = p.getValueOfSubNode(leftN);
        V rightV = p.getValueOfSubNode(rightN);

        // if annotations differ, use the one that is more precise for both
        // sides (and add it to the store if possible)
        res = strengthenAnnotationOfEqualTo(res, leftN, rightN, leftV, rightV,
                false);
        res = strengthenAnnotationOfEqualTo(res, rightN, leftN, rightV, leftV,
                false);
        return res;
    }

    @Override
    public TransferResult<V, S> visitNotEqual(NotEqualNode n,
            TransferInput<V, S> p) {
        TransferResult<V, S> res = super.visitNotEqual(n, p);

        Node leftN = n.getLeftOperand();
        Node rightN = n.getRightOperand();
        V leftV = p.getValueOfSubNode(leftN);
        V rightV = p.getValueOfSubNode(rightN);

        // if annotations differ, use the one that is more precise for both
        // sides (and add it to the store if possible)
        res = strengthenAnnotationOfEqualTo(res, leftN, rightN, leftV, rightV,
                true);
        res = strengthenAnnotationOfEqualTo(res, rightN, leftN, rightV, leftV,
                true);

        return res;
    }

    /**
     * Refine the annotation of {@code secondNode} if the annotation
     * {@code secondValue} is less precise than {@code firstvalue}. This is
     * possible, if {@code secondNode} is an expression that is tracked by the
     * store (e.g., a local variable or a field).
     *
     * @param res
     *            The previous result.
     * @param notEqualTo
     *            If true, indicates that the logic is flipped (i.e., the
     *            information is added to the {@code elseStore} instead of the
     *            {@code thenStore}) for a not-equal comparison.
     * @return The conditional transfer result (if information has been added),
     *         or {@code null}.
     */
    protected TransferResult<V, S> strengthenAnnotationOfEqualTo(
            TransferResult<V, S> res, Node firstNode, Node secondNode,
            V firstValue, V secondValue, boolean notEqualTo) {
        if (firstValue != null) {
            // Only need to insert if the second value is actually different.
            if (!firstValue.equals(secondValue)) {
                Receiver secondInternal = FlowExpressions.internalReprOf(
                        analysis.getFactory(), secondNode);
                if (CFAbstractStore.canInsertReceiver(secondInternal)) {
                    S thenStore = res.getThenStore();
                    S elseStore = res.getElseStore();
                    if (notEqualTo) {
                        elseStore.insertValue(secondInternal, firstValue);
                    } else {
                        thenStore.insertValue(secondInternal, firstValue);
                    }
                    return new ConditionalTransferResult<>(
                            res.getResultValue(), thenStore, elseStore);
                }
            }
        }
        return res;
    }

    @Override
    public TransferResult<V, S> visitAssignment(AssignmentNode n,
            TransferInput<V, S> in) {
        Node lhs = n.getTarget();
        Node rhs = n.getExpression();

        S info = in.getRegularStore();
        V rhsValue = in.getValueOfSubNode(rhs);
        processCommonAssignment(in, lhs, rhs, info, rhsValue);

        return new RegularTransferResult<>(rhsValue, info);
    }

    @Override
    public TransferResult<V, S> visitCompoundAssignment(
            CompoundAssignmentNode n, TransferInput<V, S> in) {
        TransferResult<V, S> result = super.visitCompoundAssignment(n, in);
        Node lhs = n.getLeftOperand();
        Node rhs = n.getRightOperand();

        // update the results store if the assignment target is something we can
        // process
        S info = result.getRegularStore();
        V rhsValue = result.getResultValue();
        processCommonAssignment(in, lhs, rhs, info, rhsValue);

        return result;
    }

    /**
     * Determine abstract value of right-hand side and update the store
     * accordingly to the assignment.
     */
    protected void processCommonAssignment(TransferInput<V, S> in, Node lhs,
            Node rhs, S info, V rhsValue) {

        TypeMirror rhsType = rhs.getType();
        TypeMirror lhsType = lhs.getType();
        if ((rhsType.getKind() == TypeKind.TYPEVAR || rhsType.getKind() == TypeKind.WILDCARD)
                && (lhsType.getKind() != TypeKind.TYPEVAR && lhsType.getKind() != TypeKind.WILDCARD)) {
            // Only take the effective upper bound if the LHS is not also a type
            // variable/wildcard.
            rhsValue = getEffectiveValueFromFactory(rhs.getTree());
        }

        // assignment to a local variable
        if (lhs instanceof LocalVariableNode) {
            LocalVariableNode var = (LocalVariableNode) lhs;
            info.updateForAssignment(var, rhsValue);
        }

        // assignment to a field
        else if (lhs instanceof FieldAccessNode) {
            FieldAccessNode fieldAccess = (FieldAccessNode) lhs;
            info.updateForAssignment(fieldAccess, rhsValue);
        }

        // assignment to array (not treated)
        else {
            info.updateForUnknownAssignment(lhs);
        }
    }

    @Override
    public TransferResult<V, S> visitMethodInvocation(MethodInvocationNode n,
            TransferInput<V, S> in) {

        S store = in.getRegularStore();
        ExecutableElement method = n.getTarget().getMethod();

        V factoryValue = null;

        Tree tree = n.getTree();
        if (tree != null) {
            // look up the value from factory
            factoryValue = getValueFromFactory(tree);
        }
        // look up the value in the store (if possible)
        V storeValue = store.getValue(n);
        V resValue = moreSpecificValue(factoryValue, storeValue);

        store.updateForMethodCall(n, analysis.factory, resValue);

        // add new information based on postcondition
        processPostconditions(n, store, method, tree);

        S thenStore = store;
        S elseStore = thenStore.copy();

        // add new information based on conditional postcondition
        processConditionalPostconditions(n, method, tree, thenStore, elseStore);

        return new ConditionalTransferResult<>(resValue, thenStore, elseStore);
    }

    /**
     * Add information based on all postconditions of method {@code n} with tree
     * {@code tree} and element {@code method} to the store {@code store}.
     */
    protected void processPostconditions(MethodInvocationNode n, S store,
            ExecutableElement method, Tree tree) {
        // Process a single postcondition (if present).
        AnnotationMirror ensuresAnnotation = analysis.factory
                .getDeclAnnotation(method, EnsuresAnnotation.class);
        processPostcondition(n, store, tree, ensuresAnnotation);

        // Process multiple postconditions (if present).
        AnnotationMirror ensuresAnnotations = analysis.factory
                .getDeclAnnotation(method, EnsuresAnnotations.class);
        if (ensuresAnnotations != null) {
            List<AnnotationMirror> annotations = AnnotationUtils
                    .elementValueArray(ensuresAnnotations, "value");
            for (AnnotationMirror a : annotations) {
                processPostcondition(n, store, tree, a);
            }
        }

        // Process type-system specific annotations.
        Class<PostconditionAnnotation> metaAnnotation = PostconditionAnnotation.class;
        List<Pair<AnnotationMirror, AnnotationMirror>> result = analysis.factory
                .getDeclAnnotationWithMetaAnnotation(method, metaAnnotation);
        for (Pair<AnnotationMirror, AnnotationMirror> r : result) {
            AnnotationMirror anno = r.first;
            AnnotationMirror metaAnno = r.second;
            List<String> expressions = AnnotationUtils.elementValueArray(anno,
                    "value");
            String annotation = AnnotationUtils.elementValueClassName(metaAnno,
                    "annotation");
            processPostcondition(n, store, tree, expressions, annotation);
        }
    }

    /**
     * Add information based on the postcondition {@code ensuresAnnotation} of
     * method {@code n} with tree {@code tree} to the store {@code store}.
     */
    protected void processPostcondition(MethodInvocationNode n, S store,
            Tree tree, AnnotationMirror ensuresAnnotation) {
        if (ensuresAnnotation != null) {
            List<String> expressions = AnnotationUtils.elementValueArray(
                    ensuresAnnotation, "expression");
            String annotation = AnnotationUtils.elementValueClassName(
                    ensuresAnnotation, "annotation");
            processPostcondition(n, store, tree, expressions, annotation);
        }
    }

    /**
     * Add information based on the postcondition for annotation
     * {@code annotation} of method {@code n} with tree {@code tree} to the
     * store {@code store}, for a given list of expressions.
     */
    public void processPostcondition(MethodInvocationNode n, S store,
            Tree tree, List<String> expressions, String annotation) {
        AnnotationMirror anno = analysis.factory.annotationFromName(annotation);

        FlowExpressionContext flowExprContext = FlowExpressionParseUtil
                .buildFlowExprContextForUse(n, analysis.getFactory());

        for (String exp : expressions) {
            try {
                FlowExpressions.Receiver r = FlowExpressionParseUtil.parse(exp,
                        flowExprContext, analysis.factory.getPath(tree));
                store.insertValue(r, anno);
            } catch (FlowExpressionParseException e) {
                // these errors are reported at the declaration, ignore here
            }
        }
    }

    /**
     * Add information based on all conditional postconditions of method
     * {@code n} with tree {@code tree} and element {@code method} to the
     * appropriate store.
     */
    protected void processConditionalPostconditions(MethodInvocationNode n,
            ExecutableElement method, Tree tree, S thenStore, S elseStore) {
        // Process a single postcondition (if present).
        AnnotationMirror ensuresAnnotationIf = analysis.factory
                .getDeclAnnotation(method, EnsuresAnnotationIf.class);
        processConditionalPostcondition(n, tree, ensuresAnnotationIf,
                thenStore, elseStore);

        // Process multiple postcondition (if present).
        AnnotationMirror ensuresAnnotationsIf = analysis.factory
                .getDeclAnnotation(method, EnsuresAnnotationsIf.class);
        if (ensuresAnnotationsIf != null) {
            List<AnnotationMirror> annotations = AnnotationUtils
                    .elementValueArray(ensuresAnnotationsIf, "value");
            for (AnnotationMirror a : annotations) {
                processConditionalPostcondition(n, tree, a, thenStore,
                        elseStore);
            }
        }

        // Process type-system specific annotations.
        Class<ConditionalPostconditionAnnotation> metaAnnotation = ConditionalPostconditionAnnotation.class;
        List<Pair<AnnotationMirror, AnnotationMirror>> result = analysis.factory
                .getDeclAnnotationWithMetaAnnotation(method, metaAnnotation);
        for (Pair<AnnotationMirror, AnnotationMirror> r : result) {
            AnnotationMirror anno = r.first;
            AnnotationMirror metaAnno = r.second;
            String annotation = AnnotationUtils.elementValueClassName(metaAnno,
                    "annotation");
            processConditionalPostcondition(n, tree, anno, thenStore,
                    elseStore, annotation);
        }
    }

    /**
     * Add information based on the conditional postcondition
     * {@code ensuresAnnotationIf} of method {@code n} with tree {@code tree} to
     * the appropriate store.
     */
    protected void processConditionalPostcondition(MethodInvocationNode n,
            Tree tree, AnnotationMirror ensuresAnnotationIf, S thenStore,
            S elseStore) {
        if (ensuresAnnotationIf != null) {
            String annotation = AnnotationUtils.elementValueClassName(
                    ensuresAnnotationIf, "annotation");
            processConditionalPostcondition(n, tree, ensuresAnnotationIf,
                    thenStore, elseStore, annotation);
        }
    }

    /**
     * Add information based on the conditional postcondition
     * {@code ensuresAnnotationIf} of method {@code n} with tree {@code tree} to
     * the appropriate store, where {@code annotation} is the annotation to add.
     */
    public void processConditionalPostcondition(MethodInvocationNode n,
            Tree tree, AnnotationMirror ensuresAnnotationIf, S thenStore,
            S elseStore, String annotation) {
        List<String> expressions = AnnotationUtils.elementValueArray(
                ensuresAnnotationIf, "expression");
        boolean result = AnnotationUtils.elementValue(ensuresAnnotationIf,
                "result", Boolean.class);
        AnnotationMirror anno = analysis.factory.annotationFromName(annotation);

        FlowExpressionContext flowExprContext = FlowExpressionParseUtil
                .buildFlowExprContextForUse(n, analysis.getFactory());

        for (String exp : expressions) {
            try {
                FlowExpressions.Receiver r = FlowExpressionParseUtil.parse(exp,
                        flowExprContext, analysis.factory.getPath(tree));
                if (result) {
                    thenStore.insertValue(r, anno);
                } else {
                    elseStore.insertValue(r, anno);
                }
            } catch (FlowExpressionParseException e) {
                // these errors are reported at the declaration, ignore here
            }
        }
    }

    /**
     * An assert produces no value and since it may be disabled, it has no
     * effect on the store. However, if 'assumeAssertsAreEnabled' is used, then
     * we can safely use the 'then' store, as the 'else' store will go the the
     * exceptional exit.
     */
    @Override
    public TransferResult<V, S> visitAssert(AssertNode n, TransferInput<V, S> in) {
        if (in.containsTwoStores()) {
            if (analysis.getEnv().getOptions()
                    .containsKey("assumeAssertsAreEnabled")) {
                // If assertions are enabled, then assertions stop the execution
                // if the expression is false.
                return new RegularTransferResult<>(null, in.getThenStore());
            }
        }
        return new RegularTransferResult<>(null, in.getRegularStore());
    }

    /**
     * A case produces no value, but it may imply some facts about the argument
     * to the switch statement.
     */
    @Override
    public TransferResult<V, S> visitCase(CaseNode n, TransferInput<V, S> in) {
        return new RegularTransferResult<>(null, in.getRegularStore());
    }

    /**
     * In a cast {@code (@A C) e} of some expression {@code e} to a new type
     * {@code @A C}, we usually take the annotation of the type {@code C} (here
     * {@code @A}). However, if the inferred annotation of {@code e} is more
     * precise, we keep that one.
     */
    @Override
    public TransferResult<V, S> visitTypeCast(TypeCastNode n,
            TransferInput<V, S> p) {
        TransferResult<V, S> result = super.visitTypeCast(n, p);
        V value = result.getResultValue();
        V operandValue = p.getValueOfSubNode(n.getOperand());
        // Normally we take the value of the type cast node. However, if the old
        // flow-refined value was more precise, we keep that value.
        V resultValue = moreSpecificValue(value, operandValue);
        result.setResultValue(resultValue);
        return result;
    }

    /**
     * Refine the operand of an instanceof check with more specific annotations
     * if possible.
     */
    @Override
    public TransferResult<V, S> visitInstanceOf(InstanceOfNode n,
            TransferInput<V, S> p) {
        TransferResult<V, S> result = super.visitInstanceOf(n, p);

        // Look at the annotations from the type of the instanceof check
        // (provided by the factory)
        AnnotatedTypeMirror typeAnnotations = analysis.getFactory()
                .getAnnotatedType(n.getTree().getType());
        V value = analysis
                .createAbstractValue(typeAnnotations.getAnnotations());

        // Look at the value from the operand.
        V operandValue = p.getValueOfSubNode(n.getOperand());

        // Combine the two.
        V mostPreciceValue = moreSpecificValue(value, operandValue);
        result.setResultValue(mostPreciceValue);

        // Insert into the store if possible.
        Receiver operandInternal = FlowExpressions.internalReprOf(
                analysis.getFactory(), n.getOperand());
        if (CFAbstractStore.canInsertReceiver(operandInternal)) {
            S thenStore = result.getThenStore();
            S elseStore = result.getElseStore();
            thenStore.insertValue(operandInternal, mostPreciceValue);
            return new ConditionalTransferResult<>(result.getResultValue(),
                    thenStore, elseStore);
        }

        return result;
    }

    /**
     * Returns the abstract value of {@code (value1, value2)) that is more
     * specific. If the two are incomparable, then {@code value1} is returned.
     */
    public V moreSpecificValue(V value1, V value2) {
        if (value1 == null) {
            return value2;
        }
        if (value2 == null) {
            return value1;
        }
        return value1.mostSpecific(value2);
    }

    @Override
    public TransferResult<V, S> visitVariableDeclaration(
            VariableDeclarationNode n, TransferInput<V, S> p) {
        return new RegularTransferResult<>(null, p.getRegularStore());
    }
}