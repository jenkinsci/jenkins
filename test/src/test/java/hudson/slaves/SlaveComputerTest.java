/*
 * The MIT License
 *
 * Copyright (c) 2018, suren <zxjlwt@126.com>
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import hudson.Functions;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.remoting.Launcher;
import hudson.security.ACL;
import hudson.security.ACLContext;
import java.io.IOError;
import java.io.IOException;
import jenkins.model.Jenkins;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import org.htmlunit.WebResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.SimpleCommandLauncher;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.xml.sax.SAXException;

/**
 * @author suren
 */
@WithJenkins
class SlaveComputerTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void testAgentLogs() throws Exception {
        DumbSlave node = j.createOnlineSlave();
        String log = node.getComputer().getLog();
        assertTrue(log.contains("Remoting version: " + Launcher.VERSION));
        assertTrue(log.contains("Launcher: " + SimpleCommandLauncher.class.getSimpleName()));
        assertTrue(log.contains("Communication Protocol: Standard in/out"));
        assertTrue(log.contains(String.format("This is a %s agent", Functions.isWindows() ? "Windows" : "Unix")));
    }

    @Test
    void testGetAbsoluteRemotePath() throws Exception {
        //default auth
        DumbSlave nodeA = j.createOnlineSlave();
        String path = nodeA.getComputer().getAbsoluteRemotePath();
        assertNotNull(path);
        assertEquals(getRemoteFS(nodeA, null), path);

        //not auth
        String userAlice = "alice";
        MockAuthorizationStrategy authStrategy = new MockAuthorizationStrategy();
        authStrategy.grant(Computer.CONFIGURE, Jenkins.READ).everywhere().to(userAlice);

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(authStrategy);
        try (ACLContext context = ACL.as(User.getById(userAlice, true))) {
            path = nodeA.getComputer().getAbsoluteRemotePath();
            assertNull(path);
            assertNull(getRemoteFS(nodeA, userAlice));
        }

        //with auth
        String userBob = "bob";
        authStrategy.grant(Computer.CONNECT, Jenkins.READ).everywhere().to(userBob);
        try (ACLContext context = ACL.as(User.getById(userBob, true))) {
            path = nodeA.getComputer().getAbsoluteRemotePath();
            assertNotNull(path);
            assertNotNull(getRemoteFS(nodeA, userBob));
        }
    }

    @Test
    @Issue("JENKINS-57111")
    void startupShouldNotFailOnExceptionOnlineListener() throws Exception {
        DumbSlave nodeA = j.createOnlineSlave();
        assertThat(nodeA.getComputer(), instanceOf(SlaveComputer.class));

        int retries = 10;
        while (IOExceptionOnOnlineListener.onOnlineCount == 0 && retries > 0) {
            retries--;
            Thread.sleep(500);
        }
        assertTrue(retries > 0);
        Thread.sleep(500);

        assertFalse(nodeA.getComputer().isOffline());
        assertTrue(nodeA.getComputer().isOnline());

        // Both listeners should fire and not cause the other not to fire.
        assertEquals(1, IOExceptionOnOnlineListener.onOnlineCount);
        assertEquals(1, RuntimeExceptionOnOnlineListener.onOnlineCount);

        // We should get the stack trace too.
        assertThat(nodeA.getComputer().getLog(), allOf(
                containsString("\tat " + IOExceptionOnOnlineListener.class.getName() + ".onOnline"),
                containsString("\tat " + RuntimeExceptionOnOnlineListener.class.getName() + ".onOnline")));
    }

    @TestExtension(value = "startupShouldNotFailOnExceptionOnlineListener")
    public static final class IOExceptionOnOnlineListener extends ComputerListener {

        static int onOnlineCount = 0;

        @Override
        public void onOnline(Computer c, TaskListener listener) throws IOException {
            if (c instanceof SlaveComputer) {
                onOnlineCount++;
                throw new IOException("Something happened (the listener always throws this exception)");
            }
        }
    }

    @TestExtension(value = "startupShouldNotFailOnExceptionOnlineListener")
    public static final class RuntimeExceptionOnOnlineListener extends ComputerListener {

        static int onOnlineCount = 0;

        @Override
        public void onOnline(Computer c, TaskListener listener) {
            if (c instanceof SlaveComputer) {
                onOnlineCount++;
                throw new RuntimeException("Something happened (the listener always throws this exception)");
            }
        }
    }

    @Test
    @Issue("JENKINS-57111")
    void startupShouldFailOnErrorOnlineListener() throws Exception {
        assumeFalse(Functions.isWindows() && System.getenv("CI") != null, "TODO: Windows container agents do not have enough resources to run this test");
        DumbSlave nodeA = j.createSlave();
        assertThat(nodeA.getComputer(), instanceOf(SlaveComputer.class));
        int retries = 10;
        while (ErrorOnOnlineListener.onOnlineCount == 0 && retries > 0) {
            retries--;
            Thread.sleep(500);
        }
        assertTrue(retries > 0);
        Thread.sleep(500);

        assertEquals(1, ErrorOnOnlineListener.onOnlineCount);

        assertTrue(nodeA.getComputer().isOffline());
        assertFalse(nodeA.getComputer().isOnline());
    }

    @TestExtension(value = "startupShouldFailOnErrorOnlineListener")
    public static final class ErrorOnOnlineListener extends ComputerListener {

        static volatile int onOnlineCount = 0;

        @Override
        public void onOnline(Computer c, TaskListener listener) {
            if (c instanceof SlaveComputer) {
                onOnlineCount++;
                throw new IOError(new Exception("Something happened (the listener always throws this exception)"));
            }
        }
    }


    /**
     * Get remote path through json api
     * @param node agent node
     * @param user the user for webClient
     * @return remote path
     * @throws IOException in case of communication problem.
     * @throws SAXException in case of config format problem.
     */
    private String getRemoteFS(Node node, String user) throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        if (user != null) {
            wc.login(user);
        }

        WebResponse response = wc.goTo("computer/" + node.getNodeName() + "/api/json",
                "application/json").getWebResponse();
        JSONObject json = JSONObject.fromObject(response.getContentAsString());

        Object pathObj = json.get("absoluteRemotePath");
        if (pathObj instanceof JSONNull) {
            return null; // the value is null in here
        } else {
            return pathObj.toString();
        }
    }
}
