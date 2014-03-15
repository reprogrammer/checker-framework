package org.checkerframework.checker.nullness.qual;

import java.lang.annotation.*;

import org.checkerframework.framework.qual.*;

/**
 * TODO: document that this is the top type for the KeyFor system.
 *
 * @checker_framework_manual #nullness-checker Nullness Checker
 */
@TypeQualifier
@InvisibleQualifier
@SubtypeOf({})
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface UnknownKeyFor {}
