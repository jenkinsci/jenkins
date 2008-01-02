package hudson;

import junit.framework.TestCase;

import java.util.Collections;

/**
 * @author Kohsuke Kawaguchi
 */
public class EnvVarsTest extends TestCase {
    /**
     * Makes sure that {@link EnvVars} behave in case-insensitive way.
     */
    public void test1() {
        EnvVars ev = new EnvVars(Collections.singletonMap("Path","A:B:C"));
        assertTrue(ev.containsKey("PATH"));
        assertEquals("A:B:C",ev.get("PATH"));
    }
}
