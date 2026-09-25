/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
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

package jenkins.tasks.filters.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import hudson.Functions;
import hudson.model.Build;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class RetainVariablesLocalRuleTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void retainVariable_removeUnwantedVariables_batch() throws Exception {
        assumeTrue(Functions.isWindows());

        FreeStyleProject p = j.createFreeStyleProject();
        BatchFile batch = new BatchFile("echo \"begin %what% %who% end\"");
        p.getBuildersList().add(batch);
        p.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("what", "Hello"),
                new StringParameterDefinition("who", "World")
        ));

        { // the rule allows the user to retain only a subset of variable
            RetainVariablesLocalRule localRule = new RetainVariablesLocalRule();
            localRule.setVariables("what");
            batch.setConfiguredLocalRules(List.of(localRule));

            FreeStyleBuild build = j.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0, (Cause) null, new ParametersAction(
                    new StringParameterValue("what", "hello"),
                    new StringParameterValue("who", "world")
            )));

            assertContainsSequentially(build, "begin hello  end");
            assertDoesNotContainsSequentially(build, "world");
        }

        { // the rule allows the user to retain only a subset of variable (second example)
            RetainVariablesLocalRule localRule = new RetainVariablesLocalRule();
            localRule.setVariables("who");
            batch.setConfiguredLocalRules(List.of(localRule));

            FreeStyleBuild build = j.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0, (Cause) null, new ParametersAction(
                    new StringParameterValue("what", "hello"),
                    new StringParameterValue("who", "world")
            )));

            assertContainsSequentially(build, "begin  world end");
            assertDoesNotContainsSequentially(build, "hello");
        }
    }

    @Test
    void retainVariable_removeModifiedSystemEnv_batch() throws Exception {
        assumeTrue(Functions.isWindows());

        FreeStyleProject p = j.createFreeStyleProject();
        BatchFile batch = new BatchFile("echo \"begin %what% [=[%path%]=] end\"");
        p.getBuildersList().add(batch);
        p.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("what", "Hello"),
                // override the System env variable
                new StringParameterDefinition("path", null)
        ));

        String initialValueOfPath;

        { // no attempt to modify path (except other plugin)
            RetainVariablesLocalRule localRule = new RetainVariablesLocalRule();
            localRule.setVariables("what");
            batch.setConfiguredLocalRules(List.of(localRule));

            FreeStyleBuild build = j.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0, (Cause) null, new ParametersAction(
                    new StringParameterValue("what", "hello")
            )));

            initialValueOfPath = findStringEnclosedBy(build, "[=[", "]=]");
            assertContainsSequentially(build, "hello");
        }

        { // does not accept modification of path
            RetainVariablesLocalRule localRule = new RetainVariablesLocalRule();
            localRule.setVariables("what");
            batch.setConfiguredLocalRules(List.of(localRule));

            FreeStyleBuild build = j.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0, (Cause) null, new ParametersAction(
                    new StringParameterValue("what", "hello"),
                    new StringParameterValue("path", "modificationOfPath")
            )));

            // potentially plugins modified the path also
            assertContainsSequentially(build, "begin hello");
            assertContainsSequentially(build, initialValueOfPath);
            assertDoesNotContainsSequentially(build, "modificationOfPath");
        }

        { // accept modification of path
            RetainVariablesLocalRule localRule = new RetainVariablesLocalRule();
            localRule.setVariables("what path");
            batch.setConfiguredLocalRules(List.of(localRule));

            FreeStyleBuild build = j.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0, (Cause) null, new ParametersAction(
                    new StringParameterValue("what", "hello"),
                    new StringParameterValue("path", "modificationOfPath;$PATH")
            )));

            // potentially plugins modified the path also
            assertContainsSequentially(build, "begin hello");
            assertContainsSequentially(build, "modificationOfPath");
        }
    }

    @Test
    void retainVariable_removeModifiedSystemEnv_shell() throws Exception {
        assumeFalse(Functions.isWindows());

        FreeStyleProject p = j.createFreeStyleProject();
        Shell batch = new Shell("echo \"begin $what [=[$PATH]=] end\"");
        p.getBuildersList().add(batch);
        p.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("what", "Hello"),
                // override the System env variable
                new StringParameterDefinition("path", null)
        ));

        String initialValueOfPath;

        { // no attempt to modify path (except other plugin)
            RetainVariablesLocalRule localRule = new RetainVariablesLocalRule();
            localRule.setVariables("what");
            batch.setConfiguredLocalRules(List.of(localRule));

            FreeStyleBuild build = j.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0, (Cause) null, new ParametersAction(
                    new StringParameterValue("what", "hello")
            )));

            initialValueOfPath = findStringEnclosedBy(build, "[=[", "]=]");
            assertContainsSequentially(build, "hello");
        }

        { // does not accept modification of path
            RetainVariablesLocalRule localRule = new RetainVariablesLocalRule();
            localRule.setVariables("what");
            batch.setConfiguredLocalRules(List.of(localRule));

            FreeStyleBuild build = j.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0, (Cause) null, new ParametersAction(
                    new StringParameterValue("what", "hello"),
                    new StringParameterValue("path", "modificationOfPath")
            )));

            // potentially plugins modified the path also
            assertContainsSequentially(build, "begin hello");
            assertContainsSequentially(build, initialValueOfPath);
            assertDoesNotContainsSequentially(build, "modificationOfPath");
        }

        { // accept modification of path
            RetainVariablesLocalRule localRule = new RetainVariablesLocalRule();
            localRule.setVariables("what path");
            batch.setConfiguredLocalRules(List.of(localRule));

            FreeStyleBuild build = j.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0, (Cause) null, new ParametersAction(
                    new StringParameterValue("what", "hello"),
                    new StringParameterValue("path", "modificationOfPath;$PATH")
            )));

            // potentially plugins modified the path also
            assertContainsSequentially(build, "begin hello");
            assertContainsSequentially(build, "modificationOfPath");
        }
    }

    @Test
    void retainVariable_removeUnwantedVariables_shell() throws Exception {
        assumeFalse(Functions.isWindows());

        FreeStyleProject p = j.createFreeStyleProject();
        Shell shell = new Shell("echo \"begin $what $who end\"");
        p.getBuildersList().add(shell);
        p.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("what", "Hello"),
                new StringParameterDefinition("who", "World")
        ));

        { // the rule allows the user to retain only a subset of variable
            RetainVariablesLocalRule localRule = new RetainVariablesLocalRule();
            localRule.setVariables("what");
            shell.setConfiguredLocalRules(List.of(localRule));

            FreeStyleBuild build = j.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0, (Cause) null, new ParametersAction(
                    new StringParameterValue("what", "hello"),
                    new StringParameterValue("who", "world")
            )));

            assertContainsSequentially(build, "begin hello  end");
            assertDoesNotContainsSequentially(build, "world");
        }
    }

    @Test
    void retainVariable_removeSystemVariables_shell() throws Exception {
        assumeFalse(Functions.isWindows());

        FreeStyleProject p = j.createFreeStyleProject();
        Shell shell = new Shell("env");
        p.getBuildersList().add(shell);

        FreeStyleBuild build = j.buildAndAssertSuccess(p);
        List<String> unfilteredLogOutput = build.getLog(200).stream().filter(s -> s.contains("=")).map(s -> s.substring(0, s.indexOf('='))).toList();

        p.getBuildersList().remove(shell);

        Shell filteredShell = new Shell("env");

        RetainVariablesLocalRule localRule = new RetainVariablesLocalRule();
        localRule.setVariables("path"); // seems to work without but may be env dependent
        localRule.setRetainCharacteristicEnvVars(false);
        localRule.setProcessVariablesHandling(RetainVariablesLocalRule.ProcessVariablesHandling.REMOVE);
        filteredShell.setConfiguredLocalRules(List.of(localRule));
        p.getBuildersList().add(filteredShell);

        build = j.buildAndAssertSuccess(p);
        List<String> filteredLogOutput = build.getLog(200).stream().filter(s -> s.contains("=")).map(s -> s.substring(0, s.indexOf('='))).toList();

        assertTrue(filteredLogOutput.size() < unfilteredLogOutput.size() - 10); // 10 is a value slightly larger than the number of characteristic env vars (7)
        List<String> filteredButNotUnfiltered = new ArrayList<>(filteredLogOutput);
        filteredButNotUnfiltered.removeAll(unfilteredLogOutput);
        assertFalse(filteredLogOutput.contains("HOME"));
        assertFalse(filteredLogOutput.contains("USER"));
        assertFalse(filteredLogOutput.contains("JENKINS_HOME"));
        assertFalse(filteredLogOutput.contains(""));
    }

    @Test
    void multipleBuildSteps_haveSeparateRules_batch() throws Exception {
        assumeTrue(Functions.isWindows());

        FreeStyleProject p = j.createFreeStyleProject();
        BatchFile batch1 = new BatchFile("echo \"Step1: %what% %who%\"");
        BatchFile batch2 = new BatchFile("echo \"Step2: %what% %who%\"");
        p.getBuildersList().add(batch1);
        p.getBuildersList().add(batch2);
        p.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("what", "Hello"),
                new StringParameterDefinition("who", "World")
        ));

        { // two steps with a specified local rule on each, there is not interaction
            RetainVariablesLocalRule localRule1 = new RetainVariablesLocalRule();
            // take care to allow the PATH to be used, without that the cmd is not found
            localRule1.setVariables("what");
            batch1.setConfiguredLocalRules(List.of(localRule1));

            RetainVariablesLocalRule localRule2 = new RetainVariablesLocalRule();
            // take care to allow the PATH to be used, without that the cmd is not found
            localRule2.setVariables("who");
            batch2.setConfiguredLocalRules(List.of(localRule2));

            FreeStyleBuild build = j.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0, (Cause) null, new ParametersAction(
                    new StringParameterValue("what", "hello"),
                    new StringParameterValue("who", "world")
            )));

            assertContainsSequentially(build, "Step1: hello");
            // due to the display of each command, the log displays `echo "Step2:  world"`, then on the next line the result
            assertDoesNotContainsSequentially(build, "world", "Step2:", "world");
            assertContainsSequentially(build, "Step2:  world");
        }
    }

    @Test
    void multipleBuildSteps_haveSeparateRules_shell() throws Exception {
        assumeFalse(Functions.isWindows());

        FreeStyleProject p = j.createFreeStyleProject();
        Shell batch1 = new Shell("echo \"Step1: $what $who\"");
        Shell batch2 = new Shell("echo \"Step2: $what $who\"");
        p.getBuildersList().add(batch1);
        p.getBuildersList().add(batch2);
        p.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("what", "Hello"),
                new StringParameterDefinition("who", "World")
        ));

        { // two steps with a specified local rule on each, there is not interaction
            RetainVariablesLocalRule localRule1 = new RetainVariablesLocalRule();
            // take care to allow the PATH to be used, without that the cmd is not found
            localRule1.setVariables("what");
            batch1.setConfiguredLocalRules(List.of(localRule1));

            RetainVariablesLocalRule localRule2 = new RetainVariablesLocalRule();
            // take care to allow the PATH to be used, without that the cmd is not found
            localRule2.setVariables("who");
            batch2.setConfiguredLocalRules(List.of(localRule2));

            FreeStyleBuild build = j.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0, (Cause) null, new ParametersAction(
                    new StringParameterValue("what", "hello"),
                    new StringParameterValue("who", "world")
            )));

            assertContainsSequentially(build, "Step1: hello");
            // due to the display of each command, the log displays `echo "Step2:  world"`, then on the next line the result
            assertDoesNotContainsSequentially(build, "world", "Step2:", "world");
            assertContainsSequentially(build, "Step2:  world");
        }
    }

    private void assertContainsSequentially(Build<?, ?> build, String... values) throws Exception {
        int i = 0;
        for (String line : build.getLog(128)) {
            if (line.contains(values[i])) {
                i++;
                if (i >= values.length) {
                    return;
                }
            }
        }
        fail("Does not contains the value: " + values[i]);
    }

    private String findStringEnclosedBy(Build<?, ?> build, String before, String after) throws Exception {
        for (String line : build.getLog(128)) {
            int beforeIndex = line.indexOf(before);
            int afterIndex = line.indexOf(after, beforeIndex + before.length());
            if (beforeIndex != -1 && afterIndex != -1) {
                return line.substring(beforeIndex + before.length(), afterIndex);
            }
        }
        return "";
    }

    private void assertDoesNotContainsSequentially(Build<?, ?> build, String... values) throws Exception {
        int i = 0;
        for (String line : build.getLog(128)) {
            if (line.contains(values[i])) {
                i++;
                if (i >= values.length) {
                    fail("Does contains all the values");
                    return;
                }
            }
        }
    }
}
