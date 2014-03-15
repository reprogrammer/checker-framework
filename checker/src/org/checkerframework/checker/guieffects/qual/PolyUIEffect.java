package org.checkerframework.checker.guieffects.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for the polymorphic effect on methods, or on field accesses.
 *
 * @checker_framework_manual #guieffects-checker GUI Effects Checker
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.FIELD})
public @interface PolyUIEffect {}
