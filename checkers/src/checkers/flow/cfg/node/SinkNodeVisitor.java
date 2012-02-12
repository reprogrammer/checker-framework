package checkers.flow.cfg.node;

/**
 * A visitor that calls its abstract method <code>visitNode</code> for all
 * {@link Node}s. This is useful to implement a visitor that performs the same
 * operation (e.g., nothing) for most {@link Node}s and only has special
 * behavior for a few.
 * 
 * @author Stefan Heule
 * 
 * @param <R>
 *            Return type of the visitor.
 * @param <P>
 *            Parameter type of the visitor.
 */
public abstract class SinkNodeVisitor<R, P> implements NodeVisitor<R, P> {

	abstract public R visitNode(Node n, P p);

        // Literals
	@Override
	public R visitValueLiteral(ValueLiteralNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitByteLiteral(ByteLiteralNode n, P p) {
		return visitValueLiteral(n, p);
	}

	@Override
	public R visitShortLiteral(ShortLiteralNode n, P p) {
		return visitValueLiteral(n, p);
	}

	@Override
	public R visitIntegerLiteral(IntegerLiteralNode n, P p) {
		return visitValueLiteral(n, p);
	}

	@Override
	public R visitLongLiteral(LongLiteralNode n, P p) {
		return visitValueLiteral(n, p);
	}

	@Override
	public R visitFloatLiteral(FloatLiteralNode n, P p) {
		return visitValueLiteral(n, p);
	}

	@Override
	public R visitDoubleLiteral(DoubleLiteralNode n, P p) {
		return visitValueLiteral(n, p);
	}

	@Override
	public R visitBooleanLiteral(BooleanLiteralNode n, P p) {
		return visitValueLiteral(n, p);
	}

	@Override
	public R visitCharacterLiteral(CharacterLiteralNode n, P p) {
		return visitValueLiteral(n, p);
	}

	@Override
	public R visitStringLiteral(StringLiteralNode n, P p) {
		return visitValueLiteral(n, p);
	}

	@Override
	public R visitNullLiteral(NullLiteralNode n, P p) {
		return visitValueLiteral(n, p);
	}


	@Override
	public R visitAssignment(AssignmentNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitLocalVariable(LocalVariableNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitVariableDeclaration(VariableDeclarationNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitFieldAccess(FieldAccessNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitImplicitThisLiteral(ImplicitThisLiteralNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitExplicitThis(ExplicitThisNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitSuper(SuperNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitReturn(ReturnNode n, P p) {
		return visitNode(n, p);
	};


	// Unary operations
	@Override
	public R visitNumericalMinus(NumericalMinusNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitNumericalPlus(NumericalPlusNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitBitwiseComplement(BitwiseComplementNode n, P p) {
		return visitNode(n, p);
	}


	// Binary operations
	@Override
	public R visitConditionalOr(ConditionalOrNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitStringConcatenate(StringConcatenateNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitNumericalAddition(NumericalAdditionNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitNumericalSubtraction(NumericalSubtractionNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitNumericalMultiplication(NumericalMultiplicationNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitIntegerDivision(IntegerDivisionNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitFloatingDivision(FloatingDivisionNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitIntegerRemainder(IntegerRemainderNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitFloatingRemainder(FloatingRemainderNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitLeftShift(LeftShiftNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitSignedRightShift(SignedRightShiftNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitUnsignedRightShift(UnsignedRightShiftNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitBitwiseAnd(BitwiseAndNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitBitwiseOr(BitwiseOrNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitBitwiseXor(BitwiseXorNode n, P p) {
		return visitNode(n, p);
	}


	// Compound assignments
	@Override
	public R visitStringConcatenateAssignment(StringConcatenateAssignmentNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitNumericalAdditionAssignment(NumericalAdditionAssignmentNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitNumericalSubtractionAssignment(NumericalSubtractionAssignmentNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitNumericalMultiplicationAssignment(NumericalMultiplicationAssignmentNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitIntegerDivisionAssignment(IntegerDivisionAssignmentNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitFloatingDivisionAssignment(FloatingDivisionAssignmentNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitIntegerRemainderAssignment(IntegerRemainderAssignmentNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitFloatingRemainderAssignment(FloatingRemainderAssignmentNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitLeftShiftAssignment(LeftShiftAssignmentNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitSignedRightShiftAssignment(SignedRightShiftAssignmentNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitUnsignedRightShiftAssignment(UnsignedRightShiftAssignmentNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitBitwiseAndAssignment(BitwiseAndAssignmentNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitBitwiseOrAssignment(BitwiseOrAssignmentNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitBitwiseXorAssignment(BitwiseXorAssignmentNode n, P p) {
		return visitNode(n, p);
	}


        // Comparison operations
	@Override
	public R visitLessThan(LessThanNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitLessThanOrEqual(LessThanOrEqualNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitGreaterThan(GreaterThanNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitGreaterThanOrEqual(GreaterThanOrEqualNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitEqualTo(EqualToNode n, P p) {
		return visitNode(n, p);
	}

	@Override
	public R visitNotEqual(NotEqualNode n, P p) {
		return visitNode(n, p);
	}

}
