package dataflow.analysis;

import java.util.List;

import dataflow.cfg.UnderlyingAST;
import dataflow.cfg.node.LocalVariableNode;
import dataflow.cfg.node.Node;
import dataflow.cfg.node.NodeVisitor;

/**
 * Interface of a transfer function for the abstract interpretation used for the
 * flow analysis.
 * 
 * <p>
 * 
 * A transfer function consists of the following components:
 * <ul>
 * <li>A method {@code initialStore} that determines which initial store should
 * be used in the dataflow analysis.</li>
 * <li>A function for every {@link Node} type that determines the behavior of
 * the dataflow analysis in that case. This method takes a {@link Node} and an
 * incoming store, and produces a {@link RegularTransferResult}.</li>
 * </ul>
 * 
 * <p>
 * 
 * <em>Important</em>: The individual transfer functions ( {@code visit*}) are
 * allowed to use (and modify) the stores contained in the argument passed; the
 * ownership is transfered from the caller to that function.
 * 
 * @author Stefan Heule
 * 
 * @param <S>
 *            The {@link Store} used to keep track of intermediate results.
 */
public interface TransferFunction<A extends AbstractValue<A>, S extends Store<S>>
        extends NodeVisitor<TransferResult<A, S>, TransferInput<A, S>> {

    /**
     * @return The initial store to be used by the dataflow analysis.
     *         {@code parameters} is only set if the underlying AST is a method.
     */
    S initialStore(UnderlyingAST underlyingAST, /* @Nullable */
            List<LocalVariableNode> parameters);
}
