/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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
import hudson.model.AbstractItem;
import hudson.model.Item;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.AuthorizationStrategy;
import hudson.security.SparseACL;
import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.Collections;
import jenkins.model.Jenkins;
import org.acegisecurity.acls.sid.PrincipalSid;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;

public class CreateJobCommandTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Issue("JENKINS-22262")
    @Test public void folderPermissions() throws Exception {
        CLICommand cmd = new CreateJobCommand();
        CLICommandInvoker invoker = new CLICommandInvoker(r, cmd);
        final MockFolder d = r.createFolder("d");
        final SparseACL rootACL = new SparseACL(null);
        rootACL.add(new PrincipalSid("alice"), Jenkins.READ, true);
        rootACL.add(new PrincipalSid("bob"), Jenkins.READ, true);
        final SparseACL dACL = new SparseACL(null);
        dACL.add(new PrincipalSid("alice"), Item.READ, true);
        dACL.add(new PrincipalSid("bob"), Item.READ, true);
        dACL.add(new PrincipalSid("bob"), Item.CREATE, true);
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new AuthorizationStrategy() {
            @Override public ACL getRootACL() {
                return rootACL;
            }
            @Override public ACL getACL(AbstractItem item) {
                if (item == d) {
                    return dACL;
                } else {
                    throw new AssertionError(item);
                }
            }
            @Override public Collection<String> getGroups() {
                return Collections.emptySet();
            }
        });
        cmd.setTransportAuth(User.get("alice").impersonate());
        assertThat(invoker.withStdin(new ByteArrayInputStream("<project/>".getBytes("US-ASCII"))).invokeWithArgs("d/p"), failedWith(-1));
        cmd.setTransportAuth(User.get("bob").impersonate());
        assertThat(invoker.withStdin(new ByteArrayInputStream("<project/>".getBytes("US-ASCII"))).invokeWithArgs("d/p"), succeededSilently());
        assertNotNull(d.getItem("p"));
    }

}
