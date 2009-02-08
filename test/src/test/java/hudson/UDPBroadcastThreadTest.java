package hudson;

import org.jvnet.hudson.test.HudsonTestCase;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParserFactory;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.io.StringReader;

/**
 * @author Kohsuke Kawaguchi
 */
public class UDPBroadcastThreadTest extends HudsonTestCase {
    public void test1() throws Exception {
        DatagramSocket s = new DatagramSocket();
        DatagramPacket p = new DatagramPacket(new byte[1024],1024);
        p.setAddress(InetAddress.getLocalHost());
        p.setPort(UDPBroadcastThread.PORT);
        s.send(p);
        s.setSoTimeout(5000); // to prevent test hang

        s.receive(p);
        String xml = new String(p.getData(), 0, p.getLength(), "UTF-8");
        System.out.println(xml);

        // make sure at least this XML parses
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        spf.newSAXParser().parse(new InputSource(new StringReader(xml)),new DefaultHandler());
    }
}
