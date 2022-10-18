package jenkins.util.xstream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.thoughtworks.xstream.io.binary.BinaryStreamReader;
import com.thoughtworks.xstream.io.binary.BinaryStreamWriter;
import hudson.util.XStream2;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

public class AtomicBooleanFieldsTest {

    public static class Musican {
        public String name;
        public String genre;
        public AtomicBoolean alive;

        public Musican(final String name, final String genre, final AtomicBoolean alive) {
            this.name = name;
            this.genre = genre;
            this.alive = alive;
        }
    }

    @Test
    public void testAtomicBooleanFields() {
        List<Musican> jazzIcons = new ArrayList<>();
        jazzIcons.add(new Musican("Miles Davis", "jazz", new AtomicBoolean(false)));
        jazzIcons.add(new Musican("Wynton Marsalis", "jazz", new AtomicBoolean(true)));

        XStream2 xstream = new XStream2();
        xstream.alias("musician", Musican.class);

        String xmlString = xstream.toXML(jazzIcons);
        assertEquals(
                "<list>\n"
                        + "  <musician>\n"
                        + "    <name>Miles Davis</name>\n"
                        + "    <genre>jazz</genre>\n"
                        + "    <alive>\n"
                        + "      <value>0</value>\n"
                        + "    </alive>\n"
                        + "  </musician>\n"
                        + "  <musician>\n"
                        + "    <name>Wynton Marsalis</name>\n"
                        + "    <genre>jazz</genre>\n"
                        + "    <alive>\n"
                        + "      <value>1</value>\n"
                        + "    </alive>\n"
                        + "  </musician>\n"
                        + "</list>",
                xmlString);
        List<Musican> obj = (List<Musican>) xstream.fromXML(xmlString);
        assertNotNull(obj);
        assertEquals(xstream.toXML(jazzIcons), xstream.toXML(obj));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        xstream.marshal(jazzIcons, new BinaryStreamWriter(baos));
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        obj = (List<Musican>) xstream.unmarshal(new BinaryStreamReader(bais));
        assertNotNull(obj);
        assertEquals(xstream.toXML(jazzIcons), xstream.toXML(obj));
    }
}
