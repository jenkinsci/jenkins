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

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Computer;
import hudson.model.Executor;
import jenkins.model.Jenkins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Predicts future load to the system, to assist the scheduling decisions
 *
 * <p>
 * When Hudson makes a scheduling decision, Hudson considers predicted future load
 * &mdash; e.g., "We do currently have one available executor, but we know we need this for something else in 30 minutes,
 * so we can't currently schedule a build that takes 1 hour."
 *
 * <p>
 * This extension point plugs in such estimation of future load.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class LoadPredictor implements ExtensionPoint {
    /**
     * Estimates load starting from the 'start' timestamp, up to the 'end' timestamp.
     *
     * @param start
     *      Where to start enumeration. Always bigger or equal to the current time of the execution.
     * @param plan
     *      This is the execution plan for which we are making a load prediction. Never null. While
     *      this object is still being partially constructed when this method is called, some
     *      of its properties (like {@link MappingWorksheet#item} provide access to more contextual
     *      information. 
     * @since 1.380
     */
    public Iterable<FutureLoad> predict(MappingWorksheet plan, Computer computer, long start, long end) {
        // maintain backward compatibility by calling the old signature.
        return predict(computer,start,end);
    }

    /**
     * Estimates load starting from the 'start' timestamp, up to the 'end' timestamp.
     *
     * @param start
     *      Where to start enumeration. Always bigger or equal to the current time of the execution.
     * @deprecated as of 1.380
     *      Use {@link #predict(MappingWorksheet, Computer, long, long)}
     */
    @Deprecated
    public Iterable<FutureLoad> predict(Computer computer, long start, long end) {
        return Collections.emptyList();
    }

    /**
     * All the registered instances.
     */
    public static ExtensionList<LoadPredictor> all() {
        return ExtensionList.lookup(LoadPredictor.class);
    }

    /**
     * Considers currently running tasks and their completion.
     */
    @Extension
    public static class CurrentlyRunningTasks extends LoadPredictor {
        @Override
        public Iterable<FutureLoad> predict(MappingWorksheet plan, final Computer computer, long start, long eternity) {
            long now = System.currentTimeMillis();
            List<FutureLoad> fl = new ArrayList<FutureLoad>();
            for (Executor e : computer.getExecutors()) {
                if (e.isIdle())     continue;

                long eta = e.getEstimatedRemainingTimeMillis();
                long end = eta<0 ? eternity : now + eta; // when does this task end?
                if (end < start)    continue;   // should be over by the 'start' time.
                fl.add(new FutureLoad(start, end-start, 1));
            }
            return fl;
        }
    }
}
