
import checkers.interning.quals.*;

public final class SequenceAndIndices<T extends @Interned Object> {
    public T seq;

    public SequenceAndIndices (T seq) {
        this.seq = seq;
    }
}


