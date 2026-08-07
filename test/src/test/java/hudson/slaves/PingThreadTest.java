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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import hudson.Functions;
import hudson.Platform;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author ogondza.
 */
@WithJenkins
class PingThreadTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void failedPingThreadResetsComputerChannel() throws Exception {
        assumeFalse(Functions.isWindows() || Platform.isDarwin(), "We simulate hung agent by sending the SIGSTOP signal");

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

        /*
         * Simulate lost connection by sending a STOP signal. We use the STOP signal rather than the
         * TSTP signal because the latter relies on an interactive terminal, which we do not have in
         * our CI builds. We wait for the signal to be delivered and visible in the
         * /proc/${PID}/stat output for the process because otherwise we would be testing something
         * other than the ping thread.
         */
        kill(pid, "-STOP", 'T');
        try {
            // ... do not wait for Ping Thread to notice
            Method onDead = PingThread.class.getDeclaredMethod("onDead", Throwable.class);
            onDead.setAccessible(true);
            onDead.invoke(pingThread, new TimeoutException("No ping"));

            /*
             * Channel termination happens asynchronously, so wait for the asynchronous activity to
             * complete before proceeding with the test.
             */
            await().pollInterval(250, TimeUnit.MILLISECONDS)
                    .atMost(10, TimeUnit.SECONDS)
                    .until(channel::isClosingOrClosed);
            assertThrows(ChannelClosedException.class, () -> channel.call(new GetPid()));

            assertNull(slave.getComputer().getChannel());
            assertNull(computer.getChannel());
        } finally {
            /*
             * If we fail to wait for the process to resume and start tearing down the test right
             * away, the test teardown process will hang waiting for the remote process to close,
             * which will never happen because the process is suspended. On the other hand, waiting
             * to confirm that the process has resumed via /proc/${PID}/stat is not reliable either,
             * because once the process resumes it will realize that the controller side of the
             * connection has been closed and terminate itself. Therefore we wait until either the
             * process is in the resumed state or has terminated.
             */
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
        assertEquals(0, result, output);

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
