package checkers.flow.analysis.checkers;

import java.util.Collections;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;

import checkers.flow.analysis.ConditionalTransferResult;
import checkers.flow.analysis.FlowExpressions.Receiver;
import checkers.flow.analysis.RegularTransferResult;
import checkers.flow.analysis.TransferInput;
import checkers.flow.analysis.TransferResult;
import checkers.flow.cfg.node.ClassNameNode;
import checkers.flow.cfg.node.IntegerLiteralNode;
import checkers.flow.cfg.node.MethodAccessNode;
import checkers.flow.cfg.node.MethodInvocationNode;
import checkers.flow.cfg.node.Node;
import checkers.flow.util.FlowExpressionParseUtil;
import checkers.flow.util.FlowExpressionParseUtil.FlowExpressionContext;
import checkers.flow.util.FlowExpressionParseUtil.FlowExpressionParseException;
import checkers.regex.RegexAnnotatedTypeFactory;
import checkers.regex.quals.Regex;

public class RegexTransfer extends
        CFAbstractTransfer<CFValue, CFStore, RegexTransfer> {

    /** Like super.analysis, but more specific type. */
    protected RegexAnalysis analysis;

    public RegexTransfer(RegexAnalysis analysis) {
        super(analysis);
        this.analysis = analysis;
    }

    public TransferResult<CFValue, CFStore> visitMethodInvocation(
            MethodInvocationNode n, TransferInput<CFValue, CFStore> in) {
        TransferResult<CFValue, CFStore> result = super.visitMethodInvocation(
                n, in);

        // refine result for some helper methods
        MethodAccessNode target = n.getTarget();
        ExecutableElement method = target.getMethod();
        Node receiver = target.getReceiver();
        if (receiver instanceof ClassNameNode) {
            ClassNameNode cn = (ClassNameNode) receiver;
            String receiverName = cn.getElement().toString();
            for (String clazz : RegexAnnotatedTypeFactory.regexUtilClasses) {
                // RegexUtil.isRegex(s, groups) method
                if (receiverName.equals(clazz)
                        && method.toString().equals(
                                "isRegex(java.lang.String,int)")) {
                    CFStore thenStore = result.getRegularStore();
                    CFStore elseStore = thenStore.copy();
                    ConditionalTransferResult<CFValue, CFStore> newResult = new ConditionalTransferResult<>(
                            result.getResultValue(), thenStore, elseStore);
                    FlowExpressionContext context = FlowExpressionParseUtil
                            .buildFlowExprContextForUse(n, analysis.getEnv());
                    try {
                        Receiver firstParam = FlowExpressionParseUtil.parse(
                                "#1", context,
                                analysis.factory.getPath(n.getTree()));
                        // add annotation with correct group count (if possible,
                        // regex annotation without count otherwise)
                        Node count = n.getArgument(1);
                        if (count instanceof IntegerLiteralNode) {
                            IntegerLiteralNode iln = (IntegerLiteralNode) count;
                            Integer groupCount = iln.getValue();
                            RegexAnnotatedTypeFactory f = (RegexAnnotatedTypeFactory) analysis.factory;
                            AnnotationMirror regexAnnotation = f
                                    .createRegexAnnotation(groupCount);
                            thenStore.insertValue(firstParam, regexAnnotation);
                        } else {
                            AnnotationMirror regexAnnotation = analysis.factory
                                    .annotationFromClass(Regex.class);
                            thenStore.insertValue(firstParam, regexAnnotation);
                        }
                    } catch (FlowExpressionParseException e) {
                        assert false;
                    }
                    return newResult;
                }

                // RegexUtil.asRegex(s, groups) method
                if (receiverName.equals(clazz)
                        && method.toString().equals(
                                "isRegex(java.lang.String,int)")) {
                    // add annotation with correct group count (if possible,
                    // regex annotation without count otherwise)
                    AnnotationMirror regexAnnotation;
                    Node count = n.getArgument(1);
                    if (count instanceof IntegerLiteralNode) {
                        IntegerLiteralNode iln = (IntegerLiteralNode) count;
                        Integer groupCount = iln.getValue();
                        RegexAnnotatedTypeFactory f = (RegexAnnotatedTypeFactory) analysis.factory;
                        regexAnnotation = f.createRegexAnnotation(groupCount);
                    } else {
                        regexAnnotation = analysis.factory
                                .annotationFromClass(Regex.class);
                    }
                    CFValue newResultValue = analysis
                            .createAbstractValue(Collections
                                    .singleton(regexAnnotation));
                    return new RegularTransferResult<>(newResultValue,
                            result.getRegularStore());
                }
            }
        }

        return result;
    };
}
