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
import hudson.model.queue.SubTask;
import hudson.util.Iterators;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * {@link LoadStatistics} that track the "free roam" jobs (whose {@link Task#getAssignedLabel()} is null)
 * and the # of executors that can execute them ({@link Node} whose mode is {@link Mode#EXCLUSIVE})
 *
 * @see Mode#EXCLUSIVE
 * @see Jenkins#unlabeledLoad
 * @see OverallLoadStatistics
 * @author Kohsuke Kawaguchi
 */
public class UnlabeledLoadStatistics extends LoadStatistics {

    private final Iterable<Node> nodes = new UnlabeledNodesIterable();

    UnlabeledLoadStatistics() {
        super(0, 0);
    }

    @Override
    public int computeIdleExecutors() {
        int r=0;
        for (Computer c : Jenkins.getInstance().getComputers()) {
            Node node = c.getNode();
            if (node != null && node.getMode() == Mode.NORMAL && (c.isOnline() || c.isConnecting()) && c.isAcceptingTasks()) {
                r += c.countIdle();
            }
        }
        return r;
    }

    @Override
    public int computeTotalExecutors() {
        int r=0;
        for (Computer c : Jenkins.getInstance().getComputers()) {
            Node node = c.getNode();
            if (node != null && node.getMode() == Mode.NORMAL && c.isOnline()) {
                r += c.countExecutors();
            }
        }
        return r;
    }

    @Override
    public int computeQueueLength() {
        return Jenkins.getInstance().getQueue().countBuildableItemsFor(null);
    }

    @Override
    protected Iterable<Node> getNodes() {
        return nodes;
    }

    @Override
    protected boolean matches(SubTask item) {
        return true;
    }

    private static class UnlabeledNodesIterable implements Iterable<Node> {

        @Override
        public Iterator<Node> iterator() {
            return new UnlabeledNodesIterator();
        }
    }

    private static class UnlabeledNodesIterator extends Iterators.FilterIterator<Node> {

        protected UnlabeledNodesIterator() {
            super(Jenkins.getActiveInstance().getNodes().iterator());
        }

        @Override
        protected boolean filter(Node n) {
            return n != null && n.getMode() == Mode.NORMAL;
        }

        public void remove() {
            // why does Iterators.FilterIterator do the stupid thing and allow remove?
            // (remove should remove the object last returned by next(), but it won't if hasNext() is called
            // the way Iterators.FilterIterator is written... it should just return a read-only
            // view... which is what we do!
            throw new UnsupportedOperationException("remove");
        }
    }
}
