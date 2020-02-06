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

package hudson.cli;

import java.net.InetSocketAddress;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

import hudson.model.UpdateCenter;
import jenkins.model.Jenkins;

import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNoException;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.succeeded;

/**
 * This tests can be merged into {@link InstallPluginCommandTest} when the system property {@code jenkins.permission
 * .manage.enabled} is no longer supported
 */
public class InstallPluginCommandWithManageTest {

    static {
        // this happens before the Jenkins static fields are loaded
        System.setProperty("jenkins.permission.manage.enabled", "true");
    }

    @Rule
    public JenkinsRule r = new JenkinsRule();

    private void setupUpdateCenter() {
        try {
            r.jenkins.getUpdateCenter().getSite(UpdateCenter.ID_DEFAULT).updateDirectlyNow(false);
        } catch (Exception x) {
            assumeNoException(x);
        }
        InetSocketAddress address = new InetSocketAddress("updates.jenkins-ci.org", 80);
        assumeFalse("Unable to resolve updates.jenkins-ci.org. Skip test.", address.isUnresolved());
    }

    @Issue("JENKINS-60266")
    @Test
    public void configuratorCanNotInstallPlugin() throws Exception {
        //Setup update center and authorization
        setupUpdateCenter();
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to(
                "admin").grant(Jenkins.MANAGE).everywhere().to("configurator"));

        String plugin = "git";

        assertThat("User with Jenkins.MANAGE permission shouldn't be able to install a plugin fro an UC",
                   new CLICommandInvoker(r, "install-plugin").asUser("configurator").invokeWithArgs(plugin),
                   failedWith(6));

        assertThat("Admin should be able to install a plugin from an UC",
                   new CLICommandInvoker(r, "install-plugin").asUser("admin").invokeWithArgs(plugin),
                   succeeded());
    }

}
