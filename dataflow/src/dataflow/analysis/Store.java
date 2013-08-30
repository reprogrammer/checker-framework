package dataflow.analysis;

/**
 * A store is used to keep track of the information that the dataflow analysis
 * has accumulated at any given point in time.
 *
 * @author Stefan Heule
 *
 * @param <S>
 *            The type of the store returned by {@code copy} and that is used in
 *            {@code leastUpperBound}. Usually it is the implementing class
 *            itself, e.g. in {@code T extends Store<T>}.
 */
public interface Store<S extends Store<S>> {

    // We maintain a then store and an else store before each basic block.
    // When they are identical (by reference equality), they can be treated
    // as a regular unconditional store.
    // Once we have some information for both the then and else store, we
    // create a TransferInput for the block and allow it to be analyzed.
    public static enum Kind {
        THEN,
        ELSE,
        BOTH
    }

    /** @return An exact copy of this store. */
    S copy();

    /**
     * Compute the least upper bound of two stores.
     *
     * <p>
     *
     * <em>Important</em>: This method must fulfill the following contract:
     * <ul>
     * <li>Does not change {@code this}.</li>
     * <li>Does not change {@code other}.</li>
     * <li>Returns a fresh object which is not aliased yet.</li>
     * <li>Returns an object of the same (dynamic) type as {@code this}, even if
     * the signature is more permissive.</li>
     * <li>Is commutative.</li>
     * </ul>
     */
    S leastUpperBound(S other);

    /**
     * Can the objects {@code a} and {@code b} be aliases? Returns a
     * conservative answer (i.e., returns {@code true} if not enough information
     * is available to determine aliasing).
     */
    boolean canAlias(FlowExpressions.Receiver a,
                     FlowExpressions.Receiver b);

    /** @return Whether the Store supports DOT graph output. */
    boolean hasDOToutput();

    /** @return The store encoded as a DOT graph for visualization. */
    String toDOToutput();
}
