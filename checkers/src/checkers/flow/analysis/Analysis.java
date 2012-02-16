package checkers.flow.analysis;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.util.Types;

import checkers.flow.cfg.ControlFlowGraph;
import checkers.flow.cfg.block.Block;
import checkers.flow.cfg.block.ConditionalBlock;
import checkers.flow.cfg.block.ExceptionBlock;
import checkers.flow.cfg.block.RegularBlock;
import checkers.flow.cfg.block.SpecialBlock;
import checkers.flow.cfg.node.LocalVariableNode;
import checkers.flow.cfg.node.Node;

import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;

/**
 * An implementation of an iterative algorithm to solve a dataflow problem,
 * given a control flow graph and a transfer function.
 * 
 * @author Stefan Heule
 * 
 * @param <A>
 *            The abstract value type to be tracked by the analysis.
 * @param <S>
 *            The store type used in the analsysis.
 * @param <T>
 *            The transfer function type that is used to approximated runtime
 *            behavior.
 */
public class Analysis<A extends AbstractValue<A>, S extends Store<S>, T extends TransferFunction<A, S>> {

    /** The transfer function for regular nodes. */
    protected T transferFunction;

    /** The control flow graph to perform the analysis on. */
    protected ControlFlowGraph cfg;

    /** The associated processing environment */
    protected final ProcessingEnvironment env;

    /** Instance of the types utility. */
    protected final Types types;

    /**
     * The stores before every basic blocks (assumed to be 'no information' if
     * not present).
     */
    protected Map<Block, TransferInput<A, S>> stores;

    /** The worklist used for the fix-point iteration. */
    protected Queue<Block> worklist;

    /** Abstract values of nodes. */
    protected Map<Node, A> nodeValues;

    /**
     * Construct an object that can perform a dataflow analysis over a control
     * flow graph. The transfer function is set later using
     * {@code setTransferFunction}.
     */
    public Analysis(ProcessingEnvironment env) {
        this.env = env;
        types = env.getTypeUtils();
    }

    /**
     * Construct an object that can perform a dataflow analysis over a control
     * flow graph, given a transfer function.
     */
    public Analysis(ProcessingEnvironment env, T transfer) {
        this(env);
        this.transferFunction = transfer;
    }

    public void setTransferFunction(T transfer) {
        this.transferFunction = transfer;
    }

    public Types getTypes() {
        return types;
    }

    /**
     * Perform the actual analysis. Should only be called once after the object
     * has been created.
     * 
     * @param cfg
     */
    public void performAnalysis(ControlFlowGraph cfg) {
        init(cfg);

        while (!worklist.isEmpty()) {
            Block b = worklist.poll();

            switch (b.getType()) {
            case REGULAR_BLOCK: {
                RegularBlock rb = (RegularBlock) b;

                // apply transfer function to contents
                TransferInput<A, S> storeBefore = getStoreBefore(rb);
                TransferInput<A, S> store = storeBefore.copy();
                TransferResult<A, S> transferResult = null;
                for (Node n : rb.getContents()) {
                    store.node = n;
                    transferResult = n.accept(transferFunction, store);
                    A val = transferResult.getResultValue();
                    if (val != null) {
                        nodeValues.put(n, val);
                    }
                    store = new TransferInput<>(n, nodeValues, transferResult);
                }
                // loop will run at least one, making transferResult non-null

                // propagate store to successors
                Block succ = rb.getSuccessor();
                assert succ != null : "regular basic block without non-exceptional successor unexpected";
                addStoreBefore(succ, store);
                break;
            }

            case EXCEPTION_BLOCK: {
                ExceptionBlock eb = (ExceptionBlock) b;

                // apply transfer function to content
                TransferInput<A, S> storeBefore = getStoreBefore(eb);
                TransferInput<A, S> store = storeBefore.copy();
                Node node = eb.getNode();
                store.node = node;
                TransferResult<A, S> transferResult = node.accept(
                        transferFunction, store);
                A val = transferResult.getResultValue();
                if (val != null) {
                    nodeValues.put(node, val);
                }

                // propagate store to successor
                Block succ = eb.getSuccessor();
                if (succ != null) {
                    addStoreBefore(succ, store);
                }

                // propagate store to exceptional successors
                for (Entry<Class<? extends Throwable>, Block> e : eb
                        .getExceptionalSuccessors().entrySet()) {
                    Block exceptionSucc = e.getValue();
                    Class<? extends Throwable> cause = e.getKey();
                    S exceptionalStore = transferResult
                            .getExceptionalStore(cause);
                    if (exceptionalStore != null) {
                        addStoreBefore(exceptionSucc, new TransferInput<>(node,
                                nodeValues, exceptionalStore));
                    } else {
                        addStoreBefore(exceptionSucc, storeBefore.copy());
                    }
                }
                break;
            }

            case CONDITIONAL_BLOCK: {
                ConditionalBlock cb = (ConditionalBlock) b;

                // get store before
                TransferInput<A, S> storeBefore = getStoreBefore(cb);
                TransferInput<A, S> store = storeBefore.copy();

                // propagate store to successor
                Block thenSucc = cb.getThenSuccessor();
                Block elseSucc = cb.getElseSuccessor();
                addStoreBefore(thenSucc, new TransferInput<>(null, nodeValues,
                        store.getThenStore()));
                addStoreBefore(elseSucc, new TransferInput<>(null, nodeValues,
                        store.getElseStore()));
                break;
            }

            case SPECIAL_BLOCK: {
                // special basic blocks are empty and cannot throw exceptions,
                // thus there is no need to perform any analysis.
                SpecialBlock sb = (SpecialBlock) b;
                Block succ = sb.getSuccessor();
                if (succ != null) {
                    addStoreBefore(succ, getStoreBefore(b));
                }
                break;
            }

            default:
                assert false;
                break;
            }
        }
    }

    /** Initialize the analysis with a new control flow graph. */
    protected void init(ControlFlowGraph cfg) {
        this.cfg = cfg;
        stores = new HashMap<>();
        worklist = new ArrayDeque<>();
        nodeValues = new IdentityHashMap<>();
        worklist.add(cfg.getEntryBlock());

        List<LocalVariableNode> parameters = new ArrayList<>();
        MethodTree tree = cfg.getTree();
        // An alternative approach to handling method parameters is to
        // create VariableDeclarationNodes in the EntryBlock and start
        // with an initially empty Store.
        // TODO: Remove this comment if we decide not to use this
        // alternative.
        for (VariableTree p : tree.getParameters()) {
            LocalVariableNode var = new LocalVariableNode(p);
            parameters.add(var);
            // TODO: document that LocalVariableNode has no block that it
            // belongs to
        }
        stores.put(cfg.getEntryBlock(), new TransferInput<>(null, nodeValues,
                transferFunction.initialStore(tree, parameters)));
    }

    /**
     * Add a basic block to the worklist. If <code>b</code> is already present,
     * the method does nothing.
     */
    protected void addToWorklist(Block b) {
        // TODO: use a more efficient way to check if b is already present
        if (!worklist.contains(b)) {
            worklist.add(b);
        }
    }

    /**
     * Add a store before the basic block <code>b</code> by merging with the
     * existing store for that location.
     */
    protected void addStoreBefore(Block b, TransferInput<A, S> s) {
        TransferInput<A, S> storeBefore = getStoreBefore(b);
        TransferInput<A, S> newStoreBefore;
        if (storeBefore == null) {
            newStoreBefore = s;
        } else {
            newStoreBefore = storeBefore.leastUpperBound(s);
        }
        stores.put(b, newStoreBefore);
        if (storeBefore == null || !storeBefore.equals(newStoreBefore)) {
            addToWorklist(b);
        }
    }

    /**
     * @return The store corresponding to the location right before the basic
     *         block <code>b</code>.
     */
    protected/* @Nullable */TransferInput<A, S> getStoreBefore(Block b) {
        return readFromStore(stores, b);
    }

    /**
     * Read the {@link Store} for a particular basic block from a map of stores
     * (or {@code null} if none exists yet).
     */
    public static <S> /* @Nullable */S readFromStore(Map<Block, S> stores,
            Block b) {
        return stores.get(b);
    }

    public Map<Block, TransferInput<A, S>> getStores() {
        return stores;
    }

    /**
     * @return The abstract value for {@link Node} {@code n}, or {@code null} if
     *         no information is available. Note that if the analysis has not
     *         finished yet, this value might not represent the final value for
     *         this node.
     */
    public/* @Nullable */A getValue(Node n) {
        return nodeValues.get(n);
    }

    /**
     * @return The abstract value for {@link Tree} {@code t}, or {@code null} if
     *         no information is available. Note that if the analysis has not
     *         finished yet, this value might not represent the final value for
     *         this node.
     */
    public/* @Nullable */A getValue(Tree t) {
        return nodeValues.get(cfg.getNodeCorrespondingToTree(t));
    }
}
