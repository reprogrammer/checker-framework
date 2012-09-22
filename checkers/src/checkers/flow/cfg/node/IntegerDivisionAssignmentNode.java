package checkers.flow.cfg.node;

import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;

/**
 * A node for the integer division compound assignment:
 *
 * <pre>
 *   <em>variable</em> /= <em>expression</em>
 * </pre>
 *
 * @author Stefan Heule
 * @author Charlie Garrett
 *
 */
public class IntegerDivisionAssignmentNode extends CompoundAssignmentNode {

    public IntegerDivisionAssignmentNode(Tree tree, Node left, Node right) {
        super(tree, left, right);
        assert tree.getKind() == Kind.DIVIDE_ASSIGNMENT;
    }

    @Override
    public <R, P> R accept(NodeVisitor<R, P> visitor, P p) {
        return visitor.visitIntegerDivisionAssignment(this, p);
    }

    @Override
    public String getOperator() {
        return "/";
    }
}
