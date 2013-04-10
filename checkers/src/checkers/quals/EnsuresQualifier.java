package checkers.quals;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A postcondition annotation to indicate that a method ensures certain
 * expressions to have a certain type qualifier once the method has successfully
 * terminated. The expressions for which the qualifier must hold after the
 * methods execution are indicated by {@code expression} and are specified using
 * a string. The qualifier is specified by {@code qualifier}.
 *
 * @author Stefan Heule
 * @see <a
 *      href="http://types.cs.washington.edu/checker-framework/current/checkers-manual.html#java-expressions-as-arguments">Syntax
 *      of Java expressions</a>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.CONSTRUCTOR })
public @interface EnsuresQualifier {
    /**
     * The Java expressions for which the qualifier holds after successful
     * method termination.
     *
     * @see <a
     *      href="http://types.cs.washington.edu/checker-framework/current/checkers-manual.html#java-expressions-as-arguments">Syntax
     *      of Java expressions</a>
     */
    String[] expression();

    /**
     * The qualifier that is guaranteed to hold on successfull termination of
     * the method.
     */
    Class<? extends Annotation> qualifier();
}
