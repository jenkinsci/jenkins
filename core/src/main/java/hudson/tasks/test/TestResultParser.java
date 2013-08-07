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
package hudson.tasks.test;

import hudson.AbortException;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import jenkins.model.Jenkins;
import hudson.model.TaskListener;
import hudson.tasks.Publisher;

import java.io.IOException;

import org.apache.commons.lang.NotImplementedException;

/**
 * Parses test result files and builds in-memory representation of it as {@link TestResult}.
 *
 * <p>
 * This extension point encapsulates the knowledge of a particular test report format and its parsing process,
 * thereby improving the pluggability of test result parsing; integration with a new test tool can be done
 * by just writing a parser, without writing a custom {@link Publisher}, and the test reports are displayed
 * with the default UI and recognized by the rest of Hudson as test reports.
 *
 * <p>
 * Most typical implementations of this class should extend from {@link DefaultTestResultParserImpl},
 * which handles a set of default error checks on user inputs. 
 *
 * <p>
 * Parsers are stateless, and the {@link #parse(String, AbstractBuild, Launcher, TaskListener)} method
 * can be concurrently invoked by multiple threads for different builds.
 *
 * @since 1.343
 * @see DefaultTestResultParserImpl
 */
public abstract class TestResultParser implements ExtensionPoint {
    /**
     * Returns a human readable name of the parser, like "JUnit Parser".
     */
    public String getDisplayName() {
        return "Unknown Parser"; 
    }

    /**
     * This text is used in the UI prompt for the GLOB that specifies files to be parsed by this parser.
     * For example, "JUnit XML reports:"
     */
    public String getTestResultLocationMessage() {
        return "Paths to results files to parse:";
    }

    /**
     * All registered {@link TestResultParser}s
     */
    public static ExtensionList<TestResultParser> all() {
        return Jenkins.getInstance().getExtensionList(TestResultParser.class);
    }

    /**
     * Parses the specified set of files and builds a {@link TestResult} object that represents them.
     *
     * <p>
     * The implementation is encouraged to do the following:
     *
     * <ul>
     * <li>
     * If the build is successful but GLOB didn't match anything, report that as an error. This is
     * to detect the error in GLOB. But don't do this if the build has already failed (for example,
     * think of a failure in SCM checkout.)
     *
     * <li>
     * Examine time stamp of test report files and if those are younger than the build, ignore them.
     * This is to ignore test reports created by earlier executions. Take the possible timestamp
     * difference in the master/slave into account.
     * </ul>
     *
     * @param includes
     *      GLOB pattern relative to the {@linkplain AbstractBuild#getWorkspace() workspace} that
     *      specifies the locations of the test result files. Never null.
     * @param excludes
     *      GLOB excluding paths not to parse. null when there is nothing to exclude.
     * @param build
     *      Build for which these tests are parsed. Never null.
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
     *
     * @since XXX
     */
    public TestResult parse(
            String includes, String excludes, AbstractBuild build, Launcher launcher, TaskListener listener
    ) throws InterruptedException, IOException {
        throw new NotImplementedException(
                "User is supposed to implement at least ono of " +
                "parse(String, String, AbstractBuild, Launcher, Listener) or " +
                "parse(String, AbstractBuild, Launcher, Listener)"
        );
    }

    /**
     * @param testResultLocations
     *      GLOB pattern relative to the {@linkplain AbstractBuild#getWorkspace() workspace} that
     *      specifies the locations of the test result files. Never null.
     * @see #parse(String, String, AbstractBuild, Launcher, Listener)
     */
    public TestResult parse(
            String testResultLocations, AbstractBuild build, Launcher launcher, TaskListener listener
    ) throws InterruptedException, IOException {

        return parse(testResultLocations, null, build, launcher, listener);
    }
}
