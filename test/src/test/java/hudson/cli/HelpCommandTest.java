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

import static hudson.cli.CLICommandInvoker.Matcher.*;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.text.StringContainsInOrder.stringContainsInOrder;

import java.io.PrintStream;
import java.util.Arrays;

import hudson.cli.CLICommandInvoker.Result;
import hudson.model.AbstractProject;

import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

public class HelpCommandTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test public void getHelpRunningCommand() {

        CLICommandInvoker command = new CLICommandInvoker(j, new HelpCommand());

        String generalHelp = command.invoke().stderr();

        assertContainsOverviewOfClassCommand(generalHelp);
        assertContainsOverviewOfMethodCommand(generalHelp);

        Result result = command.invokeWithArgs(ClassCommand.NAME);
        assertThat(result, succeeded());
        assertThat(result, hasNoStandardOutput());

        assertContainsUsageOfClassCommand(result.stderr());

        result = command.invokeWithArgs("offline-node");

        assertThat(result, succeeded());
        assertThat(result, hasNoStandardOutput());

        assertContainsUsageOfMethodCommand(result.stderr());
    }

    @Test public void getHelpUsingJenkinsUI() throws Exception {

        WebClient wc = j.createWebClient();
        String generalHelp = wc.goTo("cli").asText();

        assertContainsOverviewOfClassCommand(generalHelp);
        assertContainsOverviewOfMethodCommand(generalHelp);

        assertContainsUsageOfClassCommand(wc.goTo("cli/command/class-command").asText());
        assertContainsUsageOfMethodCommand(wc.goTo("cli/command/offline-node").asText());
    }

    private void assertContainsOverviewOfClassCommand(String text) {
        assertThat(text, containsString(ClassCommand.NAME));
        assertThat(text, containsString(ClassCommand.SHORT_DESCRIPTION));
    }

    private void assertContainsUsageOfClassCommand(String text) {
        assertThat(text, containsString("class-command [JOB] [-b BUILD]"));
        assertThat(text, containsString(ClassCommand.LONG_DESCRIPTION));
        assertThat(text, containsStrings("JOB", "Job arg"));
        assertThat(text, containsStrings("BUILD", "Build opt"));
    }

    private void assertContainsOverviewOfMethodCommand(String text) {
        assertThat(text, containsString("offline-node"));
    }

    private void assertContainsUsageOfMethodCommand(String text) {
        assertThat(text, containsString("offline-node NAME [-m VAL]"));
        assertThat(text, containsStrings("NAME", "Agent name, or empty string for master"));
        assertThat(text, containsStrings("-m VAL", "Record the note about why you are disconnecting this node"));
    }

    private static Matcher<String> containsStrings(String... strings) {
        return stringContainsInOrder(Arrays.asList(strings));
    }

    @TestExtension
    public static class ClassCommand extends CLICommand {

        private static final String SHORT_DESCRIPTION = "Short description of class-command";
        private static final String LONG_DESCRIPTION = "Long description of class-command";
        private static final String NAME = "class-command";

        @Argument(usage="Job arg")
        public AbstractProject<?, ?> job;

        @Option(name="-b", metaVar="BUILD", usage="Build opt")
        public String build;

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public String getShortDescription() {
            return SHORT_DESCRIPTION;
        }

        @Override
        protected void printUsageSummary(PrintStream stderr) {
            stderr.println(LONG_DESCRIPTION);
        }

        @Override
        protected int run() throws Exception {
            throw new UnsupportedOperationException();
        }
    }
}
