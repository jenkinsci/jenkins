/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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

package org.jvnet.hudson.test;

import hudson.Extension;
import hudson.console.AnnotatedLargeText;
import hudson.console.LineTransformationOutputStream;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.rules.ExternalResource;

/**
 * Echoes build output to standard error as it arrives.
 * Usage: <pre>{@code @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();}</pre>
 * Should work in combination with {@link JenkinsRule} or {@link RestartableJenkinsRule}.
 * @see JenkinsRule#waitForCompletion
 * @see JenkinsRule#waitForMessage
 * @since TODO
 */
public final class BuildWatcher extends ExternalResource {

    private static boolean active;
    private static final Map<File,RunningBuild> builds = new ConcurrentHashMap<File,RunningBuild>();

    private Thread thread;

    @Override protected void before() throws Throwable {
        active = true;
        thread = new Thread("watching builds") {
            @Override public void run() {
                try {
                    while (active) {
                        for (RunningBuild build : builds.values()) {
                            build.copy();
                        }
                        Thread.sleep(50);
                    }
                } catch (InterruptedException x) {
                    // stopped
                }
                // last chance
                for (RunningBuild build : builds.values()) {
                    build.copy();
                }
            }
        };
        thread.setDaemon(true);
        thread.start();
    }

    @Override protected void after() {
        active = false;
        thread.interrupt();
    }

    @Extension public static final class Listener extends RunListener<Run<?,?>> {

        @Override public void onStarted(Run<?,?> r, TaskListener listener) {
            if (!active) {
                return;
            }
            RunningBuild build = new RunningBuild(r);
            RunningBuild orig = builds.put(r.getLogFile(), build);
            if (orig != null) {
                System.err.println(r + " was started twice?!");
            }
        }

        @Override public void onFinalized(Run<?,?> r) {
            if (!active) {
                return;
            }
            RunningBuild build = builds.remove(r.getLogFile());
            if (build != null) {
                build.copy();
            } else {
                System.err.println(r + " was finalized but not started?!");
            }
        }

    }

    private static final class RunningBuild {

        private final AnnotatedLargeText<?> log;
        private final OutputStream sink;
        private long pos;

        RunningBuild(Run<?,?> r) {
            log = r.getLogText();
            sink = new LogLinePrefixOutputFilter(System.err, "[" + r + "] ");
        }

        synchronized void copy() {
            try {
                pos = log.writeLogTo(pos, sink);
                // Note that !log.isComplete() after the initial call to copy, even if the build is complete, because Run.getLogText never calls markComplete!
                // That is why Run.writeWholeLogTo calls getLogText repeatedly.
                // Even if it did call markComplete this might not work from RestartableJenkinsRule since you would have a different Run object after the restart.
                // Anyway we can just rely on onFinalized to let us know when to stop.
            } catch (FileNotFoundException x) {
                // build deleted or not started
            } catch (Exception x) {
                x.printStackTrace();
            }
        }

    }

    // Copied from WorkflowRun.
    private static final class LogLinePrefixOutputFilter extends LineTransformationOutputStream {

        private final PrintStream logger;
        private final String prefix;

        LogLinePrefixOutputFilter(PrintStream logger, String prefix) {
            this.logger = logger;
            this.prefix = prefix;
        }

        @Override protected void eol(byte[] b, int len) throws IOException {
            logger.append(prefix);
            logger.write(b, 0, len);
        }

    }

}
