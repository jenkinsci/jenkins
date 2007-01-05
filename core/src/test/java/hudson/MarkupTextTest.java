package hudson;

import junit.framework.TestCase;
import hudson.MarkupText.SubText;

import java.util.regex.Pattern;

/**
 * @author Kohsuke Kawaguchi
 */
public class MarkupTextTest extends TestCase {
    public void test1() {
        MarkupText t = new MarkupText("I fixed issue #155. The rest is trick text: xissue #155 issue #123x");
        for (SubText st : t.findTokens(pattern))
            st.surroundWith("<$1>","<$1>");

        System.out.println(t);
        assertEquals(t.toString(),"I fixed <155>issue #155<155>. The rest is trick text: xissue #155 issue #123x");
    }

    public void testBoundary() {
        MarkupText t = new MarkupText("issue #155---issue #123");
        for (SubText st : t.findTokens(pattern))
            st.surroundWith("<$1>","<$1>");

        System.out.println(t);
        assertEquals(t.toString(),"<155>issue #155<155>---<123>issue #123<123>");
    }

    private static final Pattern pattern = Pattern.compile("issue #([0-9]+)");
}
