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
import static org.hamcrest.text.IsEmptyString.isEmptyString;
import hudson.model.Node;
import hudson.model.User;
import hudson.security.Permission;
import hudson.security.GlobalMatrixAuthorizationStrategy;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

import jenkins.model.Jenkins;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class CreateNodeCommandTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private InputStream in;

    private CreateNodeCommand command;

    @Rule public final JenkinsRule j = new JenkinsRule();

    @Before public void setUp() {

        command = new CreateNodeCommand();
    }

    @Test public void createNodeShouldFailWithoutAdministerPermision() throws Exception {

        forUser("user");

        in = getClass().getResourceAsStream("node.xml");
        final int result = execute();

        assertThat(err.toString(), containsString("user is missing the Administer permission"));
        assertThat("No output expected", out.toString(), isEmptyString());
        assertThat("Command is expected to fail", result, equalTo(-1));
    }

    @Test public void createNode() throws Exception {

        forUser("administrator");

        in = getClass().getResourceAsStream("node.xml");
        final int result = execute();

        assertThat("No error output expected", err.toString(), isEmptyString());
        assertThat("Command is expected to succeed", result, equalTo(0));

        final Node updatedSlave = j.jenkins.getNode("SlaveFromXML");
        assertThat(updatedSlave.getNodeName(), equalTo("SlaveFromXML"));
        assertThat(updatedSlave.getNumExecutors(), equalTo(42));
    }

    @Test public void createNodeShouldFailIfNodeAlreadyExist() throws Exception {

        forUser("administrator");

        j.createSlave("SlaveFromXML", null, null);

        in = getClass().getResourceAsStream("node.xml");
        final int result = execute();

        assertThat(err.toString(), containsString("Node 'SlaveFromXML' already exists"));
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
                Arrays.asList(args), Locale.ENGLISH, in, new PrintStream(out), new PrintStream(err)
        );
    }
}
