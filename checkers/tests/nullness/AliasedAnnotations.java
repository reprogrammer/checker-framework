// This uses all the aliases listed in section "Other tools for nullness
// checking" of the Checker Framework manual.

public class AliasedAnnotations {

  void useNonNullAnnotations() {
    //:: error: (assignment.type.incompatible)
    @checkers.nullness.quals.NonNull Object nn1 = null;
    //:: error: (assignment.type.incompatible)
    @com.sun.istack.NotNull Object nn2 = null;
    //:: error: (assignment.type.incompatible)
    @edu.umd.cs.findbugs.annotations.NonNull Object nn3 = null;
    //:: error: (assignment.type.incompatible)
    @javax.annotation.Nonnull Object nn4 = null;
    //:: error: (assignment.type.incompatible)
    @org.eclipse.jdt.annotation.NonNull Object nn5 = null;
    //:: error: (assignment.type.incompatible)
    @org.jetbrains.annotations.NotNull Object nn6 = null;
    //:: error: (assignment.type.incompatible)
    @org.netbeans.api.annotations.common.NonNull Object nn7 = null;
    //:: error: (assignment.type.incompatible)
    @org.jmlspecs.annotation.NonNull Object nn8 = null;
  }

  void useNullableAnnotations1(@checkers.nullness.quals.Nullable  Object nble) {
    //:: errar: (dereference.of.nullable)
    nble.toString();
  }

  void useNullableAnnotations2(@com.sun.istack.Nullable  Object nble) {
    //:: errar: (dereference.of.nullable)
    nble.toString();
  }
  
  void useNullableAnnotations3(@edu.umd.cs.findbugs.annotations.Nullable  Object nble) {
    //:: errar: (dereference.of.nullable)
    nble.toString();
  }
  
  void useNullableAnnotations4(@edu.umd.cs.findbugs.annotations.CheckForNull  Object nble) {
    //:: errar: (dereference.of.nullable)
    nble.toString();
  }
  
  void useNullableAnnotations5(@edu.umd.cs.findbugs.annotations.UnknownNullness  Object nble) {
    //:: errar: (dereference.of.nullable)
    nble.toString();
  }
  
  void useNullableAnnotations6(@javax.annotation.Nullable  Object nble) {
    //:: errar: (dereference.of.nullable)
    nble.toString();
  }
  
  void useNullableAnnotations7(@javax.annotation.CheckForNull  Object nble) {
    //:: errar: (dereference.of.nullable)
    nble.toString();
  }
  
  void useNullableAnnotations8(@javax.validation.constraints.NotNull  Object nble) {
    //:: errar: (dereference.of.nullable)
    nble.toString();
  }
  
  void useNullableAnnotations9(@org.eclipse.jdt.annotation.Nullable  Object nble) {
    //:: errar: (dereference.of.nullable)
    nble.toString();
  }
  
  void useNullableAnnotations10(@org.jetbrains.annotations.Nullable  Object nble) {
    //:: errar: (dereference.of.nullable)
    nble.toString();
  }
  
  void useNullableAnnotations11(@org.netbeans.api.annotations.common.CheckForNull  Object nble) {
    //:: errar: (dereference.of.nullable)
    nble.toString();
  }
  
  void useNullableAnnotations12(@org.netbeans.api.annotations.common.NullAllowed  Object nble) {
    //:: errar: (dereference.of.nullable)
    nble.toString();
  }
  
  void useNullableAnnotations13(@org.netbeans.api.annotations.common.NullUnknown  Object nble) {
    //:: errar: (dereference.of.nullable)
    nble.toString();
  }
  
  void useNullableAnnotations14(@org.jmlspecs.annotation.Nullable  Object nble) {
    //:: errar: (dereference.of.nullable)
    nble.toString();
  }
  
}
