/*
 * The MIT License
 *
 * Copyright (c) 2016 Oleg Nenashev.
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
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue;
import hudson.model.StringParameterDefinition;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URL;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;


/**
 * Tests of {@link ParameterizedJobMixIn}.
 * @author Oleg Nenashev
 */
@WithJenkins
class ParameterizedJobMixInTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void doBuild_shouldFailWhenInvokingDisabledProject() throws Exception {
        final FreeStyleProject project = j.createFreeStyleProject();
        project.doDisable();

        final JenkinsRule.WebClient webClient = j.createWebClient();
        webClient.assertFails(project.getUrl() + "build", HttpServletResponse.SC_CONFLICT);
    }

    @Test
    @Issue("JENKINS-36193")
    void doBuildWithParameters_shouldFailWhenInvokingDisabledProject() throws Exception {
        final FreeStyleProject project = j.createFreeStyleProject();
        project.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("FOO", "BAR")));
        project.doDisable();

        final JenkinsRule.WebClient webClient = j.createWebClient();

        FailingHttpStatusCodeException fex = assertThrows(
                FailingHttpStatusCodeException.class,
                () -> webClient.getPage(webClient.addCrumb(new WebRequest(new URL(j.getURL(), project.getUrl() + "build?delay=0"), HttpMethod.POST))),
                "should fail when invoking disabled project");
        assertThat("Should fail with conflict", fex.getStatusCode(), is(409));
    }

    @Test
    @Issue("JENKINS-48770")
    void doBuildQuietPeriodInSeconds() throws Exception {
        final int projectQuietPeriodInSeconds = 50;

        final FreeStyleProject project = j.createFreeStyleProject();
        project.setQuietPeriod(projectQuietPeriodInSeconds);

        final JenkinsRule.WebClient webClient = j.createWebClient();
        webClient.getPage(webClient.addCrumb(new WebRequest(new URL(j.getURL(), project.getUrl() + "build"), HttpMethod.POST)));
        long triggerTime = System.currentTimeMillis();

        Queue.Item[] items = Jenkins.get().getQueue().getItems();
        assertThat(items, arrayWithSize(1));
        assertThat(items[0], instanceOf(Queue.WaitingItem.class));
        assertThat(items[0].task, instanceOf(FreeStyleProject.class));

        Queue.WaitingItem waitingItem = (Queue.WaitingItem) items[0];
        assertTrue(waitingItem.timestamp.getTimeInMillis() - triggerTime > 45000);

        Jenkins.get().getQueue().doCancelItem(1);
    }
}
