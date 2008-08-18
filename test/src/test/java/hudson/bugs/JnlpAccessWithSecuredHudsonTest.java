package hudson.bugs;

import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.PresetData;
import org.jvnet.hudson.test.recipes.PresetData.DataSet;
import org.dom4j.io.DOMReader;
import org.dom4j.Document;
import org.dom4j.Element;
import hudson.model.Slave;
import hudson.model.Node.Mode;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.RetentionStrategy.Always;

import java.util.Collections;
import java.util.List;
import java.net.URL;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.xml.XmlPage;
import sun.management.resources.agent;

/**
 * @author Kohsuke Kawaguchi
 */
public class JnlpAccessWithSecuredHudsonTest extends HudsonTestCase {

    /**
     * Creates a new slave that needs to be launched via JNLP.
     */
    protected Slave createNewJnlpSlave(String name) throws Exception {
        return new Slave(name,"",System.getProperty("java.io.tmpdir")+'/'+name,"2", Mode.NORMAL, "", new JNLPLauncher(), RetentionStrategy.INSTANCE);
    }

    @PresetData(DataSet.NO_ANONYMOUS_READACCESS)
    public void test() throws Exception {
        hudson.setSlaves(Collections.singletonList(createNewJnlpSlave("test")));
        HudsonTestCase.WebClient wc = new WebClient();
        HtmlPage p = wc.login("alice").goTo("computer/test/");

        XmlPage jnlp = (XmlPage) wc.goTo("computer/test/slave-agent.jnlp","application/x-java-jnlp-file");
        URL baseUrl = jnlp.getWebResponse().getUrl();

        Document dom = new DOMReader().read(jnlp.getXmlDocument());
        for( Element jar : (List<Element>)dom.selectNodes("//jar") ) {
            URL url = new URL(baseUrl,jar.attributeValue("href"));
            System.out.println(url);
        }
    }
}
