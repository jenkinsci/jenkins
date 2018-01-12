package hudson;

import hudson.model.Node;
import hudson.util.XStream2;
import java.io.File;
import java.io.IOException;
import java.net.URL;

import jenkins.model.Jenkins;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class XmlFileTest {

    @Test
    public void canReadXml1_0Test() throws IOException {
        URL configUrl = getClass().getResource("/hudson/config_1_0.xml");
        File configFile = new File(configUrl.getFile());
        XStream2  xs = new XStream2();
        xs.alias("hudson", Jenkins.class);

        XmlFile xmlFile =  new XmlFile(xs, configFile);
        if (xmlFile.exists()) {
            Node n = (Node) xmlFile.read();
            assertThat(n.getNumExecutors(), is(2));
            assertThat(n.getMode().toString(), is("NORMAL"));
        }
    }

    @Test
    public void canReadXml1_1Test() throws IOException {
        URL configUrl = getClass().getResource("/hudson/config_1_1.xml");
        File configFile = new File(configUrl.getFile());
        XStream2  xs = new XStream2();
        xs.alias("hudson", Jenkins.class);

        XmlFile xmlFile =  new XmlFile(xs, configFile);
        if (xmlFile.exists()) {
            Node n = (Node) xmlFile.read();
            assertThat(n.getNumExecutors(), is(2));
            assertThat(n.getMode().toString(), is("NORMAL"));
        }
    }
    
    @Test
    public void canReadXmlWithControlCharsTest() throws IOException {
        URL configUrl = getClass().getResource("/hudson/confg_1_1_with_special_chars.xml");
        File configFile = new File(configUrl.getFile());
        XStream2  xs = new XStream2();
        xs.alias("hudson", Jenkins.class);

        XmlFile xmlFile =  new XmlFile(xs, configFile);
        if (xmlFile.exists()) {
            Node n = (Node) xmlFile.read();
            assertThat(n.getNumExecutors(), is(2));
            assertThat(n.getMode().toString(), is("NORMAL"));
            assertThat(n.getLabelString(), is("LESS_TERMCAP_mb=\u001B[01;31m"));
        }
    }
}
