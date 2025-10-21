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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.User;
import jenkins.model.Jenkins;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class CopyJobCommandTest {

    private CLICommand copyJobCommand;
    private CLICommandInvoker command;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        copyJobCommand = new CopyJobCommand();
        command = new CLICommandInvoker(j, copyJobCommand);
    }

    @Test
    void copyBetweenFolders() throws Exception {
        MockFolder dir1 = j.createFolder("dir1");
        MockFolder dir2 = j.createFolder("dir2");
        FreeStyleProject p = dir1.createProject(FreeStyleProject.class, "p1");

        CLICommandInvoker.Result result = command.invokeWithArgs("dir1/p1", "dir2/p2");

        assertThat(result, succeededSilently());

        assertNotNull(j.jenkins.getItemByFullName("dir2/p2"));
        // TODO test copying from/to root, or into nonexistent folder
    }

    @Issue("JENKINS-22262")
    @Test
    void folderPermissions() throws Exception {
        final MockFolder d1 = j.createFolder("d1");
        final FreeStyleProject p = d1.createProject(FreeStyleProject.class, "p");
        final MockFolder d2 = j.createFolder("d2");
        // alice has no real permissions. bob has READ on everything but no more. charlie has CREATE on d2 but not EXTENDED_READ on p. debbie has both.
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
            grant(Jenkins.READ).everywhere().toAuthenticated(). // including alice
            grant(Item.READ).onItems(d1, p, d2).to("bob", "charlie", "debbie").
            grant(Item.CREATE).onItems(d2).to("charlie", "debbie").
            grant(Item.EXTENDED_READ).onItems(p).to("debbie"));
        copyJobCommand.setTransportAuth2(User.getOrCreateByIdOrFullName("alice").impersonate2());
        assertThat(command.invokeWithArgs("d1/p", "d2/p"), failedWith(3));
        copyJobCommand.setTransportAuth2(User.getOrCreateByIdOrFullName("bob").impersonate2());
        assertThat(command.invokeWithArgs("d1/p", "d2/p"), failedWith(6));
        copyJobCommand.setTransportAuth2(User.getOrCreateByIdOrFullName("charlie").impersonate2());
        assertThat(command.invokeWithArgs("d1/p", "d2/p"), failedWith(6));
        copyJobCommand.setTransportAuth2(User.getOrCreateByIdOrFullName("debbie").impersonate2());
        assertThat(command.invokeWithArgs("d1/p", "d2/p"), succeededSilently());
        assertNotNull(d2.getItem("p"));
    }

    // hold off build until saved only makes sense on the UI with config screen shown after copying;
    // expect the CLI copy command to leave the job buildable
    @Test
    void copiedJobIsBuildable() throws Exception {
        FreeStyleProject p1 = j.createFreeStyleProject();
        String copiedProjectName = "p2";

        CLICommandInvoker.Result result = command.invokeWithArgs(p1.getName(), copiedProjectName);

        assertThat(result, succeededSilently());

        FreeStyleProject p2 = (FreeStyleProject) j.jenkins.getItem(copiedProjectName);

        assertNotNull(p2);
        assertTrue(p2.isBuildable());
    }

    @Issue("SECURITY-2424")
    @Test
    void cannotCopyJobWithTrailingDot_regular() throws Exception {
        assertThat(j.jenkins.getItems(), Matchers.hasSize(0));
        j.createFreeStyleProject("job1");
        assertThat(j.jenkins.getItems(), Matchers.hasSize(1));

        CLICommandInvoker.Result result = command.invokeWithArgs("job1", "job1.");
        assertThat(result.stderr(), containsString(hudson.model.Messages.Hudson_TrailingDot()));
        assertThat(result, failedWith(1));

        assertThat(j.jenkins.getItems(), Matchers.hasSize(1));
    }

    @Issue("SECURITY-2424")
    @Test
    void cannotCopyJobWithTrailingDot_exceptIfEscapeHatchIsSet() throws Exception {
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
