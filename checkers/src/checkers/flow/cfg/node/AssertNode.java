package checkers.flow.cfg.node;

import java.util.Collection;
import java.util.LinkedList;

import checkers.flow.util.HashCodeUtils;
import checkers.util.InternalUtils;

import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;

/**
 * A node for the assert statement:
 * 
 * <pre>
 *   assert <em>condition</em> : <em>detail</em> ;
 * </pre>
 * 
 * @author Stefan Heule
 * @author Charlie Garrett
 * 
 */
public class AssertNode extends Node {

    protected Tree tree;
    protected Node condition;
    protected Node detail;

    public AssertNode(Tree tree, Node condition, Node detail) {
        assert tree.getKind() == Kind.ASSERT;
        this.tree = tree;
        // TODO: Find out the correct "type" for statements.
        // Is it TypeKind.NONE?
        this.type = InternalUtils.typeOf(tree);
        this.condition = condition;
        this.detail = detail;
    }

    public Node getCondition() {
        return condition;
    }

    public Node getDetail() {
        return detail;
    }

    @Override
    public Tree getTree() {
        return tree;
    }

    @Override
    public <R, P> R accept(NodeVisitor<R, P> visitor, P p) {
        return visitor.visitAssert(this, p);
    }

    @Override
    public String toString() {
        return "assert " + getCondition() + ":" + getDetail();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof AssertNode)) {
            return false;
        }
        AssertNode other = (AssertNode) obj;
        return getCondition().equals(other.getCondition()) &&
            getDetail().equals(other.getDetail());
    }

    @Override
    public int hashCode() {
        return HashCodeUtils.hash(getCondition(), getDetail());
    }

    @Override
    public Collection<Node> getOperands() {
        LinkedList<Node> list = new LinkedList<Node>();
        list.add(getCondition());
        list.add(getDetail());
        return list;
    }
}
