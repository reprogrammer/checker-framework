package checkers.util;

/*>>>
import checkers.nullness.quals.Nullable;
*/

import checkers.source.Result;
import checkers.types.AnnotatedTypeFactory;

import dataflow.analysis.FlowExpressions;
import dataflow.analysis.FlowExpressions.ClassName;
import dataflow.analysis.FlowExpressions.FieldAccess;
import dataflow.analysis.FlowExpressions.PureMethodCall;
import dataflow.analysis.FlowExpressions.Receiver;
import dataflow.analysis.FlowExpressions.ThisReference;
import dataflow.analysis.FlowExpressions.ValueLiteral;
import dataflow.cfg.node.ImplicitThisLiteralNode;
import dataflow.cfg.node.LocalVariableNode;
import dataflow.cfg.node.MethodInvocationNode;
import dataflow.cfg.node.Node;
import dataflow.util.PurityUtils;

import javacutils.ElementUtils;
import javacutils.InternalUtils;
import javacutils.Resolver;
import javacutils.TreeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Type.ClassType;

/**
 * A collection of helper methods to parse a string that represents a restricted
 * Java expression. Such expressions can be found in annotations (e.g., to
 * specify a pre- or postcondition).
 *
 * @author Stefan Heule
 */
public class FlowExpressionParseUtil {

    /** Regular expression for an identifier */
    protected static final String identifierRegex = "[a-zA-Z_$][a-zA-Z_$0-9]*";
    /** Matches a parameter */
    protected static final Pattern parameterPattern = Pattern
            .compile("^#([1-9]+[0-9]*)$");
    /** Finds all parameters */
    protected static final Pattern parametersPattern = Pattern
            .compile("#([1-9]+[0-9]*)");
    /**
     * Matches the self reference. In the future we could allow "#0" as a
     * synonym for "this".
     */
    protected static final Pattern selfPattern = Pattern.compile("^(this)$");
    /** Matches 'super' */
    protected static final Pattern superPattern = Pattern.compile("^(super)$");
    /** Matches an identifier */
    protected static final Pattern identifierPattern = Pattern.compile("^"
            + identifierRegex + "$");
    /** Matches a method call */
    protected static final Pattern methodPattern = Pattern.compile("^("
            + identifierRegex + ")\\((.*)\\)$");
    /** Matches a field access */
    protected static final Pattern dotPattern = Pattern
            .compile("^([^.]+)\\.(.+)$");
    /** Matches integer literals */
    protected static final Pattern intPattern = Pattern
            .compile("^([1-9][0-9]*)$");
    /** Matches long literals */
    protected static final Pattern longPattern = Pattern
            .compile("^([1-9][0-9]*L)$");
    /** Matches string literals */
    protected static final Pattern stringPattern = Pattern
            .compile("^(\"([^\"\\\\]|\\\\.)*\")$");
    /** Matches the null literal */
    protected static final Pattern nullPattern = Pattern.compile("^(null)$");

    /**
     * Parse a string and return its representation as a {@link Receiver}, or
     * throw an {@link FlowExpressionParseException}. The expression is assumed
     * to be used in the context of a method.
     *
     * @param s
     *            The string to parse.
     * @param context
     *            information about any receiver and arguments
     * @param path
     *            The current tree path.
     * @throws FlowExpressionParseException
     */
    public static FlowExpressions. /*@Nullable*/ Receiver parse(String s,
            FlowExpressionContext context, TreePath path)
            throws FlowExpressionParseException {
        Receiver result = parse(s, context, path, true, true, true, true, true,
                true);
        return result;
    }

    /**
     * Private implementation of {@link #parse} with a choice of which classes
     * of expressions should be parsed.
     */
    private static FlowExpressions. /*@Nullable*/ Receiver parse(String s,
            FlowExpressionContext context, TreePath path, boolean allowSelf,
            boolean allowIdentifier, boolean allowParameter, boolean allowDot,
            boolean allowMethods, boolean allowLiterals)
            throws FlowExpressionParseException {
        s = s.trim();

        Matcher identifierMatcher = identifierPattern.matcher(s);
        Matcher selfMatcher = selfPattern.matcher(s);
        Matcher superMatcher = superPattern.matcher(s);
        Matcher parameterMatcher = parameterPattern.matcher(s);
        Matcher methodMatcher = methodPattern.matcher(s);
        Matcher dotMatcher = dotPattern.matcher(s);
        Matcher intMatcher = intPattern.matcher(s);
        Matcher longMatcher = longPattern.matcher(s);
        Matcher stringMatcher = stringPattern.matcher(s);
        Matcher nullMatcher = nullPattern.matcher(s);

        ProcessingEnvironment env = context.atypeFactory.getProcessingEnv();
        Types types = env.getTypeUtils();

        if (intMatcher.matches() && allowLiterals) {
            int val = Integer.parseInt(s);
            return new ValueLiteral(types.getPrimitiveType(TypeKind.INT), val);
        } else if (nullMatcher.matches() && allowLiterals) {
            return new ValueLiteral(types.getNullType(), (Object) null);
        } else if (longMatcher.matches() && allowLiterals) {
            long val = Long.parseLong(s.substring(0, s.length() - 1));
            return new ValueLiteral(types.getPrimitiveType(TypeKind.LONG), val);
        } else if (stringMatcher.matches() && allowLiterals) {
            TypeElement stringTypeElem = env.getElementUtils().getTypeElement(
                    "java.lang.String");
            return new ValueLiteral(types.getDeclaredType(stringTypeElem),
                    s.substring(1, s.length() - 1));
        } else if (selfMatcher.matches() && allowSelf) {
            // this literal
            return new ThisReference(context.receiver.getType());
        } else if (superMatcher.matches() && allowSelf) {
            // super literal
            List<? extends TypeMirror> superTypes = types
                    .directSupertypes(context.receiver.getType());
            // find class supertype
            TypeMirror superType = null;
            for (TypeMirror t : superTypes) {
                // ignore interface types
                if (!(t instanceof ClassType)) {
                    continue;
                }
                ClassType tt = (ClassType) t;
                if (!tt.isInterface()) {
                    superType = t;
                    break;
                }
            }
            if (superType == null) {
                throw constructParserException(s);
            }
            return new ThisReference(superType);
        } else if (identifierMatcher.matches() && allowIdentifier) {
            Resolver resolver = new Resolver(env);
            try {
                // field access
                TypeMirror receiverType = context.receiver.getType();
                Element fieldElem = null;

                // Search for field in each enclosing class.
                while (receiverType.getKind() == TypeKind.DECLARED) {
                    fieldElem = resolver.findField(s, receiverType, path);
                    if (fieldElem.getKind() == ElementKind.FIELD) {
                        break;
                    }
                    receiverType = ((DeclaredType)receiverType).getEnclosingType();
                }

                if (fieldElem == null || fieldElem.getKind() != ElementKind.FIELD) {
                    throw constructParserException(s);
                }
                TypeMirror fieldType = ElementUtils.getType(fieldElem);
                if (ElementUtils.isStatic(fieldElem)) {
                    Element classElem = fieldElem.getEnclosingElement();
                    Receiver staticClassReceiver = new ClassName(
                            ElementUtils.getType(classElem));
                    return new FieldAccess(staticClassReceiver,
                            fieldType, fieldElem);
                } else {
                    return new FieldAccess(context.receiver,
                            fieldType, fieldElem);
                }
            } catch (Throwable t) {
                try {
                    // class literal
                    Element classElem = resolver.findClass(s, path);
                    TypeMirror classType = ElementUtils.getType(classElem);
                    if (classType == null) {
                        throw constructParserException(s);
                    }
                    return new ClassName(classType);
                } catch (Throwable t2) {
                    throw constructParserException(s);
                }
            }
        } else if (parameterMatcher.matches() && allowParameter) {
            // parameter syntax
            int idx = -1;
            try {
                idx = Integer.parseInt(parameterMatcher.group(1));
            } catch (NumberFormatException e) {
                // cannot occur by the way the pattern is defined (matches only
                // numbers)
                assert false;
            }
            if (idx > context.arguments.size()) {
                throw new FlowExpressionParseException(Result.failure(
                        "flowexpr.parse.index.too.big", Integer.toString(idx)));
            }
            return context.arguments.get(idx - 1);
        } else if (methodMatcher.matches() && allowMethods) {
            String methodName = methodMatcher.group(1);

            // parse parameter list
            String parameterList = methodMatcher.group(2);
            List<Receiver> parameters = ParameterListParser.parseParameterList(
                    parameterList, true, context.useOuterReceiver(), path);

            // get types for parameters
            List<TypeMirror> parameterTypes = new ArrayList<>();
            for (Receiver p : parameters) {
                parameterTypes.add(p.getType());
            }
            Element methodElement = null;
            try {
                // try to find the correct method
                Resolver resolver = new Resolver(env);
                TypeMirror receiverType = context.receiver.getType();

                // Search for method in each enclosing class.
                while (receiverType.getKind() == TypeKind.DECLARED) {
                    methodElement = resolver.findMethod(methodName, receiverType,
                            path, parameterTypes);
                    if (methodElement.getKind() == ElementKind.METHOD) {
                        break;
                    }
                    receiverType = ((DeclaredType)receiverType).getEnclosingType();
                }

                if (methodElement == null || methodElement.getKind() != ElementKind.METHOD) {
                    throw constructParserException(s);
                }
            } catch (Throwable t) {
                throw constructParserException(s);
            }
            // check that the method is pure
            assert methodElement != null;
            if (!PurityUtils.isDeterministic(context.atypeFactory,
                    methodElement)) {
                throw new FlowExpressionParseException(Result.failure(
                        "flowexpr.method.not.deterministic",
                        methodElement.getSimpleName()));
            }
            if (ElementUtils.isStatic(methodElement)) {
                Element classElem = methodElement.getEnclosingElement();
                Receiver staticClassReceiver = new ClassName(
                        ElementUtils.getType(classElem));
                return new PureMethodCall(ElementUtils.getType(methodElement),
                        methodElement, staticClassReceiver, parameters);
            } else {
                TypeMirror methodType = InternalUtils
                        .substituteMethodReturnType(
                                ElementUtils.getType(methodElement),
                                context.receiver.getType());
                return new PureMethodCall(methodType, methodElement,
                        context.receiver, parameters);
            }
        } else if (dotMatcher.matches() && allowDot) {
            String receiverString = dotMatcher.group(1);
            String remainingString = dotMatcher.group(2);

            // Parse the receiver first.
            Receiver receiver = parse(receiverString, context, path);

            // Parse the rest, with a new receiver.
            FlowExpressionContext newContext = context.changeReceiver(receiver);
            return parse(remainingString, newContext, path, false, true, false,
                    true, true, false);
        } else {
            throw constructParserException(s);
        }
    }

    /**
     * Returns a {@link FlowExpressionParseException} for the string {@code s}.
     */
    private static FlowExpressionParseException constructParserException(
            String s) {
        return new FlowExpressionParseException(Result.failure(
                "flowexpr.parse.error", s));
    }

    /**
     * A very simple parser for parameter lists, i.e. strings of the form
     * {@code a, b, c} for some expressions {@code a}, {@code b} and {@code c}.
     *
     * @author Stefan Heule
     */
    private static class ParameterListParser {

        /**
         * Parse a parameter list and return the parameters as a list (or throw
         * a {@link FlowExpressionParseException}).
         */
        private static List<Receiver> parseParameterList(
                String parameterString, boolean allowEmptyList,
                FlowExpressionContext context, TreePath path)
                throws FlowExpressionParseException {
            ArrayList<Receiver> result = new ArrayList<>();
            // the index of the character in 'parameterString' that the parser
            // is currently looking at
            int idx = 0;
            // how deeply are method calls nested at this point? callLevel is 0
            // in the beginning, and increases with every method call by 1. For
            // instance it would be 2 at the end of the following string:
            // "get(get(1,2,"
            int callLevel = 0;
            // is the parser currently in a string literal?
            boolean inString = false;
            while (true) {
                // end of string reached
                if (idx == parameterString.length()) {
                    // finish current param
                    if (inString || callLevel > 0) {
                        throw constructParserException(parameterString);
                    } else {
                        finishParam(parameterString, allowEmptyList, context,
                                path, result, idx);
                        return result;
                    }
                }

                // get next character
                char next = parameterString.charAt(idx);
                idx++;

                // case split on character
                switch (next) {
                case ',':
                    if (inString) {
                        // stay in same state and consume the character
                    } else {
                        if (callLevel == 0) {
                            // parse first parameter
                            finishParam(parameterString, allowEmptyList,
                                    context, path, result, idx - 1);
                            // parse remaining parameters
                            List<Receiver> rest = parseParameterList(
                                    parameterString.substring(idx), false,
                                    context, path);
                            result.addAll(rest);
                            return result;
                        } else {
                            // not the outermost method call, defer parsing of
                            // this parameter list to recursive call.
                        }
                    }
                    break;
                case '"':
                    // start or finish string
                    inString = !inString;
                    break;
                case '(':
                    if (inString) {
                        // stay in same state and consume the character
                    } else {
                        callLevel++;
                    }
                    break;
                case ')':
                    if (inString) {
                        // stay in same state and consume the character
                    } else {
                        if (callLevel == 0) {
                            throw constructParserException(parameterString);
                        } else {
                            callLevel--;
                        }
                    }
                    break;
                default:
                    // stay in same state and consume the character
                    break;
                }
            }
        }

        private static void finishParam(String parameterString,
                boolean allowEmptyList, FlowExpressionContext context,
                TreePath path, ArrayList<Receiver> result, int idx)
                throws FlowExpressionParseException {
            if (idx == 0) {
                if (allowEmptyList) {
                    return;
                } else {
                    throw constructParserException(parameterString);
                }
            } else {
                result.add(parse(parameterString.substring(0, idx), context,
                        path));
            }
        }
    }

    /**
     * Context used to parse a flow expression.
     */
    public static class FlowExpressionContext {
        public final Receiver receiver;
        public final List<Receiver> arguments;
        public final AnnotatedTypeFactory atypeFactory;
        public final Receiver outerReceiver;

        public FlowExpressionContext(Receiver receiver,
                List<Receiver> arguments, AnnotatedTypeFactory factory) {
            assert factory != null;
            this.receiver = receiver;
            this.arguments = arguments;
            this.atypeFactory = factory;
            this.outerReceiver = receiver;
        }

        public FlowExpressionContext(Receiver receiver, Receiver outerReceiver,
                List<Receiver> arguments, AnnotatedTypeFactory factory) {
            assert factory != null;
            this.receiver = receiver;
            this.arguments = arguments;
            this.atypeFactory = factory;
            this.outerReceiver = outerReceiver;
        }

        /**
         * Returns a copy of the context that is identical, but has a different
         * receiver. The outer receiver remains unchanged.
         */
        public FlowExpressionContext changeReceiver(Receiver receiver) {
            return new FlowExpressionContext(receiver, outerReceiver,
                    arguments, atypeFactory);
        }

        /**
         * Returns a copy of the context that is identical, but uses the outer
         * receiver as main receiver.
         */
        public FlowExpressionContext useOuterReceiver() {
            return new FlowExpressionContext(outerReceiver, outerReceiver,
                    arguments, atypeFactory);
        }
    }

    /**
     * @return The list of parameters that occur in {@code s}, identified by the
     *         number of the parameter (starting at 1).
     */
    public static List<Integer> parameterIndices(String s) {
        List<Integer> result = new ArrayList<>();
        Matcher matcher = parametersPattern.matcher(s);
        while (matcher.find()) {
            int idx = Integer.parseInt(matcher.group(1));
            result.add(idx);
        }
        return result;
    }

    /**
     * An exception that indicates a parse error. It contains a {@link Result}
     * that can be used for error reporting.
     */
    public static class FlowExpressionParseException extends Exception {
        private static final long serialVersionUID = 1L;

        protected final Result result;

        public FlowExpressionParseException(Result result) {
            this.result = result;
        }

        public Result getResult() {
            return result;
        }
    }

    /**
     * @return A {@link FlowExpressionContext} for the method {@code node} as
     *         seen at the method declaration.
     */
    public static FlowExpressionContext buildFlowExprContextForDeclaration(
            MethodTree node, Tree classTree, AnnotatedTypeFactory factory) {
        Node receiver = new ImplicitThisLiteralNode(
                InternalUtils.typeOf(classTree));
        Receiver internalReceiver = FlowExpressions.internalReprOf(factory,
                receiver);
        List<Receiver> internalArguments = new ArrayList<>();
        for (VariableTree arg : node.getParameters()) {
            internalArguments.add(FlowExpressions.internalReprOf(factory,
                    new LocalVariableNode(arg)));
        }
        FlowExpressionContext flowExprContext = new FlowExpressionContext(
                internalReceiver, internalArguments, factory);
        return flowExprContext;
    }

    /**
     * @return A {@link FlowExpressionContext} for the method {@code node} as
     *         seen at the method declaration.
     */
    public static FlowExpressionContext buildFlowExprContextForDeclaration(
            MethodTree node, TypeMirror classType, AnnotatedTypeFactory factory) {
        Node receiver = new ImplicitThisLiteralNode(classType);
        Receiver internalReceiver = FlowExpressions.internalReprOf(factory,
                receiver);
        List<Receiver> internalArguments = new ArrayList<>();
        for (VariableTree arg : node.getParameters()) {
            internalArguments.add(FlowExpressions.internalReprOf(factory,
                    new LocalVariableNode(arg)));
        }
        FlowExpressionContext flowExprContext = new FlowExpressionContext(
                internalReceiver, internalArguments, factory);
        return flowExprContext;
    }

    /**
     * @return A {@link FlowExpressionContext} for the method {@code node} as
     *         seen at the method declaration.
     */
    public static FlowExpressionContext buildFlowExprContextForDeclaration(
            MethodTree node, TreePath currentPath, AnnotatedTypeFactory factory) {
        Tree classTree = TreeUtils.enclosingClass(currentPath);
        return buildFlowExprContextForDeclaration(node, classTree, factory);
    }

    /**
     * @return A {@link FlowExpressionContext} for the method {@code node}
     *         (represented as a {@link Node} as seen at the method use (i.e.,
     *         at a method call site).
     */
    public static FlowExpressionContext buildFlowExprContextForUse(
            MethodInvocationNode n, AnnotatedTypeFactory factory) {
        Node receiver = n.getTarget().getReceiver();
        Receiver internalReceiver = FlowExpressions.internalReprOf(factory,
                receiver);
        List<Receiver> internalArguments = new ArrayList<>();
        for (Node arg : n.getArguments()) {
            internalArguments.add(FlowExpressions.internalReprOf(factory, arg));
        }
        FlowExpressionContext flowExprContext = new FlowExpressionContext(
                internalReceiver, internalArguments, factory);
        return flowExprContext;
    }
}
