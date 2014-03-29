package org.checkerframework.checker.guieffect.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.checkerframework.framework.qual.PolymorphicQualifier;
import org.checkerframework.framework.qual.TypeQualifier;

/**
 * Annotation for the polymorphic-UI effect.
 *
 * @checker_framework_manual #guieffect-checker GUI Effect Checker
 */
@TypeQualifier
@PolymorphicQualifier(UI.class)
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface PolyUI {}
