package checkers.flow.cfg.node;

import java.util.Collection;
import java.util.Collections;

import com.sun.source.tree.Tree;
import com.sun.source.tree.LiteralTree;

/**
 * A node for the null literal.
 * 
 * <pre>
 *   <em>null</em>
 * </pre>
 *
 * @author Stefan Heule
 * @author Charlie Garrett
 * 
 */
public class NullLiteralNode extends ValueLiteralNode {

	public NullLiteralNode(LiteralTree t) {
		assert t.getKind().equals(Tree.Kind.NULL_LITERAL);
		tree = t;
	}

	@Override
	public Void getValue() {
		return (Void) tree.getValue();
	}

	@Override
	public <R, P> R accept(NodeVisitor<R, P> visitor, P p) {
		return visitor.visitNullLiteral(this, p);
	}
	
	@Override
	public boolean equals(Object obj) {
		// test that obj is a NullLiteralNode
		if (obj == null || !(obj instanceof NullLiteralNode)) {
			return false;
		}
		// super method compares values
		return super.equals(obj);
	}

	@Override
	public Collection<Node> getOperands() {
		return Collections.emptyList();
	}

	@Override
	public boolean hasResult() {
		return true;
	}
}
