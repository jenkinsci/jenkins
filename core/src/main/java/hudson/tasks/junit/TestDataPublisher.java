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
import hudson.Launcher;
import hudson.model.*;

import java.io.IOException;

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
     * Called after test results are collected by Hudson, to create a resolver for {@link TestAction}s.
     *
     * @return
     *      can be null to indicate that there's nothing to contribute for this test result.
     */
	public abstract TestResultAction.Data getTestData(
			AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener, TestResult testResult) throws IOException, InterruptedException;

	public static DescriptorExtensionList<TestDataPublisher, Descriptor<TestDataPublisher>> all() {
		return Hudson.getInstance().<TestDataPublisher, Descriptor<TestDataPublisher>>getDescriptorList(TestDataPublisher.class);
	}

}
