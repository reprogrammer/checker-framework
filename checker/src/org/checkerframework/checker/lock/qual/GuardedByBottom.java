
package org.checkerframework.checker.lock.qual;

import java.lang.annotation.*;

import org.checkerframework.framework.qual.DefaultQualifierInHierarchy;
import org.checkerframework.framework.qual.SubtypeOf;
import org.checkerframework.framework.qual.TypeQualifier;

/**
 * The bottom of the guarded-by qualifier hierarchy.
 * <p>
 *
 * This annotation may not be written in source code; it is an
 * implementation detail of the checker.
 *
 * @checker_framework_manual #lock-checker Lock Checker
 */
@TypeQualifier
@SubtypeOf(GuardedBy.class)
@DefaultQualifierInHierarchy
@Documented
@Target({}) // not necessary to be used by the programmer
@Retention(RetentionPolicy.RUNTIME)
public @interface GuardedByBottom {}
