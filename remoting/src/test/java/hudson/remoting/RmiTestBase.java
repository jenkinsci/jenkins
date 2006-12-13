package hudson.remoting;

import junit.framework.TestCase;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hudson.remoting.ChannelRunner.InProcess;

/**
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
}
