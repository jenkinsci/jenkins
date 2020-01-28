package hudson;

import jenkins.model.Jenkins;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.io.StringReader;
import java.io.IOException;
import java.net.SocketTimeoutException;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Kohsuke Kawaguchi
 */
public class UDPBroadcastThreadTest {

    @Rule public JenkinsRule j = new JenkinsRule();
    
    @BeforeClass
    public static void forceActivateUDPMulticast() throws Exception {
        // required to be done before JenkinsRule starts the Jenkins instance 
        // as the usage of this port is in the constructor
        updatePort(33848);
    }

    private static void updatePort(int newValue) throws Exception {
        Field portField = UDPBroadcastThread.class.getField("PORT");

        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(portField, portField.getModifiers() & ~Modifier.FINAL);

        portField.setInt(null, newValue);
    }
    
    /**
     * Old unicast based clients should still be able to receive some reply,
     * as we haven't changed the port.
     */
    @Test public void legacy() throws Exception {
        updatePort(33848);
        DatagramSocket s = new DatagramSocket();
        sendQueryTo(s, InetAddress.getLocalHost());
        s.setSoTimeout(15000); // to prevent test hang
        try {
            receiveAndVerify(s);
        } catch (SocketTimeoutException x) {
            Assume.assumeFalse(UDPBroadcastThread.udpHandlingProblem);
            throw x;
        }
    }

    /**
     * Multicast based clients should be able to receive multiple replies.
     */
    // @Test
    // excluded to get the release going
    public void multicast() throws Exception {
        UDPBroadcastThread second = new UDPBroadcastThread(j.jenkins);
        second.start();

        UDPBroadcastThread third = new UDPBroadcastThread(j.jenkins);
        third.start();

        second.ready.block();
        third.ready.block();

        try {
            DatagramSocket s = new DatagramSocket();
            sendQueryTo(s, UDPBroadcastThread.MULTICAST);
            s.setSoTimeout(15000); // to prevent test hang

            // we should at least get two replies since we run two broadcasts
            try {
                // from first (Jenkins one) (order does not matter)
                receiveAndVerify(s);
                // from second
                receiveAndVerify(s);
                // from third
                receiveAndVerify(s);
            } catch (SocketTimeoutException x) {
                Assume.assumeFalse(UDPBroadcastThread.udpHandlingProblem);
                throw x;
            }

            // to fail fast
            s.setSoTimeout(2000);
            try {
                receiveAndVerify(s);
                fail("There should be only 3 listeners");
            } catch (SocketTimeoutException x) {
                // expected to throw
            }
        } finally {
            third.interrupt();
            second.interrupt();
        }
    }

    @Test
    public void ensureTheThreadIsRunningWithSysProp() throws Exception {
        UDPBroadcastThread thread = getPrivateThread(j.jenkins);
        assertNotNull(thread);
        assertTrue(thread.isAlive());
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
        //example: <hudson><version>2.164.4-SNAPSHOT</version><url>http://localhost:23146/jenkins/</url><server-id>be6757793486931ff50c259b66c77704</server-id><slave-port>23149</slave-port></hudson>
        System.out.println(xml);
        Assert.assertThat(xml, Matchers.containsString("<hudson>"));

        // make sure at least this XML parses
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        spf.newSAXParser().parse(new InputSource(new StringReader(xml)),new DefaultHandler());
    }

    private static UDPBroadcastThread getPrivateThread(Jenkins jenkins) throws Exception {
        Field threadField = Jenkins.class.getDeclaredField("udpBroadcastThread");
        threadField.setAccessible(true);

        return (UDPBroadcastThread) threadField.get(jenkins);
    }
}
