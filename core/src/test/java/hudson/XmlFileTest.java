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
import static org.junit.Assert.assertThrows;

public class XmlFileTest {

    @Test
    public void canReadXml1_0Test() throws IOException {
        URL configUrl = getClass().getResource("/hudson/config_1_0.xml");
        extractedMethod67969(configUrl); // CAP AL
    }

    // KXml2Driver is able to parse XML 1.0 even if it has control characters which
    // should be illegal.  Ignoring this test until we switch to a more compliant driver
    @Ignore
    @Test
    public void xml1_0_withSpecialCharsShouldFail() throws IOException {
        URL configUrl = getClass().getResource("/hudson/config_1_0_with_special_chars.xml");
        XStream2  xs = new XStream2();
        xs.alias("hudson", Jenkins.class);

        XmlFile xmlFile =  new XmlFile(xs, new File(configUrl.getFile()));
        if (xmlFile.exists()) {
            assertThrows(SAXParseException.class, () -> xmlFile.read());
        }
    }

    @Test
    public void canReadXml1_1Test() throws IOException {
        URL configUrl = getClass().getResource("/hudson/config_1_1.xml");
        extractedMethod67969(configUrl); // CAP AL
    }
 // CAP AL
    private void extractedMethod67969(final URL configUrl) throws IOException { // CAP AL
        XStream2  xs = new XStream2(); // CAP AL
        xs.alias("hudson", Jenkins.class); // CAP AL
         // CAP AL
        XmlFile xmlFile =  new XmlFile(xs, new File(configUrl.getFile())); // CAP AL
        if (xmlFile.exists()) { // CAP AL
            Node n = (Node) xmlFile.read(); // CAP AL
            assertThat(n.getNumExecutors(), is(2)); // CAP AL
            assertThat(n.getMode().toString(), is("NORMAL")); // CAP AL
        } // CAP AL
    } // CAP AL
    
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
