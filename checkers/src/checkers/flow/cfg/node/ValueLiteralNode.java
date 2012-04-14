package checkers.flow.cfg.node;

import java.util.Collection;
import java.util.Collections;

import checkers.flow.util.HashCodeUtils;

import com.sun.source.tree.LiteralTree;

/**
 * A node for a literals that have some form of value:
 * <ul>
 * <li>integer literal</li>
 * <li>long literal</li>
 * <li>char literal</li>
 * <li>string literal</li>
 * <li>float literal</li>
 * <li>double literal</li>
 * <li>boolean literal</li>
 * <li>null literal</li>
 * </ul>
 * 
 * @author Stefan Heule
 * 
 */
public abstract class ValueLiteralNode extends Node {

    protected LiteralTree tree;

    /**
     * @return The value of the literal.
     */
    abstract public /*@Nullable*/ Object getValue();

    @Override
    public LiteralTree getTree() {
        return tree;
    }

    @Override
    public String toString() {
        return String.valueOf(getValue());
    }

    /**
     * Compare the value of this nodes.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof ValueLiteralNode)) {
            return false;
        }
        ValueLiteralNode other = (ValueLiteralNode) obj;
        Object val = getValue();
        Object otherVal = other.getValue();
        return ((val == null || otherVal == null) && val == otherVal) || val.equals(otherVal);
    }

    @Override
    public int hashCode() {
        return HashCodeUtils.hash(getValue());
    }

    @Override
    public Collection<Node> getOperands() {
        return Collections.emptyList();
    }

}
