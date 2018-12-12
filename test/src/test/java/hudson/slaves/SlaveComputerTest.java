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

import com.gargoylesoftware.htmlunit.WebResponse;
import hudson.model.*;
import hudson.security.ACL;
import hudson.security.ACLContext;
import jenkins.model.Jenkins;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.xml.sax.SAXException;

import java.io.IOException;

/**
 * @author suren
 */
public class SlaveComputerTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testGetAbsoluteRemotePath() throws Exception {
        //default auth
        Node nodeA = j.createOnlineSlave();
        String path = ((DumbSlave) nodeA).getComputer().getAbsoluteRemotePath();
        Assert.assertNotNull(path);
        Assert.assertEquals(getRemoteFS(nodeA, null), path);

        //not auth
        String userAlice = "alice";
        MockAuthorizationStrategy authStrategy = new MockAuthorizationStrategy();
        authStrategy.grant(Computer.CONFIGURE, Jenkins.READ).everywhere().to(userAlice);

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(authStrategy);
        try(ACLContext context = ACL.as(User.getById(userAlice, true))) {
            path = ((DumbSlave) nodeA).getComputer().getAbsoluteRemotePath();
            Assert.assertNull(path);
            Assert.assertNull(getRemoteFS(nodeA, userAlice));
        }

        //with auth
        String userBob = "bob";
        authStrategy.grant(Computer.CONNECT, Jenkins.READ).everywhere().to(userBob);
        try(ACLContext context = ACL.as(User.getById(userBob, true))) {
            path = ((DumbSlave) nodeA).getComputer().getAbsoluteRemotePath();
            Assert.assertNotNull(path);
            Assert.assertNotNull(getRemoteFS(nodeA, userBob));
        }
    }

    /**
     * Get remote path through json api
     * @param node slave node
     * @param user the user for webClient
     * @return remote path
     * @throws IOException in case of communication problem.
     * @throws SAXException in case of config format problem.
     */
    private String getRemoteFS(Node node, String user) throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        if(user != null) {
            wc.login(user);
        }

        WebResponse response = wc.goTo("computer/" + node.getNodeName() + "/api/json",
                "application/json").getWebResponse();
        JSONObject json = JSONObject.fromObject(response.getContentAsString());

        Object pathObj = json.get("absoluteRemotePath");
        if(pathObj instanceof JSONNull) {
            return null; // the value is null in here
        } else {
            return pathObj.toString();
        }
    }
}