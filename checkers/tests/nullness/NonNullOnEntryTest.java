import checkers.nullness.quals.*;

class NonNullOnEntryTest {

	@Nullable Object field1;
	@Nullable Object field2;

	@NonNullOnEntry("field1")
	void method1() {
		field1.toString(); // OK, field1 is known to be non-null
		//:: (dereference.of.nullable)
		field2.toString(); // error, might throw NullPointerException
	}

	void method2() {
		field1 = new Object();
		method1(); // OK, satisfies method precondition
		field1 = null;
		//:: (nonnull.precondition.not.satisfied)
		method1(); // error, does not satisfy method precondition
	}

	protected @Nullable Object field;

	@NonNullOnEntry("field")
	public void requiresNonNullField() {}

	public void clientFail(NonNullOnEntryTest arg1) {
		//:: (nonnull.precondition.not.satisfied)
		arg1.requiresNonNullField();
	}

	public void clientOK(NonNullOnEntryTest arg2) {
		arg2.field = new Object();
		// note that the following line works
		// @NonNull Object o = arg2.field;
		
		arg2.requiresNonNullField(); // OK, field is known to be non-null
	}

	// TODO: forbid the field in @NNOE to be less visible than the method
	// TODO: field shadowing is probably not handled correctly

	class NNOESubTest extends NonNullOnEntryTest {
		public void subClientOK(NNOESubTest arg3) {
			arg3.field = new Object();
			arg3.requiresNonNullField();
		}

		public void subClientFail(NNOESubTest arg4) {
			//:: (nonnull.precondition.not.satisfied)
			arg4.requiresNonNullField();
		}
	}

}