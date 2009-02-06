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
package hudson.matrix;

import hudson.tasks.Publisher;
import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.model.BuildListener;

/**
 * {@link Publisher} can optionally implement this interface
 * to perform result aggregation across {@link MatrixRun}.
 *
 * <p>
 * This is useful for example to aggregate all the test results
 * in {@link MatrixRun} into a single table/graph.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.115
 */
public interface MatrixAggregatable extends ExtensionPoint {
    /**
     * Creates a new instance of the aggregator.
     *
     * <p>
     * This method is called during the build of
     * {@link MatrixBuild} and the created aggregator
     * will perform the aggregation.
     *
     * @param build
     *      The build for which the aggregation shall happen. Never null.
     * @param launcher
     *      Can be used to launch processes during the build.
     * @param listener
     *      Progress report and errors during the aggregation should
     *      be sent to this object. Never null.
     *
     * @return
     *      null if the implementation is not willing to contribute
     *      an aggregator.
     *
     * @see MatrixAggregator#build
     * @see MatrixAggregator#listener
     */
    MatrixAggregator createAggregator(MatrixBuild build, Launcher launcher, BuildListener listener);
}
