package checkers.flow.cfg.node;

import java.util.Collection;
import java.util.Collections;

import javax.lang.model.type.TypeMirror;

import checkers.flow.util.HashCodeUtils;
import checkers.util.TypesUtils;

import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;

/**
 * A node for the narrowing primitive conversion operation. See JLS 5.1.3 for
 * the definition of narrowing primitive conversion.
 * 
 * A {@link NarrowingConversionNode} does not correspond to any tree node in the
 * parsed AST. It is introduced when a value of some primitive type appears in a
 * context that requires a different primitive with more bits of precision.
 * 
 * @author Stefan Heule
 * @author Charlie Garrett
 * 
 */
public class NarrowingConversionNode extends Node {

    protected Node operand;

    public NarrowingConversionNode(Node operand, TypeMirror type) {
        assert TypesUtils.isPrimitive(type) : "non-primitive type in narrowing conversion";
        this.operand = operand;
        this.type = type;
    }

    public Node getOperand() {
        return operand;
    }

    public TypeMirror getType() {
        return type;
    }

    @Override
    public Tree getTree() {
        return null;
    }

    @Override
    public <R, P> R accept(NodeVisitor<R, P> visitor, P p) {
        return visitor.visitNarrowingConversion(this, p);
    }

    @Override
    public String toString() {
        return "NarrowingConversion(" + getOperand() + ", " + type + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof NarrowingConversionNode)) {
            return false;
        }
        NarrowingConversionNode other = (NarrowingConversionNode) obj;
        return getOperand().equals(other.getOperand())
                && TypesUtils.areSamePrimitiveTypes(getType(), other.getType());
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
