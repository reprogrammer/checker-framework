package tests;

import checkers.util.test.ParameterizedCheckerTest;

import java.io.File;
import java.util.Collection;

import org.junit.runners.Parameterized.Parameters;

/**
 */
public class PolyAllTest extends ParameterizedCheckerTest {

    public PolyAllTest(File testFile) {
        super(testFile,
                polyall.PolyAllChecker.class,
                "polyall",
                "-Anomsgtext");
    }

    @Parameters
    public static Collection<Object[]> data() {
        return testFiles("polyall");
    }
}