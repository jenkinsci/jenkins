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
import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import hudson.Functions;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Run;
import hudson.tasks.BatchFile;
import hudson.tasks.Builder;
import hudson.tasks.Shell;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author pjanouse
 */
@WithJenkins
class SetBuildDescriptionCommandTest {

    private CLICommandInvoker command;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        command = new CLICommandInvoker(j, "set-build-description");
    }

    @Test
    void setBuildDescriptionShouldFailWithoutJobReadPermission() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.getBuildersList().add(createScriptBuilder("echo 1"));
        j.assertLogContains("echo 1", j.buildAndAssertSuccess(project));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ)
                .invokeWithArgs("aProject", "1", "test");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such job 'aProject'"));
    }

    @Test
    void setBuildDescriptionShouldFailWithoutRunUpdatePermission1() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.getBuildersList().add(createScriptBuilder("echo 1"));
        j.assertLogContains("echo 1", j.buildAndAssertSuccess(project));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Item.READ, Jenkins.READ)
                .invokeWithArgs("aProject", "1", "test");
        assertThat(result, failedWith(6));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: user is missing the Run/Update permission"));
    }

    @Test
    void setBuildDescriptionShouldSucceed() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.getBuildersList().add(createScriptBuilder("echo 1"));
        FreeStyleBuild build = j.buildAndAssertSuccess(project);
        j.assertLogContains("echo 1", build);
        assertThat(build.getDescription(), equalTo(null));

        CLICommandInvoker.Result result = command
                .authorizedTo(Run.UPDATE, Item.READ, Jenkins.READ)
                .invokeWithArgs("aProject", "1", "test");
        assertThat(result, succeededSilently());
        assertThat(build.getDescription(), equalTo("test"));

        result = command
                .authorizedTo(Run.UPDATE, Item.READ, Jenkins.READ)
                .invokeWithArgs("aProject", "1", "");
        assertThat(result, succeededSilently());
        assertThat(build.getDescription(), equalTo(""));

        result = command
                .authorizedTo(Run.UPDATE, Item.READ, Jenkins.READ)
                .invokeWithArgs("aProject", "1", " ");
        assertThat(result, succeededSilently());
        assertThat(build.getDescription(), equalTo(" "));
    }

    @Test
    void setBuildDescriptionShouldFailIfJobDoesNotExist() {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Run.UPDATE, Item.READ, Jenkins.READ)
                .invokeWithArgs("never_created");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such job 'never_created'"));
    }

    @Test
    void setBuildDescriptionShouldFailIfJobDoesNotExistButNearExists() throws Exception {
        j.createFreeStyleProject("never_created");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Run.UPDATE, Item.READ, Jenkins.READ)
                .invokeWithArgs("never_created1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such job 'never_created1'; perhaps you meant 'never_created'?"));
    }

    @Test
    void setBuildDescriptionShouldFailIfBuildDoesNotExist() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.getBuildersList().add(createScriptBuilder("echo 1"));
        j.assertLogContains("echo 1", j.buildAndAssertSuccess(project));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Item.READ, Jenkins.READ)
                .invokeWithArgs("aProject", "2", "test");

        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such build #2"));
    }

    //TODO: determine if this should be pulled out into JenkinsRule or something
    /**
     * Create a script based builder (either Shell or BatchFile) depending on platform
     * @param script the contents of the script to run
     * @return A Builder instance of either Shell or BatchFile
     */
    private Builder createScriptBuilder(String script) {
        return Functions.isWindows() ? new BatchFile(script) : new Shell(script);
    }

}
