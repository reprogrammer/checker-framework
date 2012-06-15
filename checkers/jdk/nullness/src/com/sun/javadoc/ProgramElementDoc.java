package com.sun.javadoc;

import checkers.nullness.quals.Nullable;

public abstract interface ProgramElementDoc extends Doc {
  public abstract @Nullable com.sun.javadoc.ClassDoc containingClass();
  public abstract com.sun.javadoc.PackageDoc containingPackage();
  public abstract java.lang.String qualifiedName();
  public abstract int modifierSpecifier();
  public abstract java.lang.String modifiers();
  public abstract com.sun.javadoc.AnnotationDesc[] annotations();
  public abstract boolean isPublic();
  public abstract boolean isProtected();
  public abstract boolean isPrivate();
  public abstract boolean isPackagePrivate();
  public abstract boolean isStatic();
  public abstract boolean isFinal();
}
