package java.lang;

import org.checkerframework.checker.nullness.qual.Nullable;

abstract public
class VirtualMachineError extends Error {
    private static final long serialVersionUID = 4161983926571568670L;

    public VirtualMachineError() {
	super();
    }

    public VirtualMachineError(@Nullable String s) {
	super(s);
    }
}
