/*
 * The MIT License
 *
 * Copyright (c) suren
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
package hudson.slaves;

import hudson.model.Computer;
import hudson.model.Node;
import hudson.security.ACL;
import hudson.security.AuthorizationStrategy;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;

/**
 * @author suren
 */
public class SlaveComputerTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testGetAbsoluteRemotePath() throws Exception {
        Node nodeA = j.createOnlineSlave();
        String path = ((DumbSlave) nodeA).getComputer().getAbsoluteRemotePath();
        Assert.assertNotNull(path);

        setAsAnonymous();
        nodeA = j.createOnlineSlave();
        path = ((DumbSlave) nodeA).getComputer().getAbsoluteRemotePath();
        Assert.assertNull(path);
    }

    private void setAsAnonymous() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new AuthorizationStrategy(){

            @Nonnull
            @Override
            public ACL getRootACL() {
                return UNSECURED.getRootACL();
            }

            @Nonnull
            @Override
            public Collection<String> getGroups() {
                return Collections.emptyList();
            }

            @Nonnull
            @Override
            public ACL getACL(@Nonnull Computer computer) {
                return new ACL(){
                    @Override
                    public boolean hasPermission(@Nonnull Authentication a, @Nonnull Permission permission)
                    {
                        return false;
                    }
                };
            }
        });
        SecurityContextHolder.getContext().setAuthentication(Jenkins.ANONYMOUS);
    }
}