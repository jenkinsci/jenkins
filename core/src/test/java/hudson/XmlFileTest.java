package hudson;

import hudson.model.Node;
import hudson.util.XStream2;
import java.io.File;
import java.io.IOException;
import java.net.URL;

import jenkins.model.Jenkins;
import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.SAXParseException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class XmlFileTest {

    @Test
    public void canReadXml1_0Test() throws IOException {
        URL configUrl = getClass().getResource("/hudson/config_1_0.xml");
        XStream2  xs = new XStream2();
        xs.alias("hudson", Jenkins.class);

        XmlFile xmlFile =  new XmlFile(xs, new File(configUrl.getFile()));
        if (xmlFile.exists()) {
            Node n = (Node) xmlFile.read();
            assertThat(n.getNumExecutors(), is(2));
            assertThat(n.getMode().toString(), is("NORMAL"));
        }
    }

    // KXml2Driver is able to parse XML 1.0 even if it has control characters which
    // should be illegal.  Ignoring this test until we switch to a more compliant driver
    @Ignore
    @Test(expected = SAXParseException.class)
    public void xml1_0_withSpecialCharsShouldFail() throws IOException {
        URL configUrl = getClass().getResource("/hudson/config_1_0_with_special_chars.xml");
        XStream2  xs = new XStream2();
        xs.alias("hudson", Jenkins.class);

        XmlFile xmlFile =  new XmlFile(xs, new File(configUrl.getFile()));
        if (xmlFile.exists()) {
            Node n = (Node) xmlFile.read();
            assertThat(n.getNumExecutors(), is(2));
            assertThat(n.getMode().toString(), is("NORMAL"));
        }
    }

    @Test
    public void canReadXml1_1Test() throws IOException {
        URL configUrl = getClass().getResource("/hudson/config_1_1.xml");
        XStream2  xs = new XStream2();
        xs.alias("hudson", Jenkins.class);

        XmlFile xmlFile =  new XmlFile(xs, new File(configUrl.getFile()));
        if (xmlFile.exists()) {
            Node n = (Node) xmlFile.read();
            assertThat(n.getNumExecutors(), is(2));
            assertThat(n.getMode().toString(), is("NORMAL"));
        }
    }
    
    @Test
    public void canReadXmlWithControlCharsTest() throws IOException {
        URL configUrl = getClass().getResource("/hudson/config_1_1_with_special_chars.xml");
        XStream2  xs = new XStream2();
        xs.alias("hudson", Jenkins.class);

        XmlFile xmlFile =  new XmlFile(xs, new File(configUrl.getFile()));
        if (xmlFile.exists()) {
            Node n = (Node) xmlFile.read();
            assertThat(n.getNumExecutors(), is(2));
            assertThat(n.getMode().toString(), is("NORMAL"));
            assertThat(n.getLabelString(), is("LESS_TERMCAP_mb=\u001B[01;31m"));
        }
    }
}
