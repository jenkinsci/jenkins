/*
 * The MIT License
 *
 * Copyright 2013-5 Red Hat, Inc.
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
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import hudson.model.AllView;
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
 * @author ogondza, pjanouse
 */
@WithJenkins
class DeleteViewCommandTest {

    private CLICommandInvoker command;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        command = new CLICommandInvoker(j, new DeleteViewCommand()).asUser("user");
    }

    @Test
    void deleteViewShouldFailWithoutViewDeletePermission() throws IOException {

        j.jenkins.addView(new ListView("aView"));

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(View.READ, Jenkins.READ).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .invokeWithArgs("aView")
        ;

        assertThat(result, failedWith(6));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: user is missing the View/Delete permission"));
    }

    @Test
    void deleteViewShouldFailWithoutViewReadPermission() throws IOException {

        j.jenkins.addView(new ListView("aView"));

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(View.DELETE, Jenkins.READ).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .invokeWithArgs("aView")
                ;

        assertThat(result, failedWith(6));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: user is missing the View/Read permission"));
    }

    @Test
    void deleteViewShouldSucceed() throws Exception {

        j.jenkins.addView(new ListView("aView"));

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(View.READ, View.DELETE, Jenkins.READ).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .invokeWithArgs("aView")
        ;

        assertThat(result, succeededSilently());
        assertThat(j.jenkins.getView("aView"), nullValue());
    }

    @Test
    void deleteViewShouldFailIfViewDoesNotExist() {
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(View.READ, View.DELETE, Jenkins.READ).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .invokeWithArgs("never_created")
        ;

        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No view named never_created inside view Jenkins"));
    }

    // ViewGroup.canDelete()
    @Test
    void deleteViewShouldFailIfViewGroupDoesNotAllowDeletion() {
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(View.READ, View.DELETE, Jenkins.READ).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .invokeWithArgs(AllView.DEFAULT_VIEW_NAME)
        ;

        assertThat(result, failedWith(4));
        assertThat(result, hasNoStandardOutput());
        assertThat(j.jenkins.getView(AllView.DEFAULT_VIEW_NAME), notNullValue());
        assertThat(result.stderr(), containsString("ERROR: Jenkins does not allow to delete '" + AllView.DEFAULT_VIEW_NAME + "' view"));
    }

    @Test
    void deleteViewShouldFailIfViewNameIsEmpty() {
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(View.READ, View.DELETE, Jenkins.READ).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .invokeWithArgs("")
                ;

        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: View name is empty"));
    }

    @Test
    void deleteViewShouldFailIfViewNameIsSpace() {
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(View.READ, View.DELETE, Jenkins.READ).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .invokeWithArgs(" ")
                ;

        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No view named   inside view Jenkins"));
    }

    @Test
    void deleteViewManyShouldSucceed() throws Exception {

        j.jenkins.addView(new ListView("aView1"));
        j.jenkins.addView(new ListView("aView2"));
        j.jenkins.addView(new ListView("aView3"));

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(View.READ, View.DELETE, Jenkins.READ).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .invokeWithArgs("aView1", "aView2", "aView3");

        assertThat(result, succeededSilently());
        assertThat(j.jenkins.getView("aView1"), nullValue());
        assertThat(j.jenkins.getView("aView2"), nullValue());
        assertThat(j.jenkins.getView("aView3"), nullValue());
    }

    @Test
    void deleteViewManyShouldFailIfFirstViewDoesNotExist() throws Exception {

        j.jenkins.addView(new ListView("aView1"));
        j.jenkins.addView(new ListView("aView2"));

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(View.READ, View.DELETE, Jenkins.READ).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .invokeWithArgs("never_created", "aView1", "aView2");

        assertThat(result, failedWith(5));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("never_created: No view named never_created inside view Jenkins"));
        assertThat(result.stderr(), containsString("ERROR: " + CLICommand.CLI_LISTPARAM_SUMMARY_ERROR_TEXT));

        assertThat(j.jenkins.getView("aView1"), nullValue());
        assertThat(j.jenkins.getView("aView2"), nullValue());
        assertThat(j.jenkins.getView("never_created"), nullValue());
    }

    @Test
    void deleteViewManyShouldFailIfMiddleViewDoesNotExist() throws Exception {

        j.jenkins.addView(new ListView("aView1"));
        j.jenkins.addView(new ListView("aView2"));

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(View.READ, View.DELETE, Jenkins.READ).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .invokeWithArgs("aView1", "never_created", "aView2");

        assertThat(result, failedWith(5));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("never_created: No view named never_created inside view Jenkins"));
        assertThat(result.stderr(), containsString("ERROR: " + CLICommand.CLI_LISTPARAM_SUMMARY_ERROR_TEXT));

        assertThat(j.jenkins.getView("aView1"), nullValue());
        assertThat(j.jenkins.getView("aView2"), nullValue());
        assertThat(j.jenkins.getView("never_created"), nullValue());
    }

    @Test
    void deleteViewManyShouldFailIfLastViewDoesNotExist() throws Exception {

        j.jenkins.addView(new ListView("aView1"));
        j.jenkins.addView(new ListView("aView2"));

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(View.READ, View.DELETE, Jenkins.READ).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .invokeWithArgs("aView1", "aView2", "never_created");

        assertThat(result, failedWith(5));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("never_created: No view named never_created inside view Jenkins"));
        assertThat(result.stderr(), containsString("ERROR: " + CLICommand.CLI_LISTPARAM_SUMMARY_ERROR_TEXT));

        assertThat(j.jenkins.getView("aView1"), nullValue());
        assertThat(j.jenkins.getView("aView2"), nullValue());
        assertThat(j.jenkins.getView("never_created"), nullValue());
    }

    @Test
    void deleteViewManyShouldFailIfMoreViewsDoNotExist() throws Exception {

        j.jenkins.addView(new ListView("aView1"));
        j.jenkins.addView(new ListView("aView2"));

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(View.READ, View.DELETE, Jenkins.READ).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .invokeWithArgs("aView1", "never_created1", "never_created2", "aView2");

        assertThat(result, failedWith(5));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("never_created1: No view named never_created1 inside view Jenkins"));
        assertThat(result.stderr(), containsString("never_created2: No view named never_created2 inside view Jenkins"));
        assertThat(result.stderr(), containsString("ERROR: " + CLICommand.CLI_LISTPARAM_SUMMARY_ERROR_TEXT));

        assertThat(j.jenkins.getView("aView1"), nullValue());
        assertThat(j.jenkins.getView("aView2"), nullValue());
        assertThat(j.jenkins.getView("never_created1"), nullValue());
        assertThat(j.jenkins.getView("never_created2"), nullValue());
    }

    @Test
    void deleteViewManyShouldSucceedEvenAViewSpecifiedTwice() throws Exception {

        j.jenkins.addView(new ListView("aView1"));
        j.jenkins.addView(new ListView("aView2"));

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(View.READ, View.DELETE, Jenkins.READ).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .invokeWithArgs("aView1", "aView2", "aView1");

        assertThat(result, succeededSilently());
        assertThat(j.jenkins.getView("aView1"), nullValue());
        assertThat(j.jenkins.getView("aView2"), nullValue());
    }

    @Test
    void deleteViewManyShouldFailWithoutViewDeletePermissionButOthersShouldBeDeleted() throws Exception {

        j.jenkins.addView(new ListView("aView1"));
        j.jenkins.addView(new ListView("aView2"));

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(View.READ, View.DELETE, Jenkins.READ).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .invokeWithArgs("aView1", "aView2", AllView.DEFAULT_VIEW_NAME);

        assertThat(result, failedWith(5));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString(AllView.DEFAULT_VIEW_NAME + ": Jenkins does not allow to delete '" + AllView.DEFAULT_VIEW_NAME + "' view"));
        assertThat(result.stderr(), containsString("ERROR: " + CLICommand.CLI_LISTPARAM_SUMMARY_ERROR_TEXT));

        assertThat(j.jenkins.getView("aView1"), nullValue());
        assertThat(j.jenkins.getView("aView2"), nullValue());
        assertThat(j.jenkins.getView(AllView.DEFAULT_VIEW_NAME), notNullValue());
    }
}
