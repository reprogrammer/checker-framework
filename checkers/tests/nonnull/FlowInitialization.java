
import checkers.nonnull.quals.*;

public class FlowInitialization {
    
    @NonNull String f;
    @Nullable String g;
    
    //:: error: (commitment.fields.uninitialized)
    public FlowInitialization() {
        
    }
    
    public FlowInitialization(long l) {
        g = "";
        f = g;
    }
    
    //:: error: (commitment.fields.uninitialized)
    public FlowInitialization(boolean b) {
        if (b) {
            f = "";
        }
    }
    
    //:: error: (commitment.fields.uninitialized)
    public FlowInitialization(int i) {
        if (i == 0) {
            throw new RuntimeException();
        }
    }
    
    //:: error: (commitment.fields.uninitialized)
    public FlowInitialization(char c) {
        if (c == 'c') {
            return;
        }
        f = "";
    }
    
    @EnsuresAnnotation(expression="a", annotation=NonNull.class)
    public void setField(@Unclassified Initializer this) {
        a = "";
    }
}
