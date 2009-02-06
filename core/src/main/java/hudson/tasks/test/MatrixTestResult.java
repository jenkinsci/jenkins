/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
import hudson.model.Action;
import hudson.matrix.MatrixBuild;
import hudson.matrix.Combination;
import hudson.matrix.MatrixRun;

/**
 * {@link Action} that aggregates all the test results from {@link MatrixRun}s.
 *
 * <p>
 * This object is attached to {@link MatrixBuild}.
 *
 * @author Kohsuke Kawaguchi
 */
public class MatrixTestResult extends AggregatedTestResultAction {
    public MatrixTestResult(MatrixBuild owner) {
        super(owner);
    }

    /**
     * Use the configuration name.
     */
    @Override
    protected String getChildName(AbstractTestResultAction tr) {
        return tr.owner.getProject().getName();
    }

    @Override
    public AbstractBuild<?,?> resolveChild(Child child) {
        MatrixBuild b = (MatrixBuild)owner;
        return b.getRun(Combination.fromString(child.name));
    }
}
