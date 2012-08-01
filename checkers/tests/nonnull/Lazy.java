
import checkers.initialization.quals.*;
import checkers.nonnull.quals.*;

public class Lazy {
    
    @NonNull String f;
    @MonoNonNull String g;
    @MonoNonNull String g2;
    @checkers.nullness.quals.LazyNonNull String _g;
    @checkers.nullness.quals.LazyNonNull String _g2;
    
    // Initialization with null is allowed for legacy reasons.
    @MonoNonNull String init = null;
    
    public Lazy() {
        f = "";
        // does not have to initialize g
    }
    
    void test() {
        g = "";
        test2(); // retain non-null property across method calls
        g.toLowerCase();
    }
    
    void _test() {
        _g = "";
        test2(); // retain non-null property across method calls
        _g.toLowerCase();
    }
    
    void test2() {
    }
    
    void test3() {
        //:: error: (dereference.of.nullable)
        g.toLowerCase();
    }
    
    void test4() {
        //:: error: (assignment.type.incompatible)
        g = null;
        //:: error: (monotonic.type.incompatible)
        g = g2;
    }
    
    void _test3() {
        //:: error: (dereference.of.nullable)
        _g.toLowerCase();
    }
    
    void _test4() {
        //:: error: (assignment.type.incompatible)
        _g = null;
        //:: error: (monotonic.type.incompatible)
        _g = _g2;
    }
}
