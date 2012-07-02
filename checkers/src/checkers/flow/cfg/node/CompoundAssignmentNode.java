package checkers.flow.cfg.node;

import java.util.Collection;
import java.util.LinkedList;

import checkers.flow.util.HashCodeUtils;
import checkers.util.InternalUtils;

import com.sun.source.tree.Tree;

/**
 * A abstract node for compound assignments:
 * 
 * <pre>
 *   <em>variable</em> <em>operator</em>= <em>expression</em>
 * </pre>
 * 
 * @author Stefan Heule
 * @author Charlie Garrett
 * 
 */
public class CompoundAssignmentNode extends Node {

    protected Tree tree;
    protected Node left;
    protected Node right;

    public CompoundAssignmentNode(Tree tree, Node left,
            Node right) {
        this.tree = tree;
        this.type = InternalUtils.typeOf(tree);
        this.left = left;
        this.right = right;
    }

    public Node getLeftOperand() {
        return left;
    }

    public Node getRightOperand() {
        return right;
    }

    @Override
    public Tree getTree() {
        return tree;
    }

    @Override
    public <R, P> R accept(NodeVisitor<R, P> visitor, P p) {
        return visitor.visitCompoundAssignment(this, p);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null
                || !obj.getClass().equals(getClass())) {
            return false;
        }
        CompoundAssignmentNode other = (CompoundAssignmentNode) obj;
        return getLeftOperand().equals(other.getLeftOperand())
                && getRightOperand().equals(other.getRightOperand());
    }

    @Override
    public int hashCode() {
        return HashCodeUtils.hash(getLeftOperand(), getRightOperand());
    }

    @Override
    public Collection<Node> getOperands() {
        LinkedList<Node> list = new LinkedList<Node>();
        list.add(getLeftOperand());
        list.add(getRightOperand());
        return list;
    }
}
