package hudson.remoting;

import junit.framework.Test;
import junit.framework.TestSuite;

import java.util.Enumeration;

/**
 * {@link TestSuite} that configures {@link RmiTestBase} as they are added.
 *
 * <p>
 * This allows the same test method to be run twice with different
 * {@link ChannelRunner}.
 *
 * @author Kohsuke Kawaguchi
 */
public class ChannelTestSuite extends TestSuite {
    public ChannelTestSuite(Class testClass, Class<? extends ChannelRunner> channelRunner) {
        super(testClass);

        // I can't do this in addTest because it happens in the above constructor!

        Enumeration en = tests();
        while (en.hasMoreElements()) {
            Test test = (Test) en.nextElement();

            if(test instanceof RmiTestBase && channelRunner!=null) {
                ((RmiTestBase)test).setChannelRunner(channelRunner);
            }
        }
    }
}
