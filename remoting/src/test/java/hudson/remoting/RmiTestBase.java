/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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
