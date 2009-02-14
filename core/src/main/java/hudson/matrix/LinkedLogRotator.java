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

import hudson.model.Job;
import hudson.tasks.LogRotator;

import java.io.IOException;

/**
 * {@link LogRotator} for {@link MatrixConfiguration},
 * which discards the builds if and only if it's discarded
 * in the parent.
 *
 * <p>
 * Because of the serialization compatibility, we can't easily
 * refactor {@link LogRotator} into a contract and an implementation. 
 *
 * @author Kohsuke Kawaguchi
 */
final class LinkedLogRotator extends LogRotator {
    LinkedLogRotator() {
        super(-1,-1);
    }

    @Override
    public void perform(Job _job) throws IOException, InterruptedException {
        // copy it to the array because we'll be deleting builds as we go.
        MatrixConfiguration job = (MatrixConfiguration) _job;

        for( MatrixRun r : job.getBuilds().toArray(new MatrixRun[0]) ) {
            if(job.getParent().getBuildByNumber(r.getNumber())==null)
                r.delete();
        }

        if(!job.isActiveConfiguration() && job.getLastBuild()==null)
            job.delete();
    }
}
