import checkers.util.test.*;

import java.util.*;
import checkers.quals.*;
import tests.util.*;

class Postcondition {
    
    String f1, f2, f3;
    Postcondition p;
    
    /***** normal postcondition ******/
    
    @EnsuresAnnotation(expression="f1", annotation=Odd.class)
    void oddF1() {
        f1 = null;
    }
    
    @EnsuresAnnotation(expression="p.f1", annotation=Odd.class)
    void oddF1_1() {
        p.f1 = null;
    }
    
    @EnsuresAnnotation(expression="#1.f1", annotation=Odd.class)
    void oddF1_2(final Postcondition param) {
        param.f1 = null;
    }
    
    @EnsuresAnnotation(expression="f1", annotation=Value.class)
    //:: error: (contracts.postcondition.not.satisfied)
    void valueF1() {
    }
    
    @EnsuresAnnotation(expression="---", annotation=Value.class)
    //:: error: (flowexpr.parse.error)
    void error() {
    }
    
    @EnsuresAnnotation(expression="#1.#2", annotation=Value.class)
    //:: error: (flowexpr.parse.error)
    void error2(final String p1, final String p2) {
    }
    
    @EnsuresAnnotation(expression="f1", annotation=Value.class)
    void exception() {
        throw new RuntimeException();
    }
    
    @EnsuresAnnotation(expression="#1", annotation=Value.class)
    void param1(final @Value String f) {
    }
    @EnsuresAnnotation(expression={"#1","#2"}, annotation=Value.class)
    //:: error: (flowexpr.parameter.not.final)
    void param2(@Value String f, @Value String g) {
    }
    @EnsuresAnnotation(expression="#1", annotation=Value.class)
    //:: error: (flowexpr.parse.index.too.big)
    void param3() {
    }

    // basic postcondition test
    void t1(@Odd String p1, String p2) {
        valueF1();
        //:: error: (assignment.type.incompatible)
        @Odd String l1 = f1;
        oddF1();
        @Odd String l2 = f1;
        
        error();
    }
    
    // test parameter syntax
    void t2(@Odd String p1, String p2) {
        param3();
    }
    
    // postcondition with more complex flow expression
    void tn1(boolean b) {
        //:: error: (assignment.type.incompatible)
        @Odd String l1 = p.f1;
        oddF1_1();
        @Odd String l2 = p.f1;
    }
    
    // postcondition with more complex flow expression
    void tn2(boolean b) {
        Postcondition param = null;
        //:: error: (assignment.type.incompatible)
        @Odd String l1 = param.f1;
        oddF1_2(param);
        @Odd String l2 = param.f1;
    }
    
    /***** many postcondition ******/
    
    @EnsuresAnnotations({
        @EnsuresAnnotation(expression="f1", annotation=Odd.class),
        @EnsuresAnnotation(expression="f2", annotation=Value.class)
    })
    void oddValueF1(@Value String p1) {
        f1 = null;
        f2 = p1;
    }
    
    @EnsuresAnnotations({
        @EnsuresAnnotation(expression="f1", annotation=Odd.class),
        @EnsuresAnnotation(expression="f2", annotation=Value.class)
    })
    //:: error: (contracts.postcondition.not.satisfied)
    void oddValueF1_invalid(@Value String p1) {
    }
    
    @EnsuresAnnotations({
        @EnsuresAnnotation(expression="--", annotation=Odd.class),
    })
    //:: error: (flowexpr.parse.error)
    void error2() {
    }
    
    // basic postcondition test
    void tnm1(@Odd String p1, @Value String p2) {
        //:: error: (assignment.type.incompatible)
        @Odd String l1 = f1;
        //:: error: (assignment.type.incompatible)
        @Value String l2 = f2;
        oddValueF1(p2);
        @Odd String l3 = f1;
        @Value String l4 = f2;
        
        error2();
    }
    
    /***** conditional postcondition ******/
    
    @EnsuresAnnotationIf(result=true, expression="f1", annotation=Odd.class)
    boolean condOddF1(boolean b) {
        if (b) {
            f1 = null;
            return true;
        }
        return false;
    }
    @EnsuresAnnotationIf(result=false, expression="f1", annotation=Odd.class)
    boolean condOddF1False(boolean b) {
        if (b) {
            return true;
        }
        f1 = null;
        return false;
    }
    @EnsuresAnnotationIf(result=false, expression="f1", annotation=Odd.class)
    boolean condOddF1Invalid(boolean b) {
        if (b) {
            f1 = null;
            return true;
        }
        //:: error: (contracts.conditional.postcondition.not.satisfied)
        return false;
    }
    @EnsuresAnnotationIf(result=false, expression="f1", annotation=Odd.class)
    //:: error: (contracts.conditional.postcondition.invalid.returntype)
    void wrongReturnType() {
    }
    @EnsuresAnnotationIf(result=false, expression="f1", annotation=Odd.class)
    //:: error: (contracts.conditional.postcondition.invalid.returntype)
    String wrongReturnType2() {
        f1 = null;
        return "";
    }
    
    // basic conditional postcondition test
    void t3(@Odd String p1, String p2) {
        condOddF1(true);
        //:: error: (assignment.type.incompatible)
        @Odd String l1 = f1;
        if (condOddF1(false)) {
            @Odd String l2 = f1;
        }
        //:: error: (assignment.type.incompatible)
        @Odd String l3 = f1;
    }
    
    // basic conditional postcondition test (inverted)
    void t4(@Odd String p1, String p2) {
        condOddF1False(true);
        //:: error: (assignment.type.incompatible)
        @Odd String l1 = f1;
        if (!condOddF1False(false)) {
            @Odd String l2 = f1;
        }
        //:: error: (assignment.type.incompatible)
        @Odd String l3 = f1;
    }
    
    // basic conditional postcondition test 2
    void t5(boolean b) {
        condOddF1(true);
        if (b) {
            //:: error: (assignment.type.incompatible)
            @Odd String l2 = f1;
        }
    }
    
    /***** many conditional postcondition ******/
    
    @EnsuresAnnotationsIf({
        @EnsuresAnnotationIf(result=true, expression="f1", annotation=Odd.class),
        @EnsuresAnnotationIf(result=false, expression="f1", annotation=Value.class)
    })
    boolean condsOddF1(boolean b, @Value String p1) {
        if (b) {
            f1 = null;
            return true;
        }
        f1 = p1;
        return false;
    }
    
    @EnsuresAnnotationsIf({
        @EnsuresAnnotationIf(result=true, expression="f1", annotation=Odd.class),
        @EnsuresAnnotationIf(result=false, expression="f1", annotation=Value.class)
    })
    boolean condsOddF1_invalid(boolean b, @Value String p1) {
        if (b) {
            //:: error: (contracts.conditional.postcondition.not.satisfied)
            return true;
        }
        //:: error: (contracts.conditional.postcondition.not.satisfied)
        return false;
    }
    
    @EnsuresAnnotationsIf({
        @EnsuresAnnotationIf(result=false, expression="f1", annotation=Odd.class)
    })
    //:: error: (contracts.conditional.postcondition.invalid.returntype)
    String wrongReturnType3() {
        return "";
    }
    
    void t6(@Odd String p1, @Value String p2) {
        condsOddF1(true, p2);
        //:: error: (assignment.type.incompatible)
        @Odd String l1 = f1;
        //:: error: (assignment.type.incompatible)
        @Value String l2 = f1;
        if (condsOddF1(false, p2)) {
            @Odd String l3 = f1;
            //:: error: (assignment.type.incompatible)
            @Value String l4 = f1;
        } else {
            @Value String l5 = f1;
            //:: error: (assignment.type.incompatible)
            @Odd String l6 = f1;
        }
    }
}
