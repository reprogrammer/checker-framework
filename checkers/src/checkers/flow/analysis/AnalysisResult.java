package checkers.flow.analysis;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Map.Entry;

import checkers.flow.cfg.block.Block;
import checkers.flow.cfg.block.ExceptionBlock;
import checkers.flow.cfg.block.RegularBlock;
import checkers.flow.cfg.node.Node;

import com.sun.source.tree.Tree;

/**
 * An {@link AnalysisResult} represents the result of a dataflow analysis by
 * providing the abstract values given a node or a tree. Note that it does not
 * keep track of custom results computed by some analysis.
 *
 * @author Stefan Heule
 *
 * @param <A>
 *            type of the abstract value that is tracked.
 */
public class AnalysisResult<A extends AbstractValue<A>, S extends Store<S>> {

    /** Abstract values of nodes. */
    protected final Map<Node, A> nodeValues;

    /** Map from AST {@link Tree}s to {@link Node}s. */
    protected final Map<Tree, Node> treeLookup;

    /**
     * The stores before every method call.
     */
    protected final Map<Block, TransferInput<A, S>> stores;

    /**
     * Initialize with a given node-value mapping.
     */
    public AnalysisResult(Map<Node, A> nodeValues,
            Map<Block, TransferInput<A, S>> stores, Map<Tree, Node> treeLookup) {
        this.nodeValues = new IdentityHashMap<>(nodeValues);
        this.treeLookup = new IdentityHashMap<>(treeLookup);
        this.stores = stores;
    }

    /**
     * Initialize empty result.
     */
    public AnalysisResult() {
        nodeValues = new IdentityHashMap<>();
        treeLookup = new IdentityHashMap<>();
        stores = new IdentityHashMap<>();
    }

    /**
     * Combine with another analysis result.
     */
    public void combine(AnalysisResult<A, S> other) {
        for (Entry<Node, A> e : other.nodeValues.entrySet()) {
            nodeValues.put(e.getKey(), e.getValue());
        }
        for (Entry<Tree, Node> e : other.treeLookup.entrySet()) {
            treeLookup.put(e.getKey(), e.getValue());
        }
        for (Entry<Block, TransferInput<A, S>> e : other.stores.entrySet()) {
            stores.put(e.getKey(), e.getValue());
        }
    }

    /**
     * @return The abstract value for {@link Node} {@code n}, or {@code null} if
     *         no information is available.
     */
    public/* @Nullable */A getValue(Node n) {
        return nodeValues.get(n);
    }

    /**
     * @return The abstract value for {@link Tree} {@code t}, or {@code null} if
     *         no information is available.
     */
    public/* @Nullable */A getValue(Tree t) {
        A val = getValue(treeLookup.get(t));
        return val;
    }

    /**
     * @return The {@link Node} for a given {@link Tree}.
     */
    public/* @Nullable */Node getNodeForTree(Tree tree) {
        return treeLookup.get(tree);
    }

    /**
     * @return The store immediately before a given {@link Tree}.
     */
    public S getStoreBefore(Tree tree) {
        Node node = getNodeForTree(tree);
        return runAnalysisFor(node, true);
    }

    /**
     * @return The store immediately after a given {@link Tree}.
     */
    public S getStoreAfter(Tree tree) {
        Node node = getNodeForTree(tree);
        return runAnalysisFor(node, false);
    }

    /**
     * Runs the analysis again within the block of {@code node} and returns the
     * store at the location of {@code node}. If {@code before} is true, then
     * the store immediately before the {@link Node} {@code node} is returned.
     * Otherwise, the store after {@code node} is returned.
     */
    protected S runAnalysisFor(Node node, boolean before) {
        Block block = node.getBlock();
        TransferInput<A, S> transferInput = stores.get(block);
        Analysis<A, S, ?> analysis = transferInput.analysis;

        switch (block.getType()) {
        case REGULAR_BLOCK: {
            RegularBlock rb = (RegularBlock) block;

            // Apply transfer function to contents until we found the node we
            // are looking for.
            TransferInput<A, S> store = transferInput;
            TransferResult<A, S> transferResult = null;
            for (Node n : rb.getContents()) {
                if (n == node && before) {
                    return store.getRegularStore();
                }
                transferResult = analysis.callTransferFunction(n, store);
                if (n == node) {
                    return transferResult.getRegularStore();
                }
                store = new TransferInput<>(n, analysis, transferResult);
            }
            // This point should never be reached. If the block of 'node' is
            // 'block', then 'node' must be part of the contents of 'block'.
            assert false;
            return null;
        }

        case EXCEPTION_BLOCK: {
            ExceptionBlock eb = (ExceptionBlock) block;

            // apply transfer function to content
            assert eb.getNode() == node;
            if (before) {
                return transferInput.getRegularStore();
            }
            TransferResult<A, S> transferResult = analysis
                    .callTransferFunction(node, transferInput);
            return transferResult.getRegularStore();
        }

        default:
            // Only regular blocks and exceptional blocks can hold nodes.
            assert false;
            break;
        }

        return null;
    }
}
