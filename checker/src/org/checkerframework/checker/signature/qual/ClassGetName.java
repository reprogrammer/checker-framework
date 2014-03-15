package org.checkerframework.checker.signature.qual;

import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

import org.checkerframework.framework.qual.ImplicitFor;
import org.checkerframework.framework.qual.SubtypeOf;
import org.checkerframework.framework.qual.TypeQualifier;

/**
 * The type representation used by the {@link Class#getName()},
 * {@link Class#forName(String)}, and {@link Class#forName(String, boolean,
 * ClassLoader)} methods.  This format is:
 * <ul>
 *   <li>for any non-array type, the {@link BinaryName binary name}
 *   <li>for any array type, a format like the {@link FieldDescriptor field descriptor}, but using '.' where the field descriptor uses '/'</li>
 * </ul>
 * <p>
 *
 * Examples include
 * <pre>
 *   java.lang.String
 *   [Ljava.lang.Object;
 *   int
 *   [[[I
 * </pre>
 *
 * @checker_framework_manual #signature-checker Signature Checker
 */
@TypeQualifier
@SubtypeOf(UnannotatedString.class)
@ImplicitFor(stringPatterns="(^[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*)*(\\$[A-Za-z_][A-Za-z_0-9]*)?$)|^\\[+([BCDFIJSZ]|L[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*)*(\\$[A-Za-z_][A-Za-z_0-9]*)?;)$")
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface ClassGetName {}
