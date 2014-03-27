package myquals;

import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

import org.checkerframework.framework.qual.SubtypeOf;
import org.checkerframework.framework.qual.TypeQualifier;


/**
 * Denotes that the representation of an object is encrypted.
 */
@TypeQualifier
@SubtypeOf(PossibleUnencrypted.class)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface Encrypted {}
