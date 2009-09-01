package hudson.cli

import org.jvnet.hudson.test.HudsonTestCase

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class EnableJobCommandTest extends HudsonTestCase {
    void test1() {
        def p = createFreeStyleProject();

        def cli = new CLI(getURL())

        cli.execute(["disable-job",p.name])
        assertTrue(p.disabled)
        cli.execute(["enable-job",p.name])
        assertFalse(p.disabled)
    }

}