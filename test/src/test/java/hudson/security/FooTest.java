package hudson.security;

import junit.framework.TestSuite;

/**
 * @author Kohsuke Kawaguchi
 */
public class FooTest {
    public static TestSuite suite() {
        TestSuite ts = new TestSuite();
        ts.addTestSuite(PermissionGroupTest.class);
        ts.addTestSuite(CliAuthenticationTest.class);
        return ts;
    }
}
