package checkers.flow.cfg.node;

import java.util.Collection;
import java.util.Collections;

import checkers.flow.util.HashCodeUtils;
import checkers.util.InternalUtils;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.Tree.Kind;

/**
 * A node for a conditional not expression:
 * 
 * <pre>
 *   ! <em>expression</em>
 * </pre>
 * 
 * @author Stefan Heule
 * @author Charlie Garrett
 * 
 */
public class ConditionalNotNode extends Node {

    protected BinaryTree tree;
    protected Node operand;

    public ConditionalNotNode(BinaryTree tree, Node operand) {
        assert tree.getKind().equals(Kind.LOGICAL_COMPLEMENT);
        this.tree = tree;
        this.type = InternalUtils.typeOf(tree);
        this.operand = operand;
    }

    public Node getOperand() {
        return operand;
    }

    @Override
    public BinaryTree getTree() {
        return tree;
    }

    @Override
    public <R, P> R accept(NodeVisitor<R, P> visitor, P p) {
        return visitor.visitConditionalNot(this, p);
    }

    @Override
    public String toString() {
        return "(!" + getOperand() + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof ConditionalNotNode)) {
            return false;
        }
        ConditionalNotNode other = (ConditionalNotNode) obj;
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
