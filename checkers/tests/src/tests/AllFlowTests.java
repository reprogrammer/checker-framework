package tests;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * A collection of all test targets that allready work with the new dataflow
 * framework. This class will be removed once all tests pass.
 *
 * @author Stefan Heule
 */
@RunWith(Suite.class)
@SuiteClasses({AnnotationBuilderTest.class, BasicEncryptedTest.class,
            BasicSuperSubTest.class, FenumTest.class, FlowTest.class, 
            Flow2Test.class, FrameworkTest.class,
            I18nTest.class, LubGlbTest.class, NonNullTest.class,
            PuritySuggestionsTest.class, RegexTest.class, ReportModifiersTest.class,
            ReportTest.class, ReportTreeKindsTest.class, SignatureTest.class,
            TaintingTest.class, TreeParserTest.class, UnitsTest.class
            // NonNull2Test.class
            })
public class AllFlowTests {
}
