package hudson

import org.jvnet.hudson.test.GroovyHudsonTestCase


/**
 * First g
 *
 * @author Kohsuke Kawaguchi
 */
public class GroovyTest extends GroovyHudsonTestCase {
    void test1() {
        def wc = createWebClient();
        wc.goTo("/");
    }
}