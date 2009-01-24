package hudson;

import hudson.MarkupText.SubText;

import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Common part between {@link MarkupText} and {@link MarkupText.SubText}.
 *
 * <p>
 * See {@link MarkupText} for more discussion about what this class represents.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.250
 */
public abstract class AbstractMarkupText {
    /*package*/ AbstractMarkupText() {} // limit who can subclass this type.


    /**
     * Returns the plain text portion of this {@link MarkupText} without
     * any markup.
     */
    public abstract String getText();

    /**
     * Adds a start tag and end tag at the specified position.
     *
     * <p>
     * For example, if the text was "abc", then <tt>addMarkup(1,2,"&lt;b>","&lt;/b>")</tt>
     * would generate <tt>"a&lt;b>b&lt;/b>c"</tt>
     */
    public abstract void addMarkup( int startPos, int endPos, String startTag, String endTag );

    /**
     * Adds a start tag and end tag around the entire text
     */
    public final void wrapBy(String startTag, String endTag) {
        addMarkup(0,getText().length(),startTag,endTag);
    }

    /**
     * Find all "tokens" that match the given pattern in this text.
     *
     * <p>
     * A token is like a substring, except that it's aware of word boundaries.
     * For example, while "bc" is a string of "abc", calling {@code findTokens}
     * with "bc" as a pattern on string "abc" won't match anything.
     *
     * <p>
     * This method is convenient for finding keywords that follow a certain syntax
     * from natural text. You can then use {@link MarkupText.SubText#surroundWith(String,String)}
     * to put mark up around such text.
     */
    public List<MarkupText.SubText> findTokens(Pattern pattern) {
        String text = getText();
        Matcher m = pattern.matcher(text);
        List<SubText> r = new ArrayList<SubText>();

        while(m.find()) {
            int idx = m.start();
            if(idx>0) {
                char ch = text.charAt(idx-1);
                if(Character.isLetter(ch) || Character.isDigit(ch))
                    continue;   // not at a word boundary
            }
            idx = m.end();
            if(idx<text.length()) {
                char ch = text.charAt(idx);
                if(Character.isLetter(ch) || Character.isDigit(ch))
                    continue;   // not at a word boundary
            }
            r.add(createSubText(m));
        }

        return r;
    }

    protected abstract SubText createSubText(Matcher m);
}
