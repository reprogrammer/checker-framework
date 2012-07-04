import checkers.commitment.quals.*;
import checkers.nonnull.quals.*;

@SuppressWarnings("nonnull") // Commitment flow not implemented yet
public class CommitmentFlow {
	
	@NonNull CommitmentFlow t;
	
	public CommitmentFlow(CommitmentFlow arg) {
		t = arg;
	}
	
	void foo(@Unclassified CommitmentFlow mystery, @Committed CommitmentFlow triedAndTrue) {
		CommitmentFlow local = null;
		
		local = mystery;
		// TODO: this doesn't work right now (no flow) :: error: (method.invocation.invalid)
		local.hashCode();
		
		local = triedAndTrue;
		local.hashCode(); // should determine that it is Committed based on flow
	}
}
