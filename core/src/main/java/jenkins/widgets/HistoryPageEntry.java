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

import hudson.model.Queue;
import hudson.model.Run;

import javax.annotation.Nonnull;

/**
 * Represents an entry used by the {@link HistoryPageFilter}.
 *
 * <p>
 * Wraps {@link Queue.Item} and {@link Run} instances from the build queue, normalizing
 * access to the info required for pagination.
 *
 *
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

    protected static long getEntryId(@Nonnull Object entry) {
        if (entry instanceof Queue.Item) {
            return ((Queue.Item) entry).getId();
        } else if (entry instanceof Run) {
            Run run = (Run) entry;
            long queueId = run.getQueueId();
            if (queueId == Run.QUEUE_ID_UNKNOWN) {
                // Backward compatibility. This is a run from before the Queue.Item IDs
                // were mapped onto their resulting Run instance.
                return (Integer.MIN_VALUE + run.getNumber());
            } else {
                return queueId;
            }
        } else {
            return Run.QUEUE_ID_UNKNOWN;
        }
    }
}
