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

import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.ListView;
import hudson.model.View;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.hasNoStandardOutput;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * @author pjanouse
 */
public abstract class ViewManipulationTestBase {

    protected CLICommandInvoker command;

    abstract CLICommandInvoker getCommand();

    @Rule public final JenkinsRule j = new JenkinsRule();

    @Before public void setUp() {
        command = getCommand();
    }

    @Test public void jobViewManipulationShouldFailWithJenkinsReadPermissionOnly() throws IOException {

        j.jenkins.addView(new ListView("aView"));
        j.createFreeStyleProject("aProject");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ)
                .invokeWithArgs("aView", "aProject");

        assertThat(result, failedWith(6));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: user is missing the View/Read permission"));
    }

    @Test public void jobViewManipulationShouldFailWithViewReadPermissionOnly() throws IOException {

        j.jenkins.addView(new ListView("aView"));
        j.createFreeStyleProject("aProject");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, View.READ)
                .invokeWithArgs("aView", "aProject");

        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such job 'aProject'"));
    }

    @Test public void jobViewManipulationShouldFailWithViewReadAndJobReadPermissionsOnly() throws IOException {

        j.jenkins.addView(new ListView("aView"));
        j.createFreeStyleProject("aProject");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, View.READ, Job.READ)
                .invokeWithArgs("aView", "aProject");

        assertThat(result, failedWith(6));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: user is missing the View/Configure permission"));
    }

    @Test public void jobViewManipulationShouldFailIfTheViewIsNotDirectlyModifiable() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("aProject");

        assertThat(j.jenkins.getView("All").getAllItems().size(), equalTo(1));
        assertThat(j.jenkins.getView("All").contains(project), equalTo(true));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, View.READ, Job.READ, View.CONFIGURE)
                .invokeWithArgs("All", "aProject");

        assertThat(result, failedWith(4));
        assertThat(result.stderr(), containsString("ERROR: 'All' view can not be modified directly"));
        assertThat(j.jenkins.getView("All").getAllItems().size(), equalTo(1));
        assertThat(j.jenkins.getView("All").contains(project), equalTo(true));
    }

    @Test public void jobViewManipulationShouldFailIfTheJobDoesNotExist() throws Exception {

        j.jenkins.addView(new ListView("aView"));

        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));

        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, View.READ, Job.READ, View.CONFIGURE)
                .invokeWithArgs("aView", "never_created");

        assertThat(result, failedWith(3));
        assertThat(result.stderr(), containsString("ERROR: No such job 'never_created'"));
        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));

        FreeStyleProject project = j.createFreeStyleProject("aProject");

        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));

        result = command
                .authorizedTo(Jenkins.READ, View.READ, Job.READ, View.CONFIGURE)
                .invokeWithArgs("aView", "aProject1");

        assertThat(result, failedWith(3));
        assertThat(result.stderr(), containsString("ERROR: No such job 'aProject1'; perhaps you meant 'aProject'?"));
        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));
        assertThat(j.jenkins.getView("aView").contains(project), equalTo(false));
    }

    @Test public void jobViewManipulationShouldFailIfTheJobNameIsEmpty() throws Exception {

        j.jenkins.addView(new ListView("aView"));

        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, View.READ, Job.READ, View.CONFIGURE)
                .invokeWithArgs("aView", "");

        assertThat(result, failedWith(3));
        assertThat(result.stderr(), containsString("ERROR: No such job ''"));
        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));
    }

    @Test public void jobViewManipulationManyShouldFailIfFirstJobDoesNotExist() throws Exception {

        j.jenkins.addView(new ListView("aView"));
        FreeStyleProject project1 = j.createFreeStyleProject("aProject1");
        FreeStyleProject project2 = j.createFreeStyleProject("aProject2");

        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));
        assertThat(j.jenkins.getView("aView").contains(project1), equalTo(false));
        assertThat(j.jenkins.getView("aView").contains(project2), equalTo(false));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, View.READ, Job.READ, View.CONFIGURE)
                .invokeWithArgs("aView", "never_created", "aProject1", "aProject2");

        assertThat(result, failedWith(3));
        assertThat(result.stderr(), containsString("ERROR: No such job 'never_created'; perhaps you meant 'aProject1'?"));
        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));
        assertThat(j.jenkins.getView("aView").contains(project1), equalTo(false));
        assertThat(j.jenkins.getView("aView").contains(project2), equalTo(false));
    }

    @Test public void jobViewManipulationManyShouldFailIfMiddleJobDoesNotExist() throws Exception {

        j.jenkins.addView(new ListView("aView"));
        FreeStyleProject project1 = j.createFreeStyleProject("aProject1");
        FreeStyleProject project2 = j.createFreeStyleProject("aProject2");

        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));
        assertThat(j.jenkins.getView("aView").contains(project1), equalTo(false));
        assertThat(j.jenkins.getView("aView").contains(project2), equalTo(false));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, View.READ, Job.READ, View.CONFIGURE)
                .invokeWithArgs("aView", "aProject1", "never_created", "aProject2");

        assertThat(result, failedWith(3));
        assertThat(result.stderr(), containsString("ERROR: No such job 'never_created'; perhaps you meant 'aProject1'?"));
        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));
        assertThat(j.jenkins.getView("aView").contains(project1), equalTo(false));
        assertThat(j.jenkins.getView("aView").contains(project2), equalTo(false));
    }

    @Test public void jobViewManipulationManyShouldFailIfLastJobDoesNotExist() throws Exception {

        j.jenkins.addView(new ListView("aView"));
        FreeStyleProject project1 = j.createFreeStyleProject("aProject1");
        FreeStyleProject project2 = j.createFreeStyleProject("aProject2");

        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));
        assertThat(j.jenkins.getView("aView").contains(project1), equalTo(false));
        assertThat(j.jenkins.getView("aView").contains(project2), equalTo(false));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, View.READ, Job.READ, View.CONFIGURE)
                .invokeWithArgs("aView", "aProject1", "aProject2", "never_created");

        assertThat(result, failedWith(3));
        assertThat(result.stderr(), containsString("ERROR: No such job 'never_created'; perhaps you meant 'aProject1'?"));
        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));
        assertThat(j.jenkins.getView("aView").contains(project1), equalTo(false));
        assertThat(j.jenkins.getView("aView").contains(project2), equalTo(false));
    }

    @Test public void jobViewManipulationManyShouldFailIfMoreJobsDoNotExist() throws Exception {

        j.jenkins.addView(new ListView("aView"));
        FreeStyleProject project1 = j.createFreeStyleProject("aProject1");
        FreeStyleProject project2 = j.createFreeStyleProject("aProject2");

        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));
        assertThat(j.jenkins.getView("aView").contains(project1), equalTo(false));
        assertThat(j.jenkins.getView("aView").contains(project2), equalTo(false));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, View.READ, Job.READ, View.CONFIGURE)
                .invokeWithArgs("aView", "aProject1", "never_created", "aProject2", "never_created");

        assertThat(result, failedWith(3));
        assertThat(result.stderr(), containsString("ERROR: No such job 'never_created'; perhaps you meant 'aProject1'?"));
        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));
        assertThat(j.jenkins.getView("aView").contains(project1), equalTo(false));
        assertThat(j.jenkins.getView("aView").contains(project2), equalTo(false));
    }

}
