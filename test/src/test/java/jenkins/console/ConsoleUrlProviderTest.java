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

import static org.junit.Assert.assertEquals;

import hudson.model.Descriptor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.model.User;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    public void getRedirectUrl() throws Exception {
        ConsoleUrlProviderGlobalConfiguration.get().setProviders(list(new CustomConsoleUrlProvider()));
        FreeStyleProject p = r.createProject(FreeStyleProject.class);
        // Default URL
        FreeStyleBuild b = r.buildAndAssertSuccess(p);
        assertCustomConsoleUrl(r.contextPath + '/' + b.getUrl() + "console", b);
        // Custom URL without leading slash
        b.setDescription("custom my/build/console");
        assertCustomConsoleUrl(r.contextPath + "/my/build/console", b);
        // Custom URL with leading slash -> not supported, falls back to default
        b.setDescription("custom /my/build/console");
        assertCustomConsoleUrl(r.contextPath + '/' + b.getUrl() + "console", b);
        // Default URL is used when extensions throw exceptions.
        b.setDescription("NullPointerException");
        assertCustomConsoleUrl(r.contextPath + '/' + b.getUrl() + "console", b);
        // Check precedence and fallthrough behavior with ConsoleUrlProviderGlobalConfiguration.providers.
        ConsoleUrlProviderGlobalConfiguration.get().setProviders(
                list(new IgnoreAllRunsConsoleUrlProvider(), new CustomConsoleUrlProvider("-a"), new CustomConsoleUrlProvider("-b")));
        b.setDescription("custom my/build/console");
        assertCustomConsoleUrl(r.contextPath + "/my/build/console-a", b);
    }

    @Test
    public void getUserSpecificRedirectUrl() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        User admin = User.getById("admin", true);
        // Admin choses custom, user overrides to default
        ConsoleUrlProviderGlobalConfiguration.get().setProviders(list(new CustomConsoleUrlProvider()));
        admin.getProperty(ConsoleUrlProviderUserProperty.class).setProviders(list(new DefaultConsoleUrlProvider()));
        FreeStyleProject p = r.createProject(FreeStyleProject.class);
        FreeStyleBuild b = r.buildAndAssertSuccess(p);
        b.setDescription("custom my/build/console");
        assertCustomConsoleUrl(r.contextPath + "/my/build/console", b);
        assertCustomConsoleUrl(r.contextPath + "/" + b.getUrl() + "console", admin, b);
        // Admin does not configure anything, user chooses custom
        ConsoleUrlProviderGlobalConfiguration.get().setProviders(null);
        admin.getProperty(ConsoleUrlProviderUserProperty.class).setProviders(list(new CustomConsoleUrlProvider()));
        assertCustomConsoleUrl(r.contextPath + "/" + b.getUrl() + "console", b);
        assertCustomConsoleUrl(r.contextPath + "/my/build/console", admin, b);
        // Check precedence and fallthrough behavior with ConsoleUrlProviderUserProperty.providers.
        admin.getProperty(ConsoleUrlProviderUserProperty.class).setProviders(
                list(new IgnoreAllRunsConsoleUrlProvider(), new CustomConsoleUrlProvider("-a"), new CustomConsoleUrlProvider("-b")));
        assertCustomConsoleUrl(r.contextPath + "/my/build/console-a", admin, b);
    }

    @Test
    public void useGlobalProvidersIfUserProvidersDontReturnValidUrl() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        User admin = User.getById("admin", true);
        // Admin choses custom, user chooses a provider that ignores everything, so global choice still gets used.
        ConsoleUrlProviderGlobalConfiguration.get().setProviders(list(new CustomConsoleUrlProvider()));
        admin.getProperty(ConsoleUrlProviderUserProperty.class).setProviders(list(new IgnoreAllRunsConsoleUrlProvider()));
        FreeStyleProject p = r.createProject(FreeStyleProject.class);
        FreeStyleBuild b = r.buildAndAssertSuccess(p);
        b.setDescription("custom my/build/console");
        assertCustomConsoleUrl(r.contextPath + "/my/build/console", admin, b);
    }

    @Test
    public void invalidRedirectUrls() throws Exception {
        ConsoleUrlProviderGlobalConfiguration.get().setProviders(list(new CustomConsoleUrlProvider()));
        FreeStyleProject p = r.createProject(FreeStyleProject.class);
        FreeStyleBuild b = r.buildAndAssertSuccess(p);
        b.setDescription("custom https://example.com");
        assertCustomConsoleUrl(r.contextPath + "/" + b.getUrl() + "console", b);
        b.setDescription("custom <!>invalid url<!>");
        assertCustomConsoleUrl(r.contextPath + "/" + b.getUrl() + "console", b);
    }

    public void assertCustomConsoleUrl(String expectedUrl, Run<?, ?> run) throws Exception {
        assertCustomConsoleUrl(expectedUrl, null, run);
    }

    public void assertCustomConsoleUrl(String expectedUrl, User user, Run<?, ?> run) throws Exception {
        JenkinsRule.WebClient wc = r.createWebClient();
        if (user != null) {
            wc.login(user.getId(), user.getId());
        }
        String actualUrl = wc.executeOnServer(() -> ConsoleUrlProvider.getRedirectUrl(run));
        assertEquals(expectedUrl, actualUrl);
    }

    // Like List.of, but avoids JEP-200 class filter warnings.
    private static <T> List<T> list(T... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    public static class CustomConsoleUrlProvider implements ConsoleUrlProvider {
        private final String suffix;

        public CustomConsoleUrlProvider() {
            this.suffix = "";
        }

        public CustomConsoleUrlProvider(String suffix) {
            this.suffix = suffix;
        }

        @Override
        public String getConsoleUrl(Run<?, ?> run) {
            String description = run.getDescription();
            if (description == null) {
                return null;
            } else if (description.startsWith("custom ")) {
                return description.substring("custom ".length()) + suffix;
            } else {
                throw new NullPointerException("getConsoleUrl should be robust against runtime errors");
            }
        }

        @TestExtension
        public static class DescriptorImpl extends Descriptor<ConsoleUrlProvider> { }
    }

    public static class IgnoreAllRunsConsoleUrlProvider implements ConsoleUrlProvider {

        @Override
        public String getConsoleUrl(Run<?, ?> run) {
            return null;
        }

        @TestExtension
        public static class DescriptorImpl extends Descriptor<ConsoleUrlProvider> { }
    }

}
