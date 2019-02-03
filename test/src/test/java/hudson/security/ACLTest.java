/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

package hudson.security;

import hudson.model.FreeStyleProject;
import hudson.model.Item;
import java.util.Collection;
import java.util.Collections;
import org.acegisecurity.Authentication;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class ACLTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Issue("JENKINS-20474")
    @Test
    public void bypassStrategyOnSystem() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new DoNotBotherMe());
        assertTrue(p.hasPermission(Item.CONFIGURE));
        assertTrue(p.hasPermission(ACL.SYSTEM, Item.CONFIGURE));
        p.checkPermission(Item.CONFIGURE);
        p.checkAbortPermission();
        assertEquals(Collections.singletonList(p), r.jenkins.getAllItems());
    }

    private static class DoNotBotherMe extends AuthorizationStrategy {

        @Override
        public ACL getRootACL() {
            return new ACL() {
                @Override
                public boolean hasPermission(Authentication a, Permission permission) {
                    throw new AssertionError("should not have needed to check " + permission + " for " + a);
                }
            };
        }

        @Override
        public Collection<String> getGroups() {
            return Collections.emptySet();
        }

    }

}
