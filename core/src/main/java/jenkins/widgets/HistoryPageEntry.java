/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
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

package jenkins.widgets;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Run;
import jenkins.model.HistoricalBuild;
import jenkins.model.queue.QueueItem;

/**
 * Represents an entry used by the {@link HistoryPageFilter}.
 *
 * <p>
 * Wraps {@link QueueItem} and {@link HistoricalBuild} instances from the build queue, normalizing
 * access to the info required for pagination.
 * @param <T> typically {@link HistoricalBuild} or {@link QueueItem}
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class HistoryPageEntry<T> {

    private final T entry;

    public HistoryPageEntry(T entry) {
        this.entry = entry;
    }

    public T getEntry() {
        return entry;
    }

    public long getEntryId() {
        return getEntryId(entry);
    }

    protected static long getEntryId(@NonNull Object entry) {
        if (entry instanceof QueueItem) {
            return ((QueueItem) entry).getId();
        } else if (entry instanceof HistoricalBuild run) {
            return Long.MIN_VALUE + run.getNumber();
        } else if (entry instanceof Number) {
            // Used for testing purposes because of JENKINS-30899 and JENKINS-30909
            return Long.MIN_VALUE + ((Number) entry).longValue();
        } else {
            return Run.QUEUE_ID_UNKNOWN;
        }
    }
}
