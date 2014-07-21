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
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class SetBuildDisplayNameCommandTest {

    private CLICommandInvoker command;

    @Rule public final JenkinsRule j = new JenkinsRule();

    @Before public void setUp() {

        command = new CLICommandInvoker(j, new SetBuildDisplayNameCommand());
    }

    @Test public void referencingBuildThatDoesNotExistsShouldFail() throws Exception {

        j.createFreeStyleProject("project");

        final CLICommandInvoker.Result result = command
                .invokeWithArgs("project", "42", "DisplayName")
        ;

        assertThat(result.stderr(), containsString("Build #42 does not exist"));
        assertThat(result, hasNoStandardOutput());
        assertThat(result, failedWith(-1));
    }

    @Test public void setDescriptionSuccesfully() throws Exception {

        FreeStyleProject job = j.createFreeStyleProject("project");
        FreeStyleBuild build = job.scheduleBuild2(0).get();

        final CLICommandInvoker.Result result = command
                .invokeWithArgs("project", "1", "DisplayName")
        ;

        assertThat(result, succeededSilently());
        assertThat(build.getDisplayName(), equalTo("DisplayName"));
    }
}
