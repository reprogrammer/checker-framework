package checkers.flow.analysis;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

import checkers.flow.analysis.checkers.CFAbstractStore;
import checkers.flow.cfg.node.ClassNameNode;
import checkers.flow.cfg.node.ExplicitThisLiteralNode;
import checkers.flow.cfg.node.FieldAccessNode;
import checkers.flow.cfg.node.ImplicitThisLiteralNode;
import checkers.flow.cfg.node.LocalVariableNode;
import checkers.flow.cfg.node.MethodInvocationNode;
import checkers.flow.cfg.node.Node;
import checkers.flow.cfg.node.ValueLiteralNode;
import checkers.flow.util.HashCodeUtils;
import checkers.types.AnnotatedTypeFactory;
import checkers.util.ElementUtils;
import checkers.util.PurityUtils;
import checkers.util.TreeUtils;

/**
 * Collection of classes and helper functions to represent Java expressions
 * about which the dataflow analysis can possibly infer facts. Expressions
 * include:
 * <ul>
 * <li>Field accesses (e.g., <em>o.f</em>)</li>
 * <li>Local variables (e.g., <em>l</em>)</li>
 * <li>This reference (e.g., <em>this</em>)</li>
 * <li>Pure method calls (e.g., <em>o.m()</em>)</li>
 * <li>Unknown other expressions to mark that something else was present.</li>
 * </ul>
 * 
 * @author Stefan Heule
 * 
 */
public class FlowExpressions {

    /**
     * @return The internal representation (as {@link FieldAccess}) of a
     *         {@link FieldAccessNode}. Can contain {@link Unknown} as receiver.
     */
    public static FieldAccess internalReprOfFieldAccess(
            AnnotatedTypeFactory factory, FieldAccessNode node) {
        Receiver receiver;
        Node receiverNode = node.getReceiver();
        receiver = internalReprOf(factory, receiverNode);
        return new FieldAccess(receiver, node);
    }

    /**
     * @return The internal representation (as {@link Receiver}) of any
     *         {@link Node}. Can contain {@link Unknown} as receiver.
     */
    public static Receiver internalReprOf(AnnotatedTypeFactory factory,
            Node receiverNode) {
        Receiver receiver = null;
        if (receiverNode instanceof FieldAccessNode) {
            receiver = internalReprOfFieldAccess(factory,
                    (FieldAccessNode) receiverNode);
        } else if (receiverNode instanceof ImplicitThisLiteralNode
                || receiverNode instanceof ExplicitThisLiteralNode) {
            receiver = new ThisReference(receiverNode.getType());
        } else if (receiverNode instanceof LocalVariableNode) {
            LocalVariableNode lv = (LocalVariableNode) receiverNode;
            receiver = new LocalVariable(lv);
        } else if (receiverNode instanceof ClassNameNode) {
            ClassNameNode cn = (ClassNameNode) receiverNode;
            receiver = new ClassName(cn.getType(), cn.getElement());
        } else if (receiverNode instanceof ValueLiteralNode) {
            ValueLiteralNode vn = (ValueLiteralNode) receiverNode;
            receiver = new ValueLiteral(vn.getType(), vn);
        } else if (receiverNode instanceof MethodInvocationNode) {
            MethodInvocationNode mn = (MethodInvocationNode) receiverNode;
            ExecutableElement invokedMethod = TreeUtils.elementFromUse(mn
                    .getTree());
            if (PurityUtils.hasPurityAnnotation(factory, invokedMethod)) {
                List<Receiver> parameters = new ArrayList<>();
                for (Node p : mn.getArguments()) {
                    parameters.add(internalReprOf(factory, p));
                }
                Receiver methodReceiver = internalReprOf(factory, mn
                        .getTarget().getReceiver());
                receiver = new PureMethodCall(mn.getType(), invokedMethod,
                        methodReceiver, parameters);
            }
        }

        if (receiver == null) {
            receiver = new Unknown(receiverNode.getType());
        }
        return receiver;
    }

    public static abstract class Receiver {
        protected final TypeMirror type;

        public Receiver(TypeMirror type) {
            this.type = type;
        }

        public TypeMirror getType() {
            return type;
        }

        public abstract boolean containsUnknown();

        /**
         * Returns true if and only if the value this expression stands for
         * cannot be changed by a method call. This is the case for local
         * variables, the self reference as wel as final field accesses for
         * whose receiver {@link #isUnmodifiableByOtherCode} is true.
         */
        public abstract boolean isUnmodifiableByOtherCode();

        /**
         * @return True if and only if the two receiver are syntactically
         *         identical.
         */
        public boolean syntacticEquals(Receiver other) {
            return other == this;
        }

        /**
         * @return True if and only if this receiver contains a receiver that is
         *         syntactically equal to {@code other}.
         */
        public boolean containsSyntacticEqualReceiver(Receiver other) {
            return syntacticEquals(other);
        }

        /**
         * Returns true if and only if {@code other} appear anywhere in this
         * receiver or an expression appears in this receiver such that
         * {@code other} might alias this expression.
         * 
         * <p>
         * 
         * Informal examples include:
         * 
         * <pre>
         *   "a".containsAliasOf("a") == true
         *   "x.f".containsAliasOf("x.f") == true
         *   "x.f".containsAliasOf("y.g") == false
         *   "x.f".containsAliasOf("a") == true // unless information about "x != a" is available
         *   "?".containsAliasOf("a") == true // ? is Unknown, and a can be anything
         * </pre>
         */
        public boolean containsAliasOf(CFAbstractStore<?, ?> store,
                Receiver other) {
            return this.equals(other) || store.canAlias(this, other);
        }
    }

    public static class FieldAccess extends Receiver {
        protected Receiver receiver;
        protected Element field;

        public Receiver getReceiver() {
            return receiver;
        }

        public Element getField() {
            return field;
        }

        public FieldAccess(Receiver receiver, FieldAccessNode node) {
            super(node.getType());
            this.receiver = receiver;
            this.field = node.getElement();
        }

        public FieldAccess(Receiver receiver, TypeMirror type,
                Element fieldElement) {
            super(type);
            this.receiver = receiver;
            this.field = fieldElement;
        }

        public boolean isFinal() {
            return ElementUtils.isFinal(field);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || !(obj instanceof FieldAccess)) {
                return false;
            }
            FieldAccess fa = (FieldAccess) obj;
            return fa.getField().equals(getField())
                    && fa.getReceiver().equals(getReceiver());
        }

        @Override
        public int hashCode() {
            return HashCodeUtils.hash(getField(), getReceiver());
        }

        @Override
        public boolean containsAliasOf(CFAbstractStore<?, ?> store,
                Receiver other) {
            return super.containsAliasOf(store, other)
                    || receiver.containsAliasOf(store, other);
        }

        @Override
        public boolean containsSyntacticEqualReceiver(Receiver other) {
            return syntacticEquals(other)
                    || receiver.containsSyntacticEqualReceiver(other);
        }

        @Override
        public boolean syntacticEquals(Receiver other) {
            if (!(other instanceof FieldAccess)) {
                return false;
            }
            FieldAccess fa = (FieldAccess) other;
            return super.syntacticEquals(other)
                    || fa.getField().equals(getField())
                    && fa.getReceiver().syntacticEquals(getReceiver());
        }

        @Override
        public String toString() {
            return receiver + "." + field;
        }

        @Override
        public boolean containsUnknown() {
            return receiver.containsUnknown();
        }

        @Override
        public boolean isUnmodifiableByOtherCode() {
            return isFinal() && getReceiver().isUnmodifiableByOtherCode();
        }
    }

    public static class ThisReference extends Receiver {
        public ThisReference(TypeMirror type) {
            super(type);
        }

        @Override
        public boolean equals(Object obj) {
            return obj != null && obj instanceof ThisReference;
        }

        @Override
        public int hashCode() {
            return HashCodeUtils.hash(0);
        }

        @Override
        public String toString() {
            return "this";
        }

        @Override
        public boolean containsUnknown() {
            return false;
        }

        @Override
        public boolean syntacticEquals(Receiver other) {
            return other instanceof ThisReference;
        }

        @Override
        public boolean isUnmodifiableByOtherCode() {
            return true;
        }
    }

    /**
     * A ClassName represents the occurrence of a class as part of a static
     * field access or method invocation.
     */
    public static class ClassName extends Receiver {
        protected Element element;

        public ClassName(TypeMirror type, Element element) {
            super(type);
            this.element = element;
        }

        public Element getElement() {
            return element;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || !(obj instanceof ClassName)) {
                return false;
            }
            ClassName other = (ClassName) obj;
            return getElement().equals(other.getElement());
        }

        @Override
        public int hashCode() {
            return HashCodeUtils.hash(getElement());
        }

        @Override
        public String toString() {
            return getElement().getSimpleName().toString();
        }

        @Override
        public boolean containsUnknown() {
            return false;
        }

        @Override
        public boolean syntacticEquals(Receiver other) {
            return this.equals(other);
        }

        @Override
        public boolean isUnmodifiableByOtherCode() {
            return true;
        }
    }

    public static class Unknown extends Receiver {
        public Unknown(TypeMirror type) {
            super(type);
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        @Override
        public String toString() {
            return "?";
        }

        @Override
        public boolean containsAliasOf(CFAbstractStore<?, ?> store,
                Receiver other) {
            return true;
        }

        @Override
        public boolean containsUnknown() {
            return true;
        }

        @Override
        public boolean isUnmodifiableByOtherCode() {
            return false;
        }

    }

    public static class LocalVariable extends Receiver {
        protected Element element;

        public LocalVariable(LocalVariableNode localVar) {
            super(localVar.getType());
            this.element = localVar.getElement();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || !(obj instanceof LocalVariable)) {
                return false;
            }
            LocalVariable other = (LocalVariable) obj;
            return other.element.equals(element);
        }

        public Element getElement() {
            return element;
        }

        @Override
        public int hashCode() {
            return HashCodeUtils.hash(element);
        }

        @Override
        public String toString() {
            return element.toString();
        }

        @Override
        public boolean containsUnknown() {
            return false;
        }

        @Override
        public boolean syntacticEquals(Receiver other) {
            if (!(other instanceof LocalVariable)) {
                return false;
            }
            LocalVariable l = (LocalVariable) other;
            return l.getElement().equals(getElement());
        }

        @Override
        public boolean containsSyntacticEqualReceiver(Receiver other) {
            return syntacticEquals(other);
        }

        @Override
        public boolean isUnmodifiableByOtherCode() {
            return true;
        }
    }
    
    public static class ValueLiteral extends Receiver {

        protected final Object value;

        public ValueLiteral(TypeMirror type, ValueLiteralNode node) {
            super(type);
            value = node.getValue();
        }

        @Override
        public boolean containsUnknown() {
            return false;
        }

        @Override
        public boolean isUnmodifiableByOtherCode() {
            return true;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (obj == null || !(obj instanceof ValueLiteral)) {
                return false;
            }
            ValueLiteral other = (ValueLiteral) obj;
            return value.equals(other.value);
        }
        
        @Override
        public int hashCode() {
            return HashCodeUtils.hash(value);
        }
        
        @Override
        public boolean syntacticEquals(Receiver other) {
            return this.equals(other);
        }
    }

    public static class PureMethodCall extends Receiver {

        protected final Receiver receiver;
        protected final List<Receiver> parameters;
        protected final Element method;

        public PureMethodCall(TypeMirror type, Element method,
                Receiver receiver, List<Receiver> parameters) {
            super(type);
            this.receiver = receiver;
            this.parameters = parameters;
            this.method = method;
        }

        @Override
        public boolean containsUnknown() {
            if (receiver.containsUnknown()) {
                return true;
            }
            for (Receiver p : parameters) {
                if (p.containsUnknown()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean isUnmodifiableByOtherCode() {
            return false;
        }

        @Override
        public boolean containsSyntacticEqualReceiver(Receiver other) {
            return syntacticEquals(other) || receiver.syntacticEquals(other);
        }

        @Override
        public boolean syntacticEquals(Receiver other) {
            if (!(other instanceof PureMethodCall)) {
                return false;
            }
            PureMethodCall otherMethod = (PureMethodCall) other;
            if (!receiver.syntacticEquals(otherMethod.receiver)) {
                return false;
            }
            if (parameters.size() != otherMethod.parameters.size()) {
                return false;
            }
            int i = 0;
            for (Receiver p : parameters) {
                if (!p.syntacticEquals(otherMethod.parameters.get(i))) {
                    return false;
                }
                i++;
            }
            return method.equals(otherMethod.method);
        }

        public boolean containsSyntacticEqualParameter(LocalVariable var) {
            for (Receiver p : parameters) {
                if (p.containsSyntacticEqualReceiver(var)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean containsAliasOf(CFAbstractStore<?, ?> store,
                Receiver other) {
            if (receiver.containsAliasOf(store, other)) {
                return true;
            }
            for (Receiver p : parameters) {
                if (p.containsAliasOf(store, other)) {
                    return true;
                }
            }
            return super.containsAliasOf(store, other);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || !(obj instanceof PureMethodCall)) {
                return false;
            }
            PureMethodCall other = (PureMethodCall) obj;
            int i = 0;
            for (Receiver p : parameters) {
                if (!p.equals(other.parameters.get(i))) {
                    return false;
                }
                i++;
            }
            return receiver.equals(other.receiver)
                    && method.equals(other.method);
        }

        @Override
        public int hashCode() {
            int hash = HashCodeUtils.hash(method, receiver);
            for (Receiver p : parameters) {
                hash = HashCodeUtils.hash(hash, p);
            }
            return hash;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            result.append(receiver.toString());
            result.append(".");
            String methodName = method.toString();
            result.append(methodName.substring(0, methodName.length() - 2));
            result.append("(");
            boolean first = true;
            for (Receiver p : parameters) {
                if (!first) {
                    result.append(", ");
                }
                result.append(p.toString());
                first = false;
            }
            result.append(")");
            return result.toString();
        }
    }
}
