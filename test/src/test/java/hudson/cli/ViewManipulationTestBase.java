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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.ListView;
import hudson.model.View;
import java.io.IOException;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author pjanouse
 */
@WithJenkins
abstract class ViewManipulationTestBase {

    protected CLICommandInvoker command;

    protected abstract CLICommandInvoker getCommand();

    protected JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        command = getCommand().asUser("user");
    }

    @Test void jobViewManipulationShouldFailWithJenkinsReadPermissionOnly() throws IOException {

        j.jenkins.addView(new ListView("aView"));
        j.createFreeStyleProject("aProject");

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .invokeWithArgs("aView", "aProject");

        assertThat(result, failedWith(6));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: user is missing the View/Read permission"));
    }

    @Test void jobViewManipulationShouldFailWithViewReadPermissionOnly() throws IOException {

        j.jenkins.addView(new ListView("aView"));
        j.createFreeStyleProject("aProject");

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ, View.READ).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .invokeWithArgs("aView", "aProject");

        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such job 'aProject'"));
    }

    @Test void jobViewManipulationShouldFailWithViewReadAndJobReadPermissionsOnly() throws IOException {

        j.jenkins.addView(new ListView("aView"));
        j.createFreeStyleProject("aProject");

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ, View.READ, Item.READ).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .invokeWithArgs("aView", "aProject");

        assertThat(result, failedWith(6));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: user is missing the View/Configure permission"));
    }

    @Test void jobViewManipulationShouldFailIfTheViewIsNotDirectlyModifiable() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("aProject");

        assertThat(j.jenkins.getView("All").getAllItems().size(), equalTo(1));
        assertThat(j.jenkins.getView("All").contains(project), equalTo(true));

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ, View.READ, Item.READ, View.CONFIGURE).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .invokeWithArgs("All", "aProject");

        assertThat(result, failedWith(4));
        assertThat(result.stderr(), containsString("ERROR: 'All' view can not be modified directly"));
        assertThat(j.jenkins.getView("All").getAllItems().size(), equalTo(1));
        assertThat(j.jenkins.getView("All").contains(project), equalTo(true));
    }

    @Test void jobViewManipulationShouldFailIfTheJobDoesNotExist() throws Exception {

        j.jenkins.addView(new ListView("aView"));

        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ, View.READ, Item.READ, View.CONFIGURE).everywhere().toAuthenticated());
        CLICommandInvoker.Result result = command
                .invokeWithArgs("aView", "never_created");

        assertThat(result, failedWith(3));
        assertThat(result.stderr(), containsString("ERROR: No such job 'never_created'"));
        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));

        FreeStyleProject project = j.createFreeStyleProject("aProject");

        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));

        result = command
                .invokeWithArgs("aView", "aProject1");

        assertThat(result, failedWith(3));
        assertThat(result.stderr(), containsString("ERROR: No such job 'aProject1'; perhaps you meant 'aProject'?"));
        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));
        assertThat(j.jenkins.getView("aView").contains(project), equalTo(false));
    }

    @Test void jobViewManipulationShouldFailIfTheJobNameIsEmpty() throws Exception {

        j.jenkins.addView(new ListView("aView"));

        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ, View.READ, Item.READ, View.CONFIGURE).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .invokeWithArgs("aView", "");

        assertThat(result, failedWith(3));
        assertThat(result.stderr(), containsString("ERROR: No such job ''"));
        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));
    }

    @Test void jobViewManipulationManyShouldFailIfFirstJobDoesNotExist() throws Exception {

        j.jenkins.addView(new ListView("aView"));
        FreeStyleProject project1 = j.createFreeStyleProject("aProject1");
        FreeStyleProject project2 = j.createFreeStyleProject("aProject2");

        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));
        assertThat(j.jenkins.getView("aView").contains(project1), equalTo(false));
        assertThat(j.jenkins.getView("aView").contains(project2), equalTo(false));

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ, View.READ, Item.READ, View.CONFIGURE).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .invokeWithArgs("aView", "never_created", "aProject1", "aProject2");

        assertThat(result, failedWith(3));
        assertThat(result.stderr(), containsString("ERROR: No such job 'never_created'; perhaps you meant 'aProject1'?"));
        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));
        assertThat(j.jenkins.getView("aView").contains(project1), equalTo(false));
        assertThat(j.jenkins.getView("aView").contains(project2), equalTo(false));
    }

    @Test void jobViewManipulationManyShouldFailIfMiddleJobDoesNotExist() throws Exception {

        j.jenkins.addView(new ListView("aView"));
        FreeStyleProject project1 = j.createFreeStyleProject("aProject1");
        FreeStyleProject project2 = j.createFreeStyleProject("aProject2");

        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));
        assertThat(j.jenkins.getView("aView").contains(project1), equalTo(false));
        assertThat(j.jenkins.getView("aView").contains(project2), equalTo(false));

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ, View.READ, Item.READ, View.CONFIGURE).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .invokeWithArgs("aView", "aProject1", "never_created", "aProject2");

        assertThat(result, failedWith(3));
        assertThat(result.stderr(), containsString("ERROR: No such job 'never_created'; perhaps you meant 'aProject1'?"));
        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));
        assertThat(j.jenkins.getView("aView").contains(project1), equalTo(false));
        assertThat(j.jenkins.getView("aView").contains(project2), equalTo(false));
    }

    @Test void jobViewManipulationManyShouldFailIfLastJobDoesNotExist() throws Exception {

        j.jenkins.addView(new ListView("aView"));
        FreeStyleProject project1 = j.createFreeStyleProject("aProject1");
        FreeStyleProject project2 = j.createFreeStyleProject("aProject2");

        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));
        assertThat(j.jenkins.getView("aView").contains(project1), equalTo(false));
        assertThat(j.jenkins.getView("aView").contains(project2), equalTo(false));

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ, View.READ, Item.READ, View.CONFIGURE).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .invokeWithArgs("aView", "aProject1", "aProject2", "never_created");

        assertThat(result, failedWith(3));
        assertThat(result.stderr(), containsString("ERROR: No such job 'never_created'; perhaps you meant 'aProject1'?"));
        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));
        assertThat(j.jenkins.getView("aView").contains(project1), equalTo(false));
        assertThat(j.jenkins.getView("aView").contains(project2), equalTo(false));
    }

    @Test void jobViewManipulationManyShouldFailIfMoreJobsDoNotExist() throws Exception {

        j.jenkins.addView(new ListView("aView"));
        FreeStyleProject project1 = j.createFreeStyleProject("aProject1");
        FreeStyleProject project2 = j.createFreeStyleProject("aProject2");

        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));
        assertThat(j.jenkins.getView("aView").contains(project1), equalTo(false));
        assertThat(j.jenkins.getView("aView").contains(project2), equalTo(false));

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ, View.READ, Item.READ, View.CONFIGURE).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .invokeWithArgs("aView", "aProject1", "never_created", "aProject2", "never_created");

        assertThat(result, failedWith(3));
        assertThat(result.stderr(), containsString("ERROR: No such job 'never_created'; perhaps you meant 'aProject1'?"));
        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));
        assertThat(j.jenkins.getView("aView").contains(project1), equalTo(false));
        assertThat(j.jenkins.getView("aView").contains(project2), equalTo(false));
    }

}
