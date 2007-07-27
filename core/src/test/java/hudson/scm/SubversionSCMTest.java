package hudson.scm;

import junit.framework.TestCase;

import java.util.Arrays;

/**
 * @author Kohsuke Kawaguchi
 */
public class SubversionSCMTest extends TestCase {
    public void test1() {
        check("http://foobar/");
        check("https://foobar/");
        check("file://foobar/");
        check("svn://foobar/");
        check("svn+ssh://foobar/");
    }

    public void test2() {
        String[] r = "abc\\ def ghi".split("(?<!\\\\)[ \\r\\n]+");
        for (int i = 0; i < r.length; i++) {
            r[i] = r[i].replaceAll("\\\\ "," ");
        }
        System.out.println(Arrays.asList(r));
        assertEquals(r.length,2);
    }

    private void check(String url) {
        assertTrue(SubversionSCM.URL_PATTERN.matcher(url).matches());
    }
}
