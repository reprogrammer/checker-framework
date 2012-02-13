package checkers.flow.cfg.node;

import java.util.Collection;
import java.util.Collections;

import checkers.flow.util.HashCodeUtils;

import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;

/**
 * A node for the postfix decrement operations:
 * 
 * <pre>
 *   <em>expression</em>--
 * </pre>
 * 
 * @author Stefan Heule
 * @author Charlie Garrett
 * 
 */
public class PostfixDecrementNode extends Node {

	protected Tree tree;
	protected Node operand;

	public PostfixDecrementNode(Tree tree, Node operand) {
		assert tree.getKind() == Kind.POSTFIX_DECREMENT;
		this.tree = tree;
		this.operand = operand;
	}

	public Node getOperand() {
		return operand;
	}

	@Override
	public Tree getTree() {
		return tree;
	}

	@Override
	public <R, P> R accept(NodeVisitor<R, P> visitor, P p) {
		return visitor.visitPostfixDecrement(this, p);
	}

	@Override
	public String toString() {
		return "(" + getOperand() + "--)";
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof PostfixDecrementNode)) {
			return false;
		}
		PostfixDecrementNode other = (PostfixDecrementNode) obj;
		return getOperand().equals(other.getOperand());
	}
	
	@Override
	public int hashCode() {
		return HashCodeUtils.hash(getOperand());
	}

	@Override
	public Collection<Node> getOperands() {
		return Collections.singletonList(getOperand());
	}

	@Override
	public boolean hasResult() {
		return true;
	}
}
