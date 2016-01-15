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
package org.jvnet.hudson.test.junit;

import junit.framework.TestSuite;
import junit.framework.TestResult;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;

/**
 * {@link TestSuite} that requires some set up and tear down for executing nested tests.
 *
 * <p>
 * The failure in the set up or tear down will be reported as a failure.
 *
 * @author Kohsuke Kawaguchi
 */
public class GroupedTest extends TestSuite implements Filterable {
    @Override
    public int countTestCases() {
        return super.countTestCases()+1;
    }

    @Override
    public void run(TestResult result) {
        try {
            setUp();
            try {
                runGroupedTests(result);
            } finally {
                tearDown();
            }
            // everything went smoothly. report a successful test to make the ends meet
            runTest(new FailedTest(getClass(),null),result);
        } catch (Throwable e) {
            // something went wrong
            runTest(new FailedTest(getClass(),e),result);
        }
    }

    /**
     * Executes the nested tests.
     */
    protected void runGroupedTests(TestResult result) throws Exception {
        super.run(result);
    }

    protected void setUp() throws Exception {
    }
    protected void tearDown() throws Exception {
    }

    public void filter(Filter filter) throws NoTestsRemainException {} // SUREFIRE-974

}
