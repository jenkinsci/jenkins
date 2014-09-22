/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package jenkins.security;

import hudson.FilePath;
import hudson.model.Slave;
import hudson.remoting.Callable;
import java.io.File;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

public class DefaultFilePathFilterTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Test public void remotePath() throws Exception {
        Slave s = r.createOnlineSlave();
        s.getRootPath().child("forward").write("hello", null);
        FilePath reverse = new FilePath(new File(r.jenkins.root, "reverse"));
        assertFalse(reverse.exists());
        try {
            s.getChannel().call(new CallableImpl(reverse));
            fail("should have failed");
        } catch (SecurityException x) {
            // good
        }
        assertFalse(reverse.exists());
        System.setProperty(DefaultFilePathFilter.BYPASS_PROP, "true");
        s.getChannel().call(new CallableImpl(reverse));
        assertTrue(reverse.exists());
        assertEquals("goodbye", reverse.readToString());
    }

    private static class CallableImpl implements Callable<Void,Exception> {
        private final FilePath p;
        CallableImpl(FilePath p) {
            this.p = p;
        }
        @Override public Void call() throws Exception {
            assertTrue(p.isRemote());
            p.write("goodbye", null);
            return null;
        }
    }

}
