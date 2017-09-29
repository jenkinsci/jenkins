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
import static org.hamcrest.Matchers.startsWith;
import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.hasNoStandardOutput;
import static hudson.cli.CLICommandInvoker.Matcher.hasNoErrorOutput;
import static hudson.cli.CLICommandInvoker.Matcher.succeeded;

import java.io.IOException;

import hudson.model.ListView;
import hudson.model.View;
import jenkins.model.Jenkins;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class GetViewCommandTest {

    private CLICommandInvoker command;

    @Rule public final JenkinsRule j = new JenkinsRule();

    @Before public void setUp() {

        command = new CLICommandInvoker(j, new GetViewCommand());
    }

    @Test public void getViewShouldFailWithoutViewReadPermission() throws IOException {

        j.jenkins.addView(new ListView("aView"));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ)
                .invokeWithArgs("aView")
        ;

        assertThat(result, failedWith(6));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: user is missing the View/Read permission"));
    }

    @Test public void getViewShouldYieldConfigXml() throws Exception {

        j.jenkins.addView(new ListView("aView"));

        final CLICommandInvoker.Result result = command
                .authorizedTo(View.READ, Jenkins.READ)
                .invokeWithArgs("aView")
        ;

        assertThat(result, succeeded());
        assertThat(result, hasNoErrorOutput());
        assertThat(result.stdout(), startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertThat(result.stdout(), containsString("<name>aView</name>"));
    }

    @Test public void getViewShouldFailIfViewDoesNotExist() {

        final CLICommandInvoker.Result result = command
                .authorizedTo(View.READ, Jenkins.READ)
                .invokeWithArgs("never_created")
        ;

        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No view named never_created inside view Jenkins"));
    }
}
