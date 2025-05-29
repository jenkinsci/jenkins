package hudson;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.io.StreamException;
import hudson.model.Node;
import hudson.util.RobustReflectionConverter;
import hudson.util.XStream2;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;

class XmlFileTest {

    @Test
    void canReadXml1_0Test() throws IOException {
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

    @Test
    void xml1_0_withSpecialCharsShouldFail() {
        URL configUrl = getClass().getResource("/hudson/config_1_0_with_special_chars.xml");
        XStream2  xs = new XStream2();
        xs.alias("hudson", Jenkins.class);

        XmlFile xmlFile =  new XmlFile(xs, new File(configUrl.getFile()));
        if (xmlFile.exists()) {
            IOException e = assertThrows(IOException.class, xmlFile::read);
            assertThat(e.getCause(), instanceOf(ConversionException.class));
            ConversionException ce = (ConversionException) e.getCause();
            assertThat(ce.get("cause-exception"), is(StreamException.class.getName()));
            assertThat(ce.get("class"), is(Jenkins.class.getName()));
            assertThat(ce.get("required-type"), is(Jenkins.class.getName()));
            assertThat(ce.get("converter-type"), is(RobustReflectionConverter.class.getName()));
            assertThat(ce.get("path"), is("/hudson/label"));
            assertThat(ce.get("line number"), is("7"));
        }
    }

    @Test
    void canReadXml1_1Test() throws IOException {
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
    void canReadXmlWithControlCharsTest() throws IOException {
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
