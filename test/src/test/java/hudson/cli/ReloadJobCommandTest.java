/*
 * The MIT License
 *
 * Copyright 2015 Red Hat, Inc.
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

import hudson.FilePath;
import hudson.Functions;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.tasks.BatchFile;
import hudson.tasks.Builder;
import hudson.tasks.Shell;
import java.io.File;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author pjanouse
 */
@WithJenkins
class ReloadJobCommandTest {

    private CLICommandInvoker command;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        command = new CLICommandInvoker(j, "reload-job");
    }

    @Test
    void reloadJobShouldFailWithoutJobConfigurePermission() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.getBuildersList().add(createScriptBuilder("echo 1"));
        j.assertLogContains("echo 1", j.buildAndAssertSuccess(project));

        changeProjectOnTheDisc(project, "echo 1", "echo 2");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Item.READ, Jenkins.READ)
                .invokeWithArgs("aProject");

        assertThat(result, failedWith(6));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: user is missing the Job/Configure permission"));

        j.assertLogContains("echo 1", j.buildAndAssertSuccess(project));
    }

    @Test
    void reloadJobShouldFailWithoutJobReadPermission() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.getBuildersList().add(createScriptBuilder("echo 1"));
        j.assertLogContains("echo 1", j.buildAndAssertSuccess(project));

        changeProjectOnTheDisc(project, "echo 1", "echo 2");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Item.CONFIGURE, Jenkins.READ)
                .invokeWithArgs("aProject");

        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such item ‘aProject’ exists."));

        j.assertLogContains("echo 1", j.buildAndAssertSuccess(project));
    }

    @Test
    void reloadJobShouldSucceed() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.getBuildersList().add(createScriptBuilder("echo 1"));

        j.assertLogContains("echo 1", j.buildAndAssertSuccess(project));

        changeProjectOnTheDisc(project, "echo 1", "echo 2");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Item.READ, Item.CONFIGURE, Jenkins.READ)
                .invokeWithArgs("aProject");

        assertThat(result, succeededSilently());

        j.assertLogContains("echo 2", j.buildAndAssertSuccess(project));
    }

    @Test
    void reloadJobShouldFailIfJobDoesNotExist() {

        final CLICommandInvoker.Result result = command
                .authorizedTo(Item.READ, Item.CONFIGURE, Jenkins.READ)
                .invokeWithArgs("never_created");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such item ‘never_created’ exists."));
    }

    @Test
    void reloadJobShouldFailIfJobDoesNotExistButNearExists() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("never_created");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Item.READ, Item.CONFIGURE, Jenkins.READ)
                .invokeWithArgs("never_created1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such item ‘never_created1’ exists. Perhaps you meant ‘never_created’?"));
    }

    @Test
    void reloadJobManyShouldSucceed() throws Exception {
        FreeStyleProject project1 = j.createFreeStyleProject("aProject1");
        project1.getBuildersList().add(createScriptBuilder("echo 1"));
        FreeStyleProject project2 = j.createFreeStyleProject("aProject2");
        project2.getBuildersList().add(createScriptBuilder("echo 1"));
        FreeStyleProject project3 = j.createFreeStyleProject("aProject3");
        project3.getBuildersList().add(createScriptBuilder("echo 1"));

        j.assertLogContains("echo 1", j.buildAndAssertSuccess(project1));
        j.assertLogContains("echo 1", j.buildAndAssertSuccess(project2));
        j.assertLogContains("echo 1", j.buildAndAssertSuccess(project3));

        changeProjectOnTheDisc(project1, "echo 1", "echo 2");
        changeProjectOnTheDisc(project2, "echo 1", "echo 2");
        changeProjectOnTheDisc(project3, "echo 1", "echo 2");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Item.READ, Item.CONFIGURE, Jenkins.READ)
                .invokeWithArgs("aProject1", "aProject2", "aProject3");

        assertThat(result, succeededSilently());

        j.assertLogContains("echo 2", j.buildAndAssertSuccess(project1));
        j.assertLogContains("echo 2", j.buildAndAssertSuccess(project2));
        j.assertLogContains("echo 2", j.buildAndAssertSuccess(project3));
    }

    @Test
    void reloadJobManyShouldFailIfFirstJobDoesNotExist() throws Exception {

        FreeStyleProject project1 = j.createFreeStyleProject("aProject1");
        project1.getBuildersList().add(createScriptBuilder("echo 1"));
        FreeStyleProject project2 = j.createFreeStyleProject("aProject2");
        project2.getBuildersList().add(createScriptBuilder("echo 1"));

        j.assertLogContains("echo 1", j.buildAndAssertSuccess(project1));
        j.assertLogContains("echo 1", j.buildAndAssertSuccess(project2));

        changeProjectOnTheDisc(project1, "echo 1", "echo 2");
        changeProjectOnTheDisc(project2, "echo 1", "echo 2");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Item.READ, Item.CONFIGURE, Jenkins.READ)
                .invokeWithArgs("never_created", "aProject1", "aProject2");

        assertThat(result, failedWith(5));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("never_created: No such item ‘never_created’ exists."));
        assertThat(result.stderr(), containsString("ERROR: " + CLICommand.CLI_LISTPARAM_SUMMARY_ERROR_TEXT));

        j.assertLogContains("echo 2", j.buildAndAssertSuccess(project1));
        j.assertLogContains("echo 2", j.buildAndAssertSuccess(project2));
    }

    @Test
    void reloadJobManyShouldFailIfMiddleJobDoesNotExist() throws Exception {

        FreeStyleProject project1 = j.createFreeStyleProject("aProject1");
        project1.getBuildersList().add(createScriptBuilder("echo 1"));
        FreeStyleProject project2 = j.createFreeStyleProject("aProject2");
        project2.getBuildersList().add(createScriptBuilder("echo 1"));

        j.assertLogContains("echo 1", j.buildAndAssertSuccess(project1));
        j.assertLogContains("echo 1", j.buildAndAssertSuccess(project2));

        changeProjectOnTheDisc(project1, "echo 1", "echo 2");
        changeProjectOnTheDisc(project2, "echo 1", "echo 2");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Item.READ, Item.CONFIGURE, Jenkins.READ)
                .invokeWithArgs("aProject1", "never_created", "aProject2");

        assertThat(result, failedWith(5));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("never_created: No such item ‘never_created’ exists."));
        assertThat(result.stderr(), containsString("ERROR: " + CLICommand.CLI_LISTPARAM_SUMMARY_ERROR_TEXT));

        j.assertLogContains("echo 2", j.buildAndAssertSuccess(project1));
        j.assertLogContains("echo 2", j.buildAndAssertSuccess(project2));
    }

    @Test
    void reloadJobManyShouldFailIfLastJobDoesNotExist() throws Exception {

        FreeStyleProject project1 = j.createFreeStyleProject("aProject1");
        project1.getBuildersList().add(createScriptBuilder("echo 1"));
        FreeStyleProject project2 = j.createFreeStyleProject("aProject2");
        project2.getBuildersList().add(createScriptBuilder("echo 1"));

        j.assertLogContains("echo 1", j.buildAndAssertSuccess(project1));
        j.assertLogContains("echo 1", j.buildAndAssertSuccess(project2));

        changeProjectOnTheDisc(project1, "echo 1", "echo 2");
        changeProjectOnTheDisc(project2, "echo 1", "echo 2");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Item.READ, Item.CONFIGURE, Jenkins.READ)
                .invokeWithArgs("aProject1", "aProject2", "never_created");

        assertThat(result, failedWith(5));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("never_created: No such item ‘never_created’ exists."));
        assertThat(result.stderr(), containsString("ERROR: " + CLICommand.CLI_LISTPARAM_SUMMARY_ERROR_TEXT));

        j.assertLogContains("echo 2", j.buildAndAssertSuccess(project1));
        j.assertLogContains("echo 2", j.buildAndAssertSuccess(project2));
    }

    @Test
    void reloadJobManyShouldFailIfMoreJobsDoNotExist() throws Exception {

        FreeStyleProject project1 = j.createFreeStyleProject("aProject1");
        project1.getBuildersList().add(createScriptBuilder("echo 1"));
        FreeStyleProject project2 = j.createFreeStyleProject("aProject2");
        project2.getBuildersList().add(createScriptBuilder("echo 1"));

        j.assertLogContains("echo 1", j.buildAndAssertSuccess(project1));
        j.assertLogContains("echo 1", j.buildAndAssertSuccess(project2));

        changeProjectOnTheDisc(project1, "echo 1", "echo 2");
        changeProjectOnTheDisc(project2, "echo 1", "echo 2");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Item.READ, Item.CONFIGURE, Jenkins.READ)
                .invokeWithArgs("aProject1", "never_created1", "never_created2", "aProject2");

        assertThat(result, failedWith(5));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("never_created1: No such item ‘never_created1’ exists."));
        assertThat(result.stderr(), containsString("never_created2: No such item ‘never_created2’ exists."));
        assertThat(result.stderr(), containsString("ERROR: " + CLICommand.CLI_LISTPARAM_SUMMARY_ERROR_TEXT));

        j.assertLogContains("echo 2", j.buildAndAssertSuccess(project1));
        j.assertLogContains("echo 2", j.buildAndAssertSuccess(project2));
    }

    @Test
    void reloadJobManyShouldSucceedEvenAJobIsSpecifiedTwice() throws Exception {
        FreeStyleProject project1 = j.createFreeStyleProject("aProject1");
        project1.getBuildersList().add(createScriptBuilder("echo 1"));
        FreeStyleProject project2 = j.createFreeStyleProject("aProject2");
        project2.getBuildersList().add(createScriptBuilder("echo 1"));

        j.assertLogContains("echo 1", j.buildAndAssertSuccess(project1));
        j.assertLogContains("echo 1", j.buildAndAssertSuccess(project2));

        changeProjectOnTheDisc(project1, "echo 1", "echo 2");
        changeProjectOnTheDisc(project2, "echo 1", "echo 2");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Item.READ, Item.CONFIGURE, Jenkins.READ)
                .invokeWithArgs("aProject1", "aProject2", "aProject1");

        assertThat(result, succeededSilently());

        j.assertLogContains("echo 2", j.buildAndAssertSuccess(project1));
        j.assertLogContains("echo 2", j.buildAndAssertSuccess(project2));
    }

    /**
     * Modify a project directly on the disc
     *
     * @param project modified project
     * @param oldstr old configuration item - for rewrite
     * @param newstr rew configuration item - after rewrite
     * @throws Exception if an issue occurred
     */
    private void changeProjectOnTheDisc(final FreeStyleProject project, final String oldstr,
        final String newstr) throws Exception {

        FilePath fp = new FilePath(new File(project.getRootDir() + "/config.xml"));
        fp.write(fp.readToString().replace(oldstr, newstr), null);
    }

    /**
     * Create a script based builder (either Shell or BatchFile) depending on platform
     * @param script the contents of the script to run
     * @return A Builder instance of either Shell or BatchFile
     */
    private Builder createScriptBuilder(String script) {
        return Functions.isWindows() ? new BatchFile(script) : new Shell(script);
    }
}
