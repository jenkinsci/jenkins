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
import jenkins.model.Jenkins;
import org.junit.BeforeClass;
import org.junit.ClassRule;
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
public class AbstractBuildRangeCommandTest {

    private static CLICommandInvoker command = null;
    private static FreeStyleProject project = null;

    private static final String PROJECT_NAME = "aProject";
    private static final int BUILDS = 10;

    private static final int[] deleted = {5,8,9};

    @ClassRule public static final JenkinsRule j = new JenkinsRule();

    @BeforeClass public static void setUpClass() throws Exception {
        command = new CLICommandInvoker(j,  new DummyRangeCommand());
        project = j.createFreeStyleProject(PROJECT_NAME);
        for (int i=0; i<BUILDS; i++) {
            assertThat(project.scheduleBuild2(0).get(), not(equalTo(null)));
        }
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(),
                equalTo(BUILDS));

        for (int i : deleted) {
            project.getBuildByNumber(i).delete();
            assertThat(project.getBuildByNumber(i), equalTo(null));
        }
    }

    @Test public void dummyRangeShouldFailWithoutJobReadPermission() throws Exception {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ)
                .invokeWithArgs(PROJECT_NAME, "1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(),
                containsString(String.format("ERROR: No such job '%s'", PROJECT_NAME)));
    }

    @Test public void dummyRangeShouldFailIfJobDesNotExist() throws Exception {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs("never_created", "1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such job 'never_created'"));
    }

    @Test public void dummyRangeShouldFailIfJobNameIsEmpty() throws Exception {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs("", "1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(),
                containsString(String.format("ERROR: No such job ''; perhaps you meant '%s'?", PROJECT_NAME)));
    }

    @Test public void dummyRangeShouldFailIfJobNameIsSpace() throws Exception {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(" ", "1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(),
                containsString(String.format("ERROR: No such job ' '; perhaps you meant '%s'?", PROJECT_NAME)));
    }

    @Test public void dummyRangeShouldSuccessIfBuildDoesNotExist() throws Exception {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, String.valueOf(BUILDS+1));
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: \n"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, String.valueOf(deleted[0]));
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: \n"));
    }

    @Test public void dummyRangeNumberSingleShouldSuccess() throws Exception {
        // First
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1\n"));

        // First with plus symbol '+'
        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "+1");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1\n"));

        // In the middle
        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "10");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 10\n"));

        // In the middle with plus symbol '+'
        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "+10");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 10\n"));

        // Last
        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, String.valueOf(BUILDS));
        assertThat(result, succeeded());
        assertThat(result.stdout(),
                containsString(String.format("Builds: %s\n", String.valueOf(BUILDS))));

        // Last with the plus symbol '+'
        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, '+' + String.valueOf(BUILDS));
        assertThat(result, succeeded());
        assertThat(result.stdout(),
                containsString(String.format("Builds: %s\n", String.valueOf(BUILDS))));
    }

    @Test public void dummyRangeNumberSingleShouldSuccessIfBuildNumberIsZero() throws Exception {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "0");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: \n"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "+0");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: \n"));
    }

    @Test public void dummyRangeNumberSingleShouldFailIfBuildNumberIsNegative() throws Exception {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "-1");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-1\" is not a valid option"));
    }

    @Test public void dummyRangeNumberSingleShouldFailIfBuildNumberIsTooBig() throws Exception {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "2147483648");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '2147483648', expected number"));
    }

    @Test public void dummyRangeNumberSingleShouldFailIfBuildNumberIsInvalid() throws Exception {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1a");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1a', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "aa");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse 'aa', expected number"));
    }

    @Test public void dummyRangeNumberSingleShouldSuccessIfBuildNumberIsEmpty() throws Exception {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: \n"));
    }

    @Test public void dummyRangeNumberSingleShouldFailIfBuildNumberIsSpace() throws Exception {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, " ");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse ' ', expected number"));
    }

    @Test public void dummyRangeNumberSingleShouldSuccessIfBuildNumberIsComma() throws Exception {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, ",");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: \n"));
    }

    @Test public void dummyRangeNumberSingleShouldFailIfBuildNumberIsHyphen() throws Exception {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "-");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-\" is not a valid option"));
    }

    @Test public void dummyRangeNumberMultiShouldSuccess() throws Exception {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1,2");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,2\n"));

        // With plus symbol '+'
        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1,+2,4");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,2,4\n"));

        // Build specified twice
        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1,1");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,1\n"));

        // Build with zero build number
        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "0,1,2");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,2\n"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1,0,2");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,2\n"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1,2,0");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,2\n"));
    }

    @Test public void dummyRangeNumberMultiShouldSuccessIfSomeBuildDoesNotExist() throws Exception {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1,2,"+deleted[0]);
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,2\n"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, String.format("1,%d,%d", deleted[0], deleted[0]+1));
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString(String.format("Builds: 1,%d\n", deleted[0]+1)));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, String.format("%d,%d,%d", deleted[0]-1, deleted[0], deleted[0]+1));
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString(String.format("Builds: %d,%d\n", deleted[0]-1, deleted[0]+1)));
    }

    @Test public void dummyRangeNumberMultiShouldFailIfBuildNumberIsNegative() throws Exception {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "-1,2,3");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-1,2,3\" is not a valid option"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1,-2,3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse \'1,-2,3\', expected string with a range M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1,2,-3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse \'1,2,-3\', expected string with a range M-N"));
    }

    @Test public void dummyRangeNumberMultiShouldFailIfBuildNumberIsTooBig() throws Exception {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "2147483648,2,3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '2147483648,2,3', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1,2147483648,3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1,2147483648,3', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1,2,2147483648");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1,2,2147483648', expected number"));
    }

    @Test public void dummyRangeNumberMultiShouldFailIfBuildNumberIsInvalid() throws Exception {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1a,2,3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1a,2,3', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "aa,2,3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse 'aa,2,3', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1,2a,3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1,2a,3', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1,aa,3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1,aa,3', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1,2,3a");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1,2,3a', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1,2,aa");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1,2,aa', expected number"));
    }

    @Test public void dummyRangeNumberMultiShouldFailIfBuildNumberIsEmpty() throws Exception {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, ",2,3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse ',2,3', expected correct notation M,N or M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1,,3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1,,3', expected correct notation M,N or M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1,2,");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1,2,', expected correct notation M,N or M-N"));
    }

    @Test public void dummyRangeNumberMultiShouldFailIfBuildNumberIsSpace() throws Exception {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, " ,2,3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse ' ,2,3', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1, ,3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1, ,3', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1,2, ");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1,2, ', expected number"));
    }

    @Test public void dummyRangeNumberMultiShouldFailIfBuildNumberIsComma() throws Exception {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, ",,2,3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse ',,2,3', expected correct notation M,N or M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1,,,3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1,,,3', expected correct notation M,N or M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1,2,,");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1,2,,', expected correct notation M,N or M-N"));
    }

    @Test public void dummyRangeNumberMultiShouldFailIfBuildNumberIsHyphen() throws Exception {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "-,2,3");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-,2,3\" is not a valid option"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1,-,3");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1,-,3', expected string with a range M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1,2,-");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1,2,-', expected string with a range M-N"));
    }

    @Test public void dummyRangeRangeSingleShouldSuccess() throws Exception {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1-2");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,2\n"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "+1-+2");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,2\n"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1-1");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1\n"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "+1-+1");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1\n"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1-"+deleted[0]);
        assertThat(result, succeeded());
        String builds = "";
        boolean next = false;
        for (int i = 1; i < deleted[0]; i++) {
            if (next)
                builds += ",";
            builds += i;
            next = true;
        }
        assertThat(result.stdout(), containsString("Builds: "+builds+"\n"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "+1-+"+deleted[0]);
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: "+builds+"\n"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "0-1");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1\n"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "+0-+1");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1\n"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "0-2");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,2\n"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "+0-+2");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,2\n"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "0-0");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: \n"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "+0-+0");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: \n"));
    }

    @Test public void dummyRangeRangeSingleShouldSuccessIfSomeBuildDoesNotExist() throws Exception {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, String.format("%d-%d", deleted[0], deleted[0]+1));
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString(String.format("Builds: %d\n", deleted[0] + 1)));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, String.format("%d-%d", deleted[0]-1, deleted[0]+1));
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString(String.format("Builds: %d,%d\n", deleted[0]-1, deleted[0]+1)));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, String.format("%d-%d", deleted[0]-1, deleted[0]));
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString(String.format("Builds: %d\n", deleted[0]-1)));
    }

    @Test public void dummyRangeRangeSingleShouldFailIfBuildRangeContainsZeroAndNegative() throws Exception {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "0--1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '0--1', expected correct notation M,N or M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "+0--1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '+0--1', expected correct notation M,N or M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "0--2");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '0--2', expected correct notation M,N or M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "+0--2");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '+0--2', expected correct notation M,N or M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1-0");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1-0', expected string with a range M-N where M<N"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "+1-+0");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '+1-+0', expected string with a range M-N where M<N"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "2-0");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '2-0', expected string with a range M-N where M<N"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "+2-+0");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '+2-+0', expected string with a range M-N where M<N"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "-1-0");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-1-0\" is not a valid option"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "-1-+0");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-1-+0\" is not a valid option"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "-2-0");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-2-0\" is not a valid option"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "-2-+0");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-2-+0\" is not a valid option"));
    }

    @Test public void dummyRangeRangeSingleShouldFailIfBuildRangeContainsANegativeNumber() throws Exception {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "-1-1");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-1-1\" is not a valid option"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "-1-+1");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-1-+1\" is not a valid option"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "-1-2");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-1-2\" is not a valid option"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "-1-+2");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-1-+2\" is not a valid option"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1--1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1--1', expected correct notation M,N or M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "+1--1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '+1--1', expected correct notation M,N or M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1--2");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1--2', expected correct notation M,N or M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "+1--2");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '+1--2', expected correct notation M,N or M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "-1--1");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-1--1\" is not a valid option"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "-2--1");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-2--1\" is not a valid option"));
    }

    @Test public void dummyRangeRangeSingleShouldFailIfBuildRangeContainsTooBigNumber() throws Exception {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1-2147483648");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1-2147483648', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "2147483648-1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '2147483648-1', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "2147483648-2147483648");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '2147483648-2147483648', expected number"));
    }

    @Test public void dummyRangeRangeSingleShouldFailIfBuildRangeContainsInvalidNumber() throws Exception {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1-2a");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1-2a', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1-aa");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1-aa', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "2a-2");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '2a-2', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "aa-2");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse 'aa-2', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "2a-2a");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '2a-2a', expected number"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "aa-aa");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse 'aa-aa', expected number"));
    }

    @Test public void dummyRangeRangeSingleShouldFailIfBuildRangeContainsEmptyNumber() throws Exception {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "-1");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-1\" is not a valid option"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1-");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1-', expected string with a range M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "-");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-\" is not a valid option"));
    }

    @Test public void dummyRangeRangeSingleShouldFailIfBuildRangeContainsSpace() throws Exception {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, " -1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse ' -1', expected string with a range M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1- ");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1- ', expected string with a range M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, " - ");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse ' - ', expected string with a range M-N"));
    }

    @Test public void dummyRangeRangeSingleShouldFailIfBuildRangeContainsComma() throws Exception {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, ",-1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse ',-1', expected string with a range M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1-,");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1-,', expected string with a range M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, ",-,");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse ',-,', expected string with a range M-N"));
    }

    @Test public void dummyRangeRangeSingleShouldFailIfBuildRangeContainsHyphen() throws Exception {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "--1");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"--1\" is not a valid option"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1--");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1--', expected correct notation M,N or M-N"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "---");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"---\" is not a valid option"));
    }

    @Test public void dummyRangeRangeSingleShouldFailIfBuildRangeIsInverse() throws Exception {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "2-1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '2-1', expected string with a range M-N where M<N"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "10-1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '10-1', expected string with a range M-N where M<N"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "-1--2");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: \"-1--2\" is not a valid option"));
    }

    @Test public void dummyRangeRangeSingleShouldFailIfBuildRangeIsInvalid() throws Exception {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1-3-");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Unable to parse '1-3-', expected correct notation M,N or M-N"));
    }

    @Test public void dummyRangeRangeMultiShouldSuccess() throws Exception {
        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1-2,3-4");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,2,3,4\n"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1-3,3-4");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,2,3,3,4\n"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1-4,2-3");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,2,3,4,2,3\n"));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs(PROJECT_NAME, "1-2,4-5");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1,2,4\n"));
    }

    @Extension
    public static class DummyRangeCommand extends AbstractBuildRangeCommand {
        @Override
        public String getShortDescription() {
            return "DummyRangeCommand";
        }

        @Override
        protected int act(List<AbstractBuild<?, ?>> builds) throws IOException {
            boolean comma = false;

            stdout.print("Builds: ");
            for (AbstractBuild build : builds) {
                if (comma)
                    stdout.print(",");
                else
                    comma = true;
                stdout.print(build.getNumber());
            }
            stdout.println("");

            return 0;
        }
    }
}
