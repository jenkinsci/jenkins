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

package hudson.bugs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Slave;
import hudson.model.User;
import hudson.remoting.Channel;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import jenkins.model.Jenkins;
import jenkins.security.s2m.AdminWhitelistRule;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.DOMReader;
import org.htmlunit.Page;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.xml.XmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.InboundAgentExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Makes sure that the jars that web start needs are readable, even when the anonymous user doesn't have any read access.
 *
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class JnlpAccessWithSecuredHudsonTest {

    @RegisterExtension
    private final InboundAgentExtension inboundAgents = new InboundAgentExtension();

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    @Email("http://markmail.org/message/on4wkjdaldwi2atx")
    @Test
    void anonymousCanAlwaysLoadJARs() throws Exception {
        JenkinsRule.DummySecurityRealm realm = r.createDummySecurityRealm();
        r.jenkins.setSecurityRealm(realm);
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().toAuthenticated());

        inboundAgents.createAgent(r, InboundAgentExtension.Options.newBuilder().name("test").skipStart().build());
        JenkinsRule.WebClient wc = r.createWebClient();
        HtmlPage p = wc.withBasicApiToken(User.getById("alice", true)).goTo("computer/test/");

        // this fresh WebClient doesn't have a login cookie and represent JNLP launcher
        JenkinsRule.WebClient jnlpAgent = r.createWebClient();

        // parse the JNLP page into DOM to list up the jars.
        XmlPage jnlp = (XmlPage) wc.goTo("computer/test/jenkins-agent.jnlp", "application/x-java-jnlp-file");
        URL baseUrl = jnlp.getUrl();
        Document dom = new DOMReader().read(jnlp.getXmlDocument());
        for (Object jar : dom.selectNodes("//jar")) {
            URL url = new URL(baseUrl, ((Element) jar).attributeValue("href"));
            System.out.println(url);

            // now make sure that these URLs are unprotected
            Page jarResource = jnlpAgent.getPage(url);
            assertTrue(jarResource.getWebResponse().getContentType().toLowerCase(Locale.ENGLISH).startsWith("application/"));
        }
    }

    @Test
    void anonymousCannotGetSecrets() throws Exception {
        JenkinsRule.DummySecurityRealm realm = r.createDummySecurityRealm();
        r.jenkins.setSecurityRealm(realm);
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().toEveryone()
                .grant(Jenkins.ADMINISTER).everywhere().toAuthenticated());

        inboundAgents.createAgent(r, InboundAgentExtension.Options.newBuilder().name("test").skipStart().build());
        r.createWebClient().assertFails("computer/test/jenkins-agent.jnlp", HttpURLConnection.HTTP_FORBIDDEN);
    }

    @Test
    void serviceUsingDirectSecret() throws Exception {
        JenkinsRule.DummySecurityRealm realm = r.createDummySecurityRealm();
        r.jenkins.setSecurityRealm(realm);
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().toAuthenticated());

        Slave slave = inboundAgents.createAgent(r, InboundAgentExtension.Options.newBuilder().name("test").build());
        try {
            r.createWebClient().goTo("computer/test/jenkins-agent.jnlp?encrypt=true", "application/octet-stream");
            Channel channel = slave.getComputer().getChannel();
            assertFalse(channel.isRemoteClassLoadingAllowed(), "SECURITY-206");
            r.jenkins.getExtensionList(AdminWhitelistRule.class).get(AdminWhitelistRule.class).setMasterKillSwitch(false);
            final File f = new File(r.jenkins.getRootDir(), "config.xml");
            assertTrue(f.exists());
        } finally {
            inboundAgents.stop(r, slave.getNodeName());
        }
    }


}
