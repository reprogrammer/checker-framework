package checkers.nullness;

import java.util.List;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;

import checkers.basetype.BaseTypeChecker;
import checkers.nullness.quals.*;
import checkers.quals.TypeQualifiers;
import checkers.quals.Unqualified;
import checkers.types.AnnotatedTypeMirror;
import checkers.types.AnnotatedTypeMirror.AnnotatedDeclaredType;
import checkers.types.QualifierHierarchy;
import checkers.types.TypeHierarchy;

/**
 * TODO: doc
 */
@TypeQualifiers({ KeyFor.class, Unqualified.class, KeyForBottom.class})
public class KeyForSubchecker extends BaseTypeChecker {
  @Override
  protected TypeHierarchy createTypeHierarchy() {
    return new KeyForTypeHierarchy(getQualifierHierarchy());
  }

  @Override
  public final boolean isSubtype(AnnotatedTypeMirror rhs, AnnotatedTypeMirror lhs) {
  if (lhs.getKind() == TypeKind.TYPEVAR &&
      rhs.getKind() == TypeKind.TYPEVAR) {
    // TODO: Investigate whether there is a nicer and more proper way to
    // get assignments between two type variables working.
    if (lhs.getAnnotations().isEmpty()) {
      return true;
    }
  }
  // Otherwise Covariant would cause trouble.
  if (rhs.getAnnotation(KeyForBottom.class)!=null) {
    return true;
  }
  return super.isSubtype(rhs, lhs);
  }

  private class KeyForTypeHierarchy extends TypeHierarchy {

    public KeyForTypeHierarchy(QualifierHierarchy qualifierHierarchy) {
      super(qualifierHierarchy);
    }

    @Override
    protected boolean isSubtypeTypeArguments(AnnotatedDeclaredType rhs, AnnotatedDeclaredType lhs) {
      List<AnnotatedTypeMirror> rhsTypeArgs = rhs.getTypeArguments();
      List<AnnotatedTypeMirror> lhsTypeArgs = lhs.getTypeArguments();

      if (rhsTypeArgs.isEmpty() || lhsTypeArgs.isEmpty())
        return true;

      TypeElement lhsElem = (TypeElement) lhs.getUnderlyingType().asElement();
      // TypeElement rhsElem = (TypeElement) lhs.getUnderlyingType().asElement();
      // the following would be needed if Covariant were per type parameter
      // AnnotatedDeclaredType lhsDecl = currentATF.fromElement(lhsElem);
      // AnnotatedDeclaredType rhsDecl = currentATF.fromElement(rhsElem);
      // List<AnnotatedTypeMirror> lhsTVs = lhsDecl.getTypeArguments();
      // List<AnnotatedTypeMirror> rhsTVs = rhsDecl.getTypeArguments();

      int[] covarVals = null;
      if (lhsElem.getAnnotation(Covariant.class)!=null) {
        covarVals = lhsElem.getAnnotation(Covariant.class).value();
      }

      assert lhsTypeArgs.size() == rhsTypeArgs.size();
      for (int i = 0; i < lhsTypeArgs.size(); ++i) {
        boolean covar = false;
        if (covarVals != null) {
          for (int cvv = 0; cvv < covarVals.length; ++cvv) {
            if (covarVals[cvv] == i) {
              covar = true;
            }
          }
        }

        if (covar) {
          if (!KeyForSubchecker.this.isSubtype(rhsTypeArgs.get(i), lhsTypeArgs.get(i)))
            return false;
        } else {
          if (!isSubtypeAsTypeArgument(rhsTypeArgs.get(i), lhsTypeArgs.get(i)))
            return false;
        }
      }

      return true;
    }
  }
}
