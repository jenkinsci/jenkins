package hudson

import org.jvnet.hudson.test.HudsonTestCase

/**
 * First g
 *
 * @author Kohsuke Kawaguchi
 */
public class GroovyTest extends HudsonTestCase {
    void test1() {
        def wc = createWebClient();
        wc.goTo("/");
    }
}