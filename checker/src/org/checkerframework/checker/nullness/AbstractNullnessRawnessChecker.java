package org.checkerframework.checker.nullness;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.NonRaw;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;
import org.checkerframework.checker.nullness.qual.Raw;
import org.checkerframework.framework.qual.PolyAll;
import org.checkerframework.framework.qual.TypeQualifiers;
import org.checkerframework.framework.source.SupportedLintOptions;

/**
 * A concrete instantiation of {@link AbstractNullnessChecker} using rawness.
 */
@TypeQualifiers({ Nullable.class, MonotonicNonNull.class, NonNull.class,
        NonRaw.class, Raw.class, PolyNull.class, PolyAll.class })
@SupportedLintOptions({
        AbstractNullnessChecker.LINT_NOINITFORMONOTONICNONNULL,
        AbstractNullnessChecker.LINT_REDUNDANTNULLCOMPARISON,
        // Temporary option to forbid non-null array component types,
        // which is allowed by default.
        // Forbidding is sound and will eventually be the only possibility.
        // Allowing is unsound but permitted until flow-sensitivity changes are
        // made.
        "arrays:forbidnonnullcomponents" })
public class AbstractNullnessRawnessChecker extends AbstractNullnessChecker {

    public AbstractNullnessRawnessChecker() {
        super(false);
    }

}
