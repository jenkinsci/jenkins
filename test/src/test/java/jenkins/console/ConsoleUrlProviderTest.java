/*
 * The MIT License
 *
 * Copyright 2023 CloudBees, Inc.
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

package jenkins.console;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlPage;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class ConsoleUrlProviderTest {
    @ClassRule
    public static BuildWatcher watcher = new BuildWatcher();

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void getConsoleUrl() throws Exception {
        FreeStyleProject p = r.createProject(FreeStyleProject.class);
        // Default URL
        FreeStyleBuild b1 = r.buildAndAssertSuccess(p);
        assertCustomConsoleUrl(r.contextPath + '/' + b1.getUrl() + "console", b1);
        // Custom URL without leading slash
        FreeStyleBuild b2 = r.buildAndAssertSuccess(p);
        b2.setDescription("custom my/build/console");
        assertCustomConsoleUrl(r.contextPath + "/my/build/console", b2);
        // Custom URL with leading slash
        FreeStyleBuild b3 = r.buildAndAssertSuccess(p);
        b3.setDescription("custom /my/build/console");
        assertCustomConsoleUrl(r.contextPath + "/my/build/console", b3);
        // Default URL is used when extensions throw exceptions.
        FreeStyleBuild b4 = r.buildAndAssertSuccess(p);
        b4.setDescription("NullPointerException");
        assertCustomConsoleUrl(r.contextPath + '/' + b4.getUrl() + "console", b4);
    }

    // Awkward, but we can only call Functions.getConsoleUrl in the context of an HTTP request.
    public void assertCustomConsoleUrl(String expectedUrl, Run<?, ?> run) throws Exception {
        HtmlPage page = r.createWebClient().getPage(run.getParent());
        DomElement buildHistoryDiv = page.getElementById("buildHistory");
        assertThat("Console link for " + run + " should be " + expectedUrl,
                buildHistoryDiv.getByXPath("//a[@href='" + expectedUrl + "']"), not(empty()));
    }

    @TestExtension("getConsoleUrl")
    public static class CustomConsoleUrlProvider implements ConsoleUrlProvider {
        @Override
        public String getConsoleUrl(Run<?, ?> run) {
            String description = run.getDescription();
            if (description == null) {
                return null;
            } else if (description.startsWith("custom ")) {
                return description.substring("custom ".length());
            } else {
                throw new NullPointerException("getConsoleUrl should be robust against runtime errors");
            }
        }
    }

}
