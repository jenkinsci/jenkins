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

import hudson.model.AbstractBuild;
import org.kohsuke.stapler.StaplerProxy;

/**
 * A class to exercise the TestResult extension mechanism.
 */
public class TrivialTestResultAction extends AbstractTestResultAction<TrivialTestResultAction> implements StaplerProxy {

    protected TrivialTestResult result;

    @Deprecated
    protected TrivialTestResultAction(AbstractBuild owner, TrivialTestResult result) {
        super(owner);
        this.result = result;
        this.result.setParentAction(this);
    }

    /** @since 1.545 */
    protected TrivialTestResultAction(TrivialTestResult result) {
        this(null, result);
    }

    /**
     * Gets the number of failed tests.
     */
    @Override
    public int getFailCount() {
        return 0;  // (FIXME: generated)
    }

    /**
     * Gets the total number of tests.
     */
    @Override
    public int getTotalCount() {
        return 0;  // (FIXME: generated)
    }

    /**
     * Returns the object that represents the actual test result.
     * This method is used by the remote API so that the XML/JSON
     * that we are sending won't contain unnecessary indirection
     * (that is, {@link AbstractTestResultAction} in between.
     * <p/>
     * <p/>
     * If such a concept doesn't make sense for a particular subtype,
     * return <tt>this</tt>.
     */
    @Override
    public Object getResult() {
        return result;
    }

    /**
     * Returns the object that is responsible for processing web requests.
     *
     * @return If null is returned, it generates 404.
     *         If {@code this} object is returned, no further
     *         {@link org.kohsuke.stapler.StaplerProxy} look-up is done and {@code this} object
     *         processes the request.
     */
    public Object getTarget() {
        return getResult();
    }
}
