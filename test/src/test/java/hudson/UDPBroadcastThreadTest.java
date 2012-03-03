package hudson;

import org.jvnet.hudson.test.HudsonTestCase;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.io.StringReader;
import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class UDPBroadcastThreadTest extends HudsonTestCase {
    /**
     * Old unicast based clients should still be able to receive some reply,
     * as we haven't changed the port.
     */
    public void testLegacy() throws Exception {
        DatagramSocket s = new DatagramSocket();
        sendQueryTo(s, InetAddress.getLocalHost());
        s.setSoTimeout(15000); // to prevent test hang
        receiveAndVerify(s);
    }

    /**
     * Multicast based clients should be able to receive multiple replies.
     */
    public void testMulticast() throws Exception {
        UDPBroadcastThread second = new UDPBroadcastThread(hudson);
        second.start();
        second.ready.block();

        try {
            DatagramSocket s = new DatagramSocket();
            sendQueryTo(s, UDPBroadcastThread.MULTICAST);
            s.setSoTimeout(15000); // to prevent test hang

            // we should at least get two replies since we run two broadcasts
            receiveAndVerify(s);
            receiveAndVerify(s);
        } finally {
            second.interrupt();
        }
    }

    private void sendQueryTo(DatagramSocket s, InetAddress dest) throws IOException {
        DatagramPacket p = new DatagramPacket(new byte[1024],1024);
        p.setAddress(dest);
        p.setPort(UDPBroadcastThread.PORT);
        s.send(p);
    }

    /**
     * Reads a reply from the socket and makes sure its shape is in order.
     */
    private void receiveAndVerify(DatagramSocket s) throws IOException, SAXException, ParserConfigurationException {
        DatagramPacket p = new DatagramPacket(new byte[1024],1024);
        s.receive(p);
        String xml = new String(p.getData(), 0, p.getLength(), "UTF-8");
        System.out.println(xml);

        // make sure at least this XML parses
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        spf.newSAXParser().parse(new InputSource(new StringReader(xml)),new DefaultHandler());
    }
}
