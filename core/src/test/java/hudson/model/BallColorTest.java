package hudson.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * @author Kohsuke Kawaguchi
 */
public class BallColorTest {

    @Test
    public void htmlColor() {
        assertEquals("#EF2929",BallColor.RED.getHtmlBaseColor());
    }

    @Test
    public void iconClassName() {
        assertEquals("icon-red",BallColor.RED.getIconClassName());
        assertEquals("icon-aborted-anime",BallColor.ABORTED_ANIME.getIconClassName());
    }
}
