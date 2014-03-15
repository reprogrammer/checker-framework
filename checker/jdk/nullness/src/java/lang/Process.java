package java.lang;

import org.checkerframework.checker.nullness.qual.Nullable;


public abstract class Process{
  public Process() { throw new RuntimeException("skeleton method"); }
  public abstract java.io.OutputStream getOutputStream();
  public abstract java.io.InputStream getInputStream();
  public abstract java.io.InputStream getErrorStream();
  public abstract int waitFor() throws InterruptedException;
  public abstract int exitValue();
  public abstract void destroy();
}
