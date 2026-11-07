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

package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests the build capacity sections of the dashboard empty state.
 */
@WithJenkins
class AllViewEmptyStateTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void buildCapacityComesBeforeCreatingAJob() throws Exception {
        j.jenkins.setNumExecutors(0);

        String text = j.createWebClient().goTo("").asNormalizedText();
        assertThat(text, containsString("To get started, follow the steps below."));
        assertThat(text, containsString("Set up build capacity"));
        assertThat(text, containsString("Distributed builds"));
        assertThat(text, containsString("Configure built-in node"));
        assertThat(text, containsString("Start building your software project"));

        // Setting up build capacity is a prerequisite, so it is presented first.
        assertThat(text.indexOf("Set up build capacity"),
                lessThan(text.indexOf("Start building your software project")));
        assertThat(text.indexOf("Distributed builds"),
                lessThan(text.indexOf("Configure built-in node")));

        assertThat(text, containsString("No executors, agents or clouds are configured"));
    }

    @Test
    void addExecutorLinkUpdatesThePageInPlace() throws Exception {
        j.jenkins.setNumExecutors(0);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setFetchPolyfillEnabled(true);
        HtmlPage page = wc.goTo("");
        page.getAnchorByText("Add an executor now").click();
        wc.waitForBackgroundJavaScript(10000);

        assertEquals(1, j.jenkins.getNumExecutors());

        // The page is updated in place rather than reloaded, so the user stays where they were.
        assertEquals(j.getURL().toString(), page.getUrl().toString());

        String after = page.asNormalizedText();
        // Adding executors is build capacity, so the whole section is done and is removed, just as it would be
        // after setting up an agent.
        assertThat(after, not(containsString("Set up build capacity")));
        assertThat(after, not(containsString("Configure built-in node")));
        assertThat(after, not(containsString("Distributed builds")));
        assertThat(after, containsString("Start building your software project"));
        // A notification confirms the action, since nothing else on screen would.
        assertThat(after, containsString("Added an executor to the built-in node"));
        // The build executor status widget is refreshed rather than left claiming nothing is configured.
        assertThat(after, not(containsString("No executors, agents or clouds are configured")));
    }

    @Test
    void hidesBuildCapacitySectionWhenBuiltInNodeHasExecutors() throws Exception {
        assertEquals(2, j.jenkins.getNumExecutors());

        String text = j.createWebClient().goTo("").asNormalizedText();
        assertThat(text, not(containsString("Set up build capacity")));
        assertThat(text, not(containsString("Configure built-in node")));
        assertThat(text, containsString("Start building your software project"));
    }

    @Test
    void hidesBuildCapacitySectionWhenAnAgentExists() throws Exception {
        j.jenkins.setNumExecutors(0);
        j.createSlave();

        String text = j.createWebClient().goTo("").asNormalizedText();
        assertThat(text, not(containsString("Set up build capacity")));
        assertThat(text, containsString("Start building your software project"));
    }
}
