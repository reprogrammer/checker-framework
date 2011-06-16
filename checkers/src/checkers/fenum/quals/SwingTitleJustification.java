package checkers.fenum.quals;

import java.lang.annotation.*;

import checkers.quals.*;

/**
 * @author espishak
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
// @Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
@TypeQualifier
@SubtypeOf( { FenumTop.class } )
public @interface SwingTitleJustification {}