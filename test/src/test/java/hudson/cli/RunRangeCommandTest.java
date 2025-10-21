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
import static hudson.cli.CLICommandInvoker.Matcher.succeeded;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import hudson.Extension;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Run;
import java.util.List;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author pjanouse
 */
@WithJenkins
class RunRangeCommandTest {

    private static CLICommandInvoker command = null;
    private static FreeStyleProject project = null;

    private static final String PROJECT_NAME = "aProject";
    private static final int BUILDS = 10;

    private static final int[] deleted = {5, 8, 9};

    private static JenkinsRule j;

    @BeforeAll
    static void setUp(JenkinsRule rule) throws Exception {
        j = rule;
        command = new CLICommandInvoker(j,  new DummyRangeCommand());
        project = j.createFreeStyleProject(PROJECT_NAME);
        for (int i = 0; i < BUILDS; i++) {
            j.buildAndAssertSuccess(project);
        }
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(),
                equalTo(BUILDS));

        for (int i : deleted) {
            project.getBuildByNumber(i).delete();
            assertThat(project.getBuildByNumber(i), equalTo(null));
        }
    }

    @Test
    void dummyRangeShouldFailWithoutJobReadPermission() {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ)
                .invokeWithArgs(PROJECT_NAME, "1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(),
                containsString(String.format("ERROR: No such job '%s'", PROJECT_NAME)));
    }

    @Test
    void dummyRangeShouldFailIfJobDesNotExist() {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs("never_created", "1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such job 'never_created'"));
    }

    @Test
    void dummyRangeShouldFailIfJobNameIsEmpty() {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs("", "1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(),
                containsString(String.format("ERROR: No such job ''; perhaps you meant '%s'?", PROJECT_NAME)));
    }

    @Test
    void dummyRangeShouldFailIfJobNameIsSpace() {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(" ", "1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(),
                containsString(String.format("ERROR: No such job ' '; perhaps you meant '%s'?", PROJECT_NAME)));
    }

    @Test
    void dummyRangeShouldSuccessIfBuildDoesNotExist() {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, String.valueOf(BUILDS + 1));
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: " + System.lineSeparator()));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, String.valueOf(deleted[0]));
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: " + System.lineSeparator()));
    }

    @Test
    void dummyRangeNumberSingleShouldSuccess() {
        // First
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1" + System.lineSeparator()));

        // First with plus symbol '+'
        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "+1");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1" + System.lineSeparator()));

        // In the middle
        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "10");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 10" + System.lineSeparator()));

        // In the middle with plus symbol '+'
        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "+10");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 10" + System.lineSeparator()));

        // Last
        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, String.valueOf(BUILDS));
        assertThat(result, succeeded());
        assertThat(result.stdout(),
                containsString(String.format("Builds: %s" + System.lineSeparator(), BUILDS)));

        // Last with the plus symbol '+'
        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, '+' + String.valueOf(BUILDS));
        assertThat(result, succeeded());
        assertThat(result.stdout(),
                containsString(String.format("Builds: %s" + System.lineSeparator(), BUILDS)));
    }

    @Test
    void dummyRangeNumberSingleShouldSuccessIfBuildNumberIsZero() {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "0");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: " + System.lineSeparator()));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "+0");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: " + System.lineSeparator()));
    }

    @Test
    void dummyRangeNumberSingleShouldFailIfBuildNumberIsNegative() {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "-1");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-1\" is not a valid option"));
    }

    @Test
    void dummyRangeNumberSingleShouldFailIfBuildNumberIsTooBig() {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "2147483648");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '2147483648', expected number"));
    }

    @Test
    void dummyRangeNumberSingleShouldFailIfBuildNumberIsInvalid() {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1a");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1a', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "aa");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse 'aa', expected number"));
    }

    @Test
    void dummyRangeNumberSingleShouldSuccessIfBuildNumberIsEmpty() {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: " + System.lineSeparator()));
    }

    @Test
    void dummyRangeNumberSingleShouldFailIfBuildNumberIsSpace() {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, " ");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse ' ', expected number"));
    }

    @Test
    void dummyRangeNumberSingleShouldSuccessIfBuildNumberIsComma() {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, ",");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: " + System.lineSeparator()));
    }

    @Test
    void dummyRangeNumberSingleShouldFailIfBuildNumberIsHyphen() {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "-");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-\" is not a valid option"));
    }

    @Test
    void dummyRangeNumberMultiShouldSuccess() {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1,2");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,2" + System.lineSeparator()));

        // With plus symbol '+'
        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1,+2,4");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,2,4" + System.lineSeparator()));

        // Build specified twice
        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1,1");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,1" + System.lineSeparator()));

        // Build with zero build number
        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "0,1,2");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,2" + System.lineSeparator()));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1,0,2");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,2" + System.lineSeparator()));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1,2,0");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,2" + System.lineSeparator()));
    }

    @Test
    void dummyRangeNumberMultiShouldSuccessIfSomeBuildDoesNotExist() {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1,2," + deleted[0]);
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,2" + System.lineSeparator()));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, String.format("1,%d,%d", deleted[0], deleted[0] + 1));
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString(String.format("Builds: 1,%d" + System.lineSeparator(), deleted[0] + 1)));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, String.format("%d,%d,%d", deleted[0] - 1, deleted[0], deleted[0] + 1));
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString(String.format("Builds: %d,%d" + System.lineSeparator(), deleted[0] - 1, deleted[0] + 1)));
    }

    @Test
    void dummyRangeNumberMultiShouldFailIfBuildNumberIsNegative() {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "-1,2,3");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-1,2,3\" is not a valid option"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1,-2,3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1,-2,3', expected string with a range M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1,2,-3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1,2,-3', expected string with a range M-N"));
    }

    @Test
    void dummyRangeNumberMultiShouldFailIfBuildNumberIsTooBig() {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "2147483648,2,3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '2147483648,2,3', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1,2147483648,3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1,2147483648,3', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1,2,2147483648");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1,2,2147483648', expected number"));
    }

    @Test
    void dummyRangeNumberMultiShouldFailIfBuildNumberIsInvalid() {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1a,2,3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1a,2,3', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "aa,2,3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse 'aa,2,3', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1,2a,3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1,2a,3', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1,aa,3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1,aa,3', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1,2,3a");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1,2,3a', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1,2,aa");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1,2,aa', expected number"));
    }

    @Test
    void dummyRangeNumberMultiShouldFailIfBuildNumberIsEmpty() {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, ",2,3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse ',2,3', expected correct notation M,N or M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1,,3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1,,3', expected correct notation M,N or M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1,2,");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1,2,', expected correct notation M,N or M-N"));
    }

    @Test
    void dummyRangeNumberMultiShouldFailIfBuildNumberIsSpace() {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, " ,2,3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse ' ,2,3', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1, ,3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1, ,3', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1,2, ");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1,2, ', expected number"));
    }

    @Test
    void dummyRangeNumberMultiShouldFailIfBuildNumberIsComma() {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, ",,2,3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse ',,2,3', expected correct notation M,N or M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1,,,3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1,,,3', expected correct notation M,N or M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1,2,,");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1,2,,', expected correct notation M,N or M-N"));
    }

    @Test
    void dummyRangeNumberMultiShouldFailIfBuildNumberIsHyphen() {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "-,2,3");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-,2,3\" is not a valid option"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1,-,3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1,-,3', expected string with a range M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1,2,-");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1,2,-', expected string with a range M-N"));
    }

    @Test
    void dummyRangeRangeSingleShouldSuccess() {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1-2");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,2" + System.lineSeparator()));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "+1-+2");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,2" + System.lineSeparator()));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1-1");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1" + System.lineSeparator()));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "+1-+1");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1" + System.lineSeparator()));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1-" + deleted[0]);
        assertThat(result, succeeded());
        StringBuilder builds = new StringBuilder();
        boolean next = false;
        for (int i = 1; i < deleted[0]; i++) {
            if (next)
                builds.append(",");
            builds.append(i);
            next = true;
        }
        assertThat(result.stdout(), containsString("Builds: " + builds + System.lineSeparator()));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "+1-+" + deleted[0]);
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: " + builds + System.lineSeparator()));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "0-1");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1" + System.lineSeparator()));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "+0-+1");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1" + System.lineSeparator()));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "0-2");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,2" + System.lineSeparator()));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "+0-+2");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,2" + System.lineSeparator()));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "0-0");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: " + System.lineSeparator()));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "+0-+0");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: " + System.lineSeparator()));
    }

    @Test
    void dummyRangeRangeSingleShouldSuccessIfSomeBuildDoesNotExist() {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, String.format("%d-%d", deleted[0], deleted[0] + 1));
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString(String.format("Builds: %d" + System.lineSeparator(), deleted[0] + 1)));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, String.format("%d-%d", deleted[0] - 1, deleted[0] + 1));
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString(String.format("Builds: %d,%d" + System.lineSeparator(), deleted[0] - 1, deleted[0] + 1)));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, String.format("%d-%d", deleted[0] - 1, deleted[0]));
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString(String.format("Builds: %d" + System.lineSeparator(), deleted[0] - 1)));
    }

    @Test
    void dummyRangeRangeSingleShouldFailIfBuildRangeContainsZeroAndNegative() {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "0--1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '0--1', expected correct notation M,N or M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "+0--1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '+0--1', expected correct notation M,N or M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "0--2");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '0--2', expected correct notation M,N or M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "+0--2");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '+0--2', expected correct notation M,N or M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1-0");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1-0', expected string with a range M-N where M<N"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "+1-+0");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '+1-+0', expected string with a range M-N where M<N"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "2-0");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '2-0', expected string with a range M-N where M<N"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "+2-+0");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '+2-+0', expected string with a range M-N where M<N"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "-1-0");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-1-0\" is not a valid option"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "-1-+0");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-1-+0\" is not a valid option"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "-2-0");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-2-0\" is not a valid option"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "-2-+0");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-2-+0\" is not a valid option"));
    }

    @Test
    void dummyRangeRangeSingleShouldFailIfBuildRangeContainsANegativeNumber() {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "-1-1");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-1-1\" is not a valid option"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "-1-+1");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-1-+1\" is not a valid option"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "-1-2");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-1-2\" is not a valid option"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "-1-+2");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-1-+2\" is not a valid option"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1--1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1--1', expected correct notation M,N or M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "+1--1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '+1--1', expected correct notation M,N or M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1--2");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1--2', expected correct notation M,N or M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "+1--2");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '+1--2', expected correct notation M,N or M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "-1--1");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-1--1\" is not a valid option"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "-2--1");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-2--1\" is not a valid option"));
    }

    @Test
    void dummyRangeRangeSingleShouldFailIfBuildRangeContainsTooBigNumber() {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1-2147483648");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1-2147483648', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "2147483648-1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '2147483648-1', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "2147483648-2147483648");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '2147483648-2147483648', expected number"));
    }

    @Test
    void dummyRangeRangeSingleShouldFailIfBuildRangeContainsInvalidNumber() {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1-2a");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1-2a', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1-aa");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1-aa', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "2a-2");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '2a-2', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "aa-2");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse 'aa-2', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "2a-2a");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '2a-2a', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "aa-aa");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse 'aa-aa', expected number"));
    }

    @Test
    void dummyRangeRangeSingleShouldFailIfBuildRangeContainsEmptyNumber() {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "-1");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-1\" is not a valid option"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1-");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1-', expected string with a range M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "-");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-\" is not a valid option"));
    }

    @Test
    void dummyRangeRangeSingleShouldFailIfBuildRangeContainsSpace() {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, " -1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse ' -1', expected string with a range M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1- ");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1- ', expected string with a range M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, " - ");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse ' - ', expected string with a range M-N"));
    }

    @Test
    void dummyRangeRangeSingleShouldFailIfBuildRangeContainsComma() {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, ",-1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse ',-1', expected string with a range M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1-,");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1-,', expected string with a range M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, ",-,");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse ',-,', expected string with a range M-N"));
    }

    @Test
    void dummyRangeRangeSingleShouldFailIfBuildRangeContainsHyphen() {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "--1");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"--1\" is not a valid option"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1--");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1--', expected correct notation M,N or M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "---");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"---\" is not a valid option"));
    }

    @Test
    void dummyRangeRangeSingleShouldFailIfBuildRangeIsInverse() {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "2-1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '2-1', expected string with a range M-N where M<N"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "10-1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '10-1', expected string with a range M-N where M<N"));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "-1--2");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-1--2\" is not a valid option"));
    }

    @Test
    void dummyRangeRangeSingleShouldFailIfBuildRangeIsInvalid() {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1-3-");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1-3-', expected correct notation M,N or M-N"));
    }

    @Test
    void dummyRangeRangeMultiShouldSuccess() {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1-2,3-4");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,2,3,4" + System.lineSeparator()));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1-3,3-4");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,2,3,3,4" + System.lineSeparator()));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1-4,2-3");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,2,3,4,2,3" + System.lineSeparator()));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(PROJECT_NAME, "1-2,4-5");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,2,4" + System.lineSeparator()));
    }

    @Extension
    public static class DummyRangeCommand extends RunRangeCommand {
        @Override
        public String getShortDescription() {
            return "DummyRangeCommand";
        }

        @Override
        protected int act(List<Run<?, ?>> builds) {
            boolean comma = false;

            stdout.print("Builds: ");
            for (Run<?, ?> build : builds) {
                if (comma)
                    stdout.print(",");
                else
                    comma = true;
                stdout.print(build.getNumber());
            }
            stdout.println();

            return 0;
        }
    }
}
