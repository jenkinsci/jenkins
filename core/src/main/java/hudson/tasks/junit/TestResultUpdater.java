/*
 * The MIT License
 *
 * Copyright (c) 2013 Red Hat, Inc.
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
package hudson.tasks.junit;

import hudson.AbortException;
import hudson.model.BuildListener;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

/**
 * Parse report files as they arrive.
 *
 * Build is supposed to start this thread on slave before tests starts in order
 * to watch for a new test report files. Once new reports are discovered
 * {@link #update(TestResult)} method is called. The implementation
 *
 * The task can be adapted for different kinds of jobs overriding not-final methods.
 *
 * @author ogondza
 * @since TODO
 */
public abstract class TestResultUpdater extends Thread {

    protected static final Logger LOGGER = Logger.getLogger(TestResultUpdater.class.getName());

    private final @Nonnull TestResult testResult = new TestResult();
    protected final @Nonnull BuildListener listener;

    private volatile boolean terminated = false;

    public TestResultUpdater(@Nonnull BuildListener listener, @Nonnull String name) {
        super("Test result report watcher for " + name);
        this.listener = listener;
        LOGGER.fine("Creating " + getName());
    }

    /**
     * Determine whether to keep full stdio.
     */
    protected boolean keepLongStdio() {

        return false;
    }

    /**
     * Get files to be included in report.
     */
    abstract protected @Nonnull Iterable<File> reportFiles();

    /**
     * Get the time of the build according to slave's clock.
     */
    abstract protected long buildTime();

    /**
     * Update test result.
     */
    abstract protected void update(@Nonnull TestResult testResult);

    /**
     * Stop the thread.
     */
    public final void terminate() {
        LOGGER.fine("Terminating thread");
        this.terminated = true;
        parse();
        try {
            join();
        } catch (InterruptedException ex) {
            ex.printStackTrace(listener.error("Unable to terminate " + getName()));
        }
    }

    @Override
    public final void run() {

        try {
            while(!terminated) {
                Thread.sleep(5000);
                parse();
            }
        } catch (InterruptedException ex) {

            ex.printStackTrace(listener.error("Unable to parse the result"));
        }
    }

    /**
     * Perform parsing cycle manually.
     */
    public final void parse() {
        LOGGER.fine("Parsing realtime test results");
        try {

            final long started = System.currentTimeMillis();

            final long buildTime = buildTime();
            final Iterable<File> reportFiles = reportFiles();

            // Overloaded methods might have terminated the thread.
            if (terminated) return;

            if (reportFiles == null || !reportFiles.iterator().hasNext()) {
                LOGGER.fine("No reports files to parse");
                return;
            }

            testResult.parse(buildTime, reportFiles);
            LOGGER.fine(String.format("Parsing took %d ms", System.currentTimeMillis() - started));
            update(testResult);
        } catch (AbortException ex) {
            // Thrown when there are no reports or no workspace witch is normal
            // at the beginning the build. This is also a signal that there are
            // no reports to update (already parsed was excluded and no new have
            // arrived so far).
        } catch (IOException ex) {

            ex.printStackTrace(listener.error("Unable to parse the result"));
        }
    }

    public final RemoteHandler handler() {

        return new RemoteHandler() {
            public void terminate() {
                TestResultUpdater.this.terminate();
            }
        };
    }

    /**
     * And interface exposed for purposes of {@link Channel.export}.
     *
     * @author ogondza
     */
    public interface RemoteHandler {
        void terminate();
    }
}
