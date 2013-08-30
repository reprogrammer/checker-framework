package checkers.types;

/*>>>
import checkers.nullness.quals.Nullable;
*/

import checkers.basetype.BaseTypeChecker;
import checkers.flow.CFAbstractAnalysis;
import checkers.flow.CFAbstractStore;
import checkers.flow.CFAbstractTransfer;
import checkers.flow.CFAbstractValue;
import checkers.flow.CFAnalysis;
import checkers.flow.CFCFGBuilder;
import checkers.flow.CFStore;
import checkers.flow.CFTransfer;
import checkers.flow.CFValue;
import checkers.quals.DefaultFor;
import checkers.quals.DefaultLocation;
import checkers.quals.DefaultQualifier;
import checkers.quals.DefaultQualifierInHierarchy;
import checkers.quals.ImplicitFor;
import checkers.quals.Unqualified;
import checkers.types.AnnotatedTypeMirror.AnnotatedDeclaredType;
import checkers.types.AnnotatedTypeMirror.AnnotatedExecutableType;
import checkers.util.QualifierDefaults;
import checkers.util.QualifierPolymorphism;

import dataflow.analysis.AnalysisResult;
import dataflow.analysis.TransferInput;
import dataflow.analysis.TransferResult;
import dataflow.cfg.CFGBuilder;
import dataflow.cfg.ControlFlowGraph;
import dataflow.cfg.UnderlyingAST;
import dataflow.cfg.UnderlyingAST.CFGMethod;
import dataflow.cfg.UnderlyingAST.CFGStatement;
import dataflow.cfg.node.Node;
import dataflow.cfg.node.ReturnNode;

import javacutils.AnnotationUtils;
import javacutils.ErrorReporter;
import javacutils.InternalUtils;
import javacutils.Pair;
import javacutils.TreeUtils;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.VariableTree;

/**
 * A factory that extends {@link AnnotatedTypeFactory} to optionally use
 * flow-sensitive qualifier inference, qualifier polymorphism, implicit
 * annotations via {@link ImplicitFor}, and user-specified defaults via
 * {@link DefaultQualifier}.
 */
public abstract class AbstractBasicAnnotatedTypeFactory<Checker extends BaseTypeChecker<?>,
        Value extends CFAbstractValue<Value>,
        Store extends CFAbstractStore<Value, Store>,
        TransferFunction extends CFAbstractTransfer<Value, Store, TransferFunction>,
        FlowAnalysis extends CFAbstractAnalysis<Value, Store, TransferFunction>>
    extends AnnotatedTypeFactory {

    /** The type checker to use. */
    protected Checker checker;

    /** should use flow by default */
    protected static boolean FLOW_BY_DEFAULT = true;

    /** to annotate types based on the given tree */
    protected final TypeAnnotator typeAnnotator;

    /** to annotate types based on the given un-annotated types */
    protected final TreeAnnotator treeAnnotator;

    /** to handle any polymorphic types */
    protected final QualifierPolymorphism poly;

    /** to handle defaults specified by the user */
    protected final QualifierDefaults defaults;

    // Flow related fields

    /** Should use flow analysis? */
    protected boolean useFlow;

    /** An empty store. */
    private Store emptyStore;

    /**
     * Creates a type factory for checking the given compilation unit with
     * respect to the given annotation.
     *
     * @param checker the checker to which this type factory belongs
     * @param root the compilation unit to scan
     * @param useFlow whether flow analysis should be performed
     */
    public AbstractBasicAnnotatedTypeFactory(Checker checker,
            CompilationUnitTree root, boolean useFlow) {
        super(checker, checker.getQualifierHierarchy(), checker.getTypeHierarchy(), root);
        this.checker = checker;
        this.treeAnnotator = createTreeAnnotator(checker);
        this.typeAnnotator = createTypeAnnotator(checker);
        this.useFlow = useFlow;

        this.poly = createQualifierPolymorphism();
        this.defaults = createQualifierDefaults();

        // Add common aliases.
        // addAliasedDeclAnnotation(checkers.nullness.quals.Pure.class,
        //         Pure.class, AnnotationUtils.fromClass(elements, Pure.class));

        // Every subclass must call postInit, but it must be called after
        // all other initialization is finished.
    }

    /**
     * Creates a type factory for checking the given compilation unit with
     * respect to the given annotation.
     *
     * @param checker the checker to which this type factory belongs
     * @param root the compilation unit to scan
     */
    public AbstractBasicAnnotatedTypeFactory(Checker checker, CompilationUnitTree root) {
        this(checker, root, FLOW_BY_DEFAULT);
    }

    // **********************************************************************
    // Factory Methods for the appropriate annotator classes
    // **********************************************************************

    /**
     * Returns a {@link TreeAnnotator} that adds annotations to a type based
     * on the contents of a tree.
     *
     * Subclasses may override this method to specify more appriopriate
     * {@link TreeAnnotator}
     *
     * @return a tree annotator
     */
    protected TreeAnnotator createTreeAnnotator(Checker checker) {
        return new TreeAnnotator(checker, this);
    }

    /**
     * Returns a {@link TypeAnnotator} that adds annotations to a type based
     * on the content of the type itself.
     *
     * @return a type annotator
     */
    protected TypeAnnotator createTypeAnnotator(Checker checker) {
        return new TypeAnnotator(checker, this);
    }

    /**
     * Returns the appropriate flow analysis class that is used for the dataflow
     * analysis.
     *
     * <p>
     * This implementation uses the checker naming convention to create the
     * appropriate analysis. If no transfer function is found, it returns an
     * instance of {@link CFAnalysis}.
     *
     * <p>
     * Subclasses have to override this method to create the appropriate
     * analysis if they do not follow the checker naming convention.
     */
    @SuppressWarnings({ "unchecked", "rawtypes"})
    protected FlowAnalysis createFlowAnalysis(Checker checker,
            List<Pair<VariableElement, Value>> fieldValues) {

        // Try to reflectively load the visitor.
        Class<?> checkerClass = checker.getClass();

        while (checkerClass != BaseTypeChecker.class) {
            final String classToLoad = checkerClass.getName()
                    .replace("Checker", "Analysis")
                    .replace("Subchecker", "Analysis");
            FlowAnalysis result = BaseTypeChecker.invokeConstructorFor(
                    classToLoad, new Class<?>[] { this.getClass(),
                            ProcessingEnvironment.class, checkerClass,
                            List.class }, new Object[] { this, processingEnv, checker,
                            fieldValues });
            if (result != null)
                return result;
            checkerClass = checkerClass.getSuperclass();
        }

        // If an analysis couldn't be loaded reflectively, return the
        // default.
        List<Pair<VariableElement, CFValue>> tmp = new ArrayList<>();
        for (Pair<VariableElement, Value> fieldVal : fieldValues) {
            assert fieldVal.second instanceof CFValue;
            tmp.add(Pair.<VariableElement, CFValue> of(fieldVal.first,
                    (CFValue) fieldVal.second));
        }
        return (FlowAnalysis) new CFAnalysis(
                (AbstractBasicAnnotatedTypeFactory) this, processingEnv, checker,
                tmp);
    }

    /**
     * Returns the appropriate transfer function that is used for the dataflow
     * analysis.
     *
     * <p>
     * This implementation uses the checker naming convention to create the
     * appropriate transfer function. If no transfer function is found, it
     * returns an instance of {@link CFTransfer}.
     *
     * <p>
     * Subclasses have to override this method to create the appropriate
     * transfer function if they do not follow the checker naming convention.
     */
    // A more precise type for the parameter would be FlowAnalysis, which
    // is the type parameter bounded by the current parameter type CFAbstractAnalysis<Value, Store, TransferFunction>.
    // However, we ran into issues in callers of the method if we used that type.
    public TransferFunction createFlowTransferFunction(CFAbstractAnalysis<Value, Store, TransferFunction> analysis) {

        // Try to reflectively load the visitor.
        Class<?> checkerClass = this.checker.getClass();

        while (checkerClass != BaseTypeChecker.class) {
            final String classToLoad = checkerClass.getName()
                    .replace("Checker", "Transfer")
                    .replace("Subchecker", "Transfer");
            TransferFunction result = BaseTypeChecker.invokeConstructorFor(
                    classToLoad, new Class<?>[] { analysis.getClass() },
                    new Object[] { analysis });
            if (result != null)
                return result;
            checkerClass = checkerClass.getSuperclass();
        }

        // If a transfer function couldn't be loaded reflectively, return the
        // default.
        @SuppressWarnings("unchecked")
        TransferFunction ret = (TransferFunction) new CFTransfer(
                (CFAbstractAnalysis<CFValue, CFStore, CFTransfer>) analysis);
        return ret;
    }

    /**
     * Create {@link QualifierDefaults} which handles user specified defaults
     * @return the QualifierDefaults class
     *
     * TODO: should this be split in two methods to allow separate reuse?
     */
    protected QualifierDefaults createQualifierDefaults() {
        QualifierDefaults defs = new QualifierDefaults(elements, this);

        // TODO: this should be per qualifier hierarchy.
        boolean foundDefaultOtherwise = false;

        for (Class<? extends Annotation> qual : checker.getSupportedTypeQualifiers()) {
            DefaultFor defaultFor = qual.getAnnotation(DefaultFor.class);
            boolean hasDefaultFor = false;
            if (defaultFor != null) {
                defs.addAbsoluteDefaults(AnnotationUtils.fromClass(elements,qual),
                        defaultFor.value());
                hasDefaultFor = true;
                for (DefaultLocation dl : defaultFor.value()) {
                    if (dl == DefaultLocation.OTHERWISE) {
                        foundDefaultOtherwise = true;
                    }
                }
            }

            if (qual.getAnnotation(DefaultQualifierInHierarchy.class) != null) {
                if (hasDefaultFor) {
                    // A type qualifier should either have a DefaultFor or
                    // a DefaultQualifierInHierarchy annotation
                    ErrorReporter.errorAbort("AbstractBasicAnnotatedTypeFactory.createQualifierDefaults: " +
                            "qualifier has both @DefaultFor and @DefaultQualifierInHierarchy annotations: " +
                            qual.getCanonicalName());
                // } else if (foundDefaultOtherwise) {
                    // TODO: raise an error once we know whether the previous
                    // occurrence was in the same hierarchy
                } else {
                    defs.addAbsoluteDefault(AnnotationUtils.fromClass(elements, qual),
                            DefaultLocation.OTHERWISE);
                    foundDefaultOtherwise = true;
                }
            }
        }

        AnnotationMirror unqualified = AnnotationUtils.fromClass(elements, Unqualified.class);
        if (!foundDefaultOtherwise &&
                this.isSupportedQualifier(unqualified)) {
            defs.addAbsoluteDefault(unqualified,
                    DefaultLocation.OTHERWISE);
        }

        return defs;
    }

    /**
     * Creates {@link QualifierPolymorphism} which supports
     * QualifierPolymorphism mechanism
     * @return the QualifierPolymorphism class
     */
    protected QualifierPolymorphism createQualifierPolymorphism() {
        return new QualifierPolymorphism(processingEnv, this);
    }

    // **********************************************************************
    // Factory Methods for the appropriate annotator classes
    // **********************************************************************

    @Override
    protected void postDirectSuperTypes(AnnotatedTypeMirror type,
            List<? extends AnnotatedTypeMirror> supertypes) {
        super.postDirectSuperTypes(type, supertypes);
        if (type.getKind() == TypeKind.DECLARED) {
            for (AnnotatedTypeMirror supertype : supertypes) {
                Element elt = ((DeclaredType) supertype.getUnderlyingType()).asElement();
                annotateImplicit(elt, supertype);
            }
        }
    }

    /**
     * Track the state of dataflow analysis scanning for each class tree in the
     * compilation unit.
     */
    protected enum ScanState {
        IN_PROGRESS, FINISHED
    };

    protected Map<ClassTree, ScanState> scannedClasses = new HashMap<>();

    /**
     * The result of the flow analysis. Invariant:
     *
     * <pre>
     *  scannedClasses.get(c) == FINISHED for some class c &rArr; flowResult != null
     * </pre>
     *
     * Note that flowResult contains analysis results for Trees from multiple
     * classes which are produced by multiple calls to performFlowAnalysis.
     */
    protected AnalysisResult<Value, Store> flowResult = null;

    /**
     * A mapping from methods (or other code blocks) to their regular exit store (used to check
     * postconditions).
     */
    protected IdentityHashMap<Tree, Store> regularExitStores = null;

    /**
     * A mapping from methods to their a list with all return statements and the
     * corresponding store.
     */
    protected IdentityHashMap<MethodTree, List<Pair<ReturnNode, TransferResult<Value, Store>>>> returnStatementStores = null;

    /**
     * A mapping from methods to their a list with all return statements and the
     * corresponding store.
     */
    protected IdentityHashMap<MethodInvocationTree, Store> methodInvocationStores = null;

    /**
     * Returns the regular exit store for a method or another code block (such as static initializers).
     *
     * @return The regular exit store, or {@code null}, if there is no such
     *         store (because the method cannot exit through the regular exit
     *         block).
     */
    public /*@Nullable*/ Store getRegularExitStore(Tree t) {
        return regularExitStores.get(t);
    }

    /**
     * @return All return node and store pairs for a given method.
     */
    public List<Pair<ReturnNode, TransferResult<Value, Store>>> getReturnStatementStores(
            MethodTree methodTree) {
        assert returnStatementStores.containsKey(methodTree);
        return returnStatementStores.get(methodTree);
    }

    /**
     * @return The store immediately before a given {@link Tree}.
     */
    public Store getStoreBefore(Tree tree) {
        if (analyses == null || analyses.isEmpty()) {
            return flowResult.getStoreBefore(tree);
        }
        FlowAnalysis analysis = analyses.getFirst();
        Node node = analysis.getNodeForTree(tree);
        TransferInput<Value, Store> prevStore = analysis.getInput(node.getBlock());
        if (prevStore == null) {
            return null;
        }
        Store store = AnalysisResult.runAnalysisFor(node, true, prevStore);
        return store;
    }

    /**
     * @return The store immediately after a given {@link Tree}.
     */
    public Store getStoreAfter(Tree tree) {
        if (analyses == null || analyses.isEmpty()) {
            return flowResult.getStoreAfter(tree);
        }
        FlowAnalysis analysis = analyses.getFirst();
        Node node = analysis.getNodeForTree(tree);
        Store store = AnalysisResult.runAnalysisFor(node, false, analysis.getInput(node.getBlock()));
        return store;
    }

    /**
     * @return The {@link Node} for a given {@link Tree}.
     */
    public Node getNodeForTree(Tree tree) {
        return flowResult.getNodeForTree(tree);
    }

    /**
     * @return The value of effectively final local variables.
     */
    public HashMap<Element, Value> getFinalLocalValues() {
        return flowResult.getFinalLocalValues();
    }

    /**
     * Perform a dataflow analysis over a single class tree and its nested
     * classes.
     */
    protected void performFlowAnalysis(ClassTree classTree) {
        if (flowResult == null) {
            regularExitStores = new IdentityHashMap<>();
            returnStatementStores = new IdentityHashMap<>();
            flowResult = new AnalysisResult<>();
        }
        // no need to scan interfaces or enums
        if (classTree.getKind() == Kind.INTERFACE
                || classTree.getKind() == Kind.ANNOTATION_TYPE) {
            // Mark finished so that default annotations will be applied.
            scannedClasses.put(classTree, ScanState.FINISHED);
            return;
        }

        Queue<ClassTree> queue = new LinkedList<>();
        List<Pair<VariableElement, Value>> fieldValues = new ArrayList<>();
        queue.add(classTree);
        while (!queue.isEmpty()) {
            ClassTree ct = queue.remove();
            scannedClasses.put(ct, ScanState.IN_PROGRESS);

            AnnotatedDeclaredType preClassType = visitorState.getClassType();
            ClassTree preClassTree = visitorState.getClassTree();
            AnnotatedDeclaredType preAMT = visitorState.getMethodReceiver();
            MethodTree preMT = visitorState.getMethodTree();

            visitorState.setClassType(getAnnotatedType(ct));
            visitorState.setClassTree(ct);
            visitorState.setMethodReceiver(null);
            visitorState.setMethodTree(null);

            try {
                List<MethodTree> methods = new ArrayList<>();
                for (Tree m : ct.getMembers()) {
                    switch (m.getKind()) {
                    case METHOD:
                        MethodTree mt = (MethodTree) m;
                        // Skip abstract and native methods because they have no body.
                        ModifiersTree modifiers = mt.getModifiers();
                        if (modifiers != null) {
                            Set<Modifier> flags = modifiers.getFlags();
                            if (flags.contains(Modifier.ABSTRACT) ||
                                flags.contains(Modifier.NATIVE)) {
                                break;
                            }
                        }

                        // Wait with scanning the method until all other members
                        // have been processed.
                        methods.add(mt);
                        break;
                    case VARIABLE:
                        VariableTree vt = (VariableTree) m;
                        ExpressionTree initializer = vt.getInitializer();
                        // analyze initializer if present
                        if (initializer != null) {
                            analyze(queue, new CFGStatement(vt),
                                    fieldValues);
                            Value value = flowResult.getValue(initializer);
                            if (value != null) {
                                // Store the abstract value for the field.
                                VariableElement element = TreeUtils.elementFromDeclaration(vt);
                                fieldValues.add(Pair.of(element, value));
                            }
                        }
                        break;
                    case CLASS:
                        // Visit inner and nested classes.
                        queue.add((ClassTree) m);
                        break;
                    case ANNOTATION_TYPE:
                    case INTERFACE:
                    case ENUM:
                        // not necessary to handle
                        break;
                    case BLOCK:
                        BlockTree b = (BlockTree) m;
                        analyze(queue, new CFGStatement(b), fieldValues);
                        break;
                    default:
                        assert false : "Unexpected member: " + m.getKind();
                        break;
                    }
                }

                // Now analyze all methods.
                // TODO: at this point, we don't have any information about
                // fields of superclasses.
                for (MethodTree mt : methods) {
                    analyze(queue,
                            new CFGMethod(mt, TreeUtils
                                    .enclosingClass(getPath(mt))), fieldValues);
                }
            } finally {
                visitorState.setClassType(preClassType);
                visitorState.setClassTree(preClassTree);
                visitorState.setMethodReceiver(preAMT);
                visitorState.setMethodTree(preMT);
            }

            scannedClasses.put(ct, ScanState.FINISHED);
        }
    }

    // Maintain a deque of analyses to accomodate nested classes.
    Deque<FlowAnalysis> analyses = new LinkedList<>();

    /**
     * Analyze the AST {@code ast} and store the result.
     *
     * @param queue
     *            The queue to add more things to scan.
     * @param fieldValues
     *            The abstract values for all fields of the same class.
     * @param ast
     *            The AST to analyze.
     */
    protected void analyze(Queue<ClassTree> queue, UnderlyingAST ast,
            List<Pair<VariableElement, Value>> fieldValues) {
        CFGBuilder builder = new CFCFGBuilder(checker, this);
        ControlFlowGraph cfg = builder.run(root, processingEnv, ast);
        FlowAnalysis newAnalysis = createFlowAnalysis(getChecker(), fieldValues);
        if (emptyStore == null) {
            emptyStore = newAnalysis.createEmptyStore(!checker.hasOption("concurrentSemantics"));
        }
        analyses.addFirst(newAnalysis);
        analyses.getFirst().performAnalysis(cfg);
        AnalysisResult<Value, Store> result = analyses.getFirst().getResult();

        // store result
        flowResult.combine(result);
        if (ast.getKind() == UnderlyingAST.Kind.METHOD) {
            // store exit store (for checking postconditions)
            CFGMethod mast = (CFGMethod) ast;
            MethodTree method = mast.getMethod();
            Store regularExitStore = analyses.getFirst().getRegularExitStore();
            if (regularExitStore != null) {
                regularExitStores.put(method, regularExitStore);
            }
            returnStatementStores.put(method, analyses.getFirst()
                    .getReturnStatementStores());
        } else if (ast.getKind() == UnderlyingAST.Kind.ARBITRARY_CODE) {
            CFGStatement block = (CFGStatement) ast;
            Store regularExitStore = analyses.getFirst().getRegularExitStore();
            if (regularExitStore != null) {
                regularExitStores.put(block.getCode(), regularExitStore);
            }
        }

        if (checker.hasOption("flowdotdir")) {
            String dotfilename = checker.getOption("flowdotdir") + "/"
                    + dotOutputFileName(ast) + ".dot";
            // make path safe for Windows
            dotfilename = dotfilename.replace("<", "_").replace(">", "");
            System.err.println("Output to DOT file: " + dotfilename);
            analyses.getFirst().outputToDotFile(dotfilename);
        }

        analyses.removeFirst();

        // add classes declared in method
        queue.addAll(builder.getDeclaredClasses());
    }

    /** @return The file name used for DOT output. */
    protected String dotOutputFileName(UnderlyingAST ast) {
        if (ast.getKind() == UnderlyingAST.Kind.ARBITRARY_CODE) {
            return "initializer-" + ast.hashCode();
        } else if (ast.getKind() == UnderlyingAST.Kind.METHOD) {
            return ((CFGMethod) ast).getMethod().getName().toString();
        }
        assert false;
        return null;
    }


    /**
     * Get the defaulted type of a variable, without considering
     * flow inference from the initializer expression.
     * This is needed to determine the type of the assignment context,
     * which should have the "default" meaning, without flow inference.
     * TODO: describe and generalize
     */
    public AnnotatedTypeMirror getDefaultedAnnotatedType(Tree tree, ExpressionTree valueTree) {
        AnnotatedTypeMirror res = null;
        if (tree instanceof VariableTree) {
            res = fromMember(tree);
            annotateImplicit(tree, res, false);
        } else if (tree instanceof AssignmentTree) {
            res = fromExpression(((AssignmentTree) tree).getVariable());
            annotateImplicit(tree, res, false);
        } else if (tree instanceof CompoundAssignmentTree) {
            res = fromExpression(((CompoundAssignmentTree) tree).getVariable());
            annotateImplicit(tree, res, false);
        } else if (TreeUtils.isExpressionTree(tree)) {
            res = fromExpression((ExpressionTree) tree);
            annotateImplicit(tree, res, false);
        } else {
            assert false;
        }
        return res;
    }

    @Override
    public Pair<AnnotatedExecutableType, List<AnnotatedTypeMirror>> constructorFromUse(
            NewClassTree tree) {
        Pair<AnnotatedExecutableType, List<AnnotatedTypeMirror>> mfuPair = super
                .constructorFromUse(tree);
        AnnotatedExecutableType method = mfuPair.first;
        poly.annotate(tree, method);
        return mfuPair;
    }

    protected void annotateImplicit(Tree tree, AnnotatedTypeMirror type,
            boolean iUseFlow) {
        assert root != null : "root needs to be set when used on trees";
        if (iUseFlow) {
            annotateImplicitWithFlow(tree, type);
        } else {
            treeAnnotator.visit(tree, type);
            Element elt = InternalUtils.symbol(tree);
            typeAnnotator.visit(type, elt);
            defaults.annotate(tree, type);
        }
    }

    /**
     * This method is final. Override
     * {@link #annotateImplicit(Tree, AnnotatedTypeMirror, boolean)}
     * instead.
     */
    @Override
    public final void annotateImplicit(Tree tree, AnnotatedTypeMirror type) {
        annotateImplicit(tree, type, this.useFlow);
    }

    /**
     * We perform flow analysis on each {@link ClassTree} that is
     * passed to annotateImplicitWithFlow.  This works correctly when
     * a {@link ClassTree} is passed to this method before any of its
     * sub-trees.  It also helps to satisfy the requirement that a
     * {@link ClassTree} has been advanced to annotation before we
     * analyze it.
     */
    protected void annotateImplicitWithFlow(Tree tree, AnnotatedTypeMirror type) {
        assert useFlow : "useFlow must be true to use flow analysis";

        // For performance reasons, we require that getAnnotatedType is called
        // on the ClassTree before it's called on any code contained in the class,
        // so that we can perform flow analysis on the class.  Previously we
        // used TreePath.getPath to find enclosing classes, but that call
        // alone consumed more than 10% of execution time.  See BaseTypeVisitor
        // .visitClass for the call to getAnnotatedType that triggers analysis.
        if (tree instanceof ClassTree) {
            ClassTree classTree = (ClassTree) tree;
            if (!scannedClasses.containsKey(classTree)) {
                performFlowAnalysis(classTree);
            }
        }

        treeAnnotator.visit(tree, type);

        Element elt = InternalUtils.symbol(tree);

        typeAnnotator.visit(type, elt);
        defaults.annotate(tree, type);

        Value as = getInferredValueFor(tree);
        if (as != null) {
            applyInferredAnnotations(type, as);
        }
    }

    /**
     * Returns the inferred value (by the dataflow analysis) for a given tree.
     */
    public Value getInferredValueFor(Tree tree) {
        Value as = null;
        if (!analyses.isEmpty() && tree != null) {
            as = analyses.getFirst().getValue(tree);
        }
        if (as == null &&
                tree != null &&
                // TODO: this comparison shouldn't be needed, but
                // Daikon check-nullness started failing without it.
                flowResult != null) {
            as = flowResult.getValue(tree);
        }
        return as;
    }

    /**
     * Applies the annotations inferred by the dataflow analysis to the type {@code type}.
     */
    protected void applyInferredAnnotations(AnnotatedTypeMirror type, Value as) {
        AnnotatedTypeMirror inferred = as.getType();
        for (AnnotationMirror top : getQualifierHierarchy().getTopAnnotations()) {
            AnnotationMirror inferredAnnotation;
            if (QualifierHierarchy.canHaveEmptyAnnotationSet(type)) {
                inferredAnnotation = inferred.getAnnotationInHierarchy(top);
            } else {
                inferredAnnotation = inferred.getEffectiveAnnotationInHierarchy(top);
            }
            if (inferredAnnotation == null) {
                // We inferred "no annotation" for this hierarchy.
                type.removeAnnotationInHierarchy(top);
            } else {
                // We inferred an annotation.
                AnnotationMirror present = type
                        .getAnnotationInHierarchy(top);
                if (present != null) {
                    if (getQualifierHierarchy().isSubtype(
                            inferredAnnotation, present)) {
                        // TODO: why is the above check needed?
                        // Shouldn't inferred qualifiers always be
                        // subtypes?
                        type.replaceAnnotation(inferredAnnotation);
                    }
                } else {
                    type.addAnnotation(inferredAnnotation);
                }
            }
        }
    }

    @Override
    public void annotateImplicit(Element elt, AnnotatedTypeMirror type) {
        typeAnnotator.visit(type, elt);
        defaults.annotate(elt, type);
    }

    @Override
    public Pair<AnnotatedExecutableType, List<AnnotatedTypeMirror>> methodFromUse(MethodInvocationTree tree) {
        Pair<AnnotatedExecutableType, List<AnnotatedTypeMirror>> mfuPair = super.methodFromUse(tree);
        AnnotatedExecutableType method = mfuPair.first;
        poly.annotate(tree, method);
        return mfuPair;
    }

    public Checker getChecker() {
        return checker;
    }

    public Store getEmptyStore() {
        return emptyStore;
    }

    public boolean getUseFlow() {
        return useFlow;
    }

    public void setUseFlow(boolean useFlow) {
        this.useFlow = useFlow;
    }

    public void setEmptyStore(Store emptyStore) {
        this.emptyStore = emptyStore;
    }
}
