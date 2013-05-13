package java.lang;

import dataflow.quals.Pure;
import dataflow.quals.SideEffectFree;

import checkers.nullness.quals.Nullable;
import checkers.nullness.quals.Raw;
import checkers.initialization.quals.UnknownInitialization;


public class Throwable implements java.io.Serializable{
    private static final long serialVersionUID = 0L;
  public @SideEffectFree Throwable() { throw new RuntimeException("skeleton method"); }
  public @SideEffectFree Throwable(@Nullable String a1) { throw new RuntimeException("skeleton method"); }
  public @SideEffectFree Throwable(@Nullable String a1, @Nullable Throwable a2) { throw new RuntimeException("skeleton method"); }
  public @SideEffectFree Throwable(@Nullable Throwable a1) { throw new RuntimeException("skeleton method"); }
  public @Pure @Nullable String getMessage() { throw new RuntimeException("skeleton method"); }
  public @Pure @Nullable String getLocalizedMessage() { throw new RuntimeException("skeleton method"); }
  public @Pure @Nullable Throwable getCause() { throw new RuntimeException("skeleton method"); }
  // The signature of initCause should use @PolyRaw as follows, but implementing
  // @PolyRaw is a fair amount of work, so don't bother to do so yet.  See
  // https://code.google.com/p/checker-framework/issues/detail?id=216 .
  // public synchronized @PolyRaw Throwable initCause(@PolyRaw Throwable this, @Nullable Throwable a1) { throw new RuntimeException("skeleton method"); }
  public synchronized @UnknownInitialization @Raw Throwable initCause(@UnknownInitialization @Raw Throwable this, @Nullable Throwable a1) { throw new RuntimeException("skeleton method"); }
  @Pure public @SideEffectFree String toString() { throw new RuntimeException("skeleton method"); }
  public void printStackTrace() { throw new RuntimeException("skeleton method"); }
  public void printStackTrace(java.io.PrintStream a1) { throw new RuntimeException("skeleton method"); }
  public void printStackTrace(java.io.PrintWriter a1) { throw new RuntimeException("skeleton method"); }
  public StackTraceElement[] getStackTrace() { throw new RuntimeException("skeleton method"); }
  public void setStackTrace(StackTraceElement[] a1) { throw new RuntimeException("skeleton method"); }

    public synchronized native Throwable fillInStackTrace();
    private native int getStackTraceDepth();
    private native StackTraceElement getStackTraceElement(int index);

}
