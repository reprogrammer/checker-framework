import org.checkerframework.checker.nullness.qual.Nullable;
import java.util.Arrays;

public class CopyOfArray {
    protected void makeCopy(Object[] args) {
        //:: error: (assignment.type.incompatible)
        Object[] copy = Arrays.copyOf(args, args.length);
    }
    protected void makeCopyGood(Object[] args) {
        @Nullable Object @Nullable [] copy = Arrays.copyOf(args, args.length);
    }
}
