package org.checkerframework.checker.signature.qual;

import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

import org.checkerframework.framework.qual.ImplicitFor;
import org.checkerframework.framework.qual.SubtypeOf;
import org.checkerframework.framework.qual.TypeQualifier;

/**
 * Represents a fully-qualified name as defined in the <a href="http://docs.oracle.com/javase/specs/jls/se7/html/jls-6.html#jls-6.7">Java Language Specification, section 6.7</a>.
 * <p>
 *
 * For example, in
 * <pre>
 *  package org.checkerframework.checker.signature;
 *  public class SignatureChecker {
 *    private class Inner {}
 *  }
 * </pre>
 * the fully-qualified names for the two types are org.checkerframework.checker.signature.SignatureChecker
 * and org.checkerframework.checker.signature.SignatureChecker.Inner.
 *
 * <p>
 * Fully-qualified names and {@linkplain BinaryName binary names} are the same
 * for top-level classes and only differ by a '.' vs. '$' for inner classes.
 *
 * @checker_framework_manual #signature-checker Signature Checker
 */
@TypeQualifier
@SubtypeOf(UnannotatedString.class)
@ImplicitFor(stringPatterns="^[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*)*(\\[\\])*$")
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface FullyQualifiedName {}
