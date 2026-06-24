/*
 * The MIT License
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

package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hudson.Launcher;
import jenkins.model.Jenkins;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class RunConsolePermissionTest {

    private static final String CONSOLE_PROPERTY = "hudson.security.ConsolePermission";
    private static final String MARKER = "the secret console output";

    private JenkinsRule j;
    private String previousPropertyValue;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        previousPropertyValue = System.getProperty(CONSOLE_PROPERTY);
    }

    @AfterEach
    void tearDown() {
        if (previousPropertyValue == null) {
            System.clearProperty(CONSOLE_PROPERTY);
        } else {
            System.setProperty(CONSOLE_PROPERTY, previousPropertyValue);
        }
    }

    private FreeStyleBuild createBuildWithConsoleOutput() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
                listener.getLogger().println(MARKER);
                return true;
            }
        });
        return j.buildAndAssertSuccess(p);
    }

    private void configureSecurity(MockAuthorizationStrategy strategy) {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(strategy);
    }

    @Test
    void consoleVisibleToReaderByDefault() throws Exception {
        System.clearProperty(CONSOLE_PROPERTY);
        FreeStyleBuild b = createBuildWithConsoleOutput();
        configureSecurity(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Item.READ).everywhere().to("reader"));

        JenkinsRule.WebClient wc = j.createWebClient().login("reader");
        Page rsp = wc.goTo(b.getUrl() + "consoleText", "text/plain");
        assertThat(rsp.getWebResponse().getContentAsString(), containsString(MARKER));
    }

    @Test
    void consoleHiddenFromReaderWhenPermissionEnabled() throws Exception {
        System.setProperty(CONSOLE_PROPERTY, "true");
        FreeStyleBuild b = createBuildWithConsoleOutput();
        configureSecurity(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Item.READ).everywhere().to("reader")
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.READ, Item.READ, Run.CONSOLE).everywhere().to("viewer"));

        JenkinsRule.WebClient readerClient = j.createWebClient().login("reader");
        FailingHttpStatusCodeException ex = assertThrows(FailingHttpStatusCodeException.class,
                () -> readerClient.goTo(b.getUrl() + "consoleText", "text/plain"));
        assertEquals(403, ex.getStatusCode());

        JenkinsRule.WebClient viewerClient = j.createWebClient().login("viewer");
        Page rsp = viewerClient.goTo(b.getUrl() + "consoleText", "text/plain");
        assertThat(rsp.getWebResponse().getContentAsString(), containsString(MARKER));

        JenkinsRule.WebClient adminClient = j.createWebClient().login("admin");
        Page adminRsp = adminClient.goTo(b.getUrl() + "consoleText", "text/plain");
        assertThat(adminRsp.getWebResponse().getContentAsString(), containsString(MARKER));
    }
}
