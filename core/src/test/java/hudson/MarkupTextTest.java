package hudson;

import junit.framework.TestCase;
import hudson.MarkupText.SubText;

import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Kohsuke Kawaguchi
 */
public class MarkupTextTest extends TestCase {
    public void test1() {
        MarkupText t = new MarkupText("I fixed issue #155. The rest is trick text: xissue #155 issue #123x");
        for (SubText st : t.findTokens(pattern))
            st.surroundWith("<$1>","<$1>");

        assertEquals("I fixed <155>issue #155<155>. The rest is trick text: xissue #155 issue #123x", t.toString());
    }

    public void testBoundary() {
        MarkupText t = new MarkupText("issue #155---issue #123");
        for (SubText st : t.findTokens(pattern))
            st.surroundWith("<$1>","<$1>");

        assertEquals("<155>issue #155<155>---<123>issue #123<123>", t.toString());
    }

    public void testFindTokensOnSubText() {
        MarkupText t = new MarkupText("Fixed 2 issues in this commit, fixing issue 155, 145");
        List<SubText> tokens = t.findTokens(Pattern.compile("issue .*"));
        assertEquals("Expected one token", 1, tokens.size());
        assertEquals("Expected single token was incorrect", "issue 155, 145", tokens.get(0).group(0));
        for (SubText st : tokens.get(0).findTokens(Pattern.compile("([0-9]+)")))
            st.surroundWith("<$1>","<$1>");

        assertEquals("Fixed 2 issues in this commit, fixing issue <155>155<155>, <145>145<145>", t.toString());
    }

    private static final Pattern pattern = Pattern.compile("issue #([0-9]+)");
}
