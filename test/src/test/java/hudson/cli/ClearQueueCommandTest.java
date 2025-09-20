/*
 * The MIT License
 *
 * Copyright 2016 Red Hat, Inc.
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

package hudson.cli;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.hasNoStandardOutput;
import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import hudson.model.FreeStyleProject;
import hudson.model.labels.LabelAtom;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author pjanouse
 */
@WithJenkins
class ClearQueueCommandTest {

    private CLICommandInvoker command;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        command = new CLICommandInvoker(j, "clear-queue");
    }

    @Test
    void clearQueueShouldFailWithoutAdministerPermission() {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ).invoke();

        assertThat(result, failedWith(6));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("user is missing the Overall/Administer permission"));
    }

    @Test
    void clearQueueShouldSucceedOnEmptyQueue() {
        assertThat(j.jenkins.getQueue().isEmpty(), equalTo(true));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Jenkins.ADMINISTER).invoke();

        assertThat(result, succeededSilently());
        assertThat(j.jenkins.getQueue().isEmpty(), equalTo(true));
    }

    @Test
    void clearQueueShouldSucceed() throws Exception {
        assertThat(j.jenkins.getQueue().isEmpty(), equalTo(true));

        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.setAssignedLabel(new LabelAtom("never_created"));
        project.scheduleBuild2(0);

        assertThat(j.jenkins.getQueue().isEmpty(), equalTo(false));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Jenkins.ADMINISTER).invoke();

        assertThat(result, succeededSilently());
        assertThat(j.jenkins.getQueue().isEmpty(), equalTo(true));
    }
}
