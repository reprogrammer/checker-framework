import checkers.nullness.quals.RequiresNonNull;
import checkers.nullness.quals.*;

import java.util.*;

class NNOEStaticFields {
    static @Nullable String nullable = null;
    static @Nullable String otherNullable = null;

    @RequiresNonNull("nullable")
    void testF() {
        nullable.toString();
    }

    @RequiresNonNull("NNOEStaticFields.nullable")
    void testF2() {
        nullable.toString();
    }

    @RequiresNonNull("nullable")
    void testF3() {
        NNOEStaticFields.nullable.toString();
    }

    @RequiresNonNull("NNOEStaticFields.nullable")
    void testF4() {
        NNOEStaticFields.nullable.toString();
    }

    class Inner {
        void m1(NNOEStaticFields out) {
                NNOEStaticFields.nullable = "haha!";
                out.testF4();
        }

        @RequiresNonNull("NNOEStaticFields.nullable")
        void m2(NNOEStaticFields out) {
                out.testF4();
        }
    }


    //:: error: (flowexpr.parse.error)
    @RequiresNonNull("NoClueWhatThisShouldBe") void testF5() {
        //:: error: (dereference.of.nullable)
        NNOEStaticFields.nullable.toString();
    }

    void trueNegative() {
        //:: error: (dereference.of.nullable)
        nullable.toString();
        //:: error: (dereference.of.nullable)
        otherNullable.toString();
    }

    @RequiresNonNull("nullable")
    void test1() {
        nullable.toString();
        //:: error: (dereference.of.nullable)
        otherNullable.toString();
    }

    @RequiresNonNull("otherNullable")
    void test2() {
        //:: error: (dereference.of.nullable)
        nullable.toString();
        otherNullable.toString();
    }

    @RequiresNonNull({"nullable", "otherNullable"})
    void test3() {
        nullable.toString();
        otherNullable.toString();
    }

    @RequiresNonNull("System.out")
    void test4() {
        @NonNull Object f = System.out;
    }

    ///////////////////////////////////////////////////////////////////////////
    /// Copied from Daikon's ChicoryPremain
    ///

    static class ChicoryPremain1 {

        // Non-null if doPurity == true
        private static @MonotonicNonNull Set<String> pureMethods = null;

        private static boolean doPurity = false;

        @AssertNonNullIfTrue("ChicoryPremain1.pureMethods")
        public static boolean shouldDoPurity() {
            return doPurity;
        }

        @RequiresNonNull("ChicoryPremain1.pureMethods")
        public static Set<String> getPureMethods() {
            return Collections.unmodifiableSet(pureMethods);
        }

    }

    static class ClassInfo1 {
        @SuppressWarnings("contracts.precondition.not.satisfied") // TODO FIXME
        public void initViaReflection() {
            if (ChicoryPremain1.shouldDoPurity()) {
                for (String pureMeth: ChicoryPremain1.getPureMethods()) {
                }
            }
        }
    }

}
