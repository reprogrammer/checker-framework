package checkers.flow.cfg.block;

import java.util.LinkedList;
import java.util.List;

import checkers.flow.cfg.node.Node;

/**
 * Implementation of a regular basic block.
 * 
 * @author Stefan Heule
 * 
 */
public class RegularBlockImpl extends SingleSuccessorBlockImpl implements RegularBlock {

	/** Internal representation of the contents. */
	protected List<Node> contents;
	
	/**
	 * Initialize an empty basic block to be filled with contents and linked to
	 * other basic blocks later.
	 */
	public RegularBlockImpl() {
		contents = new LinkedList<>();
		type = BlockType.REGULAR_BLOCK;
	}

	/**
	 * Add a statement to the contents of this basic block.
	 */
	public void addStatement(Node t) {
		contents.add(t);
		t.setBlock(this);
	}

	/**
	 * Add multiple statements to the contents of this basic block.
	 */
	public void addStatements(List<? extends Node> ts) {
		contents.addAll(ts);
	}
	
	@Override
	public List<Node> getContents() {
		return new LinkedList<Node>(contents);
	}

	@Override
	public BlockImpl getRegularSuccessor() {
		return successor;
	}

	@Override
	public String toString() {
		return "RegularBlock(" + contents + ")";
	}

}
