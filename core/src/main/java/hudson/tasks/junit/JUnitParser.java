/*
 * The MIT License
 * 
 * Copyright (c) 2009, Yahoo!, Inc.
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

import hudson.model.TaskListener;
import hudson.tasks.test.TestResultParser;
import hudson.model.AbstractBuild;
import hudson.*;
import hudson.remoting.VirtualChannel;

import java.io.IOException;
import java.io.File;

import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.DirectoryScanner;

/**
 * Parse some JUnit xml files and generate a TestResult containing all the
 * results parsed. 
 */
@Extension
public class JUnitParser extends TestResultParser {

    private final boolean keepLongStdio;

    /** TODO TestResultParser.all does not seem to ever be called so why must this be an Extension? */
    @Deprecated
    public JUnitParser() {
        this(false);
    }

    /**
     * @param keepLongStdio if true, retain a suite's complete stdout/stderr even if this is huge and the suite passed
     * @since 1.358
     */
    public JUnitParser(boolean keepLongStdio) {
        this.keepLongStdio = keepLongStdio;
    }

    @Override
    public String getDisplayName() {
        return Messages.JUnitParser_DisplayName();
    }

    @Override
    public String getTestResultLocationMessage() {
        return Messages.JUnitParser_TestResultLocationMessage();
    }

    @Override
    public TestResult parse(String testResultLocations,
                                       AbstractBuild build, Launcher launcher,
                                       TaskListener listener)
            throws InterruptedException, IOException
    {
        final long buildTime = build.getTimestamp().getTimeInMillis();
        final long timeOnMaster = System.currentTimeMillis();

        // [BUG 3123310] TODO - Test Result Refactor: review and fix TestDataPublisher/TestAction subsystem]
        // also get code that deals with testDataPublishers from JUnitResultArchiver.perform
        
        FilePath workspace = build.getWorkspace();
        if (workspace == null) {
            throw new AbortException(Messages.JUnitParser_no_workspace_found(build));
        }
        return workspace.act(new ParseResultCallable(testResultLocations, buildTime, timeOnMaster, keepLongStdio));
    }

    private static final class ParseResultCallable implements
            FilePath.FileCallable<TestResult> {
        private final long buildTime;
        private final String testResults;
        private final long nowMaster;
        private final boolean keepLongStdio;

        private ParseResultCallable(String testResults, long buildTime, long nowMaster, boolean keepLongStdio) {
            this.buildTime = buildTime;
            this.testResults = testResults;
            this.nowMaster = nowMaster;
            this.keepLongStdio = keepLongStdio;
        }

        public TestResult invoke(File ws, VirtualChannel channel) throws IOException {
            final long nowSlave = System.currentTimeMillis();

            FileSet fs = Util.createFileSet(ws, testResults);
            DirectoryScanner ds = fs.getDirectoryScanner();

            String[] files = ds.getIncludedFiles();
            if (files.length == 0) {
                // no test result. Most likely a configuration
                // error or fatal problem
                throw new AbortException(Messages.JUnitResultArchiver_NoTestReportFound());
            }

            TestResult result = new TestResult(buildTime + (nowSlave - nowMaster), ds, keepLongStdio);
            result.tally();
            return result; 
        }
    }

}
