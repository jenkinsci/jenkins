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

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.LoggerRule;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * Tests without a running Jenkins for Java11 Telemetry of ClassNotFoundException.
 */
public class MissingClassTelemetryFasterTest {
    private CatcherClassLoader cl;

    @Rule
    public LoggerRule logging = new LoggerRule();

    @Before
    public void cleanEvents() {
        cl = new CatcherClassLoader(this.getClass().getClassLoader());
    }

    @Test
    public void maxNumberEvents() {
        Assume.assumeTrue("The telemetry should be enabled", MissingClassTelemetry.enabled());

        // Backup to restore at the end of the test
        int maxEventsBefore = MissingClassEvents.MAX_EVENTS_PER_SEND;

        try {
            MissingClassEvents.MAX_EVENTS_PER_SEND = 1;

            try {
                cl.loadClass("sun.java.MyNonExistentClass");
            } catch (ClassNotFoundException ignored) {
            }

            try {
                cl.loadClass("sun.java.MyNonExistentJavaClass");
            } catch (ClassNotFoundException ignored) {
            }


            ConcurrentHashMap<List<StackTraceElement>, MissingClassEvent> eventsGathered = MissingClassTelemetry.getEvents().getEventsAndClean();

            // Only one class miss gathered with two occurrences
            assertEquals(1, eventsGathered.size());
        } finally {
            MissingClassEvents.MAX_EVENTS_PER_SEND = maxEventsBefore;
        }
    }

    /**
     * The same class failed to be loaded in different places ends up in two records of telemetry with one occurrence
     * each.
     */
    @Test
    public void differentEventsAlthoughSameClass() {
        Assume.assumeTrue("The telemetry should be enabled", MissingClassTelemetry.enabled());

        try {
            cl.loadClass("sun.java.MyNonExistentClass");
        } catch (ClassNotFoundException ignored) {
        }

        try {
            cl.loadClass("sun.java.MyNonExistentJavaClass");
        } catch (ClassNotFoundException ignored) {
        }

        // Get the events gathered
        MissingClassEvents events = MissingClassTelemetry.getEvents();
        ConcurrentHashMap<List<StackTraceElement>, MissingClassEvent> eventsGathered = events.getEventsAndClean();

        // Only one class miss gathered with two occurrences
        assertEquals(2, eventsGathered.size());
        assertEquals(1, eventsGathered.values().iterator().next().getOccurrences());
        assertEquals(1, eventsGathered.values().iterator().next().getOccurrences());
    }

    /**
     * The same class thrown in the same line ends up in a single event with two occurrences.
     */
    @Test
    public void addOccurrenceIfSameStackTrace() {
        Assume.assumeTrue("The telemetry should be enabled", MissingClassTelemetry.enabled());

        for (int i = 0; i < 2; i++) {
            try {
                //Exceptions thrown at the same line, with the same stack trace become occurrences of just one event
                cl.loadClass("sun.java.MyNonExistentJavaClass");
            } catch (ClassNotFoundException ignored) {
            }
        }

        // Get the events gathered
        MissingClassEvents events = MissingClassTelemetry.getEvents();
        ConcurrentHashMap<List<StackTraceElement>, MissingClassEvent> eventsGathered = events.getEventsAndClean();

        // Only one class miss gathered with two occurrences
        assertEquals(1, eventsGathered.size());
        assertEquals(2, eventsGathered.values().iterator().next().getOccurrences());
    }

    /**
     * A class not from the split packages is not gathered.
     */
    @Test
    public void nonJavaClassesNotGathered() {
        Assume.assumeTrue("The telemetry should be enabled", MissingClassTelemetry.enabled());

        try {
            cl.loadClass("jenkins.MyNonExistentClass");
        } catch (ClassNotFoundException ignored) {
        }

        // Get the events gathered
        MissingClassEvents events = MissingClassTelemetry.getEvents();
        ConcurrentHashMap<List<StackTraceElement>, MissingClassEvent> eventsGathered = events.getEventsAndClean();

        // No events gathered
        assertEquals(0, eventsGathered.size());
    }

    /**
     * Only a max number of events is gathered. In this test, just one wit two occurrences
     */
    @Test
    public void maxEventsLimitedSameStackTrace() {
        Assume.assumeTrue("The telemetry should be enabled", MissingClassTelemetry.enabled());

        MissingClassEvents.MAX_EVENTS_PER_SEND = 1;
        for (int i = 0; i < 2; i++) {
            try {
                //Exceptions thrown at the same line, with the same stack trace become occurrences of just one event
                cl.loadClass("sun.java.MyNonExistentJavaClass");
            } catch (ClassNotFoundException ignored) {
            }
        }

        // Get the events gathered
        MissingClassEvents events = MissingClassTelemetry.getEvents();
        ConcurrentHashMap<List<StackTraceElement>, MissingClassEvent> eventsGathered = events.getEventsAndClean();

        // Only one event gathered
        assertEquals(1, eventsGathered.size());
        assertEquals(2, eventsGathered.values().iterator().next().getOccurrences());
    }

    /**
     * Only a max number of events is gathered. In this test, just one wit one occurrence. The second one is discarded
     */
    @Test
    public void maxEventsLimitedDifferentStackTrace() {
        Assume.assumeTrue("The telemetry should be enabled", MissingClassTelemetry.enabled());

        MissingClassEvents.MAX_EVENTS_PER_SEND = 1;

        try {
            cl.loadClass("sun.java.MyNonExistentClassGathered");
        } catch (ClassNotFoundException ignored) {
        }

        try {
            cl.loadClass("sun.java.MyNonExistentJavaClassNotGathered");
        } catch (ClassNotFoundException ignored) {
        }

        // Get the events gathered
        MissingClassEvents events = MissingClassTelemetry.getEvents();
        ConcurrentHashMap<List<StackTraceElement>, MissingClassEvent> eventsGathered = events.getEventsAndClean();

        // Only one event gathered
        assertEquals(1, eventsGathered.size());
        assertEquals(1, eventsGathered.values().iterator().next().getOccurrences());
        assertThat(eventsGathered.values().iterator().next().getStackTrace(), containsString("MyNonExistentClassGathered"));
        assertThat(eventsGathered.values().iterator().next().getStackTrace(), not(containsString("MyNonExistentJavaClassNotGathered")));
    }

    /**
     * Test the cycles in the exceptions. This specific tests shows that we first look for reportable exceptions in the
     * causes and when found a reportable exception, we stop searching. So the warning because a cycle is not logged.
     */
    @Test
    public void cyclesNotReachedBecauseCNFEReported() {
        Assume.assumeTrue("The telemetry should be enabled", MissingClassTelemetry.enabled());

        logging.record(MissingClassTelemetry.class, Logger.getLogger(MissingClassTelemetry.class.getName()).getLevel()).capture(5);

        try {
            /*
            parent -> child -> cnfe
                         \
                        parent
            We first look into the causes exceptions. When found, we don't look into the suppressed, so the cycle is not
            found here
             */

            ClassNotFoundException cnfe = new ClassNotFoundException("sun.java.MyNonExistentClassGathered");
            Exception child = new Exception("child", cnfe);
            Exception parent = new Exception("parent", child); // parent -> caused by -> child
            child.addSuppressed(parent);

            // Some extra wrapping
            throw new Exception(new Exception (new Exception (parent)));

        } catch (Exception e) {
            // Look for anything to report
            MissingClassTelemetry.reportExceptionInside(e);

            // Get the events gathered
            MissingClassEvents events = MissingClassTelemetry.getEvents();
            ConcurrentHashMap<List<StackTraceElement>, MissingClassEvent> eventsGathered = events.getEventsAndClean();

            // One event gathered
            assertEquals(1, eventsGathered.size());

            // the circular reference has not been recorded in the log because we reached a CNFE previously
            assertEquals("No circular message was printed in logs", 0, logging.getRecords().stream().filter(r -> r.getMessage().contains(MissingClassTelemetry.CIRCULAR_REFERENCE)).count());
        }
    }

    /**
     * Test the cycles in the exceptions. This specific tests shows that we first look for reportable exceptions deep
     * in the causes and suppressed exceptions in this order. We report the cycle but also the CNFE.
     */
    @Test
    public void cnfeFoundAfterCycle() {
        Assume.assumeTrue("The telemetry should be enabled", MissingClassTelemetry.enabled());

        logging.record(MissingClassTelemetry.class, Logger.getLogger(MissingClassTelemetry.class.getName()).getLevel()).capture(5);

        try {
            ClassNotFoundException cnfe = new ClassNotFoundException("sun.java.MyNonExistentClassGathered");

            /*
            parent -> child
               \         \
               cnfe      parent
             */
            Exception child = new Exception("child");
            Exception parent = new Exception("parent", child); // parent -> caused by -> child
            child.addSuppressed(parent); // Boooomm!!!! The parent is a child suppressed exception -> cycle
            parent.addSuppressed(cnfe);

            // Some extra wrapping
            throw new Exception(new Exception (new Exception (parent)));

        } catch (Exception e) {
            // Look for anything to report
            MissingClassTelemetry.reportExceptionInside(e);

            // Get the events gathered
            MissingClassEvents events = MissingClassTelemetry.getEvents();
            ConcurrentHashMap<List<StackTraceElement>, MissingClassEvent> eventsGathered = events.getEventsAndClean();

            // One event gathered
            assertEquals(1, eventsGathered.size());

            // the circular reference has been recorded in the log
            assertThat(logging, LoggerRule.recorded(containsString(MissingClassTelemetry.CIRCULAR_REFERENCE)));
        }
    }

    /**
     * Test the cycles in the exceptions. This specific tests shows that we first look for reportable exceptions deep
     * in the causes and suppressed exceptions in this order. We report the cycle but also the CNFE in the parent.
     */
    @Test
    public void cnfeAfterCNFENotJava11AndCycle() {
        Assume.assumeTrue("The telemetry should be enabled", MissingClassTelemetry.enabled());

        logging.record(MissingClassTelemetry.class, Logger.getLogger(MissingClassTelemetry.class.getName()).getLevel()).capture(5);

        try {
            ClassNotFoundException cnfe = new ClassNotFoundException("sun.java.MyNonExistentClassGathered");
            ClassNotFoundException cnfeNonJava11 = new ClassNotFoundException("MyNonExistentClassGathered");

            /*
            parent -> child -> grandchild
               \         \          \
               cnfe      parent     cnfe (non java11)
             */
            Exception grandchild = new Exception("grandchild");
            Exception child = new Exception("child");
            Exception parent = new Exception("parent", child); // parent -> caused by -> child
            child.addSuppressed(parent); // Boooomm!!!! The parent is a child suppressed exception -> cycle
            grandchild.addSuppressed(cnfeNonJava11);
            parent.addSuppressed(cnfe);

            // Some extra wrapping
            throw new Exception(new Exception (new Exception (parent)));

        } catch (Exception e) {
            // Look for anything to report
            MissingClassTelemetry.reportExceptionInside(e);

            // Get the events gathered
            MissingClassEvents events = MissingClassTelemetry.getEvents();
            ConcurrentHashMap<List<StackTraceElement>, MissingClassEvent> eventsGathered = events.getEventsAndClean();

            // One event gathered
            assertEquals(1, eventsGathered.size());

            // the circular reference has been recorded in the log
            assertThat(logging, LoggerRule.recorded(containsString(MissingClassTelemetry.CIRCULAR_REFERENCE)));
        }
    }

    @Test
    public void nothingGatheredWhenTelemetryDisabled() {
        Assume.assumeFalse("The telemetry should not be enabled", MissingClassTelemetry.enabled());

        try {
            cl.loadClass("sun.java.MyNonExistentClass");
        } catch (ClassNotFoundException ignored) {
        }

        ConcurrentHashMap<List<StackTraceElement>, MissingClassEvent> eventsGathered = MissingClassTelemetry.getEvents().getEventsAndClean();

        // No events gathered
        assertEquals(0, eventsGathered.size());
    }
}
