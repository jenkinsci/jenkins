/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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
package hudson.tasks.test;

import hudson.AbortException;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Default partial implementation of {@link TestResultParser} that handles GLOB dereferencing
 * and other checks for user errors, such as misconfigured GLOBs, up-to-date checks on test reports.
 *
 * <p>
 * The instance of the parser will be serialized to the node that performed the build and the parsing will be done
 * remotely on that slave.
 *
 * @since 1.343
 * @author Kohsuke Kawaguchi
 */
public abstract class DefaultTestResultParserImpl extends TestResultParser implements Serializable {
    /**
     * This method is executed on the slave that has the report files to parse test reports and builds {@link TestResult}.
     *
     * @param reportFiles
     *      List of files to be parsed. Never be empty nor null.
     * @param launcher
     *      Can be used to fork processes on the machine where the build is running. Never null.
     * @param listener
     *      Use this to report progress and other problems. Never null.
     *
     * @throws InterruptedException
     *      If the user cancels the build, it will be received as a thread interruption. Do not catch
     *      it, and instead just forward that through the call stack.
     * @throws IOException
     *      If you don't care about handling exceptions gracefully, you can just throw IOException
     *      and let the default exception handling in Hudson takes care of it.
     * @throws AbortException
     *      If you encounter an error that you handled gracefully, throw this exception and Hudson
     *      will not show a stack trace.
     */
    protected abstract TestResult parse(List<File> reportFiles, Launcher launcher, TaskListener listener) throws InterruptedException, IOException;

    @Override
    public TestResult parse(final String testResultLocations, final AbstractBuild build, final Launcher launcher, final TaskListener listener) throws InterruptedException, IOException {
        return build.getWorkspace().act(new FileCallable<TestResult>() {
            final boolean ignoreTimestampCheck = IGNORE_TIMESTAMP_CHECK; // so that the property can be set on the master
            final long buildTime = build.getTimestamp().getTimeInMillis();
            final long nowMaster = System.currentTimeMillis();

            public TestResult invoke(File dir, VirtualChannel channel) throws IOException, InterruptedException {
                final long nowSlave = System.currentTimeMillis();

                // files older than this timestamp is considered stale
                long localBuildTime = buildTime + (nowSlave - nowMaster);

                FilePath[] paths = new FilePath(dir).list(testResultLocations);
                if (paths.length==0)
                    throw new AbortException("No test reports that matches "+testResultLocations+" found. Configuration error?");

                // since dir is local, paths all point to the local files
                List<File> files = new ArrayList<File>(paths.length);
                for (FilePath path : paths) {
                    File report = new File(path.getRemote());
                    if (ignoreTimestampCheck || localBuildTime - 3000 /*error margin*/ < report.lastModified()) {
                        // this file is created during this build
                        files.add(report);
                    }
                }

                if (files.isEmpty()) {
                    // none of the files were new
                    throw new AbortException(
                        String.format(
                        "Test reports were found but none of them are new. Did tests run? \n"+
                        "For example, %s is %s old\n", paths[0].getRemote(),
                        Util.getTimeSpanString(localBuildTime-paths[0].lastModified())));
                }

                return parse(files,launcher,listener);
            }
        });
    }

    private static final long serialVersionUID = 1L;

    public static final boolean IGNORE_TIMESTAMP_CHECK = Boolean.getBoolean(TestResultParser.class.getName()+".ignoreTimestampCheck");
}
