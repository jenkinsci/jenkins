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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;

import static hudson.cli.CLICommandInvoker.Matcher.hasNoStandardOutput;
import static hudson.cli.CLICommandInvoker.Matcher.hasNoErrorOutput;
import static hudson.cli.CLICommandInvoker.Matcher.succeeded;
import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static hudson.cli.CLICommandInvoker.Matcher.failedWith;

import java.io.IOException;

import hudson.model.ListView;
import hudson.model.View;
import jenkins.model.Jenkins;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class DeleteViewCommandTest {

    private CLICommandInvoker command;

    @Rule public final JenkinsRule j = new JenkinsRule();

    @Before public void setUp() {

        command = new CLICommandInvoker(j, new DeleteViewCommand());
    }

    @Test public void deleteViewShouldFailWithoutViewDeletePermission() throws IOException {

        j.jenkins.addView(new ListView("aView"));

        final CLICommandInvoker.Result result = command
                .authorizedTo(View.READ, Jenkins.READ)
                .invokeWithArgs("aView")
        ;

        assertThat(result, failedWith(-1));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("user is missing the View/Delete permission"));
    }

    @Test public void deleteViewShouldSucceed() throws Exception {

        j.jenkins.addView(new ListView("aView"));

        final CLICommandInvoker.Result result = command
                .authorizedTo(View.READ, View.DELETE, Jenkins.READ)
                .invokeWithArgs("aView")
        ;

        assertThat(result, succeededSilently());
        assertThat(j.jenkins.getView("aView"), nullValue());
    }

    @Test public void deleteViewShouldFailIfViewDoesNotExist() {

        final CLICommandInvoker.Result result = command
                .authorizedTo(View.READ, View.DELETE, Jenkins.READ)
                .invokeWithArgs("never_created")
        ;

        assertThat(result, failedWith(-1));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("No view named never_created inside view Jenkins"));
    }

    // ViewGroup.canDelete()
    @Test public void deleteViewShouldFailIfViewGroupDoesNotAllowDeletion() {

        final CLICommandInvoker.Result result = command
                .authorizedTo(View.READ, View.DELETE, Jenkins.READ)
                .invokeWithArgs("All")
        ;

        assertThat(result, failedWith(-1));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("Jenkins does not allow to delete 'All' view"));
    }
}
