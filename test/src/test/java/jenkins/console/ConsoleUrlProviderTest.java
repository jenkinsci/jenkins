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

import hudson.model.Descriptor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.model.User;
import java.util.List;
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
        ConsoleUrlProviderGlobalConfiguration.get().setProviders(List.of(new CustomConsoleUrlProvider()));
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
        // TODO: Would be nice to add a test for precedence of items in the list.
    }

    @Test
    public void getUserSpecificConsoleUrl() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        User admin = User.getById("admin", true);
        // Admin choses custom, user overrides to default
        ConsoleUrlProviderGlobalConfiguration.get().setProviders(List.of(new CustomConsoleUrlProvider()));
        admin.getProperty(ConsoleUrlProviderUserProperty.class).setProviders(List.of(new ConsoleUrlProvider.Default()));
        FreeStyleProject p = r.createProject(FreeStyleProject.class);
        FreeStyleBuild b = r.buildAndAssertSuccess(p);
        b.setDescription("custom my/build/console");
        assertCustomConsoleUrl(r.contextPath + "/my/build/console", b);
        assertCustomConsoleUrl(r.contextPath + "/" + b.getUrl() + "console", admin, b);
        // Admin does not configure anything, user chooses custom
        ConsoleUrlProviderGlobalConfiguration.get().setProviders(null);
        admin.getProperty(ConsoleUrlProviderUserProperty.class).setProviders(List.of(new CustomConsoleUrlProvider()));
        assertCustomConsoleUrl(r.contextPath + "/" + b.getUrl() + "console", b);
        assertCustomConsoleUrl(r.contextPath + "/my/build/console", admin, b);
    }

    public void assertCustomConsoleUrl(String expectedUrl, Run<?, ?> run) throws Exception {
        assertCustomConsoleUrl(expectedUrl, null, run);
    }

    // Awkward, but we can only call Functions.getConsoleUrl in the context of an HTTP request.
    public void assertCustomConsoleUrl(String expectedUrl, User user, Run<?, ?> run) throws Exception {
        JenkinsRule.WebClient wc = r.createWebClient();
        if (user != null) {
            wc.login(user.getId(), user.getId());
        }
        HtmlPage page = wc.getPage(run.getParent());
        DomElement buildHistoryDiv = page.getElementById("buildHistory");
        assertThat("Console link for " + run + " should be " + expectedUrl,
                buildHistoryDiv.getByXPath("//a[@href='" + expectedUrl + "']"), not(empty()));
    }

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

        @TestExtension
        public static class DescriptorImpl extends Descriptor<ConsoleUrlProvider> { }
    }

}
