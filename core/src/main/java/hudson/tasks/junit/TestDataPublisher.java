/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Tom Huybrechts, Yahoo!, Inc.
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

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.*;
import jenkins.model.Jenkins;

import java.io.IOException;
import javax.annotation.Nonnull;

/**
 * Contributes {@link TestAction}s to test results.
 *
 * This enables plugins to annotate test results and provide richer UI, such as letting users
 * claim test failures, allowing people to file bugs, or more generally, additional actions, views, etc.
 *
 * <p>
 * To register your implementation, put {@link Extension} on your descriptor implementation. 
 *
 * @since 1.320
 */
public abstract class TestDataPublisher extends AbstractDescribableImpl<TestDataPublisher> implements ExtensionPoint {

    /**
     * Called after test results are collected by Jenkins, to create a resolver for {@link TestAction}s.
     *
     * @return
     *      can be null to indicate that there's nothing to contribute for this test result.
     * @since TODO
     */
	public TestResultAction.Data contributeTestData(
			Run<?,?> run, @Nonnull FilePath workspace, Launcher launcher,
			TaskListener listener, TestResult testResult) throws IOException, InterruptedException {
        if (run instanceof AbstractBuild && listener instanceof BuildListener) {
            return getTestData((AbstractBuild) run, launcher, (BuildListener) listener, testResult);
        } else {
            throw new AbstractMethodError("you must override contributeTestData");
        }
    }

    @Deprecated
	public TestResultAction.Data getTestData(
			AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener, TestResult testResult) throws IOException, InterruptedException {
        if (Util.isOverridden(TestDataPublisher.class, getClass(), "contributeTestData", Run.class, FilePath.class, Launcher.class, TaskListener.class, TestResult.class)) {
            FilePath workspace = build.getWorkspace();
            if (workspace == null) {
                throw new IOException("no workspace in " + build);
            }
            return contributeTestData(build, workspace, launcher, listener, testResult);
        } else {
            throw new AbstractMethodError("you must override contributeTestData");
        }
    }

	public static DescriptorExtensionList<TestDataPublisher, Descriptor<TestDataPublisher>> all() {
		return Jenkins.getInstance().<TestDataPublisher, Descriptor<TestDataPublisher>>getDescriptorList(TestDataPublisher.class);
	}

}
