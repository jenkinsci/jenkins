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

import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.Run;
import hudson.tasks.Shell;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.hasNoStandardOutput;
import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * @author pjanouse
 */
public class SetBuildDescriptionCommandTest {

    private CLICommandInvoker command;

    @Rule public final JenkinsRule j = new JenkinsRule();

    @Before public void setUp() {
        command = new CLICommandInvoker(j, "set-build-description");
    }

    @Test public void setBuildDescriptionShouldFailWithoutJobReadPermission() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.getBuildersList().add(new Shell("echo 1"));
        assertThat(project.scheduleBuild2(0).get().getLog(), containsString("echo 1"));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ)
                .invokeWithArgs("aProject", "1", "test");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such job 'aProject'"));
    }

    @Test public void setBuildDescriptionShouldFailWithoutRunUpdatePermission1() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.getBuildersList().add(new Shell("echo 1"));
        assertThat(project.scheduleBuild2(0).get().getLog(), containsString("echo 1"));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Job.READ, Jenkins.READ)
                .invokeWithArgs("aProject", "1", "test");
        assertThat(result, failedWith(6));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: user is missing the Run/Update permission"));
    }

    @Test public void setBuildDescriptionShouldSucceed() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.getBuildersList().add(new Shell("echo 1"));
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        assertThat(build.getLog(), containsString("echo 1"));
        assertThat(build.getDescription(), equalTo(null));

        CLICommandInvoker.Result result = command
                .authorizedTo(Run.UPDATE, Job.READ, Jenkins.READ)
                .invokeWithArgs("aProject", "1", "test");
        assertThat(result, succeededSilently());
        assertThat(build.getDescription(), equalTo("test"));

        result = command
                .authorizedTo(Run.UPDATE, Job.READ, Jenkins.READ)
                .invokeWithArgs("aProject", "1", "");
        assertThat(result, succeededSilently());
        assertThat(build.getDescription(), equalTo(""));

        result = command
                .authorizedTo(Run.UPDATE, Job.READ, Jenkins.READ)
                .invokeWithArgs("aProject", "1", " ");
        assertThat(result, succeededSilently());
        assertThat(build.getDescription(), equalTo(" "));
    }

    @Test public void setBuildDescriptionShouldFailIfJobDoesNotExist() throws Exception {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Run.UPDATE, Job.READ, Jenkins.READ)
                .invokeWithArgs("never_created");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such job 'never_created'"));
    }

    @Test public void setBuildDescriptionShouldFailIfJobDoesNotExistButNearExists() throws Exception {
        j.createFreeStyleProject("never_created");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Run.UPDATE, Job.READ, Jenkins.READ)
                .invokeWithArgs("never_created1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such job 'never_created1'; perhaps you meant 'never_created'?"));
    }

    @Test public void setBuildDescriptionShouldFailIfBuildDoesNotExist() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.getBuildersList().add(new Shell("echo 1"));
        assertThat(project.scheduleBuild2(0).get().getLog(), containsString("echo 1"));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Job.READ, Jenkins.READ)
                .invokeWithArgs("aProject", "2", "test");

        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such build #2"));
    }

}
