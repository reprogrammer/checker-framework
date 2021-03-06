package org.checkerframework.checker.signature.qual;

import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

import org.checkerframework.framework.qual.ImplicitFor;
import org.checkerframework.framework.qual.SubtypeOf;
import org.checkerframework.framework.qual.TypeQualifier;

/**
 * Represents a {@link FieldDescriptor field descriptor} (JVM type format)
 * as defined in the <a
 * href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.2">Java
 * Virtual Machine Specification, section 4.3.2</a>, but <b>not</b> for all
 * array types:  only for an array type whose base type is either a
 * primitive or in the unnamed package.
 *
 * <p>
 * This annotation may not be written in source code; it is an
 * implementation detail of the checker.
 *
 * @checker_framework_manual #signature-checker Signature Checker
 */
@TypeQualifier
@SubtypeOf({ClassGetName.class, FieldDescriptor.class})
@ImplicitFor(stringPatterns="^\\[+([BCDFIJSZ]|L[A-Za-z_][A-Za-z_0-9]*;)$")
// A @Target meta-annotation with an empty argument would prevent programmers
// from writing this in a program, but it might sometimes be useful.
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface FieldDescriptorForArray {}
