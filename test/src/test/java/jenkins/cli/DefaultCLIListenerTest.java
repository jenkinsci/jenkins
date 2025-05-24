/*
 * The MIT License
 *
 * Copyright (c) 2025, CloudBees, Inc.
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

package jenkins.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import hudson.cli.CLICommand;
import hudson.cli.CLICommandInvoker;
import hudson.cli.ListJobsCommand;
import java.util.List;
import java.util.logging.Level;
import jenkins.cli.listeners.DefaultCLIListener;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class DefaultCLIListenerTest {

    private final LogRecorder logging = new LogRecorder();

    private static final String USER = "cli-user";

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        j = rule;
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER)
                .everywhere()
                .to(USER));
        j.createFreeStyleProject("p");
        logging.record(DefaultCLIListener.class, Level.FINE).capture(2);
    }

    @Test
    void commandOnCompletedIsLogged() {
        CLICommandInvoker command = new CLICommandInvoker(j, new ListJobsCommand());
        command.asUser(USER).invoke();

        List<String> messages = logging.getMessages();
        assertThat(messages, hasSize(2));
        assertThat(
                messages.get(0),
                containsString("Invoking CLI command list-jobs, with 0 arguments, as user %s.".formatted(USER)));
        assertThat(
                messages.get(1),
                containsString(
                        "Executed CLI command list-jobs, with 0 arguments, as user %s, return code 0".formatted(USER)));
    }

    @Test
    void commandOnThrowableIsLogged() {
        CLICommandInvoker command = new CLICommandInvoker(j, new ListJobsCommand());
        command.asUser(USER).invokeWithArgs("view-not-found");

        List<String> messages = logging.getMessages();
        assertThat(messages, hasSize(2));
        assertThat(
                messages.get(0),
                containsString("Invoking CLI command list-jobs, with 1 arguments, as user %s.".formatted(USER)));
        assertThat(
                messages.get(1),
                containsString("Failed call to CLI command list-jobs, with 1 arguments, as user %s.".formatted(USER)));
        assertThat(
                logging.getRecords().get(0).getThrown().getMessage(),
                containsString("No view or item group with the given name 'view-not-found' found"));
    }

    @Test
    void commandOnThrowableUnexpectedIsLogged() {
        CLICommandInvoker command = new CLICommandInvoker(j, new ThrowsTestCommand());
        command.asUser(USER).invoke();

        List<String> messages = logging.getMessages();
        assertThat(messages, hasSize(2));
        assertThat(
                messages.get(0),
                containsString(
                        "Invoking CLI command throws-test-command, with 0 arguments, as user %s.".formatted(USER)));
        assertThat(
                messages.get(1),
                containsString("Unexpected exception occurred while performing throws-test-command command."));
        assertThat(logging.getRecords().get(0).getThrown().getMessage(), containsString("unexpected"));
    }

    @Test
    void methodOnCompletedIsLogged() {
        CLICommandInvoker command = new CLICommandInvoker(j, "disable-job");
        command.asUser(USER).invokeWithArgs("p");

        List<String> messages = logging.getMessages();
        assertThat(messages, hasSize(2));
        assertThat(
                messages.get(0),
                containsString("Invoking CLI command disable-job, with 1 arguments, as user %s.".formatted(USER)));
        assertThat(
                messages.get(1),
                containsString("Executed CLI command disable-job, with 1 arguments, as user %s, return code 0"
                        .formatted(USER)));
    }

    @Test
    void methodOnThrowableIsLogged() {
        CLICommandInvoker command = new CLICommandInvoker(j, "disable-job");
        command.asUser(USER).invokeWithArgs("job-not-found");

        List<String> messages = logging.getMessages();
        assertThat(messages, hasSize(2));
        assertThat(
                messages.get(0),
                containsString("Invoking CLI command disable-job, with 1 arguments, as user %s.".formatted(USER)));
        assertThat(
                messages.get(1),
                containsString(
                        "Failed call to CLI command disable-job, with 1 arguments, as user %s.".formatted(USER)));
        assertThat(
                logging.getRecords().get(0).getThrown().getMessage(),
                containsString("No such job ‘job-not-found’ exists."));
    }

    @Test
    void methodOnThrowableUnexpectedIsLogged() {
        CLICommandInvoker command = new CLICommandInvoker(j, "restart");
        command.asUser(USER).invoke();

        List<String> messages = logging.getMessages();
        assertThat(messages, hasSize(2));
        assertThat(
                messages.get(0),
                containsString("Invoking CLI command restart, with 0 arguments, as user %s.".formatted(USER)));
        assertThat(messages.get(1), containsString("Unexpected exception occurred while performing restart command."));
        assertThat(logging.getRecords().get(0).getThrown(), notNullValue());
    }

    @TestExtension
    public static class ThrowsTestCommand extends CLICommand {
        @Override
        public String getName() {
            return "throws-test-command";
        }

        @Override
        public String getShortDescription() {
            return "throws test command";
        }

        @Override
        protected int run() {
            throw new RuntimeException("unexpected");
        }
    }
}
