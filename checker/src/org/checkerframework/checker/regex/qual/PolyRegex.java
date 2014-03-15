package org.checkerframework.checker.regex.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

import org.checkerframework.framework.qual.PolymorphicQualifier;
import org.checkerframework.framework.qual.TypeQualifier;

/**
 * A polymorphic qualifier for the Regex type system.
 *
 * <p>
 * Any method written using {@code @PolyRegex} conceptually has two versions:
 * one in which every instance of {@code @PolyRegex String} has been replaced
 * by {@code @Regex String}, and one in which every instance of
 * {@code @PolyRegex String} has been replaced by {@code String}.
 *
 * @checker_framework_manual #regex-checker Regex Checker
 */
@Documented
@TypeQualifier
@PolymorphicQualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface PolyRegex {}
