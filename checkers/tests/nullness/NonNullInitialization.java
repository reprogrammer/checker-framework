import checkers.nullness.quals.*;

// TODO
// @skip-test
public class NonNullInitialization {
  private String test;
   
  //:: (fields.uninitialized)
  NonNullInitialization() { }

  public static void main(String[] args) {
    NonNullInitialization n = new NonNullInitialization();
    n.test.equals("ASD");
  }
   
}
