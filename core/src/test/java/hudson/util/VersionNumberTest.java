package hudson.util;

import junit.framework.TestCase;

/**
 * @author Xavier Le Vourch
 */
public class VersionNumberTest extends TestCase {

    public void testIsNewerThan() {
       assertTrue(new VersionNumber("2.0.*").isNewerThan(new VersionNumber("2.0.1")));
       assertTrue(new VersionNumber("2.0.1").isNewerThan(new VersionNumber("2.0.1-SNAPSHOT")));
       assertTrue(new VersionNumber("2.0.1-SNAPSHOT").isNewerThan(new VersionNumber("2.0.0.99")));
       assertTrue(new VersionNumber("2.0.0.99").isNewerThan(new VersionNumber("2.0.0")));
       assertTrue(new VersionNumber("2.0.0").isNewerThan(new VersionNumber("2.0.ea")));
       assertTrue(new VersionNumber("2.0.ea").isNewerThan(new VersionNumber("2.0")));
    }
    
    public void testEarlyAccess() {
       assertTrue(new VersionNumber("2.0.ea2").isNewerThan(new VersionNumber("2.0.ea1")));
       assertTrue(new VersionNumber("2.0.ea1").isNewerThan(new VersionNumber("2.0.ea")));
       assertEquals(new VersionNumber("2.0.ea"), new VersionNumber("2.0.ea0"));
    }
    
    public void testSnapshots() {
        assertTrue(new VersionNumber("1.12").isNewerThan(new VersionNumber("1.12-SNAPSHOT (private-08/24/2008 12:13-hudson)")));
        assertTrue(new VersionNumber("1.12-SNAPSHOT (private-08/24/2008 12:13-hudson)").isNewerThan(new VersionNumber("1.11")));
    }
}
