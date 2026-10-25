package hudson.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * @author Kohsuke Kawaguchi
 */
class BallColorTest {

    @Test
    void iconClassName() {
        assertEquals("icon-red", BallColor.RED.getIconClassName());
        assertEquals("icon-aborted-anime", BallColor.ABORTED_ANIME.getIconClassName());
    }
}
