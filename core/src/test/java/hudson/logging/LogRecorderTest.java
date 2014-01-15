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

import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.Test;
import static org.junit.Assert.*;
import org.jvnet.hudson.test.Bug;

public class LogRecorderTest {

    @Bug(17983)
    @Test public void targetIncludes() {
        assertTrue(includes("hudson", "hudson"));
        assertFalse(includes("hudson", "hudsone"));
        assertFalse(includes("hudson", "hudso"));
        assertTrue(includes("hudson", "hudson.model.Hudson"));
        assertFalse(includes("hudson", "jenkins.model.Jenkins"));
        assertTrue(includes("", "hudson.model.Hudson"));
    }

    @Test public void targetMatches() {
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

    @Test public void testSpecificExclusion() {
        LogRecorder lr = new LogRecorder("foo");

        LogRecorder.Target targetH = new LogRecorder.Target("foo.bar", Level.SEVERE);
        LogRecorder.Target targetM = new LogRecorder.Target("foo", Level.INFO);
        LogRecorder.Target targetL = new LogRecorder.Target("", Level.FINE);

        lr.targets.add(targetH);
        lr.targets.add(targetM);
        lr.targets.add(targetL);

        LogRecord r1h = createLogRecord("foo.bar.baz", Level.INFO, "hidden");
        LogRecord r1v = createLogRecord("foo.bar.baz", Level.SEVERE, "visible");
        LogRecord r2h = createLogRecord("foo.bar", Level.INFO, "hidden");
        LogRecord r2v = createLogRecord("foo.bar", Level.SEVERE, "hidden");
        LogRecord r3h = createLogRecord("foo", Level.FINE, "hidden");
        LogRecord r3v = createLogRecord("foo", Level.INFO, "visible");
        LogRecord r4v = createLogRecord("baz", Level.INFO, "visible");
        lr.handler.publish(r1h);
        lr.handler.publish(r1v);
        lr.handler.publish(r2h);
        lr.handler.publish(r2v);
        lr.handler.publish(r3v);
        lr.handler.publish(r3h);
        lr.handler.publish(r4v);

        assertTrue(lr.handler.getView().contains(r1v));
        assertFalse(lr.handler.getView().contains(r1h));
        assertFalse(lr.handler.getView().contains(r2h));
        assertTrue(lr.handler.getView().contains(r2v));
        assertFalse(lr.handler.getView().contains(r3h));
        assertTrue(lr.handler.getView().contains(r3v));
        assertTrue(lr.handler.getView().contains(r4v));
    }

    private static LogRecord createLogRecord(String logger, Level level, String message) {
        LogRecord r = new LogRecord(level, message);
        r.setLoggerName(logger);
        return r;
    }

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

}
