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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.Util;
import hudson.XmlFile;
import hudson.model.Computer;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import hudson.remoting.VirtualChannel;
import hudson.util.FormValidation;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import jenkins.security.MasterToSlaveCallable;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.Url;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class LogRecorderManagerTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * Makes sure that the logger configuration works.
     */
    @Url("http://d.hatena.ne.jp/ssogabe/20090101/1230744150")
    @Test
    void loggerConfig() throws Exception {
        Logger logger = Logger.getLogger("foo.bar.zot");

        HtmlPage page = j.createWebClient().goTo("log/levels");
        HtmlForm form = page.getFormByName("configLogger");
        form.getInputByName("name").setValue("foo.bar.zot");
        form.getSelectByName("level").getOptionByValue("finest").setSelected(true);
        j.submit(form);

        assertEquals(Level.FINEST, logger.getLevel());
    }

    @Test
    void loggerConfigNotFound() throws Exception {
        HtmlPage page = j.createWebClient().goTo("log/levels");
        HtmlForm form = page.getFormByName("configLogger");
        form.getInputByName("name").setValue("foo.bar.zot");
        form.getSelectByName("level").getOptionByValue("finest").setSelected(true);
        FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> j.submit(form));
        assertThat(e.getStatusCode(), equalTo(HttpURLConnection.HTTP_BAD_REQUEST));
        assertThat(e.getResponse().getContentAsString(), containsString("A logger named \"foo.bar.zot\" does not exist"));
    }

    @Issue("JENKINS-62472")
    @Test
    void logRecorderCheckName() {
        LogRecorder testRecorder = new LogRecorder("test");
        String warning = FormValidation.warning(Messages.LogRecorder_Target_Empty_Warning()).toString();
        assertEquals(warning, testRecorder.doCheckName("", null).toString());
        assertEquals(warning, testRecorder.doCheckName("", "illegalArgument").toString());
        assertEquals(warning, testRecorder.doCheckName("", Level.ALL.getName()).toString());
        assertEquals(warning, testRecorder.doCheckName("", Level.FINEST.getName()).toString());
        assertEquals(warning, testRecorder.doCheckName("", Level.FINER.getName()).toString());
        assertEquals(warning, testRecorder.doCheckName("", Level.FINER.getName()).toString());
        assertEquals(FormValidation.ok(), testRecorder.doCheckName("a", "illegalArgument"));
        assertEquals(FormValidation.ok(), testRecorder.doCheckName("a", null));
        assertEquals(FormValidation.ok(), testRecorder.doCheckName("a", Level.ALL.getName()));
        assertEquals(FormValidation.ok(), testRecorder.doCheckName("a", Level.FINEST.getName()));
        assertEquals(FormValidation.ok(), testRecorder.doCheckName("a", Level.FINER.getName()));
        assertEquals(FormValidation.ok(), testRecorder.doCheckName("a", Level.FINER.getName()));
        assertEquals(FormValidation.ok(), testRecorder.doCheckName("", Level.CONFIG.getName()));
        assertEquals(FormValidation.ok(), testRecorder.doCheckName("", Level.INFO.getName()));
        assertEquals(FormValidation.ok(), testRecorder.doCheckName("", Level.WARNING.getName()));
        assertEquals(FormValidation.ok(), testRecorder.doCheckName("", Level.SEVERE.getName()));
        assertEquals(FormValidation.ok(), testRecorder.doCheckName("", Level.OFF.getName()));
        assertEquals(FormValidation.ok(), testRecorder.doCheckName("a", Level.CONFIG.getName()));
        assertEquals(FormValidation.ok(), testRecorder.doCheckName("a", Level.INFO.getName()));
        assertEquals(FormValidation.ok(), testRecorder.doCheckName("a", Level.WARNING.getName()));
        assertEquals(FormValidation.ok(), testRecorder.doCheckName("a", Level.SEVERE.getName()));
        assertEquals(FormValidation.ok(), testRecorder.doCheckName("a", Level.OFF.getName()));
    }

    @Test
    void createLogRecorderWithNonAsciiName() throws Exception {
        String name = "Journal d’accès";

        HtmlPage page = j.createWebClient().goTo("log/new");
        HtmlForm form = page.getFormByName("configSubmit");
        form.getInputByName("name").setValueAttribute(name);
        j.submit(form);

        assertNotNull(j.jenkins.getLog().getLogRecorder(name));
        j.createWebClient().goTo("log/" + Util.rawEncode(name) + "/configure");
    }

    @Issue({"JENKINS-18274", "JENKINS-63458"})
    @Test
    void loggingOnSlaves() throws Exception {
        // TODO could also go through WebClient to assert that the config UI works
        LogRecorderManager mgr = j.jenkins.getLog();
        LogRecorder r1 = new LogRecorder("r1");
        mgr.getRecorders().add(r1);
        LogRecorder.Target t = new LogRecorder.Target("ns1", Level.FINE);
        r1.getLoggers().add(t);
        r1.save();
        t.enable();
        Computer c = j.createOnlineSlave().toComputer();
        assertNotNull(c);
        t = new LogRecorder.Target("ns2", Level.FINER);
        r1.getLoggers().add(t);
        r1.save();
        t.enable();
        LogRecorder r2 = new LogRecorder("r2");
        mgr.getRecorders().add(r2);
        t = new LogRecorder.Target("ns3", Level.FINE);
        r2.getLoggers().add(t);
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
        assertTrue(ch.call(new Log(Level.INFO, "other", "msg #5 {0,number,0.0} {1,number,0.0} ''OK?''", new Object[] {1.0, 2.0})));
        assertTrue(ch.call(new LambdaLog(Level.FINE, "ns1")));
        assertFalse(ch.call(new LambdaLog(Level.FINER, "ns1")));
        List<LogRecord> recs = c.getLogRecords();
        assertEquals(6, recs.size(), show(recs));
        // Would of course prefer to get "msg #5 1.0 2.0 'OK?'" but all attempts to fix this have ended in disaster (JENKINS-63458):
        assertEquals("msg #5 {0,number,0.0} {1,number,0.0} ''OK?''", new SimpleFormatter().formatMessage(recs.get(1)));
        recs = r1.getSlaveLogRecords().get(c);
        assertNotNull(recs);
        assertEquals(3, recs.size(), show(recs));
        recs = r2.getSlaveLogRecords().get(c);
        assertNotNull(recs);
        assertEquals(1, recs.size(), show(recs));
        String text = j.createWebClient().goTo("log/r1/").asNormalizedText();
        assertTrue(text.contains(c.getDisplayName()), text);
        assertTrue(text.contains("msg #1"), text);
        assertTrue(text.contains("msg #2"), text);
        assertFalse(text.contains("msg #3"), text);
        assertFalse(text.contains("msg #4"), text);
        assertTrue(text.contains("LambdaLog @FINE"), text);
        assertFalse(text.contains("LambdaLog @FINER"), text);
    }

    @Test
    void deletingLogRecorder() throws IOException {
        LogRecorderManager log = j.jenkins.getLog();
        assertThat(log.getRecorders(), empty());
        LogRecorder logRecorder = new LogRecorder("dummy");
        logRecorder.getLoggers().add(new LogRecorder.Target("dummy", Level.ALL));
        log.getRecorders().add(logRecorder);
        logRecorder.save();
        assertThat(log.getRecorders(), hasSize(1));
        logRecorder.delete();
        assertThat(log.getRecorders(), empty());
        assertTrue(DeletingLogRecorderListener.recordDeletion);
    }

    @TestExtension("deletingLogRecorder")
    public static class DeletingLogRecorderListener extends SaveableListener {
        private static boolean recordDeletion;

        @Override
        public void onDeleted(Saveable o, XmlFile file) {
            if (o instanceof LogRecorder && "dummy".equals(((LogRecorder) o).getName())) {
                if (!file.exists()) {
                    recordDeletion = true;
                }
            }
        }
    }

    private static final class Log extends MasterToSlaveCallable<Boolean, Error> {
        private final Level level;
        private final String logger;
        private final String message;
        private final Object[] params;

        Log(Level level, String logger, String message) {
            this(level, logger, message, null);
        }

        Log(Level level, String logger, String message, Object[] params) {
            this.level = level;
            this.logger = logger;
            this.message = message;
            this.params = params;
        }

        @Override public Boolean call() throws Error {
            Logger log = Logger.getLogger(logger);
            if (params != null) {
                log.log(level, message, params);
            } else {
                log.log(level, message);
            }
            return log.isLoggable(level);
        }
    }

    private static final class LambdaLog extends MasterToSlaveCallable<Boolean, Error> {
        private final Level level;
        private final String logger;

        LambdaLog(Level level, String logger) {
            this.level = level;
            this.logger = logger;
        }

        @Override public Boolean call() throws Error {
            Logger log = Logger.getLogger(logger);
            log.log(level, () -> "LambdaLog @" + level);
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
