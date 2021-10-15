/*
 * The MIT License
 *
 * Copyright 2012 Jesse Glick.
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
import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import hudson.model.Messages;
import jenkins.model.Jenkins;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

//TODO merge back to CopyJobCommandTest after security release
public class CopyJobCommandSEC2424Test {

    @Rule public JenkinsRule j = new JenkinsRule();
    private CLICommand copyJobCommand;
    private CLICommandInvoker command;

    @Before public void setUp() {
        copyJobCommand = new CopyJobCommand();
        command = new CLICommandInvoker(j, copyJobCommand);
    }

    @Issue("SECURITY-2424")
    @Test public void cannotCopyJobWithTrailingDot_regular() throws Exception {
        assertThat(j.jenkins.getItems(), Matchers.hasSize(0));
        j.createFreeStyleProject("job1");
        assertThat(j.jenkins.getItems(), Matchers.hasSize(1));

        CLICommandInvoker.Result result = command.invokeWithArgs("job1", "job1.");
        assertThat(result.stderr(), containsString(Messages.Hudson_TrailingDot()));
        assertThat(result, failedWith(1));

        assertThat(j.jenkins.getItems(), Matchers.hasSize(1));
    }

    @Issue("SECURITY-2424")
    @Test public void cannotCopyJobWithTrailingDot_exceptIfEscapeHatchIsSet() throws Exception {
        String propName = Jenkins.NAME_VALIDATION_REJECTS_TRAILING_DOT_PROP;
        String initialValue = System.getProperty(propName);
        System.setProperty(propName, "false");
        try {
            assertThat(j.jenkins.getItems(), Matchers.hasSize(0));
            j.createFreeStyleProject("job1");
            assertThat(j.jenkins.getItems(), Matchers.hasSize(1));

            CLICommandInvoker.Result result = command.invokeWithArgs("job1", "job1.");
            assertThat(result, succeededSilently());

            assertThat(j.jenkins.getItems(), Matchers.hasSize(2));
        }
        finally {
            if (initialValue == null) {
                System.clearProperty(propName);
            } else {
                System.setProperty(propName, initialValue);
            }
        }
    }
}
