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

import hudson.tasks.junit.TestAction;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.Result;

import java.util.Collection;

import static java.util.Collections.emptyList;


/**
 * A class that represents a general concept of a test result, without any
 * language or implementation specifics.
 * Subclasses must add @Exported annotation to the fields they want to export.
 *
 * @since 1.343
 */
public abstract class TestResult extends TestObject {

    /**
     * If the concept of a parent action is important to a subclass, then it should
     * provide a non-noop implementation of this method. 
     * @param action
     */
    public void setParentAction(AbstractTestResultAction action) {
    }

    /**
     * Returns the action that points to the top level test result includes
     * this test result.
     */
    public AbstractTestResultAction getParentAction() {
        return getOwner().getTestResultAction();
    }
    
    /**
     * Request that the result update its counts of its children. Does not
     * require a parent action or owner or siblings. Subclasses should
     * implement this, unless they are *always* in a tallied state.  
     */
    public void tally() {
    }
    
    /**
     * Sets the parent test result
     * @param parent
     */
    public void setParent(TestObject parent) {
    }

    /**
     * Gets the human readable title of this result object.
     */
    public /* abstract */ String getTitle(){
        return "";
    }

    /**
     * Mark a build as unstable if there are failures. Otherwise, leave the
     * build result unchanged.
     *
     * @return {@link Result#UNSTABLE} if there are test failures, null otherwise.
     *
     */
    public Result getBuildResult() {
        if (getFailCount() > 0) {
            return Result.UNSTABLE;
        } else {
            return null;
        }
    }

    /**
     * Time it took to run this test. In seconds.
     */
    public /* abstract */ float getDuration() {
        return 0.0f;
    }

    /**
     * Gets the total number of passed tests.
     */
    public /* abstract */ int getPassCount() {
        return 0;
    }

    /**
     * Gets the total number of failed tests.
     */
    public /* abstract */ int getFailCount() {
        return 0;
    }


    /**
     * Gets the total number of skipped tests.
     */
    public /* abstract */ int getSkipCount() {
        return 0;
    }
    
    /**
     * Gets the counter part of this {@link TestResult} in the previous run.
     *
     * @return null if no such counter part exists.
     */
    public TestResult getPreviousResult() {
        AbstractBuild<?,?> b = getOwner();
        if (b == null) {
            return null;
        }
        while(true) {
            b = b.getPreviousBuild();
            if(b==null)
                return null;
            AbstractTestResultAction r = b.getAction(getParentAction().getClass());
            if(r!=null) {
                TestResult result = r.findCorrespondingResult(this.getId());
                if (result!=null)
                    return result;
            }
        }
    }

    /**
     * Gets the counter part of this {@link TestResult} in the specified run.
     *
     * @return null if no such counter part exists.
     */
    public TestResult getResultInBuild(AbstractBuild<?,?> build) {
        AbstractTestResultAction tra = build.getAction(getParentAction().getClass());
        if (tra == null) {
            tra = build.getAction(AbstractTestResultAction.class);
        }
        return (tra == null) ? null : tra.findCorrespondingResult(this.getId());
    }

    /**
     * Gets the "children" of this test result that failed
     * @return the children of this test result, if any, or an empty collection
     */
    public Collection<? extends TestResult> getFailedTests() {
        return emptyList();
    }


    /**
     * Gets the "children" of this test result that passed
     * @return the children of this test result, if any, or an empty collection
     */
    public Collection<? extends TestResult> getPassedTests() {
        return emptyList();
    }

    /**
     * Gets the "children" of this test result that were skipped
     * @return the children of this test result, if any, or an empty list
     */
    public Collection<? extends TestResult> getSkippedTests() {
        return emptyList();
    }

    /**
     * If this test failed, then return the build number
     * when this test started failing.
     */
    public int getFailedSince() {
        return 0;
    }

    /**
     * If this test failed, then return the run
     * when this test started failing.
     */
    public Run<?,?> getFailedSinceRun() {
        return null;
    }

    /**
     * The stdout of this test.
     */
    public String getStdout() {
        return "";
    }

    /**
     * The stderr of this test.
     */
    public String getStderr() {
        return "";
    }

    /**
     * If there was an error or a failure, this is the stack trace, or otherwise null.
     */
    public String getErrorStackTrace() {
        return "";
    }

    /**
     * If there was an error or a failure, this is the text from the message.
     */
    public String getErrorDetails() {
        return ""; 
    }

    /**
     * @return true if the test was not skipped and did not fail, false otherwise.
     */
    public boolean isPassed() {
        return ((getSkipCount() == 0) && (getFailCount() == 0));
    }

    public String toPrettyString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("Name: ").append(this.getName()).append(", ");
        sb.append("Result: ").append(this.getBuildResult()).append(",\n");
        sb.append("Total Count: ").append(this.getTotalCount()).append(", ");
        sb.append("Fail: ").append(this.getFailCount()).append(", ");
        sb.append("Skipt: ").append(this.getSkipCount()).append(", ");
        sb.append("Pass: ").append(this.getPassCount()).append(",\n");
        sb.append("Test Result Class: " ).append(this.getClass().getName()).append(" }\n");
        return sb.toString(); 
    }

    /**
     * Annotate some text -- what does this do? 
     * @param text
     */
    public String annotate(String text) {
        if (text == null)
                return null;
        text = text.replace("&", "&amp;").replace("<", "&lt;").replaceAll(
                        "\\b(https?://[^\\s)>]+)", "<a href=\"$1\">$1</a>");

        for (TestAction action: getTestActions()) {
                text = action.annotate(text);
        }
        return text;
    }
}
