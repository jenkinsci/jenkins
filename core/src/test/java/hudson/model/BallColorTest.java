package hudson.model;

import junit.framework.TestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class BallColorTest extends TestCase {
    public void testHtmlColor() {
        assertEquals("#EF2929",BallColor.RED.getHtmlBaseColor());
    }
}
