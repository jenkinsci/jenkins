/*
 * The MIT License
 *
 * Copyright (c) 2014 Red Hat, Inc.
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

import static hudson.cli.CLICommandInvoker.Matcher.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import jenkins.model.Jenkins;
import hudson.cli.CLICommandInvoker;
import hudson.cli.CLICommandInvoker.Result;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.Run;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class KeepBuildCommandTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    private FreeStyleProject job;
    private FreeStyleBuild build;

    private CLICommandInvoker invoker;
    private Result res;

    @Before public void setUp() throws Exception {
        invoker = new CLICommandInvoker(j, "keep-build");

        job = j.createFreeStyleProject("a_project");
        build = job.scheduleBuild2(0).get();
        assertThat(build.isKeepLog(), is(false));
    }

    @Test public void keepBuildCli() {
        res = invoker.invokeWithArgs("a_project", "1");
        assertThat(res, succeededSilently());
        assertThat(build.isKeepLog(), is(true));
    }

    @Test public void failIfDoesNotExist() {
        res = invoker.invokeWithArgs("no_such_project", "1");
        assertThat(res, failedWith("No such job"));
        assertThat(build.isKeepLog(), is(false));

        res = invoker.invokeWithArgs("a_project", "42");
        assertThat(res, failedWith("No such build"));
        assertThat(build.isKeepLog(), is(false));
    }

    @Test public void failWithoutPermisssion() {
        res = invoker.authorizedTo(Jenkins.READ, Run.UPDATE).invokeWithArgs("a_project", "1");
        assertThat(res, failedWith("No such job"));
        assertThat(build.isKeepLog(), is(false));

        res = invoker.authorizedTo(Jenkins.READ, Job.READ).invokeWithArgs("a_project", "1");
        assertThat(res, failedWith("user is missing the Run/Update permission"));
        assertThat(build.isKeepLog(), is(false));
    }
}
