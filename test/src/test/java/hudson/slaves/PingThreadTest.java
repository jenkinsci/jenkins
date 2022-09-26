/*
 * The MIT License
 *
 * Copyright (c) Red Hat, Inc.
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

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;

import hudson.Functions;
import hudson.model.Computer;
import hudson.remoting.Channel;
import hudson.remoting.ChannelClosedException;
import hudson.remoting.PingThread;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import jenkins.security.MasterToSlaveCallable;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author ogondza.
 */
public class PingThreadTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void failedPingThreadResetsComputerChannel() throws Exception {
        assumeFalse("We simulate hung agent by sending the SIGSTOP signal", Functions.isWindows());

        DumbSlave slave = j.createOnlineSlave();
        Computer computer = slave.toComputer();
        Channel channel = (Channel) slave.getChannel();
        long pid = channel.call(new GetPid());

        PingThread pingThread = null;
        for (Thread it : Thread.getAllStackTraces().keySet()) {
            if (it instanceof PingThread && it.getName().endsWith(channel.toString())) {
                pingThread = (PingThread) it;
            }
        }
        assertNotNull(pingThread);

        new ProcessBuilder("kill", "-STOP", Long.toString(pid)).redirectErrorStream(true).start().waitFor();
        Thread.sleep(10000);
        String status = Files.readString(Paths.get("/proc/" + pid + "/stat"), StandardCharsets.UTF_8);
        try {
            assertEquals("basil", status);
        } finally {
            new ProcessBuilder("kill", "-CONT", Long.toString(pid)).redirectErrorStream(true).start().waitFor();
            Thread.sleep(10000);
        }

        // Simulate lost connection
        kill(pid, "-STOP", 'T');
        try {
            // ... do not wait for Ping Thread to notice
            Method onDead = PingThread.class.getDeclaredMethod("onDead", Throwable.class);
            onDead.setAccessible(true);
            onDead.invoke(pingThread, new TimeoutException("No ping"));

            await().pollInterval(250, TimeUnit.MILLISECONDS)
                    .atMost(10, TimeUnit.SECONDS)
                    .until(channel::isClosingOrClosed);
            assertThrows(ChannelClosedException.class, () -> channel.call(new GetPid()));

            assertNull(slave.getComputer().getChannel());
            assertNull(computer.getChannel());
        } finally {
            kill(pid, "-CONT", 'S');
        }
    }

    private static void kill(long pid, String signal, char expectedState)
            throws IOException, InterruptedException {
        Process process =
                new ProcessBuilder("kill", signal, Long.toString(pid))
                        .redirectErrorStream(true)
                        .start();
        int result = process.waitFor();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(output, 0, result);

        await().pollInterval(250, TimeUnit.MILLISECONDS)
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> {
                    try {
                        String status = Files.readString(Paths.get("/proc/" + pid + "/stat"), StandardCharsets.UTF_8);
                        char actualState = status.charAt(status.lastIndexOf(')') + 2);
                        return actualState == expectedState;
                    } catch (NoSuchFileException e) {
                        if (expectedState == 'S') {
                            // As soon as the process resumes, it is going to exit. Do not treat as failure.
                            return true;
                        } else {
                            throw e;
                        }
                    }
                });
    }

    private static final class GetPid extends MasterToSlaveCallable<Long, IOException> {
        @Override public Long call() {
            return ProcessHandle.current().pid();
        }
    }
}
