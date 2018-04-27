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
import hudson.model.Computer;
import hudson.model.Node;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AuthorizationStrategy;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.xml.sax.SAXException;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;

/**
 * @author suren
 */
public class SlaveComputerTest implements Serializable {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testGetAbsoluteRemotePath() throws Exception {
        Node nodeA = j.createOnlineSlave();
        String path = ((DumbSlave) nodeA).getComputer().getAbsoluteRemotePath();
        Assert.assertNotNull(path);
        Assert.assertEquals(getRemoteFS(nodeA), path);

        try(ACLContext context = ACL.as(Jenkins.ANONYMOUS)) {
            setAsAnonymous();
            nodeA = j.createOnlineSlave();
            path = ((DumbSlave) nodeA).getComputer().getAbsoluteRemotePath();
            Assert.assertNull(path);
            Assert.assertEquals(getRemoteFS(nodeA), "null");
        }
    }

    /**
     * Get remote path through json api
     * @param node slave node
     * @return remote path
     * @throws IOException in case of communication problem.
     * @throws SAXException in case of config format problem.
     */
    private String getRemoteFS(Node node) throws IOException, SAXException {
        JenkinsRule.WebClient wc = j.createWebClient();
        WebResponse response = wc.goTo("computer/" + node.getNodeName() + "/api/json",
                "application/json").getWebResponse();
        JSONObject json = JSONObject.fromObject(response.getContentAsString());
        return json.getString("absoluteRemotePath");
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
                    public boolean hasPermission(@Nonnull Authentication a, @Nonnull Permission permission) {
                        if(permission == Computer.CONFIGURE) {
                            return true;
                        }

                        return false;
                    }
                };
            }
        });
    }
}