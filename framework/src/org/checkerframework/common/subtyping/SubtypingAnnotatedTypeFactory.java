package org.checkerframework.common.subtyping;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;

public class SubtypingAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

    public SubtypingAnnotatedTypeFactory(BaseTypeChecker checker) {
        super(checker);
        postInit();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Set<Class<? extends Annotation>> createSupportedTypeQualifiers() {

        String qualNames = checker.getOption("quals");
        if (qualNames == null) {
            checker.errorAbort("SubtypingChecker: missing required option: -Aquals");
            return null; // dead code
        }

        Set<Class<? extends Annotation>> qualSet =
            new HashSet<Class<? extends Annotation>>();
        for (String qualName : qualNames.split(",")) {
            try {
                final Class<? extends Annotation> q =
                    (Class<? extends Annotation>)Class.forName(qualName);
                qualSet.add(q);
            } catch (ClassNotFoundException e) {
                checker.errorAbort("SubtypingChecker: could not load class for qualifier: " + qualName + "; ensure that your classpath is correct.");
            }
        }

        return Collections.unmodifiableSet(qualSet);
    }
}