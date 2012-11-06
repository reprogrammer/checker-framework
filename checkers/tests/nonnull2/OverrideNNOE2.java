import checkers.nonnull.quals.RequiresNonNull;
import checkers.nullness.quals.*;

class OverrideNNOE {
  static class Super {
    @Nullable Object f;

    @RequiresNonNull("f")
    void call() {}
  }

  static class Sub extends Super {
    @Nullable Object g;

    @Override
    @RequiresNonNull({"f", "g"})
    //:: error: (contracts.precondition.override.invalid)
    void call() {
      g.hashCode();
    }
  }

  public static void main(String[] args) {
    Super s = new Sub();
    s.f = new Object();
    s.call();
  }
}
