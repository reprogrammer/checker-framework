package org.checkerframework.checker.lock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;

import org.checkerframework.checker.lock.qual.Holding;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.framework.source.Result;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.ErrorReporter;
import org.checkerframework.javacutil.TreeUtils;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;

//Disclaimer:
//This class is currently in its alpha form.  For sample code on how to write
//org.checkerframework.checker, please review other org.checkerframework.checker for code samples.

/**
 * A type-checking visitor for the Lock type system.
 * This visitor reports errors ("unguarded.access") or warnings for violations
 * for accessing a field or calling a method without holding their locks.
 */
public class LockVisitor extends BaseTypeVisitor<LockAnnotatedTypeFactory> {

    public LockVisitor(BaseTypeChecker checker) {
        super(checker);
    }

    @Override
    public Void visitVariable(VariableTree node, Void p) {
        Void r = scan(node.getModifiers(), p);
        r = reduce(scan(node.getType(), p), r);
        // We do not want a call for visitIdentifier if a
        // receiver parameter is given.
        // r = scanAndReduce(node.getNameExpression(), p, r);
        r = reduce(scan(node.getInitializer(), p), r);
        return r;
    }

    @Override
    public Void visitIdentifier(IdentifierTree node, Void p) {
        AnnotatedTypeMirror type = atypeFactory.getAnnotatedType(node);
        if (atypeFactory.hasGuardedBy(type)) {
            checker.report(Result.failure("unguarded.access", node, type), node);
        }
        return super.visitIdentifier(node, p);
    }

    @Override
    public Void visitMemberSelect(MemberSelectTree node, Void p) {
        AnnotatedTypeMirror type = atypeFactory.getAnnotatedType(node);
        if (atypeFactory.hasGuardedBy(type)) {
            checker.report(Result.failure("unguarded.access", node, type), node);
        }
        return super.visitMemberSelect(node, p);
    }

    private <T> List<T> append(List<T> lst, T o) {
        if (o == null)
            return lst;

        List<T> newList = new ArrayList<T>(lst.size() + 1);
        newList.addAll(lst);
        newList.add(o);
        return newList;
    }

    @Override
    public Void visitSynchronized(SynchronizedTree node, Void p) {
        List<String> prevLocks = atypeFactory.getHeldLock();

        try {
            List<String> locks = append(prevLocks, TreeUtils.skipParens(node.getExpression()).toString());
            atypeFactory.setHeldLocks(locks);
            return super.visitSynchronized(node, p);
        } finally {
            atypeFactory.setHeldLocks(prevLocks);
        }
    }

    @Override
    public Void visitMethod(MethodTree node, Void p) {
        List<String> prevLocks = atypeFactory.getHeldLock();
        List<String> locks = prevLocks;
        try {
            ExecutableElement method = TreeUtils.elementFromDeclaration(node);
            if (method.getModifiers().contains(Modifier.SYNCHRONIZED)
                || method.getKind() == ElementKind.CONSTRUCTOR) {
                if (method.getModifiers().contains(Modifier.STATIC)) {
                    String enclosingClass = method.getEnclosingElement().getSimpleName().toString();
                    locks = append(locks, enclosingClass + ".class");
                } else {
                    locks = append(locks, "this");
                }
            }

            List<String> methodLocks = methodHolding(method);
            if (!methodLocks.isEmpty()) {
                locks = new ArrayList<String>(locks);
                locks.addAll(methodLocks);
            }
            atypeFactory.setHeldLocks(locks);

            return super.visitMethod(node, p);
        } finally {
            atypeFactory.setHeldLocks(prevLocks);
        }
    }

    private String receiver(ExpressionTree methodSel) {
        if (methodSel.getKind() == Tree.Kind.IDENTIFIER) {
            return "this";
        } else if (methodSel.getKind() == Tree.Kind.MEMBER_SELECT) {
            return ((MemberSelectTree)methodSel).getExpression().toString();
        } else {
            ErrorReporter.errorAbort("LockVisitor found unknown receiver tree type: " + methodSel);
            return null;
        }
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
        // does it introduce new locks
        ExecutableElement methodElt = TreeUtils.elementFromUse(node);

        String lock = receiver(node.getMethodSelect());
        if (methodElt.getSimpleName().contentEquals("lock")) {
            List<String> locks = append(atypeFactory.getHeldLock(), lock);
            atypeFactory.setHeldLocks(locks);
        } else if (methodElt.getSimpleName().contentEquals("unlock")) {
            List<String> locks = new ArrayList<String>(atypeFactory.getHeldLock());
            locks.remove(lock);
            atypeFactory.setHeldLocks(locks);
        }

        // does it require holding a lock
        List<String> methodLocks = methodHolding(methodElt);
        if (!methodLocks.isEmpty()
            && !atypeFactory.getHeldLock().containsAll(methodLocks)) {
            checker.report(Result.failure("unguarded.invocation",
                    methodElt, methodLocks), node);
        }

        return super.visitMethodInvocation(node, p);
    }

    @Override
    protected boolean checkOverride(MethodTree overriderTree,
            AnnotatedDeclaredType enclosingType,
            AnnotatedExecutableType overridden,
            AnnotatedDeclaredType overriddenType,
            Void p) {

        List<String> overriderLocks = methodHolding(TreeUtils.elementFromDeclaration(overriderTree));
        List<String> overriddenLocks = methodHolding(overridden.getElement());

        boolean isValid = overriddenLocks.containsAll(overriderLocks);

        if (!isValid) {
            checker.report(Result.failure("override.holding.invalid",
                    TreeUtils.elementFromDeclaration(overriderTree),
                    overridden.getElement(),
                    overriderLocks, overriddenLocks), overriderTree);
        }

        return super.checkOverride(overriderTree, enclosingType, overridden, overriddenType, p) && isValid;
    }

    @Override
    protected void checkMethodInvocability(AnnotatedExecutableType method,
            MethodInvocationTree node) {
    }

    protected List<String> methodHolding(ExecutableElement element) {
        AnnotationMirror holding = atypeFactory.getDeclAnnotation(element, Holding.class);
        AnnotationMirror guardedBy
            = atypeFactory.getDeclAnnotation(element, net.jcip.annotations.GuardedBy.class);
        if (holding == null && guardedBy == null)
            return Collections.emptyList();

        List<String> locks = new ArrayList<String>();

        if (holding != null) {
            List<String> holdingValue = AnnotationUtils.getElementValueArray(holding, "value", String.class, false);
            locks.addAll(holdingValue);
        }
        if (guardedBy != null) {
            String guardedByValue = AnnotationUtils.getElementValue(guardedBy, "value", String.class, false);
            locks.add(guardedByValue);
        }

        return locks;
    }

    @Override
    public boolean isValidUse(AnnotatedDeclaredType declarationType,
                             AnnotatedDeclaredType useType,
                             Tree tree) {
        return true;
    }

}
