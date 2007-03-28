package hudson.scm;

import junit.framework.TestCase;

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

    private void check(String url) {
        assertTrue(SubversionSCM.URL_PATTERN.matcher(url).matches());
    }
}
