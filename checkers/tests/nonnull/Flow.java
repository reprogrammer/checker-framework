
import checkers.commitment.quals.*;
import checkers.nonnull.quals.*;

public class Flow {
	
	@NonNull String f;
	@NotOnlyCommitted @NonNull String g;
	
	public Flow(String arg) {
		//:: error: (dereference.of.nullable)
		f.toLowerCase();
		//:: error: (dereference.of.nullable)
		g.toLowerCase();
		f = arg;
		g = arg;
		foo();
		f.toLowerCase();
		//:: error: (method.invocation.invalid)
		g.toLowerCase();
	}
	
	void test() {
		@Nullable String s = null;
		s = "a";
		s.toLowerCase();
	}
	
	void test2(@Nullable String s) {
		if (s != null) {
			s.toLowerCase();
		}
	}
	
	void foo(@Unclassified Flow this) {}
	
	// TODO Pure, etc.
}
