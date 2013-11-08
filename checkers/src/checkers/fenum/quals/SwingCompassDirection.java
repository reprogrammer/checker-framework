package checkers.fenum.quals;

import java.lang.annotation.*;

import checkers.quals.*;

/**
 * @checker_framework_manual #fenum-checker Fake Enum Checker
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
@TypeQualifier
@SubtypeOf(FenumTop.class)
public @interface SwingCompassDirection {}
