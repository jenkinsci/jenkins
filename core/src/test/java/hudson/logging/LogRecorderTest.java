/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

package hudson.logging;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class LogRecorderTest {

    @Issue("JENKINS-17983")
    @Test
    void targetIncludes() {
        assertTrue(includes("hudson", "hudson"));
        assertFalse(includes("hudson", "hudsone"));
        assertFalse(includes("hudson", "hudso"));
        assertTrue(includes("hudson", "hudson.model.Hudson"));
        assertFalse(includes("hudson", "jenkins.model.Jenkins"));
        assertTrue(includes("", "hudson.model.Hudson"));
    }

    @Test
    void targetMatches() {
        assertTrue(matches("hudson", "hudson"));
        assertFalse(matches("hudson", "hudson", Level.FINE));
        assertNull(matches("hudson", "hudsone"));
        assertNull(matches("hudson", "hudso"));
        assertTrue(matches("hudson", "hudson.model.Hudson"));
        assertFalse(matches("hudson", "hudson.model.Hudson", Level.FINE));
        assertNull(matches("hudson", "jenkins.model.Jenkins"));
        assertTrue(matches("", "hudson.model.Hudson"));
        assertFalse(matches("", "hudson.model.Hudson", Level.FINE));
    }

    @Test
    void testClearing() throws IOException {
        LogRecorder lr = new LogRecorder("foo");
        LogRecorder.Target t = new LogRecorder.Target("", Level.FINE);
        lr.getLoggers().add(t);

        Jenkins j = Mockito.mock(Jenkins.class);
        try (MockedStatic<Jenkins> mocked = Mockito.mockStatic(Jenkins.class)) {
            mocked.when(Jenkins::get).thenReturn(j);

            LogRecord record = createLogRecord("jenkins", Level.INFO, "message");
            lr.handler.publish(record);
            assertEquals(lr.handler.getView().get(0), record);
            assertEquals(1, lr.handler.getView().size());

            lr.doClear();

            assertEquals(0, lr.handler.getView().size());
        }
    }

    @Test
    void testSpecificExclusion() {
        LogRecorder lr = new LogRecorder("foo");

        LogRecorder.Target targetLevel0 = new LogRecorder.Target("", Level.FINE);
        LogRecorder.Target targetLevel1 = new LogRecorder.Target("foo", Level.INFO);
        LogRecorder.Target targetLevel2 = new LogRecorder.Target("foo.bar", Level.SEVERE);

        lr.getLoggers().add(targetLevel1);
        lr.getLoggers().add(targetLevel2);
        lr.getLoggers().add(targetLevel0);

        assertEquals(lr.orderedTargets()[0], targetLevel2);
        assertEquals(lr.orderedTargets()[1], targetLevel1);
        assertEquals(lr.orderedTargets()[2], targetLevel0);

        LogRecord r1 = createLogRecord("baz", Level.INFO, "visible");
        LogRecord r2 = createLogRecord("foo", Level.FINE, "hidden");
        LogRecord r3 = createLogRecord("foo.bar", Level.INFO, "hidden");
        LogRecord r4 = createLogRecord("foo.bar.baz", Level.INFO, "hidden");
        LogRecord r5 = createLogRecord("foo.bar.baz", Level.SEVERE, "visible");
        LogRecord r6 = createLogRecord("foo", Level.INFO, "visible");
        lr.handler.publish(r1);
        lr.handler.publish(r2);
        lr.handler.publish(r3);
        lr.handler.publish(r4);
        lr.handler.publish(r5);
        lr.handler.publish(r6);

        assertThat(lr.handler.getView(), contains(r6, r5, r1));
    }

    private static LogRecord createLogRecord(String logger, Level level, String message) {
        LogRecord r = new LogRecord(level, message);
        r.setLoggerName(logger);
        return r;
    }

    @SuppressWarnings("deprecation") /* testing deprecated variant */
    private static boolean includes(String target, String logger) {
        LogRecord r = createLogRecord(logger, Level.INFO, "whatever");
        return new LogRecorder.Target(target, Level.INFO).includes(r);
    }

    private static Boolean matches(String target, String logger) {
        return matches(target, logger, Level.INFO);
    }

    private static Boolean matches(String target, String logger, Level loggerLevel) {
        LogRecord r = createLogRecord(logger, loggerLevel, "whatever");
        return new LogRecorder.Target(target, Level.INFO).matches(r);
    }

    @Test
    void autocompletionTest() {
        List<String> loggers = Arrays.asList(
                "com.company.whatever.Foo", "com.foo.Bar", "com.foo.Baz",
                "org.example.app.Main", "org.example.app.impl.xml.Parser", "org.example.app.impl.xml.Validator");

        Set<String> candidates = LogRecorder.getAutoCompletionCandidates(loggers);

        isCandidate(candidates, "com");
        isCandidate(candidates, "com.company.whatever.Foo");
        isCandidate(candidates, "com.foo");
        isCandidate(candidates, "com.foo.Bar");
        isCandidate(candidates, "com.foo.Baz");
        isCandidate(candidates, "org.example.app");
        isCandidate(candidates, "org.example.app.Main");
        isCandidate(candidates, "org.example.app.impl.xml");
        isCandidate(candidates, "org.example.app.impl.xml.Parser");
        isCandidate(candidates, "org.example.app.impl.xml.Validator");

        isNotCandidate(candidates, "org");
        isNotCandidate(candidates, "org.example");

        assertEquals(10, candidates.size(), "expected number of items");
    }

    private static void isCandidate(Set<String> candidates, String candidate) {
        assertTrue(candidates.contains(candidate), candidate);
    }

    private static void isNotCandidate(Set<String> candidates, String candidate) {
        assertFalse(candidates.contains(candidate), candidate);
    }

}
