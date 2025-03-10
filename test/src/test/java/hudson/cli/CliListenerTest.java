/*
 * The MIT License
 *
 * Copyright (c) 2025
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;

import hudson.cli.listeners.DefaultCliListener;
import java.util.List;
import java.util.logging.Level;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

public class CliListenerTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public LoggerRule logging = new LoggerRule();

    private static final String USER = "cli-user";

    @Before
    public void setUp() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER)
                .everywhere()
                .to(USER));

        logging.record(DefaultCliListener.class, Level.FINE).capture(2);
    }

    @Test
    public void commandExecutionSuccessIsLogged() throws Exception {
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
    public void commandExecutionErrorIsLogged() throws Exception {
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
}
