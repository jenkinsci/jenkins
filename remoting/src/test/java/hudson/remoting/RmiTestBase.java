package hudson.remoting;

import junit.framework.TestCase;
import junit.framework.Test;
import junit.framework.TestSuite;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hudson.remoting.ChannelRunner.InProcess;

/**
 * Base class for remoting tests.
 * 
 * @author Kohsuke Kawaguchi
 */
public abstract class RmiTestBase extends TestCase {

    protected Channel channel;
    private ChannelRunner channelRunner = new InProcess();

    protected void setUp() throws Exception {
        channel = channelRunner.start();
    }

    protected void tearDown() throws Exception {
        channelRunner.stop(channel);
    }

    /*package*/ void setChannelRunner(Class<? extends ChannelRunner> runner) {
        try {
            this.channelRunner = runner.newInstance();
        } catch (InstantiationException e) {
            throw new Error(e);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        }
    }

    public String getName() {
        return super.getName()+"-"+channelRunner.getName();
    }

    /**
     * Can be used in the suite method of the derived class to build a
     * {@link TestSuite} to run the test with all the available
     * {@link ChannelRunner} configuration.
     */
    protected static Test buildSuite(Class<? extends RmiTestBase> testClass) {
        TestSuite suite = new TestSuite();
        for( Class<? extends ChannelRunner> r : ChannelRunner.LIST ) {
            suite.addTest(new ChannelTestSuite(testClass,r));
        }
        return suite;
    }
}
