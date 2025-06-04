/*
 * The MIT License
 *
 * Copyright 2020 CloudBees, Inc.
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

package hudson.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.console.HyperlinkNote;
import hudson.model.TaskListener;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.security.MasterToSlaveCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class LogTaskListenerTest {

    private final LogRecorder logging = new LogRecorder().record("LogTaskListenerTest", Level.ALL).capture(100);

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    @Test
    void annotations() throws Exception {
        TaskListener l = new LogTaskListener(Logger.getLogger("LogTaskListenerTest"), Level.FINE);
        l.getLogger().println("plain line");
        String url = "http://nowhere.net/";
        l.annotate(new HyperlinkNote(url, 0));
        l.getLogger().println("from annotate");
        l.hyperlink(url, "from hyperlink");
        l.getLogger().println();
        l.getLogger().println(HyperlinkNote.encodeTo(url, "there") + " from encoded");
        assertEquals("[plain line, from annotate, from hyperlink, there from encoded]", logging.getMessages().toString());
    }

    @Test
    void serialization() throws Exception {
        TaskListener l = new LogTaskListener(Logger.getLogger("LogTaskListenerTest"), Level.INFO);
        r.createOnlineSlave().getChannel().call(new Log(l));
        assertEquals("[from agent]", logging.getMessages().toString());
    }

    private static final class Log extends MasterToSlaveCallable<Void, RuntimeException> {

        private final TaskListener l;

        Log(TaskListener l) {
            this.l = l;
        }

        @Override
        public Void call() throws RuntimeException {
            l.getLogger().println("from agent");
            l.getLogger().flush();
            return null;
        }

    }

}
