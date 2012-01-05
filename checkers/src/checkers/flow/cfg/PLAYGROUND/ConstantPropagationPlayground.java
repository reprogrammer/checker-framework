package checkers.flow.cfg.playground;

import checkers.flow.analysis.Analysis;
import checkers.flow.cfg.JavaSource2CFGDOT;
import checkers.flow.constantpropagation.Constant;
import checkers.flow.constantpropagation.ConstantPropagationConditionalTransfer;
import checkers.flow.constantpropagation.ConstantPropagationStore;
import checkers.flow.constantpropagation.ConstantPropagationTransfer;

public class ConstantPropagationPlayground {

	/**
	 * Run constant propagation for a specific file and create a PDF of the CFG
	 * in the end.
	 */
	public static void main(String[] args) {
		
		/* Configuration: change as appropriate */
		String inputFile = "cfg-input.java"; // input file name and path
		String outputFileName = "cfg"; // output file name and path (without extension)
		String method = "test"; // name of the method to analyze
		String clazz = "Test"; // name of the class to consider

		// run the analysis and create a PDF file
		ConstantPropagationTransfer transfer = new ConstantPropagationTransfer();
		ConstantPropagationConditionalTransfer condTransfer = new ConstantPropagationConditionalTransfer();
		Analysis<Constant, ConstantPropagationStore, ConstantPropagationTransfer> analysis = new Analysis<>(
				transfer, condTransfer, transfer);
		JavaSource2CFGDOT.generateDOTofCFG(inputFile, outputFileName, method,
				clazz, true, analysis);
	}

}
