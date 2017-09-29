/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import jenkins.security.MasterToSlaveCallable;
import org.jvnet.hudson.test.Url;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import hudson.model.Computer;
import hudson.remoting.VirtualChannel;
import java.util.List;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Kohsuke Kawaguchi
 */
public class LogRecorderManagerTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    /**
     * Makes sure that the logger configuration works.
     */
    @Url("http://d.hatena.ne.jp/ssogabe/20090101/1230744150")
    @Test public void loggerConfig() throws Exception {
        Logger logger = Logger.getLogger("foo.bar.zot");

        HtmlPage page = j.createWebClient().goTo("log/levels");
        HtmlForm form = page.getFormByName("configLogger");
        form.getInputByName("name").setValueAttribute("foo.bar.zot");
        form.getSelectByName("level").getOptionByValue("finest").setSelected(true);
        j.submit(form);

        assertEquals(logger.getLevel(), Level.FINEST);
    }

    @Issue("JENKINS-18274")
    @Test public void loggingOnSlaves() throws Exception {
        // TODO could also go through WebClient to assert that the config UI works
        LogRecorderManager mgr = j.jenkins.getLog();
        LogRecorder r1 = new LogRecorder("r1");
        mgr.logRecorders.put("r1", r1);
        LogRecorder.Target t = new LogRecorder.Target("ns1", Level.FINE);
        r1.targets.add(t);
        r1.save();
        t.enable();
        Computer c = j.createOnlineSlave().toComputer();
        assertNotNull(c);
        t = new LogRecorder.Target("ns2", Level.FINER);
        r1.targets.add(t);
        r1.save();
        t.enable();
        LogRecorder r2 = new LogRecorder("r2");
        mgr.logRecorders.put("r2", r2);
        t = new LogRecorder.Target("ns3", Level.FINE);
        r2.targets.add(t);
        r2.save();
        t.enable();
        VirtualChannel ch = c.getChannel();
        assertNotNull(ch);
        assertTrue(ch.call(new Log(Level.FINE, "ns1", "msg #1")));
        assertTrue(ch.call(new Log(Level.FINER, "ns2", "msg #2")));
        assertTrue(ch.call(new Log(Level.FINE, "ns3", "msg #3")));
        assertFalse(ch.call(new Log(Level.FINER, "ns3", "not displayed")));
        assertTrue(ch.call(new Log(Level.INFO, "ns4", "msg #4")));
        assertFalse(ch.call(new Log(Level.FINE, "ns4", "not displayed")));
        List<LogRecord> recs = c.getLogRecords();
        assertEquals(show(recs), 4, recs.size());
        recs = r1.getSlaveLogRecords().get(c);
        assertNotNull(recs);
        assertEquals(show(recs), 2, recs.size());
        recs = r2.getSlaveLogRecords().get(c);
        assertNotNull(recs);
        assertEquals(show(recs), 1, recs.size());
        String text = j.createWebClient().goTo("log/r1/").asText();
        assertTrue(text, text.contains(c.getDisplayName()));
        assertTrue(text, text.contains("msg #1"));
        assertTrue(text, text.contains("msg #2"));
        assertFalse(text, text.contains("msg #3"));
        assertFalse(text, text.contains("msg #4"));
    }

    private static final class Log extends MasterToSlaveCallable<Boolean,Error> {
        private final Level level;
        private final String logger;
        private final String message;
        Log(Level level, String logger, String message) {
            this.level = level;
            this.logger = logger;
            this.message = message;
        }
        @Override public Boolean call() throws Error {
            Logger log = Logger.getLogger(logger);
            log.log(level, message);
            return log.isLoggable(level);
        }
    }

    private static String show(List<LogRecord> recs) {
        StringBuilder b = new StringBuilder();
        for (LogRecord rec : recs) {
            b.append('\n').append(rec.getLoggerName()).append(':').append(rec.getLevel()).append(':').append(rec.getMessage());
        }
        return b.toString();
    }

}
