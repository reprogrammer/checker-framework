package com.sun.javadoc;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;

public abstract interface MemberDoc extends ProgramElementDoc {
  @Pure public abstract boolean isSynthetic();
}
