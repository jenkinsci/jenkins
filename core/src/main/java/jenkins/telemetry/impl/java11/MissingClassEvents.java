/*
 * The MIT License
 *
 * Copyright (c) 2019 CloudBees, Inc.
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

package jenkins.telemetry.impl.java11;

import com.google.common.annotations.VisibleForTesting;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MissingClassEvents {

    /**
     * Only 100 exceptions a day (period of telemetry)
     */
    @VisibleForTesting
    /* package */ static /* final */ int MAX_EVENTS_PER_SEND = 100;

    /**
     * List of events, one per stack trace.
     */
    private ConcurrentHashMap<List<StackTraceElement>, MissingClassEvent> events = new ConcurrentHashMap<>(MAX_EVENTS_PER_SEND);

    /**
     * Add a new exception to the store. If the same exception already exists, it increases the occurrences. If we
     * already get the maximum number of exceptions, it doesn't add any value.
     * @param name name of the class not found
     * @param t the exception to store
     * @return the occurrences stored for this throwable. 1 the fist time it's stored. &gt; 1 for successive stores of the
     * same <strong>stack trace</strong>. 0 if we already stored MAX_EVENTS_PER_SEND (100) events for a single send.
     */
    public long put(String name, @Nonnull Throwable t) {
        // A final object to pass it to the function
        final AtomicLong occurrences = new AtomicLong();

        // We need the key (the stack trace) to be a list and unmodifiable
        List<StackTraceElement> key = Collections.unmodifiableList(Arrays.asList(t.getStackTrace()));
        events.compute(key, (stackTraceElements, missingClassEvent) -> {

            if (missingClassEvent == null) {
                // It's a new element, the size will increase
                if (events.size() < MAX_EVENTS_PER_SEND) {
                    // Create the new value
                    MissingClassEvent newEvent = new MissingClassEvent(name, t);
                    occurrences.set(1);
                    return newEvent;
                } else {
                    return null;
                }

            } else {
                // We update the occurrences and the last time it happened
                occurrences.set(missingClassEvent.getOccurrences());
                missingClassEvent.setOccurrences(occurrences.incrementAndGet());
                missingClassEvent.setTime(MissingClassTelemetry.clientDateString());
                return missingClassEvent;
            }
        });

        return occurrences.get();
    }

    /**
     * Reinitialize the events happened and return the number of events stored since last execution of this method.
     * Used to send via telemetry the events and restart the events store.
     * @return the number of events stored since previous call to this method.
     */

    @VisibleForTesting
    /* package */ synchronized @Nonnull ConcurrentHashMap<List<StackTraceElement>, MissingClassEvent> getEventsAndClean() {
        ConcurrentHashMap<List<StackTraceElement>, MissingClassEvent> currentEvents = events;
        events = new ConcurrentHashMap<>(MAX_EVENTS_PER_SEND);
        return currentEvents;
    }

    @Override
    public String toString() {
        return "MissingClassEvents{" +
                "events=" + events +
                '}';
    }
}
