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

import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import static hudson.cli.CLICommandInvoker.Matcher.*;
import hudson.model.AbstractItem;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.AuthorizationStrategy;
import hudson.security.SparseACL;
import java.util.Collection;
import java.util.Collections;
import jenkins.model.Jenkins;
import org.acegisecurity.acls.sid.PrincipalSid;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;

@SuppressWarnings("DM_DEFAULT_ENCODING")
public class CopyJobCommandTest {

    @Rule public JenkinsRule j = new JenkinsRule();
    private CLICommand copyJobCommand;
    private CLICommandInvoker command;

    @Before public void setUp() {
        copyJobCommand = new CopyJobCommand();
        command = new CLICommandInvoker(j, copyJobCommand);
    }

    @Test public void copyBetweenFolders() throws Exception {
        MockFolder dir1 = j.createFolder("dir1");
        MockFolder dir2 = j.createFolder("dir2");
        FreeStyleProject p = dir1.createProject(FreeStyleProject.class, "p1");

        CLICommandInvoker.Result result = command.invokeWithArgs("dir1/p1", "dir2/p2");

        assertThat(result, succeededSilently());

        assertNotNull(j.jenkins.getItemByFullName("dir2/p2"));
        // TODO test copying from/to root, or into nonexistent folder
    }

    @Issue("JENKINS-22262")
    @Test public void folderPermissions() throws Exception {
        final MockFolder d1 = j.createFolder("d1");
        final FreeStyleProject p = d1.createProject(FreeStyleProject.class, "p");
        final MockFolder d2 = j.createFolder("d2");
        // alice has no real permissions. bob has READ on everything but no more. charlie has CREATE on d2 but not EXTENDED_READ on p. debbie has both.
        final SparseACL rootACL = new SparseACL(null);
        rootACL.add(new PrincipalSid("alice"), Jenkins.READ, true);
        rootACL.add(new PrincipalSid("bob"), Jenkins.READ, true);
        rootACL.add(new PrincipalSid("charlie"), Jenkins.READ, true);
        rootACL.add(new PrincipalSid("debbie"), Jenkins.READ, true);
        final SparseACL d1ACL = new SparseACL(null);
        d1ACL.add(new PrincipalSid("bob"), Item.READ, true);
        d1ACL.add(new PrincipalSid("charlie"), Item.READ, true);
        d1ACL.add(new PrincipalSid("debbie"), Item.READ, true);
        final SparseACL pACL = new SparseACL(null);
        pACL.add(new PrincipalSid("bob"), Item.READ, true);
        pACL.add(new PrincipalSid("charlie"), Item.READ, true);
        pACL.add(new PrincipalSid("debbie"), Item.READ, true);
        pACL.add(new PrincipalSid("debbie"), Item.EXTENDED_READ, true);
        final SparseACL d2ACL = new SparseACL(null);
        d2ACL.add(new PrincipalSid("bob"), Item.READ, true);
        d2ACL.add(new PrincipalSid("charlie"), Item.READ, true);
        d2ACL.add(new PrincipalSid("charlie"), Item.CREATE, true);
        d2ACL.add(new PrincipalSid("debbie"), Item.READ, true);
        d2ACL.add(new PrincipalSid("debbie"), Item.CREATE, true);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new AuthorizationStrategy() {
            @Override public ACL getRootACL() {
                return rootACL;
            }
            @Override public ACL getACL(Job<?, ?> project) {
                if (project == p) {
                    return pACL;
                } else {
                    throw new AssertionError(project);
                }
            }
            @Override public ACL getACL(AbstractItem item) {
                if (item == d1) {
                    return d1ACL;
                } else if (item == d2) {
                    return d2ACL;
                } else {
                    throw new AssertionError(item);
                }
            }
            @Override public Collection<String> getGroups() {
                return Collections.emptySet();
            }
        });
        copyJobCommand.setTransportAuth(User.get("alice").impersonate());
        assertThat(command.invokeWithArgs("d1/p", "d2/p"), failedWith(3));
        copyJobCommand.setTransportAuth(User.get("bob").impersonate());
        assertThat(command.invokeWithArgs("d1/p", "d2/p"), failedWith(6));
        copyJobCommand.setTransportAuth(User.get("charlie").impersonate());
        assertThat(command.invokeWithArgs("d1/p", "d2/p"), failedWith(6));
        copyJobCommand.setTransportAuth(User.get("debbie").impersonate());
        assertThat(command.invokeWithArgs("d1/p", "d2/p"), succeededSilently());
        assertNotNull(d2.getItem("p"));
    }

    // hold off build until saved only makes sense on the UI with config screen shown after copying;
    // expect the CLI copy command to leave the job buildable
    @Test public void copiedJobIsBuildable() throws Exception {
        FreeStyleProject p1 = j.createFreeStyleProject();
        String copiedProjectName = "p2";

        CLICommandInvoker.Result result = command.invokeWithArgs(p1.getName(), copiedProjectName);

        assertThat(result, succeededSilently());

        FreeStyleProject p2 = (FreeStyleProject)j.jenkins.getItem(copiedProjectName);

        assertNotNull(p2);
        assertTrue(p2.isBuildable());
    }

}
