/*
 * The MIT License
 *
 * Copyright 2013 Red Hat, Inc.
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
import static org.hamcrest.Matchers.nullValue;

import hudson.model.ListView;
import hudson.model.View;
import java.io.IOException;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class CreateViewCommandTest {

    private CLICommandInvoker command;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        command = new CLICommandInvoker(j, new CreateViewCommand()).asUser("user");
    }

    @Test
    void createViewShouldFailWithoutViewCreatePermission() {
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .withStdin(this.getClass().getResourceAsStream("/hudson/cli/view.xml"))
                .invoke()
        ;

        assertThat(result, failedWith(6));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: user is missing the View/Create permission"));
    }

    @Test
    void createViewShouldSucceed() {
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(View.CREATE, Jenkins.READ).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .withStdin(this.getClass().getResourceAsStream("/hudson/cli/view.xml"))
                .invoke()
        ;

        assertThat(result, succeededSilently());

        final View updatedView = j.jenkins.getView("ViewFromXML");
        assertThat(updatedView.getViewName(), equalTo("ViewFromXML"));
        assertThat(updatedView.isFilterExecutors(), equalTo(true));
        assertThat(updatedView.isFilterQueue(), equalTo(false));
    }

    @Test
    void createViewSpecifyingNameExplicitlyShouldSucceed() {
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(View.CREATE, Jenkins.READ).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .withStdin(this.getClass().getResourceAsStream("/hudson/cli/view.xml"))
                .invokeWithArgs("CustomViewName")
        ;

        assertThat(result, succeededSilently());

        assertThat("A view with original name should not exist", j.jenkins.getView("ViewFromXML"), nullValue());

        final View updatedView = j.jenkins.getView("CustomViewName");
        assertThat(updatedView.getViewName(), equalTo("CustomViewName"));
        assertThat(updatedView.isFilterExecutors(), equalTo(true));
        assertThat(updatedView.isFilterQueue(), equalTo(false));
    }

    @Test
    void createViewShouldFailIfViewAlreadyExists() throws IOException {

        j.jenkins.addView(new ListView("ViewFromXML"));

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(View.CREATE, Jenkins.READ).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .withStdin(this.getClass().getResourceAsStream("/hudson/cli/view.xml"))
                .invoke()
        ;

        assertThat(result, failedWith(4));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: View 'ViewFromXML' already exists"));
    }

    @Test
    void createViewShouldFailUsingInvalidName() {
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(View.CREATE, Jenkins.READ).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .withStdin(this.getClass().getResourceAsStream("/hudson/cli/view.xml"))
                .invokeWithArgs("..")
        ;

        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Invalid view name"));
    }
}
