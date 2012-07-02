package checkers.basetype;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.Element;

import checkers.quals.Pure;
import checkers.quals.Pure.Kind;
import checkers.source.Result;
import checkers.types.AnnotatedTypeFactory;
import checkers.util.InternalUtils;
import checkers.util.PurityUtils;
import checkers.util.TreeUtils;

import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.AssertTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.BreakTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ContinueTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EmptyStatementTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ErroneousTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.LabeledStatementTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TreeVisitor;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.UnionTypeTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.tree.WildcardTree;
import com.sun.tools.javac.tree.TreeScanner;

/**
 * A visitor that checks the purity (as defined by {@link checkers.quals.Pure})
 * of a statement or expression.
 * 
 * @see The annotation {@link Pure} for more details on what is checked and the
 *      semantics of purity.
 * 
 * @author Stefan Heule
 * 
 */
public class PurityChecker {

    /**
     * Check the method {@code method} whether it is pure of the type
     * {@code type}.
     */
    public static PurityResult checkPurity(MethodTree method,
            AnnotatedTypeFactory atypeFactory) {
        PurityCheckerHelper helper = new PurityCheckerHelper(method,
                atypeFactory);
        List<Kind> types = PurityUtils.getPurityKinds(atypeFactory, method);
        PurityResult res = helper.scan(method.getBody(),
                new PurityResult(types));
        return res;
    }

    /**
     * Result of the {@link PurityChecker}.
     */
    protected static class PurityResult {

        protected final List<String> notSeFreeReasons;
        protected final List<String> notDetReasons;
        protected final List<String> notBothReasons;
        protected final List<Kind> types;

        public PurityResult(List<Kind> types) {
            notSeFreeReasons = new ArrayList<>();
            notDetReasons = new ArrayList<>();
            notBothReasons = new ArrayList<>();
            this.types = types;
        }

        /** Is the method pure? */
        public boolean isPure() {
            return notSeFreeReasons.size() + notDetReasons.size()
                    + notBothReasons.size() == 0;
        }

        /**
         * Add {@code reason} as a reason why the method is not side-effect free
         * (if applicable).
         */
        public void addNotSeFreeReason(String reason) {
            if (types.contains(Kind.SIDE_EFFECT_FREE)) {
                notSeFreeReasons.add(reason);
            }
        }

        /**
         * Add {@code reason} as a reason why the method is not deterministic
         * (if applicable).
         */
        public void addNotDetReason(String reason) {
            if (types.contains(Kind.DETERMINISTIC)) {
                notDetReasons.add(reason);
            }
        }

        /**
         * Add {@code reason} as a reason why the method is not both side-effect
         * free and deterministic (if applicable).
         */
        public void addNotBothReason(String reason) {
            if (types.contains(Kind.DETERMINISTIC)
                    && types.contains(Kind.SIDE_EFFECT_FREE)) {
                notBothReasons.add(reason);
            } else {
                addNotSeFreeReason(reason);
                addNotDetReason(reason);
            }
        }

        /**
         * Report all errors encountered.
         */
        public void reportErrors(BaseTypeChecker checker, MethodTree node) {
            assert !isPure();
            if (!notBothReasons.isEmpty()) {
                checker.report(Result.failure(
                        "pure.not.deterministic.and.sideeffect.free",
                        errorList(notBothReasons)), node);
            }
            if (!notSeFreeReasons.isEmpty()) {
                checker.report(Result.failure("pure.not.sideeffect.free",
                        errorList(notSeFreeReasons)), node);
            }
            if (!notDetReasons.isEmpty()) {
                checker.report(Result.failure("pure.not.deterministic",
                        errorList(notDetReasons)), node);
            }
        }

        private static String errorList(List<String> reasons) {
            StringBuilder s = new StringBuilder();
            boolean first = true;
            for (String r : reasons) {
                if (!first) {
                    s.append(", ");
                }
                s.append(r);
                first = false;
            }
            return s.toString();
        }
    }

    /**
     * Helper class to keep {@link PurityChecker}s interface clean. The
     * implementation is heavily based on {@link TreeScanner}, but some parts of
     * the AST are skipped (such as types or modifiers). Furthermore, scanning
     * works differently in that the input parameter (usually named {@code p})
     * gets "threaded through", instead of using {@code reduce}.
     */
    protected static class PurityCheckerHelper implements
            TreeVisitor<PurityResult, PurityResult> {

        protected final AnnotatedTypeFactory atypeFactory;
        protected final MethodTree method;
        protected/* @Nullable */List<Element> methodParameter;

        public PurityCheckerHelper(MethodTree method,
                AnnotatedTypeFactory atypeFactory) {
            this.atypeFactory = atypeFactory;
            this.method = method;
        }

        /**
         * Scan a single node.
         */
        public PurityResult scan(Tree node, PurityResult p) {
            return node == null ? p : node.accept(this, p);
        }

        /**
         * Scan a list of nodes.
         */
        public PurityResult scan(Iterable<? extends Tree> nodes, PurityResult p) {
            PurityResult r = p;
            if (nodes != null) {
                for (Tree node : nodes) {
                    r = scan(node, r);
                }
            }
            return r;
        }

        public PurityResult visitCompilationUnit(CompilationUnitTree node,
                PurityResult p) {
            assert false : "this type of tree is unexpected here";
            return null;
        }

        public PurityResult visitImport(ImportTree node, PurityResult p) {
            assert false : "this type of tree is unexpected here";
            return null;
        }

        public PurityResult visitClass(ClassTree node, PurityResult p) {
            return p;
        }

        public PurityResult visitMethod(MethodTree node, PurityResult p) {
            assert false : "this type of tree is unexpected here";
            return null;
        }

        public PurityResult visitVariable(VariableTree node, PurityResult p) {
            return scan(node.getInitializer(), p);
        }

        public PurityResult visitEmptyStatement(EmptyStatementTree node,
                PurityResult p) {
            return p;
        }

        public PurityResult visitBlock(BlockTree node, PurityResult p) {
            return scan(node.getStatements(), p);
        }

        public PurityResult visitDoWhileLoop(DoWhileLoopTree node,
                PurityResult p) {
            PurityResult r = scan(node.getStatement(), p);
            r = scan(node.getCondition(), r);
            return r;
        }

        public PurityResult visitWhileLoop(WhileLoopTree node, PurityResult p) {
            PurityResult r = scan(node.getCondition(), p);
            r = scan(node.getStatement(), r);
            return r;
        }

        public PurityResult visitForLoop(ForLoopTree node, PurityResult p) {
            PurityResult r = scan(node.getInitializer(), p);
            r = scan(node.getCondition(), r);
            r = scan(node.getUpdate(), r);
            r = scan(node.getStatement(), r);
            return r;
        }

        public PurityResult visitEnhancedForLoop(EnhancedForLoopTree node,
                PurityResult p) {
            PurityResult r = scan(node.getVariable(), p);
            r = scan(node.getExpression(), r);
            r = scan(node.getStatement(), r);
            return r;
        }

        public PurityResult visitLabeledStatement(LabeledStatementTree node,
                PurityResult p) {
            return scan(node.getStatement(), p);
        }

        public PurityResult visitSwitch(SwitchTree node, PurityResult p) {
            PurityResult r = scan(node.getExpression(), p);
            r = scan(node.getCases(), r);
            return r;
        }

        public PurityResult visitCase(CaseTree node, PurityResult p) {
            PurityResult r = scan(node.getExpression(), p);
            r = scan(node.getStatements(), r);
            return r;
        }

        public PurityResult visitSynchronized(SynchronizedTree node,
                PurityResult p) {
            PurityResult r = scan(node.getExpression(), p);
            r = scan(node.getBlock(), r);
            return r;
        }

        public PurityResult visitTry(TryTree node, PurityResult p) {
            PurityResult r = scan(node.getResources(), p);
            r = scan(node.getBlock(), r);
            r = scan(node.getCatches(), r);
            r = scan(node.getFinallyBlock(), r);
            return r;
        }

        public PurityResult visitCatch(CatchTree node, PurityResult p) {
            p.addNotDetReason("catch statement");
            PurityResult r = scan(node.getParameter(), p);
            r = scan(node.getBlock(), r);
            return r;
        }

        public PurityResult visitConditionalExpression(
                ConditionalExpressionTree node, PurityResult p) {
            PurityResult r = scan(node.getCondition(), p);
            r = scan(node.getTrueExpression(), r);
            r = scan(node.getFalseExpression(), r);
            return r;
        }

        public PurityResult visitIf(IfTree node, PurityResult p) {
            PurityResult r = scan(node.getCondition(), p);
            r = scan(node.getThenStatement(), r);
            r = scan(node.getElseStatement(), r);
            return r;
        }

        public PurityResult visitExpressionStatement(
                ExpressionStatementTree node, PurityResult p) {
            return scan(node.getExpression(), p);
        }

        public PurityResult visitBreak(BreakTree node, PurityResult p) {
            return p;
        }

        public PurityResult visitContinue(ContinueTree node, PurityResult p) {
            return p;
        }

        public PurityResult visitReturn(ReturnTree node, PurityResult p) {
            return scan(node.getExpression(), p);
        }

        public PurityResult visitThrow(ThrowTree node, PurityResult p) {
            return scan(node.getExpression(), p);
        }

        public PurityResult visitAssert(AssertTree node, PurityResult p) {
            PurityResult r = scan(node.getCondition(), p);
            r = scan(node.getDetail(), r);
            return r;
        }

        public PurityResult visitMethodInvocation(MethodInvocationTree node,
                PurityResult p) {
            Element elt = TreeUtils.elementFromUse(node);
            if (!PurityUtils.hasPurityAnnotation(atypeFactory, elt)) {
                p.addNotBothReason("non-pure method call");
            } else {
                boolean det = PurityUtils.isDeterministic(atypeFactory, elt);
                boolean seFree = PurityUtils
                        .isSideEffectFree(atypeFactory, elt);
                if (!det && !seFree) {
                    p.addNotBothReason("non-pure method call");
                } else if (!det) {
                    p.addNotDetReason("non-pure method call");
                } else if (!seFree) {
                    p.addNotSeFreeReason("non-pure method call");
                }
            }
            PurityResult r = scan(node.getMethodSelect(), p);
            r = scan(node.getArguments(), r);
            return r;
        }

        public PurityResult visitNewClass(NewClassTree node, PurityResult p) {
            Element methodElement = InternalUtils.symbol(node);
            boolean sideEffectFree = PurityUtils.isSideEffectFree(atypeFactory,
                    methodElement);
            if (sideEffectFree) {
                p.addNotDetReason("object creation");
            } else {
                p.addNotBothReason("object creation with non-pure constructor");
            }
            PurityResult r = scan(node.getEnclosingExpression(), p);
            r = scan(node.getIdentifier(), r);
            r = scan(node.getTypeArguments(), r);
            r = scan(node.getArguments(), r);
            r = scan(node.getClassBody(), r);
            return r;
        }

        public PurityResult visitNewArray(NewArrayTree node, PurityResult p) {
            PurityResult r = scan(node.getType(), p);
            r = scan(node.getDimensions(), r);
            r = scan(node.getInitializers(), r);
            return r;
        }

        public PurityResult visitLambdaExpression(LambdaExpressionTree node,
                PurityResult p) {
            PurityResult r = scan(node.getParameters(), p);
            r = scan(node.getBody(), r);
            return r;
        }

        public PurityResult visitParenthesized(ParenthesizedTree node,
                PurityResult p) {
            return scan(node.getExpression(), p);
        }

        public PurityResult visitAssignment(AssignmentTree node, PurityResult p) {
            ExpressionTree variable = node.getVariable();
            p = assignmentCheck(p, variable);
            PurityResult r = scan(variable, p);
            r = scan(node.getExpression(), r);
            return r;
        }

        protected PurityResult assignmentCheck(PurityResult p,
                ExpressionTree variable) {
            if (TreeUtils.isFieldAccess(variable)) {
                // rhs is a field access
                p.addNotBothReason("assignment to field '"
                        + TreeUtils.getFieldName(variable) + "'");
            } else if (variable instanceof ArrayAccessTree) {
                // rhs is array access
                ArrayAccessTree a = (ArrayAccessTree) variable;
                p.addNotBothReason("assignment to array '" + a.getExpression()
                        + "'");
            } else {
                // rhs is a local variable
                assert isLocalVariable(variable);
            }
            return p;
        }

        protected boolean isLocalVariable(ExpressionTree variable) {
            return variable instanceof IdentifierTree
                    && !TreeUtils.isFieldAccess(variable);
        }

        public PurityResult visitCompoundAssignment(
                CompoundAssignmentTree node, PurityResult p) {
            ExpressionTree variable = node.getVariable();
            p = assignmentCheck(p, variable);
            PurityResult r = scan(variable, p);
            r = scan(node.getExpression(), r);
            return r;
        }

        public PurityResult visitUnary(UnaryTree node, PurityResult p) {
            return scan(node.getExpression(), p);
        }

        public PurityResult visitBinary(BinaryTree node, PurityResult p) {
            PurityResult r = scan(node.getLeftOperand(), p);
            r = scan(node.getRightOperand(), r);
            return r;
        }

        public PurityResult visitTypeCast(TypeCastTree node, PurityResult p) {
            PurityResult r = scan(node.getExpression(), p);
            return r;
        }

        public PurityResult visitInstanceOf(InstanceOfTree node, PurityResult p) {
            PurityResult r = scan(node.getExpression(), p);
            return r;
        }

        public PurityResult visitArrayAccess(ArrayAccessTree node,
                PurityResult p) {
            PurityResult r = scan(node.getExpression(), p);
            r = scan(node.getIndex(), r);
            return r;
        }

        public PurityResult visitMemberSelect(MemberSelectTree node,
                PurityResult p) {
            return scan(node.getExpression(), p);
        }

        public PurityResult visitMemberReference(MemberReferenceTree node,
                PurityResult p) {
            assert false : "this type of tree is unexpected here";
            return null;
        }

        public PurityResult visitIdentifier(IdentifierTree node, PurityResult p) {
            return p;
        }

        public PurityResult visitLiteral(LiteralTree node, PurityResult p) {
            return p;
        }

        public PurityResult visitPrimitiveType(PrimitiveTypeTree node,
                PurityResult p) {
            assert false : "this type of tree is unexpected here";
            return null;
        }

        public PurityResult visitArrayType(ArrayTypeTree node, PurityResult p) {
            assert false : "this type of tree is unexpected here";
            return null;
        }

        public PurityResult visitParameterizedType(ParameterizedTypeTree node,
                PurityResult p) {
            assert false : "this type of tree is unexpected here";
            return null;
        }

        public PurityResult visitUnionType(UnionTypeTree node, PurityResult p) {
            assert false : "this type of tree is unexpected here";
            return null;
        }

        public PurityResult visitTypeParameter(TypeParameterTree node,
                PurityResult p) {
            assert false : "this type of tree is unexpected here";
            return null;
        }

        public PurityResult visitWildcard(WildcardTree node, PurityResult p) {
            assert false : "this type of tree is unexpected here";
            return null;
        }

        public PurityResult visitModifiers(ModifiersTree node, PurityResult p) {
            assert false : "this type of tree is unexpected here";
            return null;
        }

        public PurityResult visitAnnotation(AnnotationTree node, PurityResult p) {
            assert false : "this type of tree is unexpected here";
            return null;
        }

        public PurityResult visitAnnotatedType(AnnotatedTypeTree node,
                PurityResult p) {
            assert false : "this type of tree is unexpected here";
            return null;
        }

        public PurityResult visitOther(Tree node, PurityResult p) {
            assert false : "this type of tree is unexpected here";
            return null;
        }

        public PurityResult visitErroneous(ErroneousTree node, PurityResult p) {
            assert false : "this type of tree is unexpected here";
            return null;
        }
    }

}
