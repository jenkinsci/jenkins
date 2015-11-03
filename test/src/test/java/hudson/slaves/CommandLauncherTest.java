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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import hudson.Functions;
import hudson.model.Node;

import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RandomlyFails;

public class CommandLauncherTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @RandomlyFails("EOFException as in commandSucceedsWithoutChannel")
    public void commandFails() throws Exception {
        assumeTrue(!Functions.isWindows());
        DumbSlave slave = createSlave("false");

        String log = slave.toComputer().getLog();
        assertTrue(log, slave.toComputer().isOffline());
        assertThat(log, containsString("ERROR: Process terminated with exit code"));
        assertThat(log, not(containsString("ERROR: Process terminated with exit code 0")));
    }

    @RandomlyFails("Sometimes gets `EOFException: unexpected stream termination` before then on CI builder; maybe needs to wait in a loop for a message to appear?")
    @Test
    public void commandSucceedsWithoutChannel() throws Exception {
        assumeTrue(!Functions.isWindows());
        DumbSlave slave = createSlave("true");

        String log = slave.toComputer().getLog();
        assertTrue(log, slave.toComputer().isOffline());
        assertThat(log, containsString("ERROR: Process terminated with exit code 0"));
    }

    public DumbSlave createSlave(String command) throws Exception {
        DumbSlave slave;
        synchronized (j.jenkins) { // TODO this lock smells like a bug post 1.607
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

        try {
            slave.toComputer().connect(false).get(1, TimeUnit.SECONDS);
            fail("the slave was not supposed to connect successfully");
        } catch (ExecutionException e) {
            // ignore, we just want to
        }

        return slave;
    }
}
