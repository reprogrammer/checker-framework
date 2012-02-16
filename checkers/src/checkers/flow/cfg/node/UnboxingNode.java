package checkers.flow.cfg.node;

import java.util.Collection;
import java.util.Collections;

import javax.lang.model.type.TypeMirror;

import checkers.flow.util.HashCodeUtils;

import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;

/**
 * A node for the unboxing conversion operation. See JLS 5.1.8 for the
 * definition of unboxing.
 * 
 * An {@link UnboxingNode} does not correspond to any tree node in the parsed
 * AST. It is introduced when a value of reference type appears in a context
 * that requires a primitive type.
 * 
 * Unboxing a null value throws a {@link NullPointerException} while unboxing
 * any other value succeeds.
 * 
 * @author Stefan Heule
 * @author Charlie Garrett
 * 
 */
public class UnboxingNode extends Node {

    protected Node operand;

    public UnboxingNode(Node operand, TypeMirror type) {
        this.operand = operand;
        this.type = type;
    }

    public Node getOperand() {
        return operand;
    }

    @Override
    public Tree getTree() {
        return null;
    }

    @Override
    public <R, P> R accept(NodeVisitor<R, P> visitor, P p) {
        return visitor.visitUnboxing(this, p);
    }

    @Override
    public String toString() {
        return "Unboxing(" + getOperand() + ", " + type + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof UnboxingNode)) {
            return false;
        }
        UnboxingNode other = (UnboxingNode) obj;
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
