package org.checkerframework.checker.nullness;

import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Modifier;

import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeValidator;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.framework.source.Result;
import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.javacutil.AnnotationUtils;

import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.VariableTree;

public class KeyForVisitor extends BaseTypeVisitor<KeyForAnnotatedTypeFactory> {
    public KeyForVisitor(BaseTypeChecker checker) {
        super(checker);
    }

    /**
     * The type validator to ensure correct usage of ownership modifiers.
     */
    @Override
    protected BaseTypeValidator createTypeValidator() {
        return new KeyForTypeValidator(checker, this, atypeFactory);
    }

    private final static class KeyForTypeValidator extends BaseTypeValidator {

        public KeyForTypeValidator(BaseTypeChecker checker,
                BaseTypeVisitor<?> visitor, AnnotatedTypeFactory atypeFactory) {
            super(checker, visitor, atypeFactory);
        }

        @Override
        public Void visitDeclared(AnnotatedDeclaredType type, Tree p) {
            AnnotationMirror kf = type.getAnnotation(KeyFor.class);
            if (kf != null) {
                List<String> maps = AnnotationUtils.getElementValueArray(kf, "value", String.class, false);

                boolean inStatic = false;
                if (p.getKind() == Kind.VARIABLE) {
                    ModifiersTree mt = ((VariableTree) p).getModifiers();
                    if (mt.getFlags().contains(Modifier.STATIC)) {
                        inStatic = true;
                    }
                }

                for (String map : maps) {
                    if (map.equals("this")) {
                        // this is not valid in static context
                        if (inStatic) {
                            checker.report(
                                    Result.failure("keyfor.type.invalid",
                                            type.getAnnotations(),
                                            type.toString()), p);
                        }
                    } else if (map.matches("#(\\d+)")) {
                        // Accept parameter references
                        // TODO: look for total number of parameters and only
                        // allow the range 0 to n-1
                    } else {
                        // Only other option is local variable and field names?
                        // TODO: go through all possibilities.
                    }
                }
            }

            // TODO: Should BaseTypeValidator be parametric in the ATF?
            if (type.isAnnotatedInHierarchy(((KeyForAnnotatedTypeFactory)atypeFactory).KEYFOR)) {
                return super.visitDeclared(type, p);
            } else {
                // TODO: Something went wrong...
                return null;
            }
        }

        // TODO: primitive types? arrays?
        /*
        @Override
        public Void visitPrimitive(AnnotatedPrimitiveType type, Tree p) {
          return super.visitPrimitive(type, p);
        }

        @Override
        public Void visitArray(AnnotatedArrayType type, Tree p) {
          return super.visitArray(type, p);
        }
        */
    }
}
