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
import static hudson.cli.CLICommandInvoker.Matcher.hasNoErrorOutput;
import static hudson.cli.CLICommandInvoker.Matcher.succeeded;
import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import hudson.model.ListView;
import hudson.model.View;
import jenkins.model.Jenkins;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class UpdateViewCommandTest {

    private CLICommandInvoker command;

    @Rule public final JenkinsRule j = new JenkinsRule();

    @Before public void setUp() {

        command = new CLICommandInvoker(j, new UpdateViewCommand());
    }

    @Test public void updateViewShouldFailWithoutViewConfigurePermission() throws Exception {

        j.jenkins.addView(new ListView("aView"));

        final CLICommandInvoker.Result result = command
                .authorizedTo(View.READ, Jenkins.READ)
                .withStdin(this.getClass().getResourceAsStream("/hudson/cli/view.xml"))
                .invokeWithArgs("aView")
        ;

        assertThat(result, failedWith(-1));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("user is missing the View/Configure permission"));
    }

    @Test public void updateViewShouldModifyViewConfiguration() throws Exception {

        j.jenkins.addView(new ListView("aView"));

        final CLICommandInvoker.Result result = command
                .authorizedTo(View.READ, View.CONFIGURE, Jenkins.READ)
                .withStdin(this.getClass().getResourceAsStream("/hudson/cli/view.xml"))
                .invokeWithArgs("aView")
        ;

        assertThat(result, succeededSilently());

        assertThat("Update should not modify view name", j.jenkins.getView("ViewFromXML"), nullValue());

        final View updatedView = j.jenkins.getView("aView");
        assertThat(updatedView.getViewName(), equalTo("aView"));
        assertThat(updatedView.isFilterExecutors(), equalTo(true));
        assertThat(updatedView.isFilterQueue(), equalTo(false));
    }

    @Test public void updateViewShouldFailIfViewDoesNotExist() {

        final CLICommandInvoker.Result result = command
                .authorizedTo(View.READ, View.CONFIGURE, Jenkins.READ)
                .withStdin(this.getClass().getResourceAsStream("/hudson/cli/view.xml"))
                .invokeWithArgs("not_created")
        ;

        assertThat(result, failedWith(-1));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("No view named not_created inside view Jenkins"));
    }
}
