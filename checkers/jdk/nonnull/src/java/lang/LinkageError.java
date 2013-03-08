package java.lang;

import checkers.nonnull.quals.Nullable;

public
class LinkageError extends Error {
  private static final long serialVersionUID = 0;
    public LinkageError() {
	super();
    }

    public LinkageError(@Nullable String s) {
	super(s);
    }
}
