/*
 * The MIT License
 *
 * Copyright (c) 2015 Red Hat, Inc.
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
package hudson.slaves;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import hudson.Functions;
import hudson.model.Node;

import java.util.Collections;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class CommandLauncherTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void commandFails() throws Exception {
        assumeTrue(!Functions.isWindows());
        DumbSlave slave = createSlave("false");

        String log = slave.toComputer().getLog();
        assertTrue(log, slave.toComputer().isOffline());
        assertThat(log, containsString("ERROR: Process terminated with exit code"));
        assertThat(log, not(containsString("ERROR: Process terminated with exit code 0")));
    }

    @Test
    public void commandSuceedsWithoutChannel() throws Exception {
        assumeTrue(!Functions.isWindows());
        DumbSlave slave = createSlave("true");

        String log = slave.toComputer().getLog();
        assertTrue(log, slave.toComputer().isOffline());
        assertThat(log, containsString("ERROR: Process terminated with exit code 0"));
    }

    public DumbSlave createSlave(String command) throws Exception {
        DumbSlave slave;
        synchronized (j.jenkins) {
            slave = new DumbSlave(
                    "dummy",
                    "dummy",
                    j.createTmpDir().getPath(),
                    "1",
                    Node.Mode.NORMAL,
                    "",
                    new CommandLauncher(command),
                    RetentionStrategy.NOOP,
                    Collections.EMPTY_LIST
            );
            j.jenkins.addNode(slave);
        }

        Thread.sleep(100);

        return slave;
    }
}
