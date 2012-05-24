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
package jenkins.model;

import hudson.model.Computer;
import hudson.model.LoadStatistics;
import hudson.model.Node;
import hudson.model.Node.Mode;
import hudson.model.OverallLoadStatistics;
import hudson.model.Queue.Task;

/**
 * {@link LoadStatistics} that track the "free roam" jobs (whose {@link Task#getAssignedLabel()} is null)
 * and the # of executors that can execute them ({@link Node} whose mode is {@link Mode#EXCLUSIVE})
 *
 * @see Mode#EXCLUSIVE
 * @see Jenkins#unlabeledLoad
 * @see OverallLoadStatistics
 * @author Kohsuke Kawaguchi
 */
public class UnlabeldLoadStatistics extends LoadStatistics {

    UnlabeldLoadStatistics() {
        super(0, 0);
    }

    @Override
    public int computeIdleExecutors() {
        int r=0;
        for (Computer c : Jenkins.getInstance().getComputers())
            if(c.getNode().getMode()== Mode.NORMAL && (c.isOnline() || c.isConnecting()))
                r += c.countIdle();
        return r;
    }

    @Override
    public int computeTotalExecutors() {
        int r=0;
        for (Computer c : Jenkins.getInstance().getComputers()) {
            if(c.getNode().getMode()==Mode.NORMAL && c.isOnline())
                r += c.countExecutors();
        }
        return r;
    }

    @Override
    public int computeQueueLength() {
        return Jenkins.getInstance().getQueue().countBuildableItemsFor(null);
    }
}
