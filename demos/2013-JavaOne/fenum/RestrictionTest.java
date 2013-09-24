import android.content.RestrictionEntry;

class RestrictionTest {

    void setRestrictionType(RestrictionEntry re, int rtype) {
	re.setType(5);
	// re.setType(rtype);
    }

    void useRestrictionType(RestrictionEntry re) {
	setRestrictionType(re, 5);
	// setRestrictionType(re, RestrictionEntry.TYPE_BOOLEAN);
    }

    void copyRestrictionType(RestrictionEntry re1, RestrictionEntry re2) {
        int rtype = re1.getType();
        re2.setType(rtype);
    }

}
























/* Local Variables: */
/* compile-command: "javac -cp android.jar:. RestrictionTest.java" */
/* eval: (setq compile-history '("javac -processor checkers.fenum.FenumChecker -Aquals=RestrictionType -Astubs=android.astub -cp android.jar:. RestrictionTest.java")) */
/* End: */
