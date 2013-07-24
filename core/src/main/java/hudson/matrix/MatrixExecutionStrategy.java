/*
 * The MIT License
 * 
 * Copyright (c) 2012, CloudBees, Inc.
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

import hudson.ExtensionPoint;
import hudson.matrix.MatrixBuild.MatrixBuildExecution;
import hudson.model.AbstractDescribableImpl;
import hudson.model.BuildListener;
import hudson.model.Result;

import java.io.IOException;
import java.util.List;

/**
 * Controls the execution sequence of {@link MatrixConfiguration} when {@link MatrixProject} builds,
 * including what degree it gets serialized/parallelled, how the whole build is abandoned when
 * some fails, etc.
 * 
 * @author Kohsuke Kawaguchi
 * @since 1.456
 */
public abstract class MatrixExecutionStrategy extends AbstractDescribableImpl<MatrixExecutionStrategy> implements ExtensionPoint {
    public Result run(MatrixBuildExecution execution) throws InterruptedException, IOException {
        return run(execution.getBuild(), execution.getAggregators(), execution.getListener());
    }

    /**
     * @deprecated
     *      Override {@link #run(MatrixBuild.MatrixBuildExecution)}
     */
    public Result run(MatrixBuild build, List<MatrixAggregator> aggregators, BuildListener listener) throws InterruptedException, IOException {
        throw new UnsupportedOperationException(getClass()+" needs to override run(MatrixBuildExecution)");
    }

    @Override
    public MatrixExecutionStrategyDescriptor getDescriptor() {
        return (MatrixExecutionStrategyDescriptor)super.getDescriptor();
    }
}
