package checkers.flow.cfg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.ReferenceType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.UnionType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import checkers.flow.cfg.CFGBuilder.ExtendedNode.ExtendedNodeType;
import checkers.flow.cfg.UnderlyingAST.CFGMethod;
import checkers.flow.cfg.block.Block;
import checkers.flow.cfg.block.Block.BlockType;
import checkers.flow.cfg.block.BlockImpl;
import checkers.flow.cfg.block.ConditionalBlockImpl;
import checkers.flow.cfg.block.ExceptionBlockImpl;
import checkers.flow.cfg.block.RegularBlockImpl;
import checkers.flow.cfg.block.SingleSuccessorBlockImpl;
import checkers.flow.cfg.block.SpecialBlock.SpecialBlockType;
import checkers.flow.cfg.block.SpecialBlockImpl;
import checkers.flow.cfg.node.ArrayAccessNode;
import checkers.flow.cfg.node.ArrayCreationNode;
import checkers.flow.cfg.node.ArrayTypeNode;
import checkers.flow.cfg.node.AssertNode;
import checkers.flow.cfg.node.AssignmentNode;
import checkers.flow.cfg.node.BitwiseAndAssignmentNode;
import checkers.flow.cfg.node.BitwiseAndNode;
import checkers.flow.cfg.node.BitwiseComplementNode;
import checkers.flow.cfg.node.BitwiseOrAssignmentNode;
import checkers.flow.cfg.node.BitwiseOrNode;
import checkers.flow.cfg.node.BitwiseXorAssignmentNode;
import checkers.flow.cfg.node.BitwiseXorNode;
import checkers.flow.cfg.node.BooleanLiteralNode;
import checkers.flow.cfg.node.BoxingNode;
import checkers.flow.cfg.node.CaseNode;
import checkers.flow.cfg.node.CharacterLiteralNode;
import checkers.flow.cfg.node.ClassNameNode;
import checkers.flow.cfg.node.ConditionalAndNode;
import checkers.flow.cfg.node.ConditionalNotNode;
import checkers.flow.cfg.node.ConditionalOrNode;
import checkers.flow.cfg.node.DoubleLiteralNode;
import checkers.flow.cfg.node.EqualToNode;
import checkers.flow.cfg.node.ExplicitThisLiteralNode;
import checkers.flow.cfg.node.FieldAccessNode;
import checkers.flow.cfg.node.FloatLiteralNode;
import checkers.flow.cfg.node.FloatingDivisionAssignmentNode;
import checkers.flow.cfg.node.FloatingDivisionNode;
import checkers.flow.cfg.node.FloatingRemainderAssignmentNode;
import checkers.flow.cfg.node.FloatingRemainderNode;
import checkers.flow.cfg.node.GreaterThanNode;
import checkers.flow.cfg.node.GreaterThanOrEqualNode;
import checkers.flow.cfg.node.ImplicitThisLiteralNode;
import checkers.flow.cfg.node.InstanceOfNode;
import checkers.flow.cfg.node.IntegerDivisionAssignmentNode;
import checkers.flow.cfg.node.IntegerDivisionNode;
import checkers.flow.cfg.node.IntegerLiteralNode;
import checkers.flow.cfg.node.IntegerRemainderAssignmentNode;
import checkers.flow.cfg.node.IntegerRemainderNode;
import checkers.flow.cfg.node.LeftShiftAssignmentNode;
import checkers.flow.cfg.node.LeftShiftNode;
import checkers.flow.cfg.node.LessThanNode;
import checkers.flow.cfg.node.LessThanOrEqualNode;
import checkers.flow.cfg.node.LocalVariableNode;
import checkers.flow.cfg.node.LongLiteralNode;
import checkers.flow.cfg.node.MarkerNode;
import checkers.flow.cfg.node.MethodAccessNode;
import checkers.flow.cfg.node.MethodInvocationNode;
import checkers.flow.cfg.node.NarrowingConversionNode;
import checkers.flow.cfg.node.Node;
import checkers.flow.cfg.node.NotEqualNode;
import checkers.flow.cfg.node.NullLiteralNode;
import checkers.flow.cfg.node.NumericalAdditionAssignmentNode;
import checkers.flow.cfg.node.NumericalAdditionNode;
import checkers.flow.cfg.node.NumericalMinusNode;
import checkers.flow.cfg.node.NumericalMultiplicationAssignmentNode;
import checkers.flow.cfg.node.NumericalMultiplicationNode;
import checkers.flow.cfg.node.NumericalPlusNode;
import checkers.flow.cfg.node.NumericalSubtractionAssignmentNode;
import checkers.flow.cfg.node.NumericalSubtractionNode;
import checkers.flow.cfg.node.ObjectCreationNode;
import checkers.flow.cfg.node.PackageNameNode;
import checkers.flow.cfg.node.ParameterizedTypeNode;
import checkers.flow.cfg.node.PostfixDecrementNode;
import checkers.flow.cfg.node.PostfixIncrementNode;
import checkers.flow.cfg.node.PrefixDecrementNode;
import checkers.flow.cfg.node.PrefixIncrementNode;
import checkers.flow.cfg.node.PrimitiveTypeNode;
import checkers.flow.cfg.node.ReturnNode;
import checkers.flow.cfg.node.SignedRightShiftAssignmentNode;
import checkers.flow.cfg.node.SignedRightShiftNode;
import checkers.flow.cfg.node.StringConcatenateAssignmentNode;
import checkers.flow.cfg.node.StringConcatenateNode;
import checkers.flow.cfg.node.StringConversionNode;
import checkers.flow.cfg.node.StringLiteralNode;
import checkers.flow.cfg.node.TernaryExpressionNode;
import checkers.flow.cfg.node.ThisLiteralNode;
import checkers.flow.cfg.node.ThrowNode;
import checkers.flow.cfg.node.TypeCastNode;
import checkers.flow.cfg.node.UnboxingNode;
import checkers.flow.cfg.node.UnsignedRightShiftAssignmentNode;
import checkers.flow.cfg.node.UnsignedRightShiftNode;
import checkers.flow.cfg.node.ValueLiteralNode;
import checkers.flow.cfg.node.VariableDeclarationNode;
import checkers.flow.cfg.node.WideningConversionNode;
import checkers.types.AnnotatedTypeFactory;
import checkers.types.AnnotatedTypeMirror;
import checkers.util.ElementUtils;
import checkers.util.InternalUtils;
import checkers.util.Pair;
import checkers.util.TreeUtils;
import checkers.util.TypesUtils;
import checkers.util.trees.TreeBuilder;

import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.AssertTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.BreakTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ContinueTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EmptyStatementTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ErroneousTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.LabeledStatementTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.UnionTypeTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.tree.WildcardTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

/**
 * Builds the control flow graph of some Java code (either a method, or an
 * arbitrary statement).
 *
 * <p>
 *
 * The translation of the AST to the CFG is split into three phases:
 * <ol>
 * <li><em>Phase one.</em> In the first phase, the AST is translated into a
 * sequence of {@link ExtendedNode}s. An extended node can either be a
 * {@link Node}, or one of several meta elements such as a conditional or
 * unconditional jump or a node with additional information about exceptions.
 * Some of the extended nodes contain labels (e.g., for the jump target), and
 * phase one additionally creates a mapping from labels to extended nodes.
 * Finally, the list of leaders is computed: A leader is an extended node which
 * will give rise to a basic block in phase two.</li>
 * <li><em>Phase two.</em> In this phase, the sequence of extended nodes is
 * translated to a graph of control flow blocks that contain nodes. The meta
 * elements from phase one are translated into the correct edges.</li>
 * <li><em>Phase three.</em> The control flow graph generated in phase two can
 * contain degenerate basic blocks such as empty regular basic blocks or
 * conditional basic blocks that have the same block as both 'then' and 'else'
 * successor. This phase removes these cases while preserving the control flow
 * structure.</li>
 * </ol>
 *
 * @author Stefan Heule
 *
 */
public class CFGBuilder {

    /**
     * Class declarations that have been encountered when building the
     * control-flow graph for a method.
     */
    protected List<ClassTree> declaredClasses;

    public List<ClassTree> getDeclaredClasses() {
        return declaredClasses;
    }

    /**
     * Build the control flow graph of some code.
     */
    public static ControlFlowGraph build(AnnotatedTypeFactory factory,
            CompilationUnitTree root, ProcessingEnvironment env,
            UnderlyingAST underlyingAST) {
        return new CFGBuilder().run(factory, root, env, underlyingAST);
    }

    /**
     * Build the control flow graph of a method.
     */
    public static ControlFlowGraph build(AnnotatedTypeFactory factory,
            CompilationUnitTree root, ProcessingEnvironment env,
            MethodTree tree, Tree classTree) {
        return new CFGBuilder().run(factory, root, env, tree, classTree);
    }

    /**
     * Build the control flow graph of some code.
     */
    public ControlFlowGraph run(AnnotatedTypeFactory factory,
            CompilationUnitTree root, ProcessingEnvironment env,
            UnderlyingAST underlyingAST) {
        declaredClasses = new LinkedList<>();
        PhaseOneResult phase1result = new CFGTranslationPhaseOne().process(
                factory, root, env, underlyingAST, exceptionalExitLabel);
        ControlFlowGraph phase2result = new CFGTranslationPhaseTwo()
                .process(phase1result);
        ControlFlowGraph phase3result = CFGTranslationPhaseThree
                .process(phase2result);
        return phase3result;
    }

    /**
     * Build the control flow graph of a method.
     */
    public ControlFlowGraph run(AnnotatedTypeFactory factory,
            CompilationUnitTree root, ProcessingEnvironment env,
            MethodTree tree, Tree classTree) {
        UnderlyingAST underlyingAST = new CFGMethod(tree, classTree);
        return run(factory, root, env, underlyingAST);
    }

    /* --------------------------------------------------------- */
    /* Extended Node Types and Labels */
    /* --------------------------------------------------------- */

    /** Special label to identify the exceptional exit. */
    protected final Label exceptionalExitLabel = new Label();

    /** Special label to identify the regular exit. */
    protected final Label regularExitLabel = new Label();

    /**
     * An extended node can be one of several things (depending on its
     * {@code type}):
     * <ul>
     * <li><em>NODE</em>. An extended node of this type is just a wrapper for a
     * {@link Node} (that cannot throw exceptions).</li>
     * <li><em>EXCEPTION_NODE</em>. A wrapper for a {@link Node} which can throw
     * exceptions. It contains a label for every possible exception type the
     * node might throw.</li>
     * <li><em>UNCONDITIONAL_JUMP</em>. An unconditional jump to a label.</li>
     * <li><em>TWO_TARGET_CONDITIONAL_JUMP</em>. A conditional jump with two
     * targets for both the 'then' and 'else' branch.</li>
     * </ul>
     */
    protected static abstract class ExtendedNode {

        /**
         * The basic block this extended node belongs to (as determined in phase
         * two).
         */
        protected BlockImpl block;

        /** Type of this node. */
        protected ExtendedNodeType type;

        /** Does this node terminate the execution? (e.g., "System.exit()") */
        protected boolean terminatesExecution = false;

        public ExtendedNode(ExtendedNodeType type) {
            this.type = type;
        }

        /** Extended node types (description see above). */
        public enum ExtendedNodeType {
            NODE, EXCEPTION_NODE, UNCONDITIONAL_JUMP, CONDITIONAL_JUMP
        }

        public ExtendedNodeType getType() {
            return type;
        }

        public boolean getTerminatesExecution() {
            return terminatesExecution;
        }

        public void setTerminatesExecution(boolean terminatesExecution) {
            this.terminatesExecution = terminatesExecution;
        }

        /**
         * @return The node contained in this extended node (only applicable if
         *         the type is {@code NODE} or {@code EXCEPTION_NODE}).
         */
        public Node getNode() {
            assert false;
            return null;
        }

        /**
         * @return The label associated with this extended node (only applicable
         *         if type is {@code CONDITIONAL_JUMP} or
         *         {@link UNCONDITIONAL_JUMP}).
         */
        public Label getLabel() {
            assert false;
            return null;
        }

        public BlockImpl getBlock() {
            return block;
        }

        public void setBlock(BlockImpl b) {
            this.block = b;
        }

        @Override
        public String toString() {
            return "ExtendedNode(" + type + ")";
        }
    }

    /**
     * An extended node of type {@code NODE}.
     */
    protected static class NodeHolder extends ExtendedNode {

        protected Node node;

        public NodeHolder(Node node) {
            super(ExtendedNodeType.NODE);
            this.node = node;
        }

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public String toString() {
            return "NodeHolder(" + node + ")";
        }

    }

    /**
     * An extended node of type {@code EXCEPTION_NODE}.
     */
    protected static class NodeWithExceptionsHolder extends ExtendedNode {

        protected Node node;
        // Map from exception type to labels of successors that may
        // be reached as a result of that exception.
        protected Map<TypeMirror, Set<Label>> exceptions;

        public NodeWithExceptionsHolder(Node node,
                Map<TypeMirror, Set<Label>> exceptions) {
            super(ExtendedNodeType.EXCEPTION_NODE);
            this.node = node;
            this.exceptions = exceptions;
        }

        public Map<TypeMirror, Set<Label>> getExceptions() {
            return exceptions;
        }

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public String toString() {
            return "NodeWithExceptionsHolder(" + node + ")";
        }

    }

    /**
     * An extended node of type {@code CONDITIONAL_JUMP}.
     *
     * <p>
     *
     * <em>Important:</em> In the list of extended nodes, there should not be
     * any labels that point to a conditional jump. Furthermore, the node
     * directly ahead of any conditional jump has to be a
     * {@link NodeWithExceptionsHolder} or {@link NodeHolder}, and the node held
     * by that extended node is required to be of boolean type.
     */
    protected static class ConditionalJump extends ExtendedNode {

        protected Label trueSucc;
        protected Label falseSucc;

        public ConditionalJump(Label trueSucc, Label falseSucc) {
            super(ExtendedNodeType.CONDITIONAL_JUMP);
            this.trueSucc = trueSucc;
            this.falseSucc = falseSucc;
        }

        public Label getThenLabel() {
            return trueSucc;
        }

        public Label getElseLabel() {
            return falseSucc;
        }

        @Override
        public String toString() {
            return "TwoTargetConditionalJump(" + getThenLabel() + ","
                    + getElseLabel() + ")";
        }
    }

    /**
     * An extended node of type {@code UNCONDITIONAL_JUMP}.
     */
    protected static class UnconditionalJump extends ExtendedNode {

        protected Label jumpTarget;

        public UnconditionalJump(Label jumpTarget) {
            super(ExtendedNodeType.UNCONDITIONAL_JUMP);
            this.jumpTarget = jumpTarget;
        }

        @Override
        public Label getLabel() {
            return jumpTarget;
        }

        @Override
        public String toString() {
            return "JumpMarker(" + getLabel() + ")";
        }
    }

    /**
     * A label is used to refer to other extended nodes using a mapping from
     * labels to extended nodes. Labels get their names either from labeled
     * statements in the source code or from internally generated unique names.
     */
    protected static class Label {
        private static int uid = 0;

        protected String name;

        public Label(String name) {
            this.name = name;
        }

        public Label() {
            this.name = uniqueName();
        }

        @Override
        public String toString() {
            return name;
        }

        /**
         * Return a new unique label name that cannot be confused with a Java
         * source code label.
         *
         * @return a new unique label name
         */
        private static String uniqueName() {
            return "%L" + uid++;
        }
    }

    /**
     * A TryFrame takes a thrown exception type and maps it to a set
     * of possible control-flow successors.
     */
    protected static interface TryFrame {
        /**
         * Given a type of thrown exception, add the set of possible control
         * flow successor {@link Label}s to the argument set.  Return true
         * if the exception is known to be caught by one of those labels and
         * false if it may propagate still further.
         */
        public boolean possibleLabels(TypeMirror thrown, Set<Label> labels);
    }

    /**
     * A TryCatchFrame contains an ordered list of catch labels that apply
     * to exceptions with specific types.
     */
    protected static class TryCatchFrame implements TryFrame {
        protected Types types;

        // An ordered list of pairs because catch blocks are ordered.
        protected List<Pair<TypeMirror, Label>> catchLabels;

        public TryCatchFrame(Types types, List<Pair<TypeMirror, Label>> catchLabels) {
            this.types = types;
            this.catchLabels = catchLabels;
        }

        /**
         * Given a type of thrown exception, add the set of possible control
         * flow successor {@link Label}s to the argument set.  Return true
         * if the exception is known to be caught by one of those labels and
         * false if it may propagate still further.
         */
        @Override
        public boolean possibleLabels(TypeMirror thrown, Set<Label> labels) {
            // A conservative approach would be to say that every catch block
            // might execute for any thrown exception, but we try to do better.
            //
            // We rely on several assumptions that seem to hold as of Java 7.
            // 1) An exception parameter in a catch block must be either
            //    a declared type or a union composed of declared types,
            //    all of which are subtypes of Throwable.
            // 2) A thrown type must either be a declared type or a variable
            //    that extends a declared type, which is a subtype of Throwable.
            //
            // Under those assumptions, if the thrown type (or its bound) is
            // a subtype of the caught type (or one of its alternatives), then
            // the catch block must apply and none of the later ones can apply.
            // Otherwise, if the thrown type (or its bound) is a supertype
            // of the caught type (or one of its alternatives), then the catch
            // block may apply, but so may later ones.
            // Otherwise, the thrown type and the caught type are unrelated
            // declared types, so they do not overlap on any non-null value.

            while (!(thrown instanceof DeclaredType)) {
                assert thrown instanceof TypeVariable :
                    "thrown type must be a variable or a declared type";
                thrown = ((TypeVariable)thrown).getUpperBound();
            }
            DeclaredType declaredThrown = (DeclaredType)thrown;
            assert thrown != null : "thrown type must be bounded by a declared type";

            for (Pair<TypeMirror, Label> pair : catchLabels) {
                TypeMirror caught = pair.first;
                boolean canApply = false;

                if (caught instanceof DeclaredType) {
                    DeclaredType declaredCaught = (DeclaredType)caught;
                    if (types.isSubtype(declaredThrown, declaredCaught)) {
                        // No later catch blocks can apply.
                        labels.add(pair.second);
                        return true;
                    } else if (types.isSubtype(declaredCaught, declaredThrown)) {
                        canApply = true;
                    }
                } else {
                    assert caught instanceof UnionType :
                        "caught type must be a union or a declared type";
                    UnionType caughtUnion = (UnionType)caught;
                    for (TypeMirror alternative : caughtUnion.getAlternatives()) {
                        assert alternative instanceof DeclaredType :
                            "alternatives of an caught union type must be declared types";
                        DeclaredType declaredAlt = (DeclaredType)alternative;
                        if (types.isSubtype(declaredThrown, declaredAlt)) {
                            // No later catch blocks can apply.
                            labels.add(pair.second);
                            return true;
                        } else if (types.isSubtype(declaredAlt, declaredThrown)) {
                            canApply = true;
                        }
                    }
                }

                if (canApply) {
                    labels.add(pair.second);
                }
            }

            return false;
        }
    }

    /**
     * A TryFinallyFrame applies to exceptions of any type
     */
    protected class TryFinallyFrame implements TryFrame {
        protected Label finallyLabel;

        public TryFinallyFrame(Label finallyLabel) {
            this.finallyLabel = finallyLabel;
        }

        @Override
        public boolean possibleLabels(TypeMirror thrown, Set<Label> labels) {
            labels.add(finallyLabel);
            return true;
        }
    }

    /**
     * An exception stack represents the set of all try-catch blocks
     * in effect at a given point in a program.  It maps an exception
     * type to a set of Labels and it maps a block exit (via return or
     * fall-through) to a single Label.
     */
    protected static class TryStack {
        protected Label exitLabel;
        protected LinkedList<TryFrame> frames;

        public TryStack(Label exitLabel) {
            this.exitLabel = exitLabel;
            this.frames = new LinkedList<>();
        }

        public void pushFrame(TryFrame frame) {
            frames.addFirst(frame);
        }

        public void popFrame() {
            frames.removeFirst();
        }

        /**
         * Returns the set of possible {@link Label}s where control may
         * transfer when an exception of the given type is thrown.
         */
        public Set<Label> possibleLabels(TypeMirror thrown) {
            // Work up from the innermost frame until the exception is known to
            // be caught.
            Set<Label> labels = new HashSet<>();
            for (TryFrame frame : frames) {
                if (frame.possibleLabels(thrown, labels)) {
                    return labels;
                }
            }
            labels.add(exitLabel);
            return labels;
        }
    }

    /* --------------------------------------------------------- */
    /* Phase Three */
    /* --------------------------------------------------------- */

    /**
     * Class that performs phase three of the translation process. In
     * particular, the following degenerated cases of basic blocks are removed:
     *
     * <ol>
     * <li>Empty regular basic blocks: These blocks will be removed and their
     * predecessors linked directly to the successor.</li>
     * <li>Conditional basic blocks that have the same basic block as the 'then'
     * and 'else' successor: The conditional basic block will be removed in this
     * case.</li>
     * <li>Two consecutive, non-empty, regular basic blocks where the second
     * block has exactly one predecessor (namely the other of the two blocks):
     * In this case, the two blocks are merged.</li>
     * <li>Some basic blocks might not be reachable from the entryBlock. These
     * basic blocks are removed, and the list of predecessors (in the
     * doubly-linked structure of basic blocks) are adapted correctly.</li>
     * </ol>
     *
     * Eliminating the second type of degenerate cases might introduce cases of
     * the third problem. These are also removed.
     */
    public static class CFGTranslationPhaseThree {

        /**
         * A simple wrapper object that holds a basic block and allows to set
         * one of its successors.
         */
        protected interface PredecessorHolder {
            void setSuccessor(BlockImpl b);

            BlockImpl getBlock();
        }

        /**
         * Perform phase three on the control flow graph {@code cfg}.
         *
         * @param cfg
         *            The control flow graph. Ownership is transfered to this
         *            method and the caller is not allowed to read or modify
         *            {@code cfg} after the call to {@code process} any more.
         * @return The resulting control flow graph.
         */
        public static ControlFlowGraph process(ControlFlowGraph cfg) {
            Set<Block> worklist = cfg.getAllBlocks();
            Set<Block> dontVisit = new HashSet<>();

            // note: this method has to be careful when relinking basic blocks
            // to not forget to adjust the predecessors, too

            // fix predecessor lists by removing any unreachable predecessors
            for (Block c : worklist) {
                BlockImpl cur = (BlockImpl) c;
                for (BlockImpl pred : new HashSet<>(cur.getPredecessors())) {
                    if (!worklist.contains(pred)) {
                        cur.removePredecessor(pred);
                    }
                }
            }

            // remove empty blocks
            for (Block cur : worklist) {
                if (dontVisit.contains(cur)) {
                    continue;
                }

                if (cur.getType() == BlockType.REGULAR_BLOCK) {
                    RegularBlockImpl b = (RegularBlockImpl) cur;
                    if (b.isEmpty()) {
                        Set<RegularBlockImpl> empty = new HashSet<>();
                        Set<PredecessorHolder> predecessors = new HashSet<>();
                        BlockImpl succ = computeNeighborhoodOfEmptyBlock(b,
                                empty, predecessors);
                        for (RegularBlockImpl e : empty) {
                            succ.removePredecessor(e);
                            dontVisit.add(e);
                        }
                        for (PredecessorHolder p : predecessors) {
                            BlockImpl block = p.getBlock();
                            dontVisit.add(block);
                            succ.removePredecessor(block);
                            p.setSuccessor(succ);
                        }
                    }
                }
            }

            // remove useless conditional blocks
            worklist = cfg.getAllBlocks();
            for (Block c : worklist) {
                BlockImpl cur = (BlockImpl) c;

                if (cur.getType() == BlockType.CONDITIONAL_BLOCK) {
                    ConditionalBlockImpl cb = (ConditionalBlockImpl) cur;
                    assert cb.getPredecessors().size() == 1;
                    if (cb.getThenSuccessor() == cb.getElseSuccessor()) {
                        BlockImpl pred = cb.getPredecessors().iterator().next();
                        PredecessorHolder predecessorHolder = getPredecessorHolder(
                                pred, cb);
                        BlockImpl succ = (BlockImpl) cb.getThenSuccessor();
                        succ.removePredecessor(cb);
                        predecessorHolder.setSuccessor(succ);
                    }
                }
            }

            // merge consecutive basic blocks if possible
            worklist = cfg.getAllBlocks();
            for (Block cur : worklist) {
                if (cur.getType() == BlockType.REGULAR_BLOCK) {
                    RegularBlockImpl b = (RegularBlockImpl) cur;
                    Block succ = b.getRegularSuccessor();
                    if (succ.getType() == BlockType.REGULAR_BLOCK) {
                        RegularBlockImpl rs = (RegularBlockImpl) succ;
                        if (rs.getPredecessors().size() == 1) {
                            b.setSuccessor(rs.getRegularSuccessor());
                            b.addNodes(rs.getContents());
                            rs.getRegularSuccessor().removePredecessor(rs);
                        }
                    }
                }
            }

            return cfg;
        }

        /**
         * Compute the set of empty regular basic blocks {@code empty}, starting
         * at {@code start} and going both forward and backwards. Furthermore,
         * compute the predecessors of these empty blocks ({@code predecessors}
         * ), and their single successor (return value).
         *
         * @param start
         *            The starting point of the search (an empty, regular basic
         *            block).
         * @param empty
         *            An empty set to be filled by this method with all empty
         *            basic blocks found (including {@code start}).
         * @param predecessors
         *            An empty set to be filled by this method with all
         *            predecessors.
         * @return The single successor of the set of the empty basic blocks.
         */
        protected static BlockImpl computeNeighborhoodOfEmptyBlock(
                RegularBlockImpl start, Set<RegularBlockImpl> empty,
                Set<PredecessorHolder> predecessors) {

            // get empty neighborhood that come before 'start'
            computeNeighborhoodOfEmptyBlockBackwards(start, empty, predecessors);

            // go forward
            BlockImpl succ = (BlockImpl) start.getSuccessor();
            while (succ.getType() == BlockType.REGULAR_BLOCK) {
                RegularBlockImpl cur = (RegularBlockImpl) succ;
                if (cur.isEmpty()) {
                    computeNeighborhoodOfEmptyBlockBackwards(cur, empty,
                            predecessors);
                    empty.add(cur);
                    succ = (BlockImpl) cur.getSuccessor();
                } else {
                    break;
                }
            }
            return succ;
        }

        /**
         * Compute the set of empty regular basic blocks {@code empty}, starting
         * at {@code start} and looking only backwards in the control flow
         * graph. Furthermore, compute the predecessors of these empty blocks (
         * {@code predecessors}).
         *
         * @param start
         *            The starting point of the search (an empty, regular basic
         *            block).
         * @param empty
         *            A set to be filled by this method with all empty basic
         *            blocks found (including {@code start}).
         * @param predecessors
         *            A set to be filled by this method with all predecessors.
         */
        protected static void computeNeighborhoodOfEmptyBlockBackwards(
                RegularBlockImpl start, Set<RegularBlockImpl> empty,
                Set<PredecessorHolder> predecessors) {
            Queue<RegularBlockImpl> worklist = new LinkedList<>();
            worklist.add(start);
            while (!worklist.isEmpty()) {
                RegularBlockImpl cur = worklist.poll();
                empty.add(cur);
                for (final BlockImpl pred : cur.getPredecessors()) {
                    switch (pred.getType()) {
                    case SPECIAL_BLOCK:
                        // add pred correctly to predecessor list
                        predecessors.add(getPredecessorHolder(pred, cur));
                        break;
                    case CONDITIONAL_BLOCK:
                        // add pred correctly to predecessor list
                        predecessors.add(getPredecessorHolder(pred, cur));
                        break;
                    case EXCEPTION_BLOCK:
                        // add pred correctly to predecessor list
                        predecessors.add(getPredecessorHolder(pred, cur));
                        break;
                    case REGULAR_BLOCK:
                        RegularBlockImpl r = (RegularBlockImpl) pred;
                        if (r.isEmpty()) {
                            // recursively look backwards
                            computeNeighborhoodOfEmptyBlockBackwards(r, empty,
                                    predecessors);
                        } else {
                            // add pred correctly to predecessor list
                            predecessors.add(getPredecessorHolder(pred, cur));
                        }
                        break;
                    }
                }
            }
        }

        /**
         * Return a predecessor holder that can be used to set the successor of
         * {@code pred} in the place where previously the edge pointed to
         * {@code cur}. Additionally, the predecessor holder also takes care of
         * unlinking (i.e., removing the {@code pred} from {@code cur's}
         * predecessors).
         */
        protected static PredecessorHolder getPredecessorHolder(
                final BlockImpl pred, final BlockImpl cur) {
            switch (pred.getType()) {
            case SPECIAL_BLOCK:
                SingleSuccessorBlockImpl s = (SingleSuccessorBlockImpl) pred;
                return singleSuccessorHolder(s, cur);
            case CONDITIONAL_BLOCK:
                // add pred correctly to predecessor list
                final ConditionalBlockImpl c = (ConditionalBlockImpl) pred;
                if (c.getThenSuccessor() == cur) {
                    return new PredecessorHolder() {
                        @Override
                        public void setSuccessor(BlockImpl b) {
                            c.setThenSuccessor(b);
                            cur.removePredecessor(pred);
                        }

                        @Override
                        public BlockImpl getBlock() {
                            return c;
                        }
                    };
                } else {
                    assert c.getElseSuccessor() == cur;
                    return new PredecessorHolder() {
                        @Override
                        public void setSuccessor(BlockImpl b) {
                            c.setElseSuccessor(b);
                            cur.removePredecessor(pred);
                        }

                        @Override
                        public BlockImpl getBlock() {
                            return c;
                        }
                    };
                }
            case EXCEPTION_BLOCK:
                // add pred correctly to predecessor list
                final ExceptionBlockImpl e = (ExceptionBlockImpl) pred;
                if (e.getSuccessor() == cur) {
                    return singleSuccessorHolder(e, cur);
                } else {
                    Set<Entry<TypeMirror, Block>> entrySet = e
                            .getExceptionalSuccessors().entrySet();
                    for (final Entry<TypeMirror, Block> entry : entrySet) {
                        if (entry.getValue() == cur) {
                            return new PredecessorHolder() {
                                @Override
                                public void setSuccessor(BlockImpl b) {
                                    e.addExceptionalSuccessor(b, entry.getKey());
                                    cur.removePredecessor(pred);
                                }

                                @Override
                                public BlockImpl getBlock() {
                                    return e;
                                }
                            };
                        }
                    }
                }
                assert false;
                break;
            case REGULAR_BLOCK:
                RegularBlockImpl r = (RegularBlockImpl) pred;
                return singleSuccessorHolder(r, cur);
            }
            return null;
        }

        /**
         * @return A {@link PredecessorHolder} that sets the successor of a
         *         single successor block {@code s}.
         */
        protected static PredecessorHolder singleSuccessorHolder(
                final SingleSuccessorBlockImpl s, final BlockImpl old) {
            return new PredecessorHolder() {
                @Override
                public void setSuccessor(BlockImpl b) {
                    s.setSuccessor(b);
                    old.removePredecessor(s);
                }

                @Override
                public BlockImpl getBlock() {
                    return s;
                }
            };
        }
    }

    /* --------------------------------------------------------- */
    /* Phase Two */
    /* --------------------------------------------------------- */

    /** Tuple class with up to three members. */
    protected static class Tuple<A, B, C> {
        public A a;
        public B b;
        public C c;

        public Tuple(A a, B b) {
            this.a = a;
            this.b = b;
        }

        public Tuple(A a, B b, C c) {
            this.a = a;
            this.b = b;
            this.c = c;
        }
    }

    /**
     * Class that performs phase two of the translation process.
     */
    public class CFGTranslationPhaseTwo {

        public CFGTranslationPhaseTwo() {
        }

        /**
         * Perform phase two of the translation.
         *
         * @param in
         *            The result of phase one.
         * @return A control flow graph that might still contain degenerate
         *         basic block (such as empty regular basic blocks or
         *         conditional blocks with the same block as 'then' and 'else'
         *         sucessor).
         */
        public ControlFlowGraph process(PhaseOneResult in) {

            Map<Label, Integer> bindings = in.bindings;
            ArrayList<ExtendedNode> nodeList = in.nodeList;
            Set<Integer> leaders = in.leaders;

            assert in.nodeList.size() > 0;

            // exit blocks
            SpecialBlockImpl regularExitBlock = new SpecialBlockImpl(
                    SpecialBlockType.EXIT);
            SpecialBlockImpl exceptionalExitBlock = new SpecialBlockImpl(
                    SpecialBlockType.EXCEPTIONAL_EXIT);

            // record missing edges that will be added later
            Set<Tuple<? extends SingleSuccessorBlockImpl, Integer, ?>> missingEdges = new HashSet<>();

            // missing exceptional edges
            Set<Tuple<ExceptionBlockImpl, Integer, TypeMirror>> missingExceptionalEdges = new HashSet<>();

            // create start block
            SpecialBlockImpl startBlock = new SpecialBlockImpl(
                    SpecialBlockType.ENTRY);
            missingEdges.add(new Tuple<>(startBlock, 0));

            // loop through all 'leaders' (while dynamically detecting the
            // leaders)
            RegularBlockImpl block = new RegularBlockImpl();
            int i = 0;
            for (ExtendedNode node : nodeList) {
                switch (node.getType()) {
                case NODE:
                    if (leaders.contains(i)) {
                        RegularBlockImpl b = new RegularBlockImpl();
                        block.setSuccessor(b);
                        block = b;
                    }
                    block.addNode(node.getNode());
                    node.setBlock(block);

                    // does this node end the execution (modeled as an edge to
                    // the exceptional exit block)
                    boolean terminatesExecution = node.getTerminatesExecution();
                    if (terminatesExecution) {
                        block.setSuccessor(exceptionalExitBlock);
                        block = new RegularBlockImpl();
                    }
                    break;
                case CONDITIONAL_JUMP: {
                    ConditionalJump cj = (ConditionalJump) node;
                    // Exception nodes may fall through to conditional jumps,
                    // so we set the block which is required for the insertion
                    // of missing edges.
                    node.setBlock(block);
                    assert block != null;
                    final ConditionalBlockImpl cb = new ConditionalBlockImpl();
                    block.setSuccessor(cb);
                    block = new RegularBlockImpl();
                    // use two anonymous SingleSuccessorBlockImpl that set the
                    // 'then' and 'else' successor of the conditional block
                    missingEdges.add(new Tuple<>(
                            new SingleSuccessorBlockImpl() {
                                @Override
                                public void setSuccessor(BlockImpl successor) {
                                    cb.setThenSuccessor(successor);
                                }
                            }, bindings.get(cj.getThenLabel())));
                    missingEdges.add(new Tuple<>(
                            new SingleSuccessorBlockImpl() {
                                @Override
                                public void setSuccessor(BlockImpl successor) {
                                    cb.setElseSuccessor(successor);
                                }
                            }, bindings.get(cj.getElseLabel())));
                    break;
                }
                case UNCONDITIONAL_JUMP:
                    if (leaders.contains(i)) {
                        RegularBlockImpl b = new RegularBlockImpl();
                        block.setSuccessor(b);
                        block = b;
                    }
                    node.setBlock(block);
                    if (node.getLabel() == regularExitLabel) {
                        block.setSuccessor(regularExitBlock);
                    } else if (node.getLabel() == exceptionalExitLabel) {
                        block.setSuccessor(exceptionalExitBlock);
                    } else {
                        missingEdges.add(new Tuple<>(block, bindings.get(node
                                .getLabel())));
                    }
                    block = new RegularBlockImpl();
                    break;
                case EXCEPTION_NODE:
                    NodeWithExceptionsHolder en = (NodeWithExceptionsHolder) node;
                    // create new exception block and link with previous block
                    ExceptionBlockImpl e = new ExceptionBlockImpl();
                    Node nn = en.getNode();
                    e.setNode(nn);
                    node.setBlock(e);
                    block.setSuccessor(e);
                    block = new RegularBlockImpl();

                    // ensure linking between e and next block (normal edge)
                    // Note: do not link to the next block for throw statements
                    // (these throw exceptions for sure)
                    if (!node.getTerminatesExecution())
                        missingEdges.add(new Tuple<>(e, i + 1));

                    // exceptional edges
                    for (Entry<TypeMirror, Set<Label>> entry : en.getExceptions()
                            .entrySet()) {
                        // missingEdges.put(e, bindings.get(key))
                        TypeMirror cause = entry.getKey();
                        for (Label label : entry.getValue()) {
                            Integer target = bindings.get(label);
                            missingExceptionalEdges
                                    .add(new Tuple<ExceptionBlockImpl, Integer, TypeMirror>(
                                            e, target, cause));
                        }
                    }
                    break;
                }
                i++;
            }

            // add missing edges
            for (Tuple<? extends SingleSuccessorBlockImpl, Integer, ?> p : missingEdges) {
                Integer index = p.b;
                ExtendedNode extendedNode = nodeList.get(index);
                BlockImpl target = extendedNode.getBlock();
                SingleSuccessorBlockImpl source = p.a;
                source.setSuccessor(target);
            }

            // add missing exceptional edges
            for (Tuple<ExceptionBlockImpl, Integer, ?> p : missingExceptionalEdges) {
                Integer index = p.b;
                TypeMirror cause = (TypeMirror) p.c;
                ExceptionBlockImpl source = p.a;
                if (index == null) {
                    // edge to exceptional exit
                    source.addExceptionalSuccessor(exceptionalExitBlock, cause);
                } else {
                    // edge to specific target
                    ExtendedNode extendedNode = nodeList.get(index);
                    BlockImpl target = extendedNode.getBlock();
                    source.addExceptionalSuccessor(target, cause);
                }
            }

            return new ControlFlowGraph(startBlock, regularExitBlock, exceptionalExitBlock, in.underlyingAST,
                    in.treeLookupMap, in.returnNodes);
        }
    }

    /* --------------------------------------------------------- */
    /* Phase One */
    /* --------------------------------------------------------- */

    /**
     * A wrapper object to pass around the result of phase one. For a
     * documentation of the fields see {@link CFGTranslationPhaseOne}.
     */
    protected static class PhaseOneResult {

        private IdentityHashMap<Tree, Node> treeLookupMap;
        private UnderlyingAST underlyingAST;
        private Map<Label, Integer> bindings;
        private ArrayList<ExtendedNode> nodeList;
        private Set<Integer> leaders;
        private List<ReturnNode> returnNodes;

        public PhaseOneResult(UnderlyingAST underlyingAST,
                IdentityHashMap<Tree, Node> treeLookupMap,
                ArrayList<ExtendedNode> nodeList, Map<Label, Integer> bindings,
                Set<Integer> leaders, List<ReturnNode> returnNodes) {
            this.underlyingAST = underlyingAST;
            this.treeLookupMap = treeLookupMap;
            this.nodeList = nodeList;
            this.bindings = bindings;
            this.leaders = leaders;
            this.returnNodes = returnNodes;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (ExtendedNode n : nodeList) {
                sb.append(nodeToString(n));
                sb.append("\n");
            }
            return sb.toString();
        }

        protected String nodeToString(ExtendedNode n) {
            if (n.getType() == ExtendedNodeType.CONDITIONAL_JUMP) {
                ConditionalJump t = (ConditionalJump) n;
                return "TwoTargetConditionalJump("
                        + resolveLabel(t.getThenLabel()) + ","
                        + resolveLabel(t.getElseLabel()) + ")";
            } else if (n.getType() == ExtendedNodeType.UNCONDITIONAL_JUMP) {
                return "UnconditionalJump(" + resolveLabel(n.getLabel()) + ")";
            } else {
                return n.toString();
            }
        }

        private String resolveLabel(Label label) {
            Integer index = bindings.get(label);
            if (index == null) {
                return "null";
            }
            return nodeToString(nodeList.get(index));
        }

    }

    /**
     * Class that performs phase one of the translation process. It generates
     * the following information:
     * <ul>
     * <li>A sequence of extended nodes.</li>
     * <li>A set of bindings from {@link Label}s to positions in the node
     * sequence.</li>
     * <li>A set of leader nodes that give rise to basic blocks in phase two.</li>
     * <li>A lookup map that gives the mapping from AST tree nodes to
     * {@link Node}s.</li>
     * </ul>
     *
     * <p>
     *
     * The return type of this scanner is {@link Node}. For expressions, the
     * corresponding node is returned to allow linking between different nodes.
     *
     * However, for statements there is usually no single {@link Node} that is
     * created, and thus no node is returned (rather, null is returned).
     *
     * <p>
     *
     * Every {@code visit*} method is assumed to add at least one extended node
     * to the list of nodes (which might only be a jump).
     *
     */
    public class CFGTranslationPhaseOne extends TreePathScanner<Node, Void> {

        public CFGTranslationPhaseOne() {
        }

        /**
         * Annotation processing environment and its associated type and tree
         * utilities.
         */
        protected ProcessingEnvironment env;
        protected Elements elements;
        protected Types types;
        protected Trees trees;
        protected TreeBuilder treeBuilder;

        /**
         * Annotated type factory used to get flow-insensitive annotations
         * during CFG construction.
         */
        protected AnnotatedTypeFactory factory;

        /**
         * The translation starts in regular mode, that is
         * <code>conditionalMode</code> is false. In this case, no conditional
         * jump nodes are generated.
         *
         * To correctly model control flow when the evaluation of an expression
         * determines control flow (e.g. for if-conditions, while loops, or
         * short-circuiting conditional expressions),
         * <code>conditionalMode</code> can be set to true.
         *
         * <p>
         *
         * Whenever {@code conditionalMode} is true, the two fields
         * {@code thenTargetL} and {@code elseTargetL} are used to indicate
         * where the control flow should jump to after the evaluation of a
         * boolean node.
         */
        protected boolean conditionalMode;

        /**
         * Label for the then branch. Description see above.
         */
        protected Label thenTargetL;

        /**
         * Label for the else branch. Description see above.
         */
        protected Label elseTargetL;

        /**
         * Current {@link Label} to which a break statement with no label should
         * jump, or null if there is no valid destination.
         */
        protected/* @Nullable */Label breakTargetL;

        /**
         * Map from AST label Names to CFG {@link Label}s for breaks. Each
         * labeled statement creates two CFG {@link Label}s, one for break and
         * one for continue.
         */
        protected HashMap<Name, Label> breakLabels;

        /**
         * Current {@link Label} to which a continue statement with no label
         * should jump, or null if there is no valid destination.
         */
        protected/* @Nullable */Label continueTargetL;

        /**
         * Map from AST label Names to CFG {@link Label}s for continues. Each
         * labeled statement creates two CFG {@link Label}s, one for break and
         * one for continue.
         */
        protected HashMap<Name, Label> continueLabels;

        /**
         * Node yielding the value for the lexically enclosing switch statement,
         * or null if there is no such statement.
         */
        protected Node switchExpr;

        /** Map from AST {@link Tree}s to {@link Node}s. */
        protected IdentityHashMap<Tree, Node> treeLookupMap;

        /** The list of extended nodes. */
        protected ArrayList<ExtendedNode> nodeList;

        /**
         * The bindings of labels to positions (i.e., indices) in the
         * {@code nodeList}.
         */
        protected Map<Label, Integer> bindings;

        /** The set of leaders (represented as indices into {@code nodeList}). */
        protected Set<Integer> leaders;

        /**
         * All return nodes (if any) encountered. Only includes return
         * statements that actually return something
         */
        private List<ReturnNode> returnNodes;

        /**
         * Nested scopes of try-catch blocks in force at the current
         * program point.
         */
        private TryStack tryStack;

        /**
         * Performs the actual work of phase one.
         *
         * @param factory
         *            factory for flow-insensitive type annotations
         * @param root
         *            compilation unit tree containing the method
         * @param env
         *            annotation processing environment containing type
         *            utilities
         * @param t
         *            A method (identified by its AST element).
         * @return The result of phase one.
         */
        public PhaseOneResult process(AnnotatedTypeFactory factory,
                CompilationUnitTree root, ProcessingEnvironment env,
                UnderlyingAST underlyingAST, Label exceptionalExitLabel) {
            this.factory = factory;
            this.env = env;
            this.tryStack = new TryStack(exceptionalExitLabel);
            elements = env.getElementUtils();
            types = env.getTypeUtils();
            trees = Trees.instance(env);
            treeBuilder = new TreeBuilder(env);

            // start in regular mode
            conditionalMode = false;

            // initialize lists and maps
            treeLookupMap = new IdentityHashMap<>();
            nodeList = new ArrayList<>();
            bindings = new HashMap<>();
            leaders = new HashSet<>();
            breakLabels = new HashMap<>();
            continueLabels = new HashMap<>();
            returnNodes = new ArrayList<>();

            // traverse AST of the method body
            TreePath bodyPath = trees.getPath(root, underlyingAST.getCode());
            scan(bodyPath, null);

            // add marker to indicate that the next block will be the exit block
            // Note: if there is a return statement earlier in the method (which
            // is always the case for non-void methods), then this is not
            // strictly necessary. However, it is also not a problem, as it will
            // just generate a degenerated control graph case that will be
            // removed in a later phase.
            nodeList.add(new UnconditionalJump(regularExitLabel));

            return new PhaseOneResult(underlyingAST, treeLookupMap, nodeList,
                    bindings, leaders, returnNodes);
        }

        /* --------------------------------------------------------- */
        /* Nodes and Labels Management */
        /* --------------------------------------------------------- */

        /**
         * Add a node to the lookup map if it not already present.
         *
         * @param node
         *            The node to add to the lookup map.
         */
        protected void addToLookupMap(Node node) {
            Tree tree = node.getTree();
            if (tree != null && !treeLookupMap.containsKey(tree)) {
                treeLookupMap.put(tree, node);
            }
        }

        /**
         * Replace a node in the lookup map. The node should refer to a Tree and
         * that Tree should already be in the lookup map. This method is used to
         * update the Tree-Node mapping with conversion nodes.
         *
         * @param node
         *            The node to add to the lookup map.
         */
        protected void replaceInLookupMap(Node node) {
            Tree tree = node.getTree();
            assert tree != null && treeLookupMap.containsKey(tree);
            treeLookupMap.put(tree, node);
        }

        /**
         * Extend the list of extended nodes with a node.
         *
         * @param node
         *            The node to add.
         * @return The same node (for convenience).
         */
        protected <T extends Node> T extendWithNode(T node) {
            addToLookupMap(node);
            extendWithExtendedNode(new NodeHolder(node));
            return node;
        }

        /**
         * Extend the list of extended nodes with a node, where
         * <code>node</code> might throw the exception <code>cause</code>.
         *
         * @param node
         *            The node to add.
         * @param causes
         *            An exception that the node might throw.
         * @return The node holder.
         */
        protected NodeWithExceptionsHolder extendWithNodeWithException(Node node, TypeMirror cause) {
            addToLookupMap(node);
            Set<TypeMirror> causes = new HashSet<>();
            causes.add(cause);
            return extendWithNodeWithExceptions(node, causes);
        }

        /**
         * Extend the list of extended nodes with a node, where
         * <code>node</code> might throw any of the exception in
         * <code>causes</code>.
         *
         * @param node
         *            The node to add.
         * @param causes
         *            Set of exceptions that the node might throw.
         * @return The node holder.
         */
        protected NodeWithExceptionsHolder extendWithNodeWithExceptions(Node node,
                Set<TypeMirror> causes) {
            addToLookupMap(node);
            Map<TypeMirror, Set<Label>> exceptions = new HashMap<>();
            for (TypeMirror cause : causes) {
                exceptions.put(cause, tryStack.possibleLabels(cause));
            }
            NodeWithExceptionsHolder exNode = new NodeWithExceptionsHolder(
                    node, exceptions);
            extendWithExtendedNode(exNode);
            return exNode;
        }

        /**
         * Extend the list of extended nodes with an extended node.
         *
         * @param n
         *            The extended node.
         */
        protected void extendWithExtendedNode(ExtendedNode n) {
            nodeList.add(n);
        }

        /**
         * Add the label {@code l} to the extended node that will be placed next
         * in the sequence.
         */
        protected void addLabelForNextNode(Label l) {
            leaders.add(nodeList.size());
            bindings.put(l, nodeList.size());
        }

        /* --------------------------------------------------------- */
        /* Utility Methods */
        /* --------------------------------------------------------- */

        protected long uid = 0;
        protected String uniqueName(String prefix) {
            return prefix + "#num" + uid++;
        }

        /**
         * If the input node is an unboxed primitive type, box it, otherwise
         * leave it alone.
         *
         * @param node
         *            in input node
         * @return a Node representing the boxed version of the input, which may
         *         simply be the input node
         */
        protected Node box(Node node) {
            // For boxing conversion, see JLS 5.1.7
            if (TypesUtils.isPrimitive(node.getType())) {
                PrimitiveType primitive = types.getPrimitiveType(node.getType()
                        .getKind());
                TypeMirror boxedType = types.getDeclaredType(types
                        .boxedClass(primitive));
                node = new BoxingNode(node.getTree(), node, boxedType);
                replaceInLookupMap(node);
                extendWithNode(node);
            }
            return node;
        }

        /**
         * If the input node is a boxed type, unbox it, otherwise leave it
         * alone.
         *
         * @param node
         *            in input node
         * @return a Node representing the unboxed version of the input, which
         *         may simply be the input node
         */
        protected Node unbox(Node node) {
            if (TypesUtils.isBoxedPrimitive(node.getType())) {
                node = new UnboxingNode(node.getTree(), node,
                        types.unboxedType(node.getType()));
                replaceInLookupMap(node);
                extendWithNode(node);
            }
            return node;
        }

        /**
         * Convert the input node to String type, if it isn't already.
         *
         * @param node
         *            an input node
         * @param stringType
         *            representation of the String type
         * @return a Node with the value promoted to String, which may be the
         *         input node
         */
        protected Node stringConversion(Node node, TypeMirror stringType) {
            // For string conversion, see JLS 5.1.11
            assert TypesUtils.isString(stringType);
            if (!TypesUtils.isString(node.getType())) {
                node = new StringConversionNode(node.getTree(), node,
                        stringType);
                replaceInLookupMap(node);
                extendWithNode(node);
            }
            return node;
        }

        /**
         * Perform unary numeric promotion on the input node.
         *
         * @param node
         *            a node producing a value of numeric primitive or boxed
         *            type
         * @return a Node with the value promoted to the int, long float or
         *         double, which may be the input node
         */
        protected Node unaryNumericPromotion(Node node) {
            // For unary numeric promotion, see JLS 5.6.1
            node = unbox(node);

            switch (node.getType().getKind()) {
            case BYTE:
            case CHAR:
            case SHORT: {
                TypeMirror intType = types.getPrimitiveType(TypeKind.INT);
                node = new WideningConversionNode(node.getTree(), node, intType);
                replaceInLookupMap(node);
                extendWithNode(node);
                break;
            }
            }

            return node;
        }

        /**
         * Perform binary numeric promotion on the input node to make it match
         * the expression type.
         *
         * @param node
         *            a node producing a value of numeric primitive or boxed
         *            type
         * @param exprType
         *            the type to promote the value to
         * @return a Node with the value promoted to the exprType, which may be
         *         the input node
         */
        protected Node binaryNumericPromotion(Node node, TypeMirror exprType) {
            // For binary numeric promotion, see JLS 5.6.2
            node = unbox(node);

            if (!types.isSameType(node.getType(), exprType)) {
                node = new WideningConversionNode(node.getTree(), node,
                        exprType);
                replaceInLookupMap(node);
                extendWithNode(node);
            }
            return node;
        }

        /**
         * Perform widening primitive conversion on the input node to make it
         * match the destination type.
         *
         * @param node
         *            a node producing a value of numeric primitive type
         * @param destType
         *            the type to widen the value to
         * @return a Node with the value widened to the exprType, which may be
         *         the input node
         */
        protected Node widen(Node node, TypeMirror destType) {
            // For widening conversion, see JLS 5.1.2
            assert TypesUtils.isPrimitive(node.getType())
                    && TypesUtils.isPrimitive(destType) : "widening must be applied to primitive types";
            if (types.isSubtype(node.getType(), destType)
                    && !types.isSameType(node.getType(), destType)) {
                node = new WideningConversionNode(node.getTree(), node,
                        destType);
                replaceInLookupMap(node);
                extendWithNode(node);
            }

            return node;
        }

        /**
         * Perform narrowing conversion on the input node to make it match the
         * destination type.
         *
         * @param node
         *            a node producing a value of numeric primitive type
         * @param destType
         *            the type to narrow the value to
         * @return a Node with the value narrowed to the exprType, which may be
         *         the input node
         */
        protected Node narrow(Node node, TypeMirror destType) {
            // For narrowing conversion, see JLS 5.1.3
            assert TypesUtils.isPrimitive(node.getType())
                    && TypesUtils.isPrimitive(destType) : "narrowing must be applied to primitive types";
            if (types.isSubtype(destType, node.getType())
                    && !types.isSameType(destType, node.getType())) {
                node = new NarrowingConversionNode(node.getTree(), node,
                        destType);
                replaceInLookupMap(node);
                extendWithNode(node);
            }

            return node;
        }

        /**
         * Perform narrowing conversion and optionally boxing conversion on the
         * input node to make it match the destination type.
         *
         * @param node
         *            a node producing a value of numeric primitive type
         * @param destType
         *            the type to narrow the value to (possibly boxed)
         * @return a Node with the value narrowed and boxed to the destType,
         *         which may be the input node
         */
        protected Node narrowAndBox(Node node, TypeMirror destType) {
            if (TypesUtils.isBoxedPrimitive(destType)) {
                return box(narrow(node, types.unboxedType(destType)));
            } else {
                return narrow(node, destType);
            }
        }

        /**
         * Assignment conversion and method invocation conversion are almost
         * identical, except that assignment conversion allows narrowing. We
         * factor out the common logic here.
         *
         * @param node
         *            a Node producing a value
         * @param varType
         *            the type of a variable
         * @param allowNarrowing
         *            whether to allow narrowing (for assignment conversion) or
         *            not (for method invocation conversion)
         * @return a Node with the value converted to the type of the variable,
         *         which may be the input node itself
         */
        protected Node commonConvert(Node node, TypeMirror varType,
                boolean allowNarrowing) {
            // For assignment conversion, see JLS 5.2
            // For method invocation conversion, see JLS 5.3

            // Check for identical types or "identity conversion"
            TypeMirror nodeType = node.getType();
            boolean isSameType = types.isSameType(nodeType, varType);
            if (isSameType) {
                return node;
            }

            boolean isRightNumeric = TypesUtils.isNumeric(nodeType);
            boolean isRightPrimitive = TypesUtils.isPrimitive(nodeType);
            boolean isRightBoxed = TypesUtils.isBoxedPrimitive(nodeType);
            boolean isRightReference = nodeType instanceof ReferenceType;
            boolean isLeftNumeric = TypesUtils.isNumeric(varType);
            boolean isLeftPrimitive = TypesUtils.isPrimitive(varType);
            boolean isLeftBoxed = TypesUtils.isBoxedPrimitive(varType);
            boolean isLeftReference = varType instanceof ReferenceType;
            boolean isSubtype = types.isSubtype(nodeType, varType);

            if (isRightNumeric && isLeftNumeric && isSubtype) {
                node = widen(node, varType);
                nodeType = node.getType();
            } else if (isRightReference && isLeftReference && isSubtype) {
                // widening reference conversion is a no-op, but if it
                // applies, then later conversions do not.
            } else if (isRightPrimitive && isLeftBoxed) {
                node = box(node);
                nodeType = node.getType();
            } else if (isRightBoxed && isLeftPrimitive) {
                node = unbox(node);
                nodeType = node.getType();

                if (types.isSubtype(nodeType, varType)
                        && !types.isSameType(nodeType, varType)) {
                    node = widen(node, varType);
                    nodeType = node.getType();
                }
            }

            // Unchecked conversion of raw types
            boolean isRightRaw = (nodeType instanceof DeclaredType)
                    && ((DeclaredType) nodeType).getTypeArguments().isEmpty();
            if (isRightRaw) {
                // TODO: if checkers need to know about unchecked conversions
                // add a Node class for them. Otherwise, we can omit this
                // case.
            }

            if (allowNarrowing) {
                // Narrowing is restricted to cases where the left hand side
                // is byte, char, short or Byte, Char, Short and the right
                // hand side is a constant.
                TypeMirror unboxedVarType = isLeftBoxed ? types
                        .unboxedType(varType) : varType;
                TypeKind unboxedVarKind = unboxedVarType.getKind();
                boolean isLeftNarrowableTo = unboxedVarKind == TypeKind.BYTE
                        || unboxedVarKind == TypeKind.SHORT
                        || unboxedVarKind == TypeKind.CHAR;
                boolean isRightConstant = node instanceof ValueLiteralNode;
                if (isLeftNarrowableTo && isRightConstant) {
                    if (isLeftBoxed) {
                        node = narrowAndBox(node, varType);
                        nodeType = node.getType();
                    } else {
                        node = narrow(node, varType);
                        nodeType = node.getType();
                    }
                }
            }

            // TODO: if checkers need to know about null references of
            // a particular type, add logic for them here.

            return node;
        }

        /**
         * Perform assignment conversion so that it can be assigned to a
         * variable of the given type.
         *
         * @param node
         *            a Node producing a value
         * @param varType
         *            the type of a variable
         * @return a Node with the value converted to the type of the variable,
         *         which may be the input node itself
         */
        protected Node assignConvert(Node node, TypeMirror varType) {
            return commonConvert(node, varType, true);
        }

        /**
         * Perform method invocation conversion so that the node can be passed
         * as a formal parameter of the given type.
         *
         * @param node
         *            a Node producing a value
         * @param formalType
         *            the type of a formal parameter
         * @return a Node with the value converted to the type of the formal,
         *         which may be the input node itself
         */
        protected Node methodInvocationConvert(Node node, TypeMirror formalType) {
            return commonConvert(node, formalType, false);
        }

        /**
         * Given a method element and as list of argument expressions, return a
         * list of {@link Node}s representing the arguments converted for a call
         * of the method. This method applies to both method invocations and
         * constructor calls.
         *
         * @param method
         *            an ExecutableElement representing a method to be called
         * @param actualExprs
         *            a List of argument expressions to a call
         * @return a List of {@link Node}s representing arguments after
         *         conversions required by a call to this method.
         */
        protected List<Node> convertCallArguments(ExecutableElement method,
                List<? extends ExpressionTree> actualExprs) {
            List<? extends VariableElement> formals = method.getParameters();

            ArrayList<Node> actualNodes = new ArrayList<Node>();

            for (ExpressionTree actual : actualExprs) {
                actualNodes.add(scan(actual, null));
            }

            if (method.isVarArgs()) {
                // Create a new array argument if the actuals outnumber
                // the formals, or if the last actual is not assignable
                // to the last formal.
                int numFormals = formals.size();
                int lastArgIndex = numFormals - 1;
                int numActuals = actualExprs.size();
                TypeMirror lastParamType = formals.get(lastArgIndex).asType();
                List<Node> dimensions = new ArrayList<>();
                List<Node> initializers = new ArrayList<>();

                if (numActuals == numFormals - 1) {
                    // Create an empty array for the last parameter
                    Node lastArgument = new ArrayCreationNode(null,
                            lastParamType, dimensions, initializers);
                    extendWithNode(lastArgument);

                    actualNodes.add(lastArgument);
                } else {
                    TypeMirror actualType = InternalUtils.typeOf(actualExprs
                            .get(lastArgIndex));
                    if (numActuals == numFormals
                            && types.isAssignable(actualType, lastParamType)) {
                        // Normal call with no array creation
                    } else {
                        for (int i = lastArgIndex; i < numActuals; i++) {
                            initializers.add(actualNodes.remove(lastArgIndex));
                        }

                        Node lastArgument = new ArrayCreationNode(null,
                                lastParamType, dimensions, initializers);
                        extendWithNode(lastArgument);

                        actualNodes.add(lastArgument);
                    }
                }
            }

            // Convert arguments
            ArrayList<Node> convertedNodes = new ArrayList<Node>();
            for (int i = 0; i < formals.size(); i++) {
                convertedNodes.add(methodInvocationConvert(actualNodes.get(i),
                        formals.get(i).asType()));
            }

            return convertedNodes;
        }

        /**
         * Convert an operand of a conditional expression to the type of the
         * whole expression.
         *
         * @param node
         *            a node occurring as the second or third operand of
         *            a conditional expression
         * @param destType
         *            the type to promote the value to
         * @return a Node with the value promoted to the destType, which may be
         *         the input node
         */
        protected Node conditionalExprPromotion(Node node, TypeMirror destType) {
            // For rules on converting operands of conditional expressions,
            // JLS 15.25
            TypeMirror nodeType = node.getType();

            // If the operand is already the same type as the whole
            // expression, then do nothing.
            if (types.isSameType(nodeType, destType)) {
                return node;
            }

            // If the operand is a primitive and the whole expression is
            // boxed, then apply boxing.
            if (TypesUtils.isPrimitive(nodeType) &&
                TypesUtils.isBoxedPrimitive(destType)) {
                return box(node);
            }

            // If the operand is byte or Byte and the whole expression is
            // short, then convert to short.
            boolean isBoxedPrimitive = TypesUtils.isBoxedPrimitive(nodeType);
            TypeMirror unboxedNodeType =
                isBoxedPrimitive ? types.unboxedType(nodeType) : nodeType;
            if (TypesUtils.isNumeric(unboxedNodeType)) {
                if (unboxedNodeType.getKind() == TypeKind.BYTE &&
                    destType.getKind() == TypeKind.SHORT) {
                    if (isBoxedPrimitive) {
                        node = unbox(node);
                    }
                    return widen(node, destType);
                }

                // If the operand is Byte, Short or Character and the whole expression
                // is the unboxed version of it, then apply unboxing.
                TypeKind destKind = destType.getKind();
                if (destKind == TypeKind.BYTE || destKind == TypeKind.CHAR ||
                    destKind == TypeKind.SHORT) {
                    if (isBoxedPrimitive) {
                        return unbox(node);
                    } else if (nodeType.getKind() == TypeKind.INT) {
                        return narrow(node, destType);
                    }
                }

                return binaryNumericPromotion(node, destType);
            }

            // TODO: Do we need to cast to lub(box(nodeType)) if the final
            // case in JLS 15.25 applies?
            return node;
        }

        /**
         * Returns the label {@link Name} of the leaf in the argument path, or
         * null if the leaf is not a labeled statement.
         */
        protected/* @Nullable */Name getLabel(TreePath path) {
            if (path.getParentPath() != null) {
                Tree parent = path.getParentPath().getLeaf();
                if (parent.getKind() == Tree.Kind.LABELED_STATEMENT) {
                    return ((LabeledStatementTree) parent).getLabel();
                }
            }
            return null;
        }

        /* --------------------------------------------------------- */
        /* Visitor Methods */
        /* --------------------------------------------------------- */

        @Override
        public Node visitAnnotatedType(AnnotatedTypeTree tree, Void p) {
            return scan(tree.getUnderlyingType(), p);
        }

        @Override
        public Node visitAnnotation(AnnotationTree tree, Void p) {
            assert false : "AnnotationTree is unexpected in AST to CFG translation";
            return null;
        }

        @Override
        public MethodInvocationNode visitMethodInvocation(MethodInvocationTree tree, Void p) {

            // see JLS 15.12.4

            // First, compute the receiver, if any (15.12.4.1)
            // Second, evaluate the actual arguments, left to right and
            // possibly some arguments are stored into an array for variable
            // arguments calls (15.12.4.2)
            // Third, test the receiver, if any, for nullness (15.12.4.4)
            // Fourth, convert the arguments to the type of the formal
            // parameters (15.12.4.5)
            // Fifth, if the method is synchronized, lock the receiving
            // object or class (15.12.4.5)
            ExecutableElement method = TreeUtils.elementFromUse(tree);
            boolean isBooleanMethod = TypesUtils.isBooleanType(method.getReturnType());

            ConditionalJump cjump = null;
            if (conditionalMode && isBooleanMethod) {
                cjump = new ConditionalJump(thenTargetL, elseTargetL);
            }
            boolean outerConditionalMode = conditionalMode;
            conditionalMode = false;

            ExpressionTree methodSelect = tree.getMethodSelect();
            assert TreeUtils.isMethodAccess(methodSelect);

            List<? extends ExpressionTree> actualExprs = tree.getArguments();

            List<Node> arguments = convertCallArguments(method, actualExprs);

            // Look up method to invoke and possibly throw NullPointerException
            Node receiver = getReceiver(methodSelect,
                    TreeUtils.enclosingClass(getCurrentPath()));

            MethodAccessNode target = new MethodAccessNode(methodSelect,
                    receiver);

            ExecutableElement element = TreeUtils.elementFromUse(tree);
            if (ElementUtils.isStatic(element) ||
                receiver instanceof ThisLiteralNode) {
                // No NullPointerException can be thrown, use normal node
                extendWithNode(target);
            } else {
                TypeElement npeElement = elements
                    .getTypeElement("java.lang.NullPointerException");
                extendWithNodeWithException(target, npeElement.asType());
            }

            // TODO: lock the receiver for synchronized methods

            MethodInvocationNode node = new MethodInvocationNode(tree, target, arguments, getCurrentPath());
            Set<TypeMirror> thrownSet = new HashSet<>();

            // Add exceptions explicitly mentioned in the throws clause.
            List<? extends TypeMirror> thrownTypes = element.getThrownTypes();
            thrownSet.addAll(thrownTypes);
            // Add Throwable to account for unchecked exceptions
            TypeElement throwableElement = elements
                    .getTypeElement("java.lang.Throwable");
            thrownSet.add(throwableElement.asType());

            extendWithNodeWithExceptions(node, thrownSet);

            conditionalMode = outerConditionalMode;

            if (conditionalMode && isBooleanMethod) {
                extendWithExtendedNode(cjump);
            }

            return node;
        }

        @Override
        public Node visitAssert(AssertTree tree, Void p) {
            Node condition = unbox(scan(tree.getCondition(), p));
            Node detail = scan(tree.getDetail(), p);

            return extendWithNode(new AssertNode(tree, condition, detail));
        }

        @Override
        public Node visitAssignment(AssignmentTree tree, Void p) {

            // see JLS 15.26.1

            assert !conditionalMode;

            Node expression;
            ExpressionTree variable = tree.getVariable();
            TypeMirror varType = InternalUtils.typeOf(variable);

            // case 1: field access
            if (TreeUtils.isFieldAccess(variable)) {
                // visit receiver
                Node receiver = getReceiver(variable,
                        TreeUtils.enclosingClass(getCurrentPath()));

                // visit expression
                expression = scan(tree.getExpression(), p);
                expression = assignConvert(expression, varType);

                // visit field access (throws null-pointer exception)
                FieldAccessNode target = new FieldAccessNode(variable, receiver);
                target.setLValue();

                Element element = TreeUtils.elementFromUse(variable);
                if (ElementUtils.isStatic(element) ||
                    receiver instanceof ThisLiteralNode) {
                    // No NullPointerException can be thrown, use normal node
                    extendWithNode(target);
                } else {
                    TypeElement npeElement = elements
                            .getTypeElement("java.lang.NullPointerException");
                    extendWithNodeWithException(target, npeElement.asType());
                }

                // add assignment node
                AssignmentNode assignmentNode = new AssignmentNode(tree,
                        target, expression);
                extendWithNode(assignmentNode);
            }

            // case 2: other cases
            else {
                Node target = scan(variable, p);
                target.setLValue();

                expression = translateAssignment(tree, target,
                        tree.getExpression());
            }
            return expression;
        }

        /**
         * Translate an assignment.
         */
        protected Node translateAssignment(Tree tree, Node target,
                ExpressionTree rhs) {
            Node expression = scan(rhs, null);
            return translateAssignment(tree, target, expression);
        }

        /**
         * Translate an assignment where the RHS has already been scanned.
         */
        protected Node translateAssignment(Tree tree, Node target,
                Node expression) {
            assert tree instanceof AssignmentTree
                    || tree instanceof VariableTree;
            target.setLValue();
            expression = assignConvert(expression, target.getType());
            AssignmentNode assignmentNode = new AssignmentNode(tree, target,
                    expression);
            extendWithNode(assignmentNode);
            return expression;
        }

        /**
         * Note 1: Requires <code>tree</code> to be a field or method access
         * tree.
         * <p>
         * Note 2: Visits the receiver and adds all necessary blocks to the CFG.
         *
         * @param tree
         *            the field access tree containing the receiver
         * @param classTree
         *            the ClassTree enclosing the field access
         * @return The receiver of the field access.
         */
        private Node getReceiver(Tree tree, ClassTree classTree) {
            assert TreeUtils.isFieldAccess(tree)
                    || TreeUtils.isMethodAccess(tree);
            if (tree.getKind().equals(Tree.Kind.MEMBER_SELECT)) {
                MemberSelectTree mtree = (MemberSelectTree) tree;
                return scan(mtree.getExpression(), null);
            } else {
                TypeMirror classType = InternalUtils.typeOf(classTree);
                Node node = new ImplicitThisLiteralNode(classType);
                extendWithNode(node);
                return node;
            }
        }

        @Override
        public Node visitCompoundAssignment(CompoundAssignmentTree tree, Void p) {
            // According the JLS 15.26.2, E1 op= E2 is equivalent to
            // E1 = (T) ((E1) op (E2)), where T is the type of E1,
            // except that E1 is evaluated only once.
            //
            // We do not separate compound assignments into separate
            // operation and assignment nodes. For example, += is not
            // split into a + followed by an =. We do perform
            // promotions of operands to compound assignments,
            // though. So our representation of E1 op= E2 will be:
            //
            // ... nodes for E1 ...
            // N1: optional promotion of E1
            // ... nodes for E2 ...
            // N2: optional promotion of E2
            // op= N1 N2
            //
            // This has several consequences. First, the variable
            // being assigned to may not be the immediate left operand
            // of op=. If promotion or conversion happens, the
            // variable will have to be extracted from those nodes.
            // Second, the type cast (T) will not be explicitly
            // represented. The transfer function for op= will need
            // to account for that possible type cast.

            // TODO: correct evaluation rules (e.g. arrays)

            assert !conditionalMode;
            Node r = null;
            Tree.Kind kind = tree.getKind();
            switch (kind) {
            case DIVIDE_ASSIGNMENT:
            case MULTIPLY_ASSIGNMENT:
            case REMAINDER_ASSIGNMENT: {
                // see JLS 15.17 and 15.26.2
                Node target = scan(tree.getVariable(), p);
                Node value = scan(tree.getExpression(), p);

                TypeMirror exprType = InternalUtils.typeOf(tree);

                target = binaryNumericPromotion(target, exprType);
                value = binaryNumericPromotion(value, exprType);

                if (kind == Tree.Kind.MULTIPLY_ASSIGNMENT) {
                    r = new NumericalMultiplicationAssignmentNode(tree, target,
                            value);
                } else if (kind == Tree.Kind.DIVIDE_ASSIGNMENT) {
                    if (TypesUtils.isIntegral(exprType)) {
                        r = new IntegerDivisionAssignmentNode(tree, target,
                                value);
                    } else {
                        r = new FloatingDivisionAssignmentNode(tree, target,
                                value);
                    }
                } else {
                    assert kind == Kind.REMAINDER_ASSIGNMENT;
                    if (TypesUtils.isIntegral(exprType)) {
                        r = new IntegerRemainderAssignmentNode(tree, target,
                                value);
                    } else {
                        r = new FloatingRemainderAssignmentNode(tree, target,
                                value);
                    }
                }
                break;
            }

            case MINUS_ASSIGNMENT:
            case PLUS_ASSIGNMENT: {
                // see JLS 15.18 and 15.26.2

                Node target = scan(tree.getVariable(), p);
                Node value = scan(tree.getExpression(), p);

                TypeMirror exprType = InternalUtils.typeOf(tree);

                if (TypesUtils.isString(exprType)) {
                    assert (kind == Tree.Kind.PLUS_ASSIGNMENT);
                    target = stringConversion(target, exprType);
                    value = stringConversion(value, exprType);
                    r = new StringConcatenateAssignmentNode(tree, target, value);
                } else {
                    target = binaryNumericPromotion(target, exprType);
                    value = binaryNumericPromotion(value, exprType);

                    if (kind == Tree.Kind.PLUS_ASSIGNMENT) {
                        r = new NumericalAdditionAssignmentNode(tree, target,
                                value);
                    } else {
                        assert kind == Kind.MINUS_ASSIGNMENT;
                        r = new NumericalSubtractionAssignmentNode(tree,
                                target, value);
                    }
                }
                break;
            }

            case LEFT_SHIFT_ASSIGNMENT:
            case RIGHT_SHIFT_ASSIGNMENT:
            case UNSIGNED_RIGHT_SHIFT_ASSIGNMENT: {
                // see JLS 15.19 and 15.26.2
                Node target = scan(tree.getVariable(), p);
                Node value = scan(tree.getExpression(), p);

                target = unaryNumericPromotion(target);
                value = unaryNumericPromotion(value);

                if (kind == Tree.Kind.LEFT_SHIFT_ASSIGNMENT) {
                    r = new LeftShiftAssignmentNode(tree, target, value);
                } else if (kind == Tree.Kind.RIGHT_SHIFT_ASSIGNMENT) {
                    r = new SignedRightShiftAssignmentNode(tree, target, value);
                } else {
                    assert kind == Kind.UNSIGNED_RIGHT_SHIFT_ASSIGNMENT;
                    r = new UnsignedRightShiftAssignmentNode(tree, target,
                            value);
                }
                break;
            }

            case AND_ASSIGNMENT:
            case OR_ASSIGNMENT:
            case XOR_ASSIGNMENT:
                // see JLS 15.22
                Node target = scan(tree.getVariable(), p);
                Node value = scan(tree.getExpression(), p);

                TypeMirror exprType = InternalUtils.typeOf(tree);

                if (TypesUtils.isNumeric(exprType)) {
                    target = binaryNumericPromotion(target, exprType);
                    value = binaryNumericPromotion(value, exprType);
                } else if (TypesUtils.isBooleanType(exprType)) {
                    target = unbox(target);
                    value = unbox(value);
                }

                if (kind == Tree.Kind.AND_ASSIGNMENT) {
                    r = new BitwiseAndAssignmentNode(tree, target, value);
                } else if (kind == Tree.Kind.OR_ASSIGNMENT) {
                    r = new BitwiseOrAssignmentNode(tree, target, value);
                } else {
                    assert kind == Kind.XOR_ASSIGNMENT;
                    r = new BitwiseXorAssignmentNode(tree, target, value);
                }
                break;
            }
            assert r != null : "unexpected compound assignment type";
            extendWithNode(r);
            return r;
        }

        @Override
        public Node visitBinary(BinaryTree tree, Void p) {
            Node r = null;
            Tree.Kind kind = tree.getKind();
            switch (kind) {
            case DIVIDE:
            case MULTIPLY:
            case REMAINDER: {
                // see JLS 15.17
                assert !conditionalMode;

                Node left = scan(tree.getLeftOperand(), p);
                Node right = scan(tree.getRightOperand(), p);

                TypeMirror exprType = InternalUtils.typeOf(tree);

                left = binaryNumericPromotion(left, exprType);
                right = binaryNumericPromotion(right, exprType);

                if (kind == Tree.Kind.MULTIPLY) {
                    r = new NumericalMultiplicationNode(tree, left, right);
                } else if (kind == Tree.Kind.DIVIDE) {
                    if (TypesUtils.isIntegral(exprType)) {
                        r = new IntegerDivisionNode(tree, left, right);
                    } else {
                        r = new FloatingDivisionNode(tree, left, right);
                    }
                } else {
                    assert kind == Kind.REMAINDER;
                    if (TypesUtils.isIntegral(exprType)) {
                        r = new IntegerRemainderNode(tree, left, right);
                    } else {
                        r = new FloatingRemainderNode(tree, left, right);
                    }
                }
                break;
            }

            case MINUS:
            case PLUS: {
                // see JLS 15.18
                assert !conditionalMode;

                Node left = scan(tree.getLeftOperand(), p);
                Node right = scan(tree.getRightOperand(), p);

                TypeMirror exprType = InternalUtils.typeOf(tree);

                if (TypesUtils.isString(exprType)) {
                    assert (kind == Tree.Kind.PLUS);
                    left = stringConversion(left, exprType);
                    right = stringConversion(right, exprType);
                    r = new StringConcatenateNode(tree, left, right);
                } else {
                    left = binaryNumericPromotion(left, exprType);
                    right = binaryNumericPromotion(right, exprType);

                    // TODO: Decide whether to deal with floating-point value
                    // set conversion.
                    if (kind == Tree.Kind.PLUS) {
                        r = new NumericalAdditionNode(tree, left, right);
                    } else {
                        assert kind == Kind.MINUS;
                        r = new NumericalSubtractionNode(tree, left, right);
                    }
                }
                break;
            }

            case LEFT_SHIFT:
            case RIGHT_SHIFT:
            case UNSIGNED_RIGHT_SHIFT: {
                // see JLS 15.19
                assert !conditionalMode;

                Node left = scan(tree.getLeftOperand(), p);
                Node right = scan(tree.getRightOperand(), p);

                left = unaryNumericPromotion(left);
                right = unaryNumericPromotion(right);

                if (kind == Tree.Kind.LEFT_SHIFT) {
                    r = new LeftShiftNode(tree, left, right);
                } else if (kind == Tree.Kind.RIGHT_SHIFT) {
                    r = new SignedRightShiftNode(tree, left, right);
                } else {
                    assert kind == Kind.UNSIGNED_RIGHT_SHIFT;
                    r = new UnsignedRightShiftNode(tree, left, right);
                }
                break;
            }

            case GREATER_THAN:
            case GREATER_THAN_EQUAL:
            case LESS_THAN:
            case LESS_THAN_EQUAL: {
                // see JLS 15.20.1
                ConditionalJump cjump = null;
                if (conditionalMode) {
                    cjump = new ConditionalJump(thenTargetL, elseTargetL);
                }
                boolean outerConditionalMode = conditionalMode;
                conditionalMode = false;

                Node left = scan(tree.getLeftOperand(), p);
                Node right = scan(tree.getRightOperand(), p);

                TypeMirror leftType = left.getType();
                if (TypesUtils.isBoxedPrimitive(leftType)) {
                    leftType = types.unboxedType(leftType);
                }

                TypeMirror rightType = right.getType();
                if (TypesUtils.isBoxedPrimitive(rightType)) {
                    rightType = types.unboxedType(rightType);
                }

                TypeKind widenedTypeKind = TypesUtils.widenedNumericType(
                        leftType, rightType);
                TypeMirror widenedType = types
                        .getPrimitiveType(widenedTypeKind);

                left = binaryNumericPromotion(left, widenedType);
                right = binaryNumericPromotion(right, widenedType);

                Node node;
                if (kind == Tree.Kind.GREATER_THAN) {
                    node = new GreaterThanNode(tree, left, right);
                } else if (kind == Tree.Kind.GREATER_THAN_EQUAL) {
                    node = new GreaterThanOrEqualNode(tree, left, right);
                } else if (kind == Tree.Kind.LESS_THAN) {
                    node = new LessThanNode(tree, left, right);
                } else {
                    assert kind == Tree.Kind.LESS_THAN_EQUAL;
                    node = new LessThanOrEqualNode(tree, left, right);
                }

                extendWithNode(node);

                conditionalMode = outerConditionalMode;

                if (conditionalMode) {
                    extendWithExtendedNode(cjump);
                }
                return node;
            }

            case EQUAL_TO:
            case NOT_EQUAL_TO: {
                // see JLS 15.21
                ConditionalJump cjump = null;
                if (conditionalMode) {
                    cjump = new ConditionalJump(thenTargetL, elseTargetL);
                }
                boolean outerConditionalMode = conditionalMode;
                conditionalMode = false;

                Node left = scan(tree.getLeftOperand(), p);
                Node right = scan(tree.getRightOperand(), p);

                TypeMirror leftType = left.getType();
                TypeMirror rightType = right.getType();

                boolean isLeftNumeric = TypesUtils.isNumeric(leftType);
                boolean isLeftBoxed = TypesUtils.isBoxedPrimitive(leftType);
                boolean isLeftBoxedNumeric = isLeftBoxed
                        && TypesUtils.isNumeric(types.unboxedType(leftType));
                boolean isLeftBoxedBoolean = isLeftBoxed
                        && TypesUtils.isBooleanType(leftType);

                boolean isRightNumeric = TypesUtils.isNumeric(rightType);
                boolean isRightBoxed = TypesUtils.isBoxedPrimitive(rightType);
                boolean isRightBoxedNumeric = isRightBoxed
                        && TypesUtils.isNumeric(types.unboxedType(rightType));
                boolean isRightBoxedBoolean = isRightBoxed
                        && TypesUtils.isBooleanType(rightType);

                if (isLeftNumeric && (isRightNumeric || isRightBoxedNumeric)
                        || isLeftBoxedNumeric && isRightNumeric) {
                    TypeMirror leftUnboxedType = isLeftBoxedNumeric ? types
                            .unboxedType(leftType) : leftType;
                    TypeMirror rightUnboxedType = isRightBoxedNumeric ? types
                            .unboxedType(rightType) : rightType;
                    TypeKind widened = TypesUtils.widenedNumericType(
                            leftUnboxedType, rightUnboxedType);
                    TypeMirror commonType = types.getPrimitiveType(widened);
                    left = binaryNumericPromotion(left, commonType);
                    right = binaryNumericPromotion(right, commonType);
                } else if (isLeftBoxedBoolean && !isRightBoxedBoolean) {
                    left = unbox(left);
                } else if (isRightBoxedBoolean && !isLeftBoxedBoolean) {
                    right = unbox(right);
                }

                Node node;
                if (kind == Tree.Kind.EQUAL_TO) {
                    node = new EqualToNode(tree, left, right);
                } else {
                    assert kind == Kind.NOT_EQUAL_TO;
                    node = new NotEqualNode(tree, left, right);
                }
                extendWithNode(node);

                conditionalMode = outerConditionalMode;

                if (conditionalMode) {
                    extendWithExtendedNode(cjump);
                }
                return node;
            }

            case AND:
            case OR:
            case XOR: {
                // see JLS 15.22
                TypeMirror exprType = InternalUtils.typeOf(tree);
                boolean isBooleanOp = TypesUtils.isBooleanType(exprType);
                assert !conditionalMode || isBooleanOp;

                ConditionalJump cjump = null;
                if (conditionalMode) {
                    cjump = new ConditionalJump(thenTargetL, elseTargetL);
                }
                boolean outerConditionalMode = conditionalMode;
                conditionalMode = false;

                Node left = scan(tree.getLeftOperand(), p);
                Node right = scan(tree.getRightOperand(), p);

                if (TypesUtils.isNumeric(exprType)) {
                    left = binaryNumericPromotion(left, exprType);
                    right = binaryNumericPromotion(right, exprType);
                } else if (isBooleanOp) {
                    left = unbox(left);
                    right = unbox(right);
                }

                Node node;
                if (kind == Tree.Kind.AND) {
                    node = new BitwiseAndNode(tree, left, right);
                } else if (kind == Tree.Kind.OR) {
                    node = new BitwiseOrNode(tree, left, right);
                } else {
                    assert kind == Kind.XOR;
                    node = new BitwiseXorNode(tree, left, right);
                }

                extendWithNode(node);

                conditionalMode = outerConditionalMode;

                if (conditionalMode) {
                    extendWithExtendedNode(cjump);
                }
                return node;
            }

            case CONDITIONAL_AND:
            case CONDITIONAL_OR: {
                // see JLS 15.23 and 15.24

                boolean condMode = conditionalMode;
                conditionalMode = true;

                // all necessary labels
                Label rightStartL = new Label();
                Label trueNodeL = new Label();
                Label falseNodeL = new Label();
                Label oldTrueTargetL = thenTargetL;
                Label oldFalseTargetL = elseTargetL;

                // left-hand side
                if (kind == Tree.Kind.CONDITIONAL_AND) {
                    thenTargetL = rightStartL;
                    elseTargetL = falseNodeL;
                } else {
                    thenTargetL = trueNodeL;
                    elseTargetL = rightStartL;
                }
                Node left = scan(tree.getLeftOperand(), p);

                // right-hand side
                thenTargetL = trueNodeL;
                elseTargetL = falseNodeL;
                addLabelForNextNode(rightStartL);
                Node right = scan(tree.getRightOperand(), p);

                conditionalMode = condMode;

                if (conditionalMode) {
                    Node node;
                    if (kind == Tree.Kind.CONDITIONAL_AND) {
                        node = new ConditionalAndNode(tree, left, right);
                    } else {
                        node = new ConditionalOrNode(tree, left, right);
                    }

                    // node for true case
                    addLabelForNextNode(trueNodeL);
                    extendWithNode(node);
                    extendWithExtendedNode(new UnconditionalJump(oldTrueTargetL));

                    // node for false case
                    addLabelForNextNode(falseNodeL);
                    extendWithNode(node);
                    extendWithExtendedNode(new UnconditionalJump(
                            oldFalseTargetL));

                    return node;
                } else {
                    // one node for true/false
                    addLabelForNextNode(trueNodeL);
                    addLabelForNextNode(falseNodeL);
                    Node node;
                    if (kind == Tree.Kind.CONDITIONAL_AND) {
                        node = new ConditionalAndNode(tree, left, right);
                    } else {
                        node = new ConditionalOrNode(tree, left, right);
                    }
                    extendWithNode(node);
                    return node;
                }
            }

            /*
             * case CONDITIONAL_OR: {
             *
             * // see JLS 15.24
             *
             * boolean condMode = conditionalMode; conditionalMode = true;
             *
             * // all necessary labels Label rightStartL = new Label(); Label
             * trueNodeL = new Label(); Label falseNodeL = new Label(); Label
             * oldTrueTargetL = thenTargetL; Label oldFalseTargetL =
             * elseTargetL;
             *
             * // left-hand side thenTargetL = trueNodeL; elseTargetL =
             * rightStartL; Node left = scan(tree.getLeftOperand(), p);
             *
             * // right-hand side thenTargetL = trueNodeL; elseTargetL =
             * falseNodeL; addLabelForNextNode(rightStartL); Node right =
             * scan(tree.getRightOperand(), p);
             *
             * conditionalMode = condMode;
             *
             * if (conditionalMode) { Node node = new ConditionalOrNode(tree,
             * left, right);
             *
             * // node for true case addLabelForNextNode(trueNodeL);
             * extendWithNode(node); extendWithExtendedNode(new
             * UnconditionalJump(oldTrueTargetL));
             *
             * // node for false case addLabelForNextNode(falseNodeL);
             * extendWithNode(node); extendWithExtendedNode(new
             * UnconditionalJump(oldFalseTargetL));
             *
             * return node; } else { // one node for true/false
             * addLabelForNextNode(trueNodeL); addLabelForNextNode(falseNodeL);
             * Node node = new ConditionalOrNode(tree, left, right);
             * extendWithNode(node); return node; } }
             */
            }
            assert r != null : "unexpected binary tree";
            return extendWithNode(r);
        }

        @Override
        public Node visitBlock(BlockTree tree, Void p) {
            for (StatementTree n : tree.getStatements()) {
                scan(n, null);
            }
            return null;
        }

        @Override
        public Node visitBreak(BreakTree tree, Void p) {
            assert !conditionalMode;

            Name label = tree.getLabel();
            if (label == null) {
                assert breakTargetL != null : "no target for break statement";

                extendWithExtendedNode(new UnconditionalJump(breakTargetL));
            } else {
                assert breakLabels.containsKey(label);

                extendWithExtendedNode(new UnconditionalJump(
                        breakLabels.get(label)));
            }

            return null;
        }

        @Override
        public Node visitCase(CaseTree tree, Void p) {
            assert !conditionalMode;

            assert switchExpr != null : "no switch expression in case";

            Tree exprTree = tree.getExpression();
            if (exprTree != null) {
                // a case with a constant expression
                Label thisBlockL = new Label();
                Label nextCaseL = new Label();

                Node expr = scan(exprTree, p);
                CaseNode test = new CaseNode(tree, switchExpr, expr);
                extendWithNode(test);
                extendWithExtendedNode(new ConditionalJump(thisBlockL,
                        nextCaseL));
                addLabelForNextNode(thisBlockL);
                for (StatementTree stmt : tree.getStatements()) {
                    scan(stmt, p);
                }
                addLabelForNextNode(nextCaseL);
            } else {
                // the default case
                for (StatementTree stmt : tree.getStatements()) {
                    scan(stmt, p);
                }
            }
            return null;
        }

        @Override
        public Node visitCatch(CatchTree tree, Void p) {
            scan(tree.getParameter(), p);
            scan(tree.getBlock(), p);
            return null;
        }

        @Override
        public Node visitClass(ClassTree tree, Void p) {
            declaredClasses.add(tree);
            return null;
        }

        @Override
        public Node visitConditionalExpression(ConditionalExpressionTree tree,
                Void p) {
            // see JLS 15.25
            TypeMirror exprType = InternalUtils.typeOf(tree);
            boolean isBooleanOp = TypesUtils.isBooleanType(exprType);
            assert !conditionalMode || isBooleanOp;

            Label trueStart = new Label();
            Label falseStart = new Label();
            Label merge = new Label();

            ConditionalJump cjump = null;
            if (conditionalMode) {
                cjump = new ConditionalJump(thenTargetL, elseTargetL);
        }
            boolean outerConditionalMode = conditionalMode;

            conditionalMode = true;
            thenTargetL = trueStart;
            elseTargetL = falseStart;
            Node condition = unbox(scan(tree.getCondition(), p));
            conditionalMode = false;

            addLabelForNextNode(trueStart);
            Node trueExpr = scan(tree.getTrueExpression(), p);
            trueExpr = conditionalExprPromotion(trueExpr, exprType);
            extendWithExtendedNode(new UnconditionalJump(merge));

            addLabelForNextNode(falseStart);
            Node falseExpr = scan(tree.getFalseExpression(), p);
            falseExpr = conditionalExprPromotion(falseExpr, exprType);

            addLabelForNextNode(merge);
            Node node = new TernaryExpressionNode(tree, condition, trueExpr, falseExpr);
            extendWithNode(node);

            // Finally, emit a jump if the expression occurs within a conditional.

            conditionalMode = outerConditionalMode;

            if (conditionalMode) {
                extendWithExtendedNode(cjump);
            }

            return node;
        }

        @Override
        public Node visitContinue(ContinueTree tree, Void p) {
            Name label = tree.getLabel();
            if (label == null) {
                assert continueTargetL != null : "no target for continue statement";

                extendWithExtendedNode(new UnconditionalJump(continueTargetL));
            } else {
                assert continueLabels.containsKey(label);

                extendWithExtendedNode(new UnconditionalJump(
                        continueLabels.get(label)));
            }

            return null;
        }

        @Override
        public Node visitDoWhileLoop(DoWhileLoopTree tree, Void p) {
            assert !conditionalMode;

            Name parentLabel = getLabel(getCurrentPath());

            Label loopEntry = new Label();
            Label loopExit = new Label();

            // If the loop is a labeled statement, then its continue
            // target is identical for continues with no label and
            // continues with the loop's label.
            Label conditionStart;
            if (parentLabel != null) {
                conditionStart = continueLabels.get(parentLabel);
            } else {
                conditionStart = new Label();
            }

            Label oldBreakTargetL = breakTargetL;
            breakTargetL = loopExit;

            Label oldContinueTargetL = continueTargetL;
            continueTargetL = conditionStart;

            // Loop body
            addLabelForNextNode(loopEntry);
            if (tree.getStatement() != null) {
                scan(tree.getStatement(), p);
            }

            // Condition
            addLabelForNextNode(conditionStart);
            conditionalMode = true;
            thenTargetL = loopEntry;
            elseTargetL = loopExit;
            if (tree.getCondition() != null) {
                unbox(scan(tree.getCondition(), p));
            }
            conditionalMode = false;

            // Loop exit
            addLabelForNextNode(loopExit);

            breakTargetL = oldBreakTargetL;
            continueTargetL = oldContinueTargetL;

            return null;
        }

        @Override
        public Node visitErroneous(ErroneousTree tree, Void p) {
            assert false : "ErroneousTree is unexpected in AST to CFG translation";
            return null;
        }

        @Override
        public Node visitExpressionStatement(ExpressionStatementTree tree,
                Void p) {
            return scan(tree.getExpression(), p);
        }

        @Override
        public Node visitEnhancedForLoop(EnhancedForLoopTree tree, Void p) {
            // see JLS 14.14.2
            assert !conditionalMode;

            Name parentLabel = getLabel(getCurrentPath());

            Label conditionStart = new Label();
            Label loopEntry = new Label();
            Label loopExit = new Label();

            // If the loop is a labeled statement, then its continue
            // target is identical for continues with no label and
            // continues with the loop's label.
            Label updateStart;
            if (parentLabel != null) {
                updateStart = continueLabels.get(parentLabel);
            } else {
                updateStart = new Label();
            }

            Label oldBreakTargetL = breakTargetL;
            breakTargetL = loopExit;

            Label oldContinueTargetL = continueTargetL;
            continueTargetL = updateStart;

            // Distinguish loops over Iterables from loops over arrays.

            TypeElement iterableElement = elements.getTypeElement("java.lang.Iterable");
            TypeMirror iterableType = types.erasure(iterableElement.asType());

            VariableTree variable = tree.getVariable();
            VariableElement variableElement =
                TreeUtils.elementFromDeclaration(variable);
            ExpressionTree expression = tree.getExpression();
            StatementTree statement = tree.getStatement();

            TypeMirror exprType = InternalUtils.typeOf(expression);

            if (types.isSubtype(exprType, iterableType)) {
                // Take the upper bound of a type variable or wildcard
                exprType = TypesUtils.upperBound(exprType);

                assert (exprType instanceof DeclaredType) : "an Iterable must be a DeclaredType";
                DeclaredType declaredExprType = (DeclaredType) exprType;
                declaredExprType.getTypeArguments();

                MemberSelectTree iteratorSelect =
                    treeBuilder.buildIteratorMethodAccess(expression);

                MethodInvocationTree iteratorCall =
                    treeBuilder.buildMethodInvocation(iteratorSelect);

                AnnotatedTypeMirror annotatedIteratorType =
                    factory.getAnnotatedType(iteratorCall);

                Tree annotatedIteratorTypeTree =
                    treeBuilder.buildAnnotatedType(annotatedIteratorType);

                // Declare and initialize a new, unique iterator variable
                VariableTree iteratorVariable =
                    treeBuilder.buildVariableDecl(annotatedIteratorTypeTree,
                                                  uniqueName("iter"),
                                                  variableElement.getEnclosingElement(),
                                                  iteratorCall);

                extendWithNode(new VariableDeclarationNode(iteratorVariable));

                Node expressionNode = scan(expression, p);

                MethodAccessNode iteratorAccessNode =
                    extendWithNode(new MethodAccessNode(iteratorSelect, expressionNode));
                MethodInvocationNode iteratorCallNode =
                    extendWithNode(new MethodInvocationNode(iteratorCall, iteratorAccessNode,
                                                            Collections.<Node>emptyList(), getCurrentPath()));

                translateAssignment(iteratorVariable,
                                    new LocalVariableNode(iteratorVariable),
                                    iteratorCallNode);

                // Test the loop ending condition
                addLabelForNextNode(conditionStart);
                IdentifierTree iteratorUse1 =
                    treeBuilder.buildVariableUse(iteratorVariable);

                LocalVariableNode iteratorReceiverNode =
                    extendWithNode(new LocalVariableNode(iteratorUse1));

                MemberSelectTree hasNextSelect =
                    treeBuilder.buildHasNextMethodAccess(iteratorUse1);

                MethodAccessNode hasNextAccessNode =
                    extendWithNode(new MethodAccessNode(hasNextSelect, iteratorReceiverNode));

                MethodInvocationTree hasNextCall =
                    treeBuilder.buildMethodInvocation(hasNextSelect);

                extendWithNode(new MethodInvocationNode(hasNextCall, hasNextAccessNode,
                                                        Collections.<Node>emptyList(), getCurrentPath()));
                extendWithExtendedNode(new ConditionalJump(loopEntry, loopExit));

                // Loop body, starting with declaration of the loop iteration variable
                addLabelForNextNode(loopEntry);
                extendWithNode(new VariableDeclarationNode(variable));

                IdentifierTree iteratorUse2 =
                    treeBuilder.buildVariableUse(iteratorVariable);

                LocalVariableNode iteratorReceiverNode2 =
                    extendWithNode(new LocalVariableNode(iteratorUse2));

                MemberSelectTree nextSelect =
                    treeBuilder.buildNextMethodAccess(iteratorUse2);

                MethodAccessNode nextAccessNode =
                    extendWithNode(new MethodAccessNode(nextSelect, iteratorReceiverNode2));

                MethodInvocationTree nextCall =
                    treeBuilder.buildMethodInvocation(nextSelect);

                extendWithNode(new MethodInvocationNode(nextCall, nextAccessNode,
                    Collections.<Node>emptyList(), getCurrentPath()));

                translateAssignment(variable,
                                    new LocalVariableNode(variable),
                                    nextCall);

                if (statement != null) {
                    scan(statement, p);
                }

                // Loop back edge
                addLabelForNextNode(updateStart);
                extendWithExtendedNode(new UnconditionalJump(conditionStart));

            } else {
                // TODO: Shift any labels after the initialization of the
                // temporary array variable.

                AnnotatedTypeMirror annotatedArrayType =
                    factory.getAnnotatedType(expression);

                assert (annotatedArrayType instanceof AnnotatedTypeMirror.AnnotatedArrayType) :
                    "ArrayType must be represented by AnnotatedArrayType";

                Tree annotatedArrayTypeTree =
                    treeBuilder.buildAnnotatedType(annotatedArrayType);

                // Declare and initialize a temporary array variable
                VariableTree arrayVariable =
                    treeBuilder.buildVariableDecl(annotatedArrayTypeTree,
                                                  uniqueName("array"),
                                                  variableElement.getEnclosingElement(),
                                                  expression);

                extendWithNode(new VariableDeclarationNode(arrayVariable));
                Node expressionNode = scan(expression, p);

                translateAssignment(arrayVariable,
                                    new LocalVariableNode(arrayVariable),
                                    expressionNode);

                // Declare and initialize the loop index variable
                TypeMirror intType = types.getPrimitiveType(TypeKind.INT);

                LiteralTree zero =
                    treeBuilder.buildLiteral(new Integer(0));

                VariableTree indexVariable =
                    treeBuilder.buildVariableDecl(intType,
                                                  uniqueName("index"),
                                                  variableElement.getEnclosingElement(),
                                                  zero);
                extendWithNode(new VariableDeclarationNode(indexVariable));
                IntegerLiteralNode zeroNode =
                    extendWithNode(new IntegerLiteralNode(zero));

                translateAssignment(indexVariable,
                                    new LocalVariableNode(indexVariable),
                                    zeroNode);

                // Compare index to array length
                addLabelForNextNode(conditionStart);
                IdentifierTree indexUse1 =
                    treeBuilder.buildVariableUse(indexVariable);
                LocalVariableNode indexNode1 =
                    extendWithNode(new LocalVariableNode(indexUse1));

                IdentifierTree arrayUse1 =
                    treeBuilder.buildVariableUse(arrayVariable);
                LocalVariableNode arrayNode1 =
                    extendWithNode(new LocalVariableNode(arrayUse1));

                MemberSelectTree lengthSelect =
                    treeBuilder.buildArrayLengthAccess(arrayUse1);
                FieldAccessNode lengthAccessNode =
                    extendWithNode(new FieldAccessNode(lengthSelect, arrayNode1));

                BinaryTree lessThan =
                    treeBuilder.buildLessThan(indexUse1, lengthSelect);
                extendWithNode(new LessThanNode(lessThan, indexNode1, lengthAccessNode));
                extendWithExtendedNode(new ConditionalJump(loopEntry, loopExit));

                // Loop body, starting with declaration of the loop iteration variable
                addLabelForNextNode(loopEntry);
                extendWithNode(new VariableDeclarationNode(variable));

                IdentifierTree arrayUse2 =
                    treeBuilder.buildVariableUse(arrayVariable);
                LocalVariableNode arrayNode2 =
                    extendWithNode(new LocalVariableNode(arrayUse2));

                IdentifierTree indexUse2 =
                    treeBuilder.buildVariableUse(indexVariable);
                LocalVariableNode indexNode2 =
                    extendWithNode(new LocalVariableNode(indexUse2));

                ArrayAccessTree arrayAccess =
                    treeBuilder.buildArrayAccess(arrayUse2, indexUse2);
                ArrayAccessNode arrayAccessNode =
                    extendWithNode(new ArrayAccessNode(arrayAccess, arrayNode2,
                                                       indexNode2));
                translateAssignment(variable,
                                    new LocalVariableNode(variable),
                                    arrayAccessNode);

                if (statement != null) {
                    scan(statement, p);
                }

                // Loop back edge
                addLabelForNextNode(updateStart);

                IdentifierTree indexUse3 =
                    treeBuilder.buildVariableUse(indexVariable);
                LocalVariableNode indexNode3 =
                    extendWithNode(new LocalVariableNode(indexUse3));

                UnaryTree postfixIncrement =
                    treeBuilder.buildPostfixIncrement(indexUse3);
                extendWithNode(new PostfixIncrementNode(postfixIncrement,
                                                            indexNode3));
                extendWithExtendedNode(new UnconditionalJump(conditionStart));
            }

            // Loop exit
            addLabelForNextNode(loopExit);

            breakTargetL = oldBreakTargetL;
            continueTargetL = oldContinueTargetL;

            return null;
        }

        @Override
        public Node visitForLoop(ForLoopTree tree, Void p) {
            assert !conditionalMode;

            Name parentLabel = getLabel(getCurrentPath());

            Label conditionStart = new Label();
            Label loopEntry = new Label();
            Label loopExit = new Label();

            // If the loop is a labeled statement, then its continue
            // target is identical for continues with no label and
            // continues with the loop's label.
            Label updateStart;
            if (parentLabel != null) {
                updateStart = continueLabels.get(parentLabel);
            } else {
                updateStart = new Label();
            }

            Label oldBreakTargetL = breakTargetL;
            breakTargetL = loopExit;

            Label oldContinueTargetL = continueTargetL;
            continueTargetL = updateStart;

            // Initializer
            for (StatementTree init : tree.getInitializer()) {
                scan(init, p);
            }

            // Condition
            addLabelForNextNode(conditionStart);
            conditionalMode = true;
            thenTargetL = loopEntry;
            elseTargetL = loopExit;
            if (tree.getCondition() != null) {
                unbox(scan(tree.getCondition(), p));
            }
            conditionalMode = false;

            // Loop body
            addLabelForNextNode(loopEntry);
            if (tree.getStatement() != null) {
                scan(tree.getStatement(), p);
            }

            // Update
            addLabelForNextNode(updateStart);
            for (ExpressionStatementTree update : tree.getUpdate()) {
                scan(update, p);
            }

            extendWithExtendedNode(new UnconditionalJump(conditionStart));

            // Loop exit
            addLabelForNextNode(loopExit);

            breakTargetL = oldBreakTargetL;
            continueTargetL = oldContinueTargetL;

            return null;
        }

        @Override
        public Node visitIdentifier(IdentifierTree tree, Void p) {
            Node node;
            if (TreeUtils.isFieldAccess(tree)) {
                Node receiver = getReceiver(tree,
                        TreeUtils.enclosingClass(getCurrentPath()));
                node = new FieldAccessNode(tree, receiver);
            } else {
                Element element = TreeUtils.elementFromUse(tree);
                switch (element.getKind()) {
                case ANNOTATION_TYPE:
                case CLASS:
                case ENUM:
                case INTERFACE:
                    node = new ClassNameNode(tree);
                    break;
                case EXCEPTION_PARAMETER:
                case FIELD:
                    // Note that "this" is a field, but not a field access.
                    node = new ExplicitThisLiteralNode(tree);
                    break;
                case LOCAL_VARIABLE:
                case PARAMETER:
                    node = new LocalVariableNode(tree);
                    break;
                case PACKAGE:
                    node = new PackageNameNode(tree);
                    break;
                default:
                    throw new IllegalArgumentException(
                            "unexpected element kind : " + element.getKind());
                }
            }
            extendWithNode(node);
            if (conditionalMode) {
                extendWithExtendedNode(new ConditionalJump(thenTargetL,
                        elseTargetL));
            }
            return node;
        }

        @Override
        public Node visitIf(IfTree tree, Void p) {

            assert !conditionalMode;

            // all necessary labels
            Label thenEntry = new Label();
            Label elseEntry = new Label();
            Label endIf = new Label();

            // basic block for the condition
            conditionalMode = true;
            thenTargetL = thenEntry;
            elseTargetL = elseEntry;
            tree.getCondition().accept(this, null);
            conditionalMode = false;

            // then branch
            addLabelForNextNode(thenEntry);
            StatementTree thenStatement = tree.getThenStatement();
            thenStatement.accept(this, null);
            extendWithExtendedNode(new UnconditionalJump(endIf));

            // else branch
            addLabelForNextNode(elseEntry);
            StatementTree elseStatement = tree.getElseStatement();
            if (elseStatement != null) {
                elseStatement.accept(this, null);
            }

            // label the end of the if statement
            addLabelForNextNode(endIf);

            return null;
        }

        @Override
        public Node visitImport(ImportTree tree, Void p) {
            assert false : "ImportTree is unexpected in AST to CFG translation";
            return null;
        }

        @Override
        public Node visitArrayAccess(ArrayAccessTree tree, Void p) {
            Node array = scan(tree.getExpression(), p);
            Node index = unaryNumericPromotion(scan(tree.getIndex(), p));
            return extendWithNode(new ArrayAccessNode(tree, array, index));
        }

        @Override
        public Node visitLabeledStatement(LabeledStatementTree tree, Void p) {
            // This method can set the break target after generating all Nodes
            // in the contained statement, but it can't set the continue target,
            // which may be in the middle of a sequence of nodes. Labeled loops
            // must look up and use the continue Labels.
            Name labelName = tree.getLabel();

            Label breakL = new Label(labelName + "_break");
            Label continueL = new Label(labelName + "_continue");

            breakLabels.put(labelName, breakL);
            continueLabels.put(labelName, continueL);

            scan(tree.getStatement(), p);

            addLabelForNextNode(breakL);

            breakLabels.remove(labelName);
            continueLabels.remove(labelName);

            return null;
        }

        @Override
        public Node visitLiteral(LiteralTree tree, Void p) {
            Node r = null;
            switch (tree.getKind()) {
            case BOOLEAN_LITERAL:
                r = new BooleanLiteralNode(tree);
                break;
            case CHAR_LITERAL:
                r = new CharacterLiteralNode(tree);
                break;
            case DOUBLE_LITERAL:
                r = new DoubleLiteralNode(tree);
                break;
            case FLOAT_LITERAL:
                r = new FloatLiteralNode(tree);
                break;
            case INT_LITERAL:
                r = new IntegerLiteralNode(tree);
                break;
            case LONG_LITERAL:
                r = new LongLiteralNode(tree);
                break;
            case NULL_LITERAL:
                r = new NullLiteralNode(tree);
                break;
            case STRING_LITERAL:
                r = new StringLiteralNode(tree);
                break;
            }
            assert r != null : "unexpected literal tree";
            Node result = extendWithNode(r);
            if (conditionalMode) {
                extendWithExtendedNode(new ConditionalJump(thenTargetL,
                        elseTargetL));
            }
            return result;
        }

        @Override
        public Node visitMethod(MethodTree tree, Void p) {
            assert false : "MethodTree is unexpected in AST to CFG translation";
            return null;
        }

        @Override
        public Node visitModifiers(ModifiersTree tree, Void p) {
            assert false : "ModifiersTree is unexpected in AST to CFG translation";
            return null;
        }

        @Override
        public Node visitNewArray(NewArrayTree tree, Void p) {
            // see JLS 15.10

            List<? extends ExpressionTree> dimensions = tree.getDimensions();
            List<? extends ExpressionTree> initializers = tree
                    .getInitializers();

            List<Node> dimensionNodes = new ArrayList<Node>();
            if (dimensions != null) {
                for (ExpressionTree dim : dimensions) {
                    dimensionNodes.add(unaryNumericPromotion(scan(dim, p)));
                }
            }

            List<Node> initializerNodes = new ArrayList<Node>();
            if (initializers != null) {
                for (ExpressionTree init : initializers) {
                    initializerNodes.add(scan(init, p));
                }
            }

            TypeMirror type = tree == null ? null : InternalUtils.typeOf(tree);
            Node node = new ArrayCreationNode(tree, type, dimensionNodes,
                    initializerNodes);
            return extendWithNode(node);
        }

        @Override
        public Node visitNewClass(NewClassTree tree, Void p) {
            // see JLS 15.9

            Tree enclosingExpr = tree.getEnclosingExpression();
            if (enclosingExpr != null) {
                scan(enclosingExpr, p);
            }

            // We ignore any class body because its methods should
            // be visited separately.

            // Convert constructor arguments
            ExecutableElement constructor = TreeUtils.elementFromUse(tree);

            List<? extends ExpressionTree> actualExprs = tree.getArguments();

            List<Node> arguments = convertCallArguments(constructor,
                    actualExprs);

            Node constructorNode = scan(tree.getIdentifier(), p);

            Node node = new ObjectCreationNode(tree, constructorNode, arguments);
            return extendWithNode(node);
        }

        @Override
        public Node visitParenthesized(ParenthesizedTree tree, Void p) {
            return scan(tree.getExpression(), p);
        }

        @Override
        public Node visitReturn(ReturnTree tree, Void p) {
            ExpressionTree ret = tree.getExpression();
            // TODO: also have a return-node if nothing is returned
            ReturnNode result = null;
            if (ret != null) {
                Node node = scan(ret, p);
                result = new ReturnNode(tree, node);
                returnNodes.add(result);
                extendWithNode(result);
            }
            extendWithExtendedNode(new UnconditionalJump(regularExitLabel));
            // TODO: return statements should also flow to an enclosing finally block
            return result;
        }

        @Override
        public Node visitMemberSelect(MemberSelectTree tree, Void p) {
            Node expr = scan(tree.getExpression(), p);
            if (!TreeUtils.isFieldAccess(tree)) {
                // Could be a selector of a class or package
                Element element = TreeUtils.elementFromUse(tree);
                switch (element.getKind()) {
                case ANNOTATION_TYPE:
                case CLASS:
                case ENUM:
                case INTERFACE:
                    return extendWithNode(new ClassNameNode(tree, expr));
                case PACKAGE:
                    return extendWithNode(new PackageNameNode(tree, (PackageNameNode) expr));
                default:
                    assert false : "Unexpected element kind: " + element.getKind();
                    return null;
                }
            }

            Node node = new FieldAccessNode(tree, expr);

            Element element = TreeUtils.elementFromUse(tree);
            if (ElementUtils.isStatic(element) ||
                expr instanceof ImplicitThisLiteralNode ||
                expr instanceof ExplicitThisLiteralNode) {
                // No NullPointerException can be thrown, use normal node
                extendWithNode(node);
            } else {
                TypeElement npeElement = elements
                    .getTypeElement("java.lang.NullPointerException");
                extendWithNodeWithException(node, npeElement.asType());
            }

            return node;
        }

        @Override
        public Node visitEmptyStatement(EmptyStatementTree tree, Void p) {
            return null;
        }

        @Override
        public Node visitSwitch(SwitchTree tree, Void p) {
            switchExpr = unbox(scan(tree.getExpression(), p));

            extendWithNode(new MarkerNode(tree, "start of switch statement"));

            Label oldBreakTargetL = breakTargetL;
            breakTargetL = new Label();

            for (CaseTree caseTree : tree.getCases()) {
                scan(caseTree, p);
            }

            addLabelForNextNode(breakTargetL);

            breakTargetL = oldBreakTargetL;

            return null;
        }

        @Override
        public Node visitSynchronized(SynchronizedTree tree, Void p) {
            // see JLS 14.19

            scan(tree.getExpression(), p);

            extendWithNode(new MarkerNode(tree, "start of synchronized block"));

            scan(tree.getBlock(), p);

            extendWithNode(new MarkerNode(tree, "end of synchronized block"));

            return null;
        }

        @Override
        public Node visitThrow(ThrowTree tree, Void p) {
            Node expression = scan(tree.getExpression(), p);
            TypeMirror exception = expression.getType();
            ThrowNode throwsNode = new ThrowNode(tree, expression);
            NodeWithExceptionsHolder exNode = extendWithNodeWithException(
                    throwsNode, exception);
            exNode.setTerminatesExecution(true);
            return throwsNode;
        }

        @Override
        public Node visitCompilationUnit(CompilationUnitTree tree, Void p) {
            assert false : "CompilationUnitTree is unexpected in AST to CFG translation";
            return null;
        }

        @Override
        public Node visitTry(TryTree tree, Void p) {
            List<? extends CatchTree> catches = tree.getCatches();
            BlockTree finallyBlock = tree.getFinallyBlock();

            extendWithNode(new MarkerNode(tree, "start of try statement"));

            // TODO: Handle try-with-resources blocks.
            // List<? extends Tree> resources = tree.getResources();

            List<Pair<TypeMirror, Label>> catchLabels = new ArrayList<>();
            for (CatchTree c : catches) {
                TypeMirror type = InternalUtils.typeOf(c.getParameter().getType());
                assert type != null : "exception parameters must have a type";
                catchLabels.add(Pair.of(type, new Label()));
            }

            Label finallyLabel = null;
            if (finallyBlock != null) {
                finallyLabel = new Label();
                tryStack.pushFrame(new TryFinallyFrame(finallyLabel));
            }

            Label doneLabel = new Label();

            tryStack.pushFrame(new TryCatchFrame(types, catchLabels));

            scan(tree.getBlock(), p);
            extendWithExtendedNode(new UnconditionalJump(doneLabel));

            tryStack.popFrame();

            int catchIndex = 0;
            for (CatchTree c : catches) {
                addLabelForNextNode(catchLabels.get(catchIndex).second);
                scan(c, p);
                catchIndex++;

                if (finallyLabel != null) {
                    // Normal completion of the catch block flows to the finally block.
                    extendWithExtendedNode(new UnconditionalJump(finallyLabel));
                } else {
                    extendWithExtendedNode(new UnconditionalJump(doneLabel));
                }
            }

            if (finallyLabel != null) {
                tryStack.popFrame();
                addLabelForNextNode(finallyLabel);
                scan(finallyBlock, p);

                TypeMirror throwableType =
                    elements.getTypeElement("java.lang.Throwable").asType();
                extendWithNodeWithException(new MarkerNode(tree, "end of finally block"),
                                            throwableType);
            }

            addLabelForNextNode(doneLabel);

            return null;
        }

        @Override
        public Node visitParameterizedType(ParameterizedTypeTree tree, Void p) {
            return extendWithNode(new ParameterizedTypeNode(tree));
        }

        @Override
        public Node visitUnionType(UnionTypeTree tree, Void p) {
            assert false : "UnionTypeTree is unexpected in AST to CFG translation";
            return null;
        }

        @Override
        public Node visitArrayType(ArrayTypeTree tree, Void p) {
            assert !conditionalMode;
            return extendWithNode(new ArrayTypeNode(tree));
        }

        @Override
        public Node visitTypeCast(TypeCastTree tree, Void p) {
            assert !conditionalMode;

            Node operand = scan(tree.getExpression(), p);
            TypeMirror type = InternalUtils.typeOf(tree.getType());

            return extendWithNode(new TypeCastNode(tree, operand, type));
        }

        @Override
        public Node visitPrimitiveType(PrimitiveTypeTree tree, Void p) {
            assert !conditionalMode;
            return extendWithNode(new PrimitiveTypeNode(tree));
        }

        @Override
        public Node visitTypeParameter(TypeParameterTree tree, Void p) {
            assert false : "TypeParameterTree is unexpected in AST to CFG translation";
            return null;
        }

        @Override
        public Node visitInstanceOf(InstanceOfTree tree, Void p) {
            ConditionalJump cjump = null;
            if (conditionalMode) {
                cjump = new ConditionalJump(thenTargetL, elseTargetL);
            }
            boolean outerConditionalMode = conditionalMode;
            conditionalMode = false;

            Node operand = scan(tree.getExpression(), p);
            TypeMirror refType = InternalUtils.typeOf(tree.getType());
            InstanceOfNode node = new InstanceOfNode(tree, operand, refType,
                    types);
            extendWithNode(node);

            conditionalMode = outerConditionalMode;

            if (conditionalMode) {
                extendWithExtendedNode(cjump);
            }
            return node;
        }

        @Override
        public Node visitUnary(UnaryTree tree, Void p) {
            Node result = null;
            ConditionalJump cjump = null;
            if (conditionalMode) {
                cjump = new ConditionalJump(thenTargetL, elseTargetL);
            }
            boolean outerConditionalMode = conditionalMode;
            conditionalMode = false;

            Node expr = scan(tree.getExpression(), p);

            Tree.Kind kind = tree.getKind();
            switch (kind) {
            case BITWISE_COMPLEMENT:
            case POSTFIX_DECREMENT:
            case POSTFIX_INCREMENT:
            case PREFIX_DECREMENT:
            case PREFIX_INCREMENT:
            case UNARY_MINUS:
            case UNARY_PLUS: {
                // see JLS 15.14 and 15.15
                expr = unaryNumericPromotion(expr);

                TypeMirror exprType = InternalUtils.typeOf(tree);

                switch (kind) {
                case BITWISE_COMPLEMENT:
                    result = extendWithNode(new BitwiseComplementNode(tree,
                            expr));
                    break;
                case POSTFIX_DECREMENT: {
                    Node node = extendWithNode(new PostfixDecrementNode(tree,
                            expr));
                    result = narrowAndBox(node, exprType);
                    break;
                }
                case POSTFIX_INCREMENT: {
                    Node node = extendWithNode(new PostfixIncrementNode(tree,
                            expr));
                    result = narrowAndBox(node, exprType);
                    break;
                }
                case PREFIX_DECREMENT: {
                    Node node = extendWithNode(new PrefixDecrementNode(tree,
                            expr));
                    result = narrowAndBox(node, exprType);
                    break;
                }
                case PREFIX_INCREMENT: {
                    Node node = extendWithNode(new PrefixIncrementNode(tree,
                            expr));
                    result = narrowAndBox(node, exprType);
                    break;
                }
                case UNARY_MINUS:
                    result = extendWithNode(new NumericalMinusNode(tree, expr));
                    break;
                case UNARY_PLUS:
                    result = extendWithNode(new NumericalPlusNode(tree, expr));
                    break;
                }
            }
                break;
            case LOGICAL_COMPLEMENT: {
                // see JLS 15.15.6
                result = extendWithNode(new ConditionalNotNode(tree,
                        unbox(expr)));
                if (conditionalMode) {
                    extendWithExtendedNode(new ConditionalJump(thenTargetL,
                            elseTargetL));
                }
                break;
            }

            default:
                assert false : "Unknown kind of unary expression";
            }

            conditionalMode = outerConditionalMode;

            if (conditionalMode) {
                extendWithExtendedNode(cjump);
            }

            return result;
        }

        @Override
        public Node visitVariable(VariableTree tree, Void p) {

            // see JLS 14.4

            // local variable definition
            VariableDeclarationNode decl = new VariableDeclarationNode(tree);
            extendWithNode(decl);

            // initializer
            Node node = null;
            ExpressionTree initializer = tree.getInitializer();
            if (initializer != null) {
                node = translateAssignment(tree, new LocalVariableNode(tree),
                        initializer);
            }

            return node;
        }

        @Override
        public Node visitWhileLoop(WhileLoopTree tree, Void p) {
            assert !conditionalMode;

            Name parentLabel = getLabel(getCurrentPath());

            Label loopEntry = new Label();
            Label loopExit = new Label();

            // If the loop is a labeled statement, then its continue
            // target is identical for continues with no label and
            // continues with the loop's label.
            Label conditionStart;
            if (parentLabel != null) {
                conditionStart = continueLabels.get(parentLabel);
            } else {
                conditionStart = new Label();
            }

            Label oldBreakTargetL = breakTargetL;
            breakTargetL = loopExit;

            Label oldContinueTargetL = continueTargetL;
            continueTargetL = conditionStart;

            // Condition
            addLabelForNextNode(conditionStart);
            conditionalMode = true;
            thenTargetL = loopEntry;
            elseTargetL = loopExit;
            if (tree.getCondition() != null) {
                unbox(scan(tree.getCondition(), p));
            }
            conditionalMode = false;

            // Loop body
            addLabelForNextNode(loopEntry);
            if (tree.getStatement() != null) {
                scan(tree.getStatement(), p);
            }
            extendWithExtendedNode(new UnconditionalJump(conditionStart));

            // Loop exit
            addLabelForNextNode(loopExit);

            breakTargetL = oldBreakTargetL;
            continueTargetL = oldContinueTargetL;

            return null;
        }

        @Override
        public Node visitWildcard(WildcardTree tree, Void p) {
            assert false : "WildcardTree is unexpected in AST to CFG translation";
            return null;
        }

        @Override
        public Node visitOther(Tree tree, Void p) {
            assert false : "Unknown AST element encountered in AST to CFG translation.";
            return null;
        }

        @Override
        public Node visitLambdaExpression(LambdaExpressionTree node, Void p) {
            assert false : "Lambda expressions not yet handled in AST to CFG translation.";
            return null;
        }

        @Override
        public Node visitMemberReference(MemberReferenceTree node, Void p) {
            assert false : "Member references not yet handled in AST to CFG translation.";
            return null;
        }
    }

    /* --------------------------------------------------------- */
    /* Utility routines for debugging CFG building */
    /* --------------------------------------------------------- */

    /**
     * Print a set of {@link Block}s and the edges between them. This is useful
     * for examining the results of phase two.
     */
    protected void printBlocks(Set<Block> blocks) {
        for (Block b : blocks) {
            System.out.print(b.hashCode() + ": " + b);
            switch (b.getType()) {
            case REGULAR_BLOCK:
            case EXCEPTION_BLOCK:
            case SPECIAL_BLOCK: {
                Block succ = ((SingleSuccessorBlockImpl) b).getSuccessor();
                System.out.println(" -> "
                        + (succ != null ? succ.hashCode() : "||"));
                break;
            }
            case CONDITIONAL_BLOCK: {
                Block tSucc = ((ConditionalBlockImpl) b).getThenSuccessor();
                Block eSucc = ((ConditionalBlockImpl) b).getElseSuccessor();
                System.out.println(" -> T "
                        + (tSucc != null ? tSucc.hashCode() : "||") + " F "
                        + (eSucc != null ? eSucc.hashCode() : "||"));
                break;
            }
            }
        }
    }
}
