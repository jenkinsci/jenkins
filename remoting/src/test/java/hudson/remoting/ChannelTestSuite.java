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
