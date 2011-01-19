import checkers.nullness.quals.*;
import checkers.quals.*;

@TypeQualifier
@SubtypeOf({})
@interface DoesNotUseF {}

public class Uninit11 {

  @Unused(when=DoesNotUseF.class)
  public Object f;

  // parameter x is just to distinguish the overloaded constructors
  public @DoesNotUseF Uninit11(int x) {
  }

  public Uninit11(long x) {
    f = new Object();
  }
  
}

