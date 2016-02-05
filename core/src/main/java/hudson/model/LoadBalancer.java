/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.model;

import com.google.common.collect.Maps;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.Queue.Task;
import hudson.model.queue.MappingWorksheet;
import hudson.model.queue.MappingWorksheet.ExecutorChunk;
import hudson.model.queue.MappingWorksheet.Mapping;
import hudson.util.ConsistentHash;
import hudson.util.ConsistentHash.Hash;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Strategy that decides which {@link Task} gets run on which {@link Executor}.
 *
 * <p>
 * Even though this is marked as {@link ExtensionPoint}, you do not register
 * your implementation with @{@link Extension}. Instead, call {@link Queue#setLoadBalancer(LoadBalancer)}
 * to install your implementation.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.301
 */
public abstract class LoadBalancer implements ExtensionPoint {
    /**
     * Chooses the executor(s) to carry out the build for the given task.
     *
     * <p>
     * This method is invoked from different threads, but the execution is serialized by the caller.
     * The thread that invokes this method always holds a lock to {@link Queue}, so queue contents
     * can be safely introspected from this method, if that information is necessary to make
     * decisions.
     * 
     * @param  task
     *      The task whose execution is being considered. Never null.
     * @param worksheet
     *      The work sheet that represents the matching that needs to be made.
     *      The job of this method is to determine which work units on this worksheet
     *      are executed on which executors (also on this worksheet.)
     *
     * @return
     *      Build up the mapping by using the given worksheet and return it.
     *      Return null if you don't want the task to be executed right now,
     *      in which case this method will be called some time later with the same task.
     */
    public abstract Mapping map(Task task, MappingWorksheet worksheet);

    /**
     * Uses a consistent hash for scheduling.
     */
    public static final LoadBalancer CONSISTENT_HASH = new LoadBalancer() {
        @Override
        public Mapping map(Task task, MappingWorksheet ws) {
            // build consistent hash for each work chunk
            List<ConsistentHash<ExecutorChunk>> hashes = new ArrayList<ConsistentHash<ExecutorChunk>>(ws.works.size());
            for (int i=0; i<ws.works.size(); i++) {
                ConsistentHash<ExecutorChunk> hash = new ConsistentHash<ExecutorChunk>(new Hash<ExecutorChunk>() {
                    public String hash(ExecutorChunk node) {
                        return node.getName();
                    }
                });

                // Build a Map to pass in rather than repeatedly calling hash.add() because each call does lots of expensive work
                List<ExecutorChunk> chunks = ws.works(i).applicableExecutorChunks();
                Map<ExecutorChunk, Integer> toAdd = Maps.newHashMapWithExpectedSize(chunks.size());
                for (ExecutorChunk ec : chunks) {
                    toAdd.put(ec, ec.size()*100);
                }
                hash.addAll(toAdd);

                hashes.add(hash);
            }

            // do a greedy assignment
            Mapping m = ws.new Mapping();
            assert m.size()==ws.works.size();   // just so that you the reader of the source code don't get confused with the for loop index

            if (assignGreedily(m,task,hashes,0)) {
                assert m.isCompletelyValid();
                return m;
            } else
                return null;
        }

        private boolean assignGreedily(Mapping m, Task task, List<ConsistentHash<ExecutorChunk>> hashes, int i) {
            if (i==hashes.size())   return true;    // fully assigned

            String key = task.getFullDisplayName() + (i>0 ? String.valueOf(i) : "");

            for (ExecutorChunk ec : hashes.get(i).list(key)) {
                // let's attempt this assignment
                m.assign(i,ec);

                if (m.isPartiallyValid() && assignGreedily(m,task,hashes,i+1))
                    return true;    // successful greedily allocation

                // otherwise 'ec' wasn't a good fit for us. try next.
            }

            // every attempt failed
            m.assign(i,null);
            return false;
        }
    };

    /**
     * Traditional implementation of this.
     *
     * @deprecated as of 1.377
     *      The only implementation in the core now is the one based on consistent hash.
     */
    @Deprecated
    public static final LoadBalancer DEFAULT = CONSISTENT_HASH;


    /**
     * Wraps this {@link LoadBalancer} into a decorator that tests the basic sanity of the implementation.
     * Only override this if you find some of the checks excessive, but beware that it's like driving without a seat belt.
     */
    protected LoadBalancer sanitize() {
        final LoadBalancer base = this;
        return new LoadBalancer() {
            @Override
            public Mapping map(Task task, MappingWorksheet worksheet) {
                if (Queue.isBlockedByShutdown(task)) {
                    // if we are quieting down, don't start anything new so that
                    // all executors will be eventually free.
                    return null;
                }
                return base.map(task, worksheet);
            }

            /**
             * Double-sanitization is pointless.
             */
            @Override
            protected LoadBalancer sanitize() {
                return this;
            }
        };
    }

}
