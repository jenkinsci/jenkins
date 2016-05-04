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

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.labels.LabelAtom;
import hudson.tasks.Shell;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.List;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.hasNoStandardOutput;
import static hudson.cli.CLICommandInvoker.Matcher.succeeded;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

/**
 * @author pjanouse
 */
public class AbstractBuildRangeCommandTest2 {

    private CLICommandInvoker command;

    @Rule public final JenkinsRule j = new JenkinsRule();

    @Before public void setUp() {
        command = new CLICommandInvoker(j, new AbstractBuildRangeCommandTest.DummyRangeCommand());
    }

    @Test public void dummyRangeShouldFailIfJobNameIsEmptyOnEmptyJenkins() throws Exception {
        j.createFreeStyleProject("aProject").scheduleBuild2(0).get();
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(1));

        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs("", "1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such job ''"));
    }

    @Test public void dummyRangeShouldFailIfJobNameIsSpaceOnEmptyJenkins() throws Exception {
        j.createFreeStyleProject("aProject").scheduleBuild2(0).get();
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(1));

        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(" ", "1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such job ' '"));
    }

    @Test public void dummyRangeShouldSuccessEvenTheBuildIsRunning() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.getBuildersList().add(new Shell("echo 1\nsleep 10s"));
        assertThat("Job wasn't scheduled properly", project.scheduleBuild(0), equalTo(true));

        // Wait until classProject is started (at least 1s)
        while(!project.isBuilding()) {
            System.out.println("Waiting for build to start and sleep 1s...");
            Thread.sleep(1000);
        }

        // Wait for the first sleep
        if(!project.getBuildByNumber(1).getLog().contains("echo 1")) {
            Thread.sleep(1000);
        }

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs("aProject", "1");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1\n"));
    }

    @Test public void dummyRangeShouldSuccessEvenTheBuildIsStuckInTheQueue() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.getBuildersList().add(new Shell("echo 1\nsleep 10s"));
        project.setAssignedLabel(new LabelAtom("never_created"));
        assertThat("Job wasn't scheduled properly", project.scheduleBuild(0), equalTo(true));
        Thread.sleep(1000);
        assertThat("Job wasn't scheduled properly - it isn't in the queue",
                project.isInQueue(), equalTo(true));
        assertThat("Job wasn't scheduled properly - it is running on non-exist node",
                project.isBuilding(), equalTo(false));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs("aProject", "1");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: \n"));
    }

}
