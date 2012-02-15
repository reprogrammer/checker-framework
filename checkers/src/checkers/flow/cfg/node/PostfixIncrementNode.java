package checkers.flow.cfg.node;

import java.util.Collection;
import java.util.Collections;

import checkers.flow.util.HashCodeUtils;
import checkers.util.InternalUtils;

import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;

/**
 * A node for the postfix increment operations:
 * 
 * <pre>
 *   ++<em>expression</em>
 * </pre>
 * 
 * @author Stefan Heule
 * @author Charlie Garrett
 * 
 */
public class PostfixIncrementNode extends Node {

	protected Tree tree;
	protected Node operand;

	public PostfixIncrementNode(Tree tree, Node operand) {
		assert tree.getKind() == Kind.POSTFIX_INCREMENT;
		this.tree = tree;
		this.type = InternalUtils.typeOf(tree);
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
		return visitor.visitPostfixIncrement(this, p);
	}

	@Override
	public String toString() {
		return "(" + getOperand() + "++)";
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof PostfixIncrementNode)) {
			return false;
		}
		PostfixIncrementNode other = (PostfixIncrementNode) obj;
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
}
