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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.text.IsEmptyString.isEmptyString;
import hudson.model.User;
import hudson.security.Permission;
import hudson.security.GlobalMatrixAuthorizationStrategy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

import jenkins.model.Jenkins;

import org.apache.commons.io.input.NullInputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class GetNodeCommandTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private GetNodeCommand command;

    @Rule public final JenkinsRule j = new JenkinsRule();

    @Before public void setUp() {

        command = new GetNodeCommand();
    }

    @Test public void getNodeShouldFailWithoutAdministerPermision() throws Exception {

        forUser("user");

        j.createSlave("MySlave", null, null);

        final int result = execute("MySlave");

        assertThat(err.toString(), containsString("user is missing the Administer permission"));
        assertThat("No output expected", out.toString(), isEmptyString());
        assertThat("Command is expected to fail", result, equalTo(-1));
    }

    @Test public void getNodeShouldYieldConfigXml() throws Exception {

        forUser("administrator");

        j.createSlave("MySlave", null, null);

        final int result = execute("MySlave");

        assertThat(out.toString(), startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertThat(out.toString(), containsString("<name>MySlave</name>"));
        assertThat("No error output expected", err.toString(), isEmptyString());
        assertThat("Command is expected to succeed", result, equalTo(0));
    }

    @Test public void getNodeShouldFailIfNodeDoesNotExist() throws Exception {

        forUser("administrator");

        final int result = execute("MySlave");

        assertThat(err.toString(), containsString("No such node 'MySlave'"));
        assertThat("No output expected", out.toString(), isEmptyString());
        assertThat("Command is expected to fail", result, equalTo(-1));
    }

    private void forUser(final String user) {

        JenkinsRule.DummySecurityRealm realm = j.createDummySecurityRealm();
        realm.addGroups("user", "group");
        realm.addGroups("administrator", "administrator");
        j.jenkins.setSecurityRealm(realm);

        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        auth.add(Permission.READ, "group");
        auth.add(Jenkins.ADMINISTER, "administrator");
        j.jenkins.setAuthorizationStrategy(auth);

        command.setTransportAuth(User.get(user).impersonate());
    }

    private int execute(final String... args) {

        return command.main(
                Arrays.asList(args), Locale.ENGLISH, new NullInputStream(0), new PrintStream(out), new PrintStream(err)
        );
    }
}
