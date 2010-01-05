import checkers.interning.quals.*;

import java.util.*;

public class FlowInterning {

  public boolean isSame(Object a, Object b) {
    return ((a == null)
            ? (a == b)
            : (a.equals(b)));
  }

  public void testAppendingChar() {
      String arg = "";
      arg += ' ';

      // Interning checker should NOT suggest == here.
      if (!arg.equals (""));
    }

  public String[] parse (String args) {

    // Split the args string on whitespace boundaries accounting for quoted
    // strings.
    args = args.trim();
    List<String> arg_list = new ArrayList<String>();
    String arg = "";
    char active_quote = 0;
    for (int ii = 0; ii < args.length(); ii++) {
      char ch = args.charAt (ii);
      if ((ch == '\'') || (ch == '"')) {
        arg+= ch;
        ii++;
        while ((ii < args.length()) && (args.charAt(ii) != ch))
          arg += args.charAt(ii++);
        arg += ch;
      } else if (Character.isWhitespace (ch)) {
        // System.out.printf ("adding argument '%s'%n", arg);
        arg_list.add (arg);
        arg = "";
        while ((ii < args.length()) && Character.isWhitespace(args.charAt(ii)))
          ii++;
        if (ii < args.length())
          ii--;
      } else { // must be part of current argument
        arg += ch;
      }
    }
    // Interning checker should NOT suggest == here.
    if (!arg.equals (""))
      arg_list.add (arg);

    String[] argsArray = arg_list.toArray (new String[arg_list.size()]);
    return argsArray;
  }

}
