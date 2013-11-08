package checkers.oigj.quals;

import java.lang.annotation.*;

import checkers.quals.*;

/**
 * @checker_framework_manual #oigj-checker OIGJ Checker
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
@TypeQualifier
@SubtypeOf(Dominator.class)
@DefaultQualifierInHierarchy
public @interface Modifier {}
