import org.checkerframework.checker.regex.qual.Regex;

public class RegexUtilTest {
    void packagedRegexUtil(String s) {
        if (org.checkerframework.checker.regex.RegexUtil.isRegex(s, 2)) {
            @Regex(2) String s2 = s;
        }
        @Regex(2) String s2 = org.checkerframework.checker.regex.RegexUtil.asRegex(s, 2);
    }

    void noPackageRegexUtil(String s) {
        if (RegexUtil.isRegex(s, 2)) {
            @Regex(2) String s2 = s;
        }
        @Regex(2) String s2 = RegexUtil.asRegex(s, 2);
    }

    void illegalName(String s) {
        if (IllegalName.isRegex(s, 2)) {
            //:: error: (assignment.type.incompatible)
            @Regex(2) String s2 = s;
        }
        //:: error: (assignment.type.incompatible)
        @Regex(2) String s2 = IllegalName.asRegex(s, 2);
    }

    void illegalNameRegexUtil(String s) {
        if (IllegalNameRegexUtil.isRegex(s, 2)) {
            //:: error: (assignment.type.incompatible)
            @Regex(2) String s2 = s;
        }
        //:: error: (assignment.type.incompatible)
        @Regex(2) String s2 = IllegalNameRegexUtil.asRegex(s, 2);
    }
}

// A dummy RegexUtil class to make sure RegexUtil in no package works.
class RegexUtil {
    public static boolean isRegex(String s, int n) {
        return false;
    }

    public static @Regex String asRegex(String s, int n) {
        return null;
    }
}

// These methods shouldn't work.
class IllegalName {
    public static boolean isRegex(String s, int n) {
        return false;
    }

    public static @Regex String asRegex(String s, int n) {
        return null;
    }
}

// These methods shouldn't work.
class IllegalNameRegexUtil {
    public static boolean isRegex(String s, int n) {
        return false;
    }

    public static @Regex String asRegex(String s, int n) {
        return null;
    }
}
