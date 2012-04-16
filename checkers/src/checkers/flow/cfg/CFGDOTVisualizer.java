package checkers.flow.cfg;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;

import javax.lang.model.type.TypeMirror;

import checkers.flow.analysis.AbstractValue;
import checkers.flow.analysis.Analysis;
import checkers.flow.analysis.Store;
import checkers.flow.analysis.TransferFunction;
import checkers.flow.analysis.TransferInput;
import checkers.flow.analysis.checkers.CFAbstractStore;
import checkers.flow.cfg.block.Block;
import checkers.flow.cfg.block.Block.BlockType;
import checkers.flow.cfg.block.ConditionalBlock;
import checkers.flow.cfg.block.ExceptionBlock;
import checkers.flow.cfg.block.RegularBlock;
import checkers.flow.cfg.block.SingleSuccessorBlock;
import checkers.flow.cfg.block.SpecialBlock;
import checkers.flow.cfg.node.Node;

/**
 * Generate a graph description in the DOT language of a control graph.
 * 
 * @author Stefan Heule
 * 
 */
public class CFGDOTVisualizer {

    public static String visualize(Block entry) {
        return visualize(entry, null);
    }

    /**
     * Output a graph description in the DOT language, representing the control
     * flow graph starting at <code>entry</code>.
     * 
     * @param entry
     *            The entry node of the control flow graph to be represented.
     * @param analysis
     *            An analysis containing information about the program
     *            represented by the CFG. The information includes {@link Store}
     *            s that are valid at the beginning of basic blocks reachable
     *            from <code>entry</code> and per-node information for value
     *            producing {@link Node}s. Can also be <code>null</code> to
     *            indicate that this information should not be output.
     * @return String representation of the graph in the DOT language.
     */
    public static <A extends AbstractValue<A>, S extends Store<S>, T extends TransferFunction<A, S>> String visualize(
            Block entry,
            /* @Nullable */Analysis<A, S, T> analysis) {
        StringBuilder sb1 = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();
        Set<Block> visited = new HashSet<>();
        Queue<Block> worklist = new LinkedList<>();
        Block cur = entry;
        visited.add(entry);

        // header
        sb1.append("digraph {\n");
        sb1.append("    node [shape=rectangle];\n\n");

        // traverse control flow graph and define all arrows
        while (true) {
            if (cur == null)
                break;

            if (cur.getType() == BlockType.CONDITIONAL_BLOCK) {
                ConditionalBlock ccur = ((ConditionalBlock) cur);
                Block thenSuccessor = ccur.getThenSuccessor();
                sb2.append("    " + ccur.getId() + " -> "
                        + thenSuccessor.getId());
                sb2.append(" [label=\"then\"];\n");
                if (!visited.contains(thenSuccessor)) {
                    visited.add(thenSuccessor);
                    worklist.add(thenSuccessor);
                }
                Block elseSuccessor = ccur.getElseSuccessor();
                sb2.append("    " + ccur.getId() + " -> "
                        + elseSuccessor.getId());
                sb2.append(" [label=\"else\"];\n");
                if (!visited.contains(elseSuccessor)) {
                    visited.add(elseSuccessor);
                    worklist.add(elseSuccessor);
                }
            } else {
                assert cur instanceof SingleSuccessorBlock;
                Block b = ((SingleSuccessorBlock) cur).getSuccessor();
                if (b != null) {
                    sb2.append("    " + cur.getId() + " -> " + b.getId());
                    sb2.append(";\n");
                    if (!visited.contains(b)) {
                        visited.add(b);
                        worklist.add(b);
                    }
                }
            }

            // exceptional edges
            if (cur.getType() == BlockType.EXCEPTION_BLOCK) {
                ExceptionBlock ecur = (ExceptionBlock) cur;
                for (Entry<TypeMirror, Block> e : ecur
                        .getExceptionalSuccessors().entrySet()) {
                    Block b = e.getValue();
                    TypeMirror cause = e.getKey();
                    String exception = cause.toString();
                    if (exception.startsWith("java.lang.")) {
                        exception = exception.replace("java.lang.", "");
                    }

                    sb2.append("    " + cur.getId() + " -> " + b.getId());
                    sb2.append(" [label=\"" + exception + "\"];\n");
                    if (!visited.contains(b)) {
                        visited.add(b);
                        worklist.add(b);
                    }
                }
            }

            cur = worklist.poll();
        }

        // definition of all nodes including their labels
        for (Block v : visited) {
            sb1.append("    " + v.getId() + " [");
            if (v.getType() == BlockType.CONDITIONAL_BLOCK) {
                sb1.append("shape=polygon sides=8 ");
            } else if (v.getType() == BlockType.SPECIAL_BLOCK) {
                sb1.append("shape=oval ");
            }
            sb1.append("label=\""
                    + visualizeContent(v, analysis).replace("\\n", "\\l")
                    + "\",];\n");
        }

        sb1.append("\n");
        sb1.append(sb2);

        // footer
        sb1.append("}\n");

        return sb1.toString();
    }

    /**
     * Produce a string representation of the contests of a basic block.
     * 
     * @param bb
     *            Basic block to visualize.
     * @return String representation.
     */
    protected static <A extends AbstractValue<A>, S extends Store<S>, T extends TransferFunction<A, S>> String visualizeContent(
            Block bb,
            /* @Nullable */Analysis<A, S, T> analysis) {
        StringBuilder sb = new StringBuilder();

        // loop over contents
        List<Node> contents = new LinkedList<>();
        switch (bb.getType()) {
        case REGULAR_BLOCK:
            contents.addAll(((RegularBlock) bb).getContents());
            break;
        case EXCEPTION_BLOCK:
            contents.add(((ExceptionBlock) bb).getNode());
            break;
        case CONDITIONAL_BLOCK:
            break;
        case SPECIAL_BLOCK:
            break;
        default:
            assert false : "All types of basic blocks covered";
        }
        boolean notFirst = false;
        for (Node t : contents) {
            if (notFirst) {
                sb.append("\\n");
            }
            notFirst = true;
            sb.append(prepareString(visualizeNode(t, analysis)));
        }

        // handle case where no contents are present
        boolean centered = false;
        if (sb.length() == 0) {
            centered = true;
            if (bb.getType() == BlockType.SPECIAL_BLOCK) {
                SpecialBlock sbb = (SpecialBlock) bb;
                switch (sbb.getSpecialType()) {
                case ENTRY:
                    sb.append("<entry>");
                    break;
                case EXIT:
                    sb.append("<exit>");
                    break;
                case EXCEPTIONAL_EXIT:
                    sb.append("<exceptional-exit>");
                    break;
                }
            } else if (bb.getType() == BlockType.CONDITIONAL_BLOCK) {
                return "";
            } else {
                return "?? empty ??";
            }
        }

        // visualize store if necessary
        if (analysis != null) {
            TransferInput<A, S> store = analysis.getStore(bb);
            StringBuilder sb2 = new StringBuilder();

            // split store representation to two lines
            String s = store.toDOToutput().replace("}, else={", "}\\nelse={");
            sb2.append(s.subSequence(1, s.length() - 1));

            // separator
            sb2.append("\\n~~~~~~~~~\\n");
            sb2.append(sb);
            sb = sb2;
        }

        return sb.toString() + (centered ? "" : "\\n");
    }

    protected static <A extends AbstractValue<A>, S extends Store<S>, T extends TransferFunction<A, S>> String visualizeNode(
            Node t, /* @Nullable */Analysis<A, S, T> analysis) {
        A value = analysis.getValue(t);
        String valueInfo = "";
        if (value != null) {
            valueInfo = "    > " + value.toString();
        }
        return t.toString() + "   [ " + visualizeType(t) + " ]" + valueInfo;
    }

    protected static String visualizeType(Node t) {
        String name = t.getClass().getSimpleName();
        return name.replace("Node", "");
    }

    protected static String prepareString(String s) {
        return s.replace("\"", "\\\"");
    }
}
