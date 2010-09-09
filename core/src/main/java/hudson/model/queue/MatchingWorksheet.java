/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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
package hudson.model.queue;

import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Queue.JobOffer;
import hudson.model.Queue.Task;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Kohsuke Kawaguchi
 */
public class MatchingWorksheet {
    public final List<ExecutorChunk> executors;
    public final List<WorkChunk> works;

//    private final BitSet applicability = new BitSet();

    private static class ReadOnlyList<E> extends AbstractList<E> {
        protected final List<E> base;

        ReadOnlyList(List<E> base) {
            this.base = base;
        }

        public E get(int index) {
            return base.get(index);
        }

        public int size() {
            return base.size();
        }
    }

    public final class ExecutorChunk extends ReadOnlyList<JobOffer> {
        public final int index;
        public final Computer computer;
        public final Node node;

        private ExecutorChunk(List<JobOffer> base, int index) {
            super(base);
            this.index = index;
            assert base.size()>1;
            computer = base.get(0).executor.getOwner();
            node = computer.getNode();
        }

        public boolean canAccept(WorkChunk c) {
            return this.size() > c.size()
                && (c.assignedLabel==null || c.assignedLabel.contains(node));
        }

        public String getName() {
            return node.getNodeName();
        }

        /**
         * Alias for size but more readable.
         */
        public int capacity() {
            return size();
        }

        private void execute(WorkChunk wc, WorkUnitContext wuc) {
            assert capacity() > wc.size();
            for (int i=0; i<wc.size(); i++)
                get(i).set(wuc.createWorkUnit(wc.get(i)));
        }
    }

    public class WorkChunk extends ReadOnlyList<ExecutionUnit> {
        public final int index;

        // the main should be always at position 0
//        /**
//         * This chunk includes {@linkplain WorkUnit#isMainWork() the main work unit}.
//         */
//        public final boolean isMain;

        /**
         * If this task needs to be run on a node with a particular label,
         * return that {@link Label}. Otherwise null, indicating
         * it can run on anywhere.
         */
        public final Label assignedLabel;

        /**
         * If the previous execution of this task run on a certain node
         * and this task prefers to run on the same node, return that.
         * Otherwise null.
         */
        public final ExecutorChunk lastBuiltOn;


        private WorkChunk(List<ExecutionUnit> base, int index) {
            super(base);
            assert base.size()>1;
            this.index = index;
            this.assignedLabel = base.get(0).getAssignedLabel();

            Node lbo = base.get(0).getLastBuiltOn();
            for (ExecutorChunk ec : executors) {
                if (ec.node==lbo) {
                    lastBuiltOn = ec;
                    return;
                }
            }
            lastBuiltOn = null;
        }

        public List<ExecutorChunk> applicableExecutorChunks() {
            List<ExecutorChunk> r = new ArrayList<ExecutorChunk>(executors.size());
            for (ExecutorChunk e : executors) {
                if (e.canAccept(this))
                    r.add(e);
            }
            return r;
        }
    }

    public final class Mapping {
        // for each WorkChunk, identify ExecutorChunk where it is assigned to.
        private final ExecutorChunk[] mapping = new ExecutorChunk[works.size()];

        public ExecutorChunk assigned(int index) {
            return mapping[index];
        }

        public WorkChunk get(int index) {
            return works.get(index);
        }

        public ExecutorChunk assign(int index, ExecutorChunk element) {
            ExecutorChunk o = mapping[index];
            mapping[index] = element;
            return o;
        }

        public int size() {
            return mapping.length;
        }

        public boolean isPartiallyValid() {
            int[] used = new int[executors.size()];
            for (int i=0; i<mapping.length; i++) {
                ExecutorChunk ec = mapping[i];
                if (ec==null)   continue;
                if (!ec.canAccept(works(i)))
                    return false;   // invalid assignment
                if ((used[ec.index] += works(i).size()) > ec.capacity())
                    return false;
            }
            return true;
        }

        public boolean isCompletelyValid() {
            for (ExecutorChunk ec : mapping)
                if (ec==null)   return false;   // unassigned
            return isPartiallyValid();
        }

        /**
         * Executes this mapping
         */
        public void execute(WorkUnitContext wuc) {
            if (!isCompletelyValid())
                throw new IllegalStateException();

            for (int i=0; i<size(); i++)
                assigned(i).execute(get(i),wuc);
        }
    }


    public MatchingWorksheet(Task task, List<JobOffer> offers) {
        // executors
        Map<Computer,List<JobOffer>> j = new HashMap<Computer, List<JobOffer>>();
        for (JobOffer o : offers) {
            Computer c = o.executor.getOwner();
            List<Queue.JobOffer> l = j.get(c);
            if (l==null)
                j.put(c,l=new ArrayList<JobOffer>());
            l.add(o);
        }

        // build into the final shape
        List<ExecutorChunk> executors = new ArrayList<ExecutorChunk>();
        for (List<JobOffer> group : j.values()) {
            ExecutorChunk ec = new ExecutorChunk(group, executors.size());
            if (ec.node==null)  continue;   // evict out of sync node
            executors.add(ec);
        }
        this.executors = Collections.unmodifiableList(executors);

        // group execution units into chunks. use of LinkedHashMap ensures that the main work comes at the top
        Map<Object,List<ExecutionUnit>> m = new LinkedHashMap<Object,List<ExecutionUnit>>();
        build(task,m);
        for (ExecutionUnit meu : task.getMemberExecutionUnits())
            build(meu,m);

        // build into the final shape
        List<WorkChunk> works = new ArrayList<WorkChunk>();
        for (List<ExecutionUnit> group : m.values()) {
            works.add(new WorkChunk(group,works.size()));
        }
        this.works = Collections.unmodifiableList(works);
    }

    private void build(ExecutionUnit eu, Map<Object, List<ExecutionUnit>> m) {
        Object c = eu.getSameNodeConstraint();
        if (c==null)    c = new Object();

        List<ExecutionUnit> l = m.get(c);
        if (l==null)
            m.put(c,l= new ArrayList<ExecutionUnit>());
        l.add(eu);
    }

    public WorkChunk works(int index) {
        return works.get(index);
    }

    public ExecutorChunk executors(int index) {
        return executors.get(index);
    }
//
//    public void setIncompatible(int x, int u) {
//        assert 0<=x && x<=executors.size();
//        assert 0<=u && u<= works.size();
//        applicability.set(x* works.size()+u);
//    }
//
//    public boolean isIncompatible(int x, int u) {
//        assert 0<=x && x<=executors.size();
//        assert 0<=u && u<= works.size();
//        return applicability.get(x* works.size()+u);
//    }
//
//    /**
//     * Creates a clone.
//     */
//    public MatchingWorksheet copy() {
//        throw new UnsupportedOperationException();
//    }
}
