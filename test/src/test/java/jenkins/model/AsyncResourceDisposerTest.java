/*
 * The MIT License
 *
 * Copyright (c) 2015 Red Hat, Inc.
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
package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import java.io.IOException;

import jenkins.model.AsyncResourceDisposer.Disposable;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

import com.gargoylesoftware.htmlunit.html.HtmlPage;

public class AsyncResourceDisposerTest {

    @Rule public final JenkinsRule j = new JenkinsRule();

    private AsyncResourceDisposer disposer;

    @Before
    public void setUp() {
        disposer = (AsyncResourceDisposer) j.jenkins.getAdministrativeMonitor("AsyncResourceDisposer");
    }

    @Test
    public void showProblems() throws Exception {
        disposer.dispose(new FailingDisposable());

        Thread.sleep(1000);

        WebClient wc = j.createWebClient();
        HtmlPage manage = wc.goTo("manage");
        assertThat(manage.asText(), containsString("There are resources Jenkins was not able to dispose automatically"));
        HtmlPage report = wc.goTo(disposer.getUrl());
        assertThat(report.asText(), containsString("Failing disposable"));
        assertThat(report.asText(), containsString("IOException: Unable to dispose"));
    }

    private static class FailingDisposable implements Disposable {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean dispose() throws Exception {
            throw new IOException("Unable to dispose");
        }

        @Override
        public String getDisplayName() {
            return "Failing disposable";
        }
    }
}
