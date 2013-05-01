package net.i2p.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DataHelper was getting too big so putting new stuff here.
 *
 * @since 0.8.13
 */
public abstract class StringUtil {

    /**
     * http://unicode.org/standard/reports/tr13/tr13-5.html
     */
    public static final String NEWLINES = "\r\u000b\u000c\n\u0085\u2028\u2029";

    /**
     * ' ' is first element.
     *
     * http://en.wikipedia.org/wiki/Whitespace_character#Unicode
     * http://en.wikipedia.org/wiki/Unicode_control_characters
     * http://docs.oracle.com/javase/1.5.0/docs/api/java/lang/Character.html#isWhitespace%28char%29
     * http://www.cs.tut.fi/~jkorpela/chars/spaces.html
     * http://closingbraces.net/2008/11/11/javastringtrim/
     */
    public static final String WHITESPACE =
                  ' ' +
                  // unicode
                  NEWLINES +
                  "\t\u00a0\u1680\u180e" +
                  "\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007" +
                  "\u2008\u2009\u200a\u202f\u205f\u3000" +
                  // Java definition
                  "\u001c\u001d\u001e\u001f" +
                  // all the other control codes
                  "\u0000\u0001\u0002\u0003\u0004\u0005\u0006\u0007" +
                  "\u0008\u000e\u000f\u0010\u0011\u0012\u0013\u0014" +
                  "\u0015\u0016\u0017\u0018\u0019\u001a\u001b\u007f" +
                  "\u0080\u0081\u0082\u0083\u0084\u0086\u0087" +
                  "\u0088\u0089\u008a\u008b\u008c\u008d\u008e\u008f" +
                  "\u0090\u0091\u0092\u0093\u0094\u0095\u0096\u0097" +
                  "\u0099\u0099\u009a\u009b\u009c\u009d\u009e\u009f" +
                  // zero-width
                  "\u200b\ufeff";

    private static final Pattern whiteStart = Pattern.compile("^[" + WHITESPACE + "]++");
    private static final Pattern whiteEnd = Pattern.compile("[" + WHITESPACE + "]++$");

    private static String ltrim(String text) {
        Matcher mStart = whiteStart.matcher(text);
        return mStart.find() ? text.substring(mStart.end()) : text;
    }

    private static String rtrim(String text) {
        Matcher mEnd = whiteEnd.matcher(text);
        return mEnd.find() ? text.substring(0, mEnd.start()) : text;
    }

    /**
     *  Trim darn near everything.
     *  See WHITESPACE for definition, references, and why String.trim() is bad.
     *  Adapted from https://gist.github.com/1173941
     *  apparently public domain
     *  @return "" for null
     */
    public static String trim(String text) {
        if (text == null)
            return "";
        return rtrim(ltrim(text));
    }

/****
    public static void main(String[] args) {
        if (args.length <= 0) {
            System.err.println("Usage: StringUtil ' trimme '");
            System.exit(1);
        }
        System.out.println('"' + trim(args[0]) + '"');
    }
****/
}
