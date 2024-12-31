/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import hudson.model.Hudson;
import hudson.model.Messages;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.JenkinsRule;

public class PermissionGroupTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    /**
     * "Overall" permission group should be always the first.
     */
    @Email("http://jenkins-ci.361315.n4.nabble.com/Master-agent-refactor-tp391495.html")
    @Test public void order() {
        assertSame(Jenkins.PERMISSIONS, PermissionGroup.getAll().get(0));
    }

    @SuppressWarnings("ResultOfObjectAllocationIgnored")
    @Test public void duplicatedGroups() {
        assertThrows(IllegalStateException.class, () -> new PermissionGroup(Hudson.class, Messages._Hudson_Permissions_Title()));
    }

    @SuppressWarnings("ResultOfObjectAllocationIgnored")
    @Test public void duplicatedPermissions() {
        assertThrows(IllegalStateException.class, () -> new Permission(Jenkins.PERMISSIONS, "Read", Messages._Hudson_ReadPermission_Description(), Permission.READ, PermissionScope.JENKINS));
    }

}
