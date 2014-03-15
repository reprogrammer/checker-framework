package org.checkerframework.checker.igj;

import java.util.Collection;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;

import org.checkerframework.checker.igj.qual.Assignable;
import org.checkerframework.checker.igj.qual.AssignsFields;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.InternalUtils;
import org.checkerframework.javacutil.TreeUtils;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.Tree;

/**
 * A type-checking visitor for the IGJ type
 * qualifier that uses the {@link BaseTypeVisitor} implementation. This visitor
 * reports errors or warnings for violations for the following cases:
 *
 * <ol>
 * <li value="1">constructing an infeasible type
 * </ol>
 *
 * @see BaseTypeVisitor
 */
public class IGJVisitor extends BaseTypeVisitor<IGJAnnotatedTypeFactory> {

    public IGJVisitor(BaseTypeChecker checker) {
        super(checker);
        checkForAnnotatedJdk();
    }

    @Override
    protected boolean checkConstructorInvocation(AnnotatedDeclaredType dt,
            AnnotatedExecutableType constructor, Tree src) {
        Collection<AnnotationMirror> annos = constructor.getReturnType().getAnnotations();
        if (annos.contains(atypeFactory.I) || annos.contains(atypeFactory.ASSIGNS_FIELDS))
            return true;
        else
            return super.checkConstructorInvocation(dt, constructor, src);
    }

    @Override
    public boolean isValidUse(AnnotatedDeclaredType elemType, AnnotatedDeclaredType use, Tree tree) {
        return true;
        /*
        if (elemType.hasEffectiveAnnotation(atypeFactory.I) ||
                use.hasEffectiveAnnotation(atypeFactory.READONLY)) {
            return true;
        }
        if (use.hasEffectiveAnnotation(atypeFactory.ASSIGNS_FIELDS) &&
                tree.getKind() == Tree.Kind.METHOD &&
                TreeUtils.isConstructor((MethodTree) tree)) {
            return true;
        }

        return super.isValidUse(elemType, use, tree);
        */
    }

    /**
     * Return true if the assignment variable is an assignable field or
     * variable, and returns false otherwise.
     *
     * A field is assignable if it is
     *
     * 1. a static field
     * 2. marked {@link Assignable}
     * 3. accessed through a mutable reference
     * 4. reassigned with an {@link AssignsFields} method and owned by 'this'
     *
     */
    @Override
    public boolean isAssignable(AnnotatedTypeMirror varType,
            AnnotatedTypeMirror receiverType, Tree varTree) {
        if (!TreeUtils.isExpressionTree(varTree))
            return true;

        Element varElement = InternalUtils.symbol(varTree);
        if (varTree.getKind() != Tree.Kind.ARRAY_ACCESS
                && (varElement == null // a variable element should never be null
                        || !varElement.getKind().isField()
                        || ElementUtils.isStatic(varElement)
                        || atypeFactory.getDeclAnnotation(varElement, Assignable.class) != null))
            return true;

        assert receiverType != null;

        final boolean isAssignable =
            receiverType.hasEffectiveAnnotation(atypeFactory.MUTABLE)
             || receiverType.hasEffectiveAnnotation(atypeFactory.BOTTOM_QUAL)
             || (receiverType.hasEffectiveAnnotation(atypeFactory.ASSIGNS_FIELDS)
                     && atypeFactory.isMostEnclosingThisDeref((ExpressionTree)varTree));

        return isAssignable;
    }

}
