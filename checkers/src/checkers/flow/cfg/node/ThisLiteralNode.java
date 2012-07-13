package checkers.flow.cfg.node;

import java.util.Collection;
import java.util.Collections;

import checkers.flow.util.HashCodeUtils;

/**
 * A node for a reference to 'this'.
 * 
 * <pre>
 *   <em>this</em>
 * </pre>
 * 
 * @author Stefan Heule
 * @author Charlie Garrett
 * 
 */
public abstract class ThisLiteralNode extends Node {

    public String getName() {
        return "this";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof ThisLiteralNode)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return HashCodeUtils.hash(getName());
    }

    @Override
    public Collection<Node> getOperands() {
        return Collections.emptyList();
    }
}
