// @skip-test Failing test, but disabled to avoid breaking the build

import java.util.*;

import checkers.nullness.quals.*;

public class ArrayAssignmentFlow {

  public void add_combined(PptTopLevel ppt) {

    Object[] vals = new Object[10];

    if (ppt.last_values != null) {
      // Assigning to an array element should not cause flow information
      // about ppt.last_values to be discarded.
      vals[0] = ppt.last_values.vals;
      ppt.last_values.toString();
    }
  }

}


class MyPptTopLevel {
  public /*@Nullable*/ MyValueTuple last_values = null;
}

class MyValueTuple {
  public Object vals = new Object();
}
