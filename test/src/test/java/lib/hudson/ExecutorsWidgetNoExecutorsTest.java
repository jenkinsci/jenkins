/*
 * The MIT License
 *
 * Copyright (c) 2026, CloudBees, Inc.
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

package lib.hudson;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import hudson.model.Item;
import jenkins.model.Jenkins;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests the "no executors" message of the build executor status widget.
 */
@WithJenkins
class ExecutorsWidgetNoExecutorsTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        j = rule;
        j.jenkins.setNumExecutors(0);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.READ, Item.READ).everywhere().to("dev"));
    }

    @Test
    void administratorSeesActionableLinks() throws Exception {
        HtmlPage page = j.createWebClient().login("admin").goTo("");
        String text = page.asNormalizedText();
        assertThat(text, containsString("No executors, agents or clouds are configured."));
        assertThat(text, containsString("Set up an agent"));

        // All three links must resolve, not 404.
        for (String label : new String[] {"an agent", "a cloud", "configure executors"}) {
            HtmlAnchor a = page.getAnchorByText(label);
            HtmlPage target = a.click();
            assertThat("link for '" + label + "' should resolve",
                    target.getWebResponse().getStatusCode(), org.hamcrest.Matchers.is(200));
        }
    }

    @Test
    void nonAdministratorSeesPlainMessage() throws Exception {
        HtmlPage page = j.createWebClient().login("dev").goTo("");
        String text = page.asNormalizedText();
        assertThat(text, containsString("No executors, agents or clouds are configured."));
        // No actionable guidance for users who cannot act on it.
        assertThat(text, not(containsString("Set up an agent")));
        assertThat(page.getWebResponse().getContentAsString(), not(containsString("(built-in)/configure")));
    }
}
