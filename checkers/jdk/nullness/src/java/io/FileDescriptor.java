package java.io;

import checkers.nullness.quals.Nullable;


public final class FileDescriptor{
  public final static FileDescriptor in;
  public final static FileDescriptor out;
  public final static FileDescriptor err;
  public FileDescriptor() { throw new RuntimeException("skeleton method"); }
  public boolean valid() { throw new RuntimeException("skeleton method"); }
  public native void sync() throws SyncFailedException;
}
