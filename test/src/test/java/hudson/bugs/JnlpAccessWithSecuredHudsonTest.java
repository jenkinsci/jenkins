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

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.xml.XmlPage;
import hudson.Proc;
import hudson.model.Node.Mode;
import hudson.model.Slave;
import hudson.model.User;
import hudson.remoting.Channel;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.DumbSlave;
import hudson.util.StreamTaskListener;
import jenkins.security.apitoken.ApiTokenTestHelper;
import jenkins.security.s2m.AdminWhitelistRule;
import org.dom4j.Document;
import org.dom4j.io.DOMReader;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.recipes.PresetData;
import org.jvnet.hudson.test.recipes.PresetData.DataSet;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Locale;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.util.JavaEnvUtils;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Makes sure that the jars that web start needs are readable, even when the anonymous user doesn't have any read access. 
 *
 * @author Kohsuke Kawaguchi
 */
public class JnlpAccessWithSecuredHudsonTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /**
     * Creates a new agent that needs to be launched via JNLP.
     */
    protected Slave createNewJnlpSlave(String name) throws Exception {
        return new DumbSlave(name,"",System.getProperty("java.io.tmpdir")+'/'+name,"2", Mode.NORMAL, "", new JNLPLauncher(true), RetentionStrategy.INSTANCE, Collections.EMPTY_LIST);
    }

    @PresetData(DataSet.NO_ANONYMOUS_READACCESS)
    @Email("http://markmail.org/message/on4wkjdaldwi2atx")
    @Test
    public void anonymousCanAlwaysLoadJARs() throws Exception {
        ApiTokenTestHelper.enableLegacyBehavior();
        
        r.jenkins.setNodes(Collections.singletonList(createNewJnlpSlave("test")));
        JenkinsRule.WebClient wc = r.createWebClient();
        HtmlPage p = wc.withBasicApiToken(User.getById("alice", true)).goTo("computer/test/");

        // this fresh WebClient doesn't have a login cookie and represent JNLP launcher
        JenkinsRule.WebClient jnlpAgent = r.createWebClient();

        // parse the JNLP page into DOM to list up the jars.
        XmlPage jnlp = (XmlPage) wc.goTo("computer/test/slave-agent.jnlp","application/x-java-jnlp-file");
        URL baseUrl = jnlp.getUrl();
        Document dom = new DOMReader().read(jnlp.getXmlDocument());
        for( Object jar : dom.selectNodes("//jar") ) {
            URL url = new URL(baseUrl,((org.dom4j.Element)jar).attributeValue("href"));
            System.out.println(url);
            
            // now make sure that these URLs are unprotected
            Page jarResource = jnlpAgent.getPage(url);
            assertTrue(jarResource.getWebResponse().getContentType().toLowerCase(Locale.ENGLISH).startsWith("application/"));
        }
    }

    @PresetData(DataSet.ANONYMOUS_READONLY)
    @Test
    public void anonymousCannotGetSecrets() throws Exception {
        r.jenkins.setNodes(Collections.singletonList(createNewJnlpSlave("test")));
        r.createWebClient().assertFails("computer/test/slave-agent.jnlp", HttpURLConnection.HTTP_FORBIDDEN);
    }

    @PresetData(DataSet.NO_ANONYMOUS_READACCESS)
    @SuppressWarnings("SleepWhileInLoop")
    @Test
    public void serviceUsingDirectSecret() throws Exception {
        Slave slave = createNewJnlpSlave("test");
        r.jenkins.setNodes(Collections.singletonList(slave));
        r.createWebClient().goTo("computer/test/slave-agent.jnlp?encrypt=true", "application/octet-stream");
        String secret = slave.getComputer().getJnlpMac();
        // To watch it fail: secret = secret.replace('1', '2');
        File slaveJar = tmp.newFile();
        FileUtils.copyURLToFile(new Slave.JnlpJar("slave.jar").getURL(), slaveJar);
        Proc p = new hudson.Launcher.LocalLauncher(StreamTaskListener.fromStderr()).launch().
            stdout(System.out).stderr(System.err).
            cmds(JavaEnvUtils.getJreExecutable("java"), "-jar", slaveJar.getAbsolutePath(), "-jnlpUrl", r.getURL() + "computer/test/slave-agent.jnlp", "-secret", secret).
            start();
        try {
            r.waitOnline(slave);
            Channel channel = slave.getComputer().getChannel();
            assertFalse("SECURITY-206", channel.isRemoteClassLoadingAllowed());
            r.jenkins.getExtensionList(AdminWhitelistRule.class).get(AdminWhitelistRule.class).setMasterKillSwitch(false);
            final File f = new File(r.jenkins.getRootDir(), "config.xml");
            assertTrue(f.exists());
        } finally {
            p.kill();
        }
    }


}
