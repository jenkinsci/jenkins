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

package jenkins.security.s2m;

import hudson.FilePath;
import hudson.Functions;
import hudson.model.Slave;
import hudson.remoting.Callable;
import java.io.File;

import org.jenkinsci.remoting.RoleChecker;
import static org.hamcrest.Matchers.*;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

import javax.inject.Inject;

public class DefaultFilePathFilterTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Inject
    AdminWhitelistRule rule;

    @Before
    public void setUp() {
        r.jenkins.getInjector().injectMembers(this);
        rule.setMasterKillSwitch(false);
    }

    @Test public void remotePath() throws Exception {
        Slave s = r.createOnlineSlave();
        FilePath forward = s.getRootPath().child("forward");
        forward.write("hello", null);
        assertEquals("hello", s.getRootPath().act(new LocalCallable(forward)));
        FilePath reverse = new FilePath(new File(r.jenkins.root, "reverse"));
        assertFalse(reverse.exists());
        try {
            s.getChannel().call(new ReverseCallable(reverse));
            fail("should have failed");
        } catch (Exception x) {
            // good

            // make sure that the stack trace contains the call site info to help assist diagnosis
            assertThat(Functions.printThrowable(x), containsString(DefaultFilePathFilterTest.class.getName()+".remotePath"));
        }
        assertFalse(reverse.exists());
        DefaultFilePathFilter.BYPASS = true;
        s.getChannel().call(new ReverseCallable(reverse));
        assertTrue(reverse.exists());
        assertEquals("goodbye", reverse.readToString());
    }

    private static class LocalCallable implements Callable<String,Exception> {
        private final FilePath p;
        LocalCallable(FilePath p) {
            this.p = p;
        }
        @Override public String call() throws Exception {
            assertFalse(p.isRemote());
            return p.readToString();
        }
        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
            throw new NoSuchMethodError(); // simulate legacy Callable impls
        }
    }

    private static class ReverseCallable implements Callable<Void,Exception> {
        private final FilePath p;
        ReverseCallable(FilePath p) {
            this.p = p;
        }
        @Override public Void call() throws Exception {
            assertTrue(p.isRemote());
            p.write("goodbye", null);
            return null;
        }
        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
            throw new NoSuchMethodError(); // simulate legacy Callable impls
        }
    }

}
