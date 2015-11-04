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
import hudson.model.Node.Mode;
import hudson.model.Slave;
import hudson.remoting.Launcher;
import hudson.remoting.Which;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.DumbSlave;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.DOMReader;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.PresetData;
import org.jvnet.hudson.test.recipes.PresetData.DataSet;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.apache.tools.ant.util.JavaEnvUtils;

/**
 * Makes sure that the jars that web start needs are readable, even when the anonymous user doesn't have any read access. 
 *
 * @author Kohsuke Kawaguchi
 */
public class JnlpAccessWithSecuredHudsonTest extends HudsonTestCase {

    /**
     * Creates a new slave that needs to be launched via JNLP.
     */
    protected Slave createNewJnlpSlave(String name) throws Exception {
        return new DumbSlave(name,"",System.getProperty("java.io.tmpdir")+'/'+name,"2", Mode.NORMAL, "", new JNLPLauncher(), RetentionStrategy.INSTANCE, Collections.EMPTY_LIST);
    }

    @PresetData(DataSet.NO_ANONYMOUS_READACCESS)
    @Email("http://markmail.org/message/on4wkjdaldwi2atx")
    public void testAnonymousCanAlwaysLoadJARs() throws Exception {
        jenkins.setNodes(Collections.singletonList(createNewJnlpSlave("test")));
        HudsonTestCase.WebClient wc = new WebClient();
        HtmlPage p = wc.login("alice").goTo("computer/test/");

        // this fresh WebClient doesn't have a login cookie and represent JNLP launcher
        HudsonTestCase.WebClient jnlpAgent = new WebClient();

        // parse the JNLP page into DOM to list up the jars.
        XmlPage jnlp = (XmlPage) wc.goTo("computer/test/slave-agent.jnlp","application/x-java-jnlp-file");
        URL baseUrl = jnlp.getUrl();
        Document dom = new DOMReader().read(jnlp.getXmlDocument());
        for( Element jar : (List<Element>)dom.selectNodes("//jar") ) {
            URL url = new URL(baseUrl,jar.attributeValue("href"));
            System.out.println(url);
            
            // now make sure that these URLs are unprotected
            Page jarResource = jnlpAgent.getPage(url);
            assertTrue(jarResource.getWebResponse().getContentType().toLowerCase(Locale.ENGLISH).startsWith("application/"));
        }
    }

    @PresetData(DataSet.ANONYMOUS_READONLY)
    public void testAnonymousCannotGetSecrets() throws Exception {
        jenkins.setNodes(Collections.singletonList(createNewJnlpSlave("test")));
        new WebClient().assertFails("computer/test/slave-agent.jnlp", HttpURLConnection.HTTP_FORBIDDEN);
    }

    @PresetData(DataSet.NO_ANONYMOUS_READACCESS)
    @SuppressWarnings("SleepWhileInLoop")
    public void testServiceUsingDirectSecret() throws Exception {
        Slave slave = createNewJnlpSlave("test");
        jenkins.setNodes(Collections.singletonList(slave));
        new WebClient().goTo("computer/test/slave-agent.jnlp?encrypt=true", "application/octet-stream");
        String secret = slave.getComputer().getJnlpMac();
        // To watch it fail: secret = secret.replace('1', '2');
        ProcessBuilder pb = new ProcessBuilder(JavaEnvUtils.getJreExecutable("java"), "-jar", Which.jarFile(Launcher.class).getAbsolutePath(), "-jnlpUrl", getURL() + "computer/test/slave-agent.jnlp", "-secret", secret);
        try {
            pb = (ProcessBuilder) ProcessBuilder.class.getMethod("inheritIO").invoke(pb);
        } catch (NoSuchMethodException x) {
            // prior to Java 7
        }
        System.err.println("Running: " + pb.command());
        Process p = pb.start();
        try {
            for (int i = 0; i < /* one minute */600; i++) {
                if (slave.getComputer().isOnline()) {
                    System.err.println("JNLP slave successfully connected");
                    return;
                }
                Thread.sleep(100);
            }
            fail("JNLP slave agent failed to connect");
        } finally {
            p.destroy();
        }
    }

}
