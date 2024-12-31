package hudson.agents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import hudson.remoting.Channel;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ChannelPingerTest {

    @Mock private Channel mockChannel;

    private Map<String, String> savedSystemProperties = new HashMap<>();

    private AutoCloseable mocks;

    @After
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Before
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @Before
    public void preserveSystemProperties() {
        preserveSystemProperty("hudson.agents.ChannelPinger.pingInterval");
        preserveSystemProperty("hudson.agents.ChannelPinger.pingIntervalSeconds");
        preserveSystemProperty("hudson.agents.ChannelPinger.pingTimeoutSeconds");
    }

    @After
    public void restoreSystemProperties() {
        for (Map.Entry<String, String> entry : savedSystemProperties.entrySet()) {
            if (entry.getValue() != null) {
                System.setProperty(entry.getKey(), entry.getValue());
            } else {
                System.clearProperty(entry.getKey());
            }
        }
    }

    private void preserveSystemProperty(String propertyName) {
        savedSystemProperties.put(propertyName, System.getProperty(propertyName));
        System.clearProperty(propertyName);
    }

    @Test
    public void testDefaults() throws IOException, InterruptedException {
        ChannelPinger channelPinger = new ChannelPinger();
        channelPinger.install(mockChannel, null);

        verify(mockChannel).call(eq(new ChannelPinger.SetUpRemotePing(ChannelPinger.PING_TIMEOUT_SECONDS_DEFAULT,
                                                                      ChannelPinger.PING_INTERVAL_SECONDS_DEFAULT)));
        ChannelPinger.setUpPingForChannel(mockChannel, null, ChannelPinger.PING_TIMEOUT_SECONDS_DEFAULT,
                                          ChannelPinger.PING_INTERVAL_SECONDS_DEFAULT, true);
    }

    @Test
    public void testFromSystemProperties() throws IOException, InterruptedException {
        System.setProperty("hudson.agents.ChannelPinger.pingTimeoutSeconds", "42");
        System.setProperty("hudson.agents.ChannelPinger.pingIntervalSeconds", "73");

        ChannelPinger channelPinger = new ChannelPinger();
        channelPinger.install(mockChannel, null);

        verify(mockChannel).call(new ChannelPinger.SetUpRemotePing(42, 73));
        ChannelPinger.setUpPingForChannel(mockChannel, null, 42, 73, true);
    }

    @Test
    public void testFromOldSystemProperty() throws IOException, InterruptedException {
        System.setProperty("hudson.agents.ChannelPinger.pingInterval", "7");

        ChannelPinger channelPinger = new ChannelPinger();
        channelPinger.install(mockChannel, null);

        verify(mockChannel).call(eq(new ChannelPinger.SetUpRemotePing(ChannelPinger.PING_TIMEOUT_SECONDS_DEFAULT, 420)));
        ChannelPinger.setUpPingForChannel(mockChannel, null, ChannelPinger.PING_TIMEOUT_SECONDS_DEFAULT, 420, true);
    }

    @Test
    public void testNewSystemPropertyTrumpsOld() throws IOException, InterruptedException {
        System.setProperty("hudson.agents.ChannelPinger.pingIntervalSeconds", "73");
        System.setProperty("hudson.agents.ChannelPinger.pingInterval", "7");

        ChannelPinger channelPinger = new ChannelPinger();
        channelPinger.install(mockChannel, null);

        verify(mockChannel).call(eq(new ChannelPinger.SetUpRemotePing(ChannelPinger.PING_TIMEOUT_SECONDS_DEFAULT, 73)));
        ChannelPinger.setUpPingForChannel(mockChannel, null, ChannelPinger.PING_TIMEOUT_SECONDS_DEFAULT, 73, true);
    }

    @Test
    public void testSetUpRemotePingEquality() {
        ChannelPinger.SetUpRemotePing pinger1a = new ChannelPinger.SetUpRemotePing(1, 2);
        ChannelPinger.SetUpRemotePing pinger1b = new ChannelPinger.SetUpRemotePing(1, 2);
        ChannelPinger.SetUpRemotePing pinger2a = new ChannelPinger.SetUpRemotePing(2, 3);
        ChannelPinger.SetUpRemotePing pinger2b = new ChannelPinger.SetUpRemotePing(2, 3);

        for (ChannelPinger.SetUpRemotePing item : Arrays.asList(pinger1a, pinger1b, pinger2a, pinger2b)) {
            assertNotEquals(null, item);
            assertEquals(item, item);
            assertEquals(item.hashCode(), item.hashCode());
        }

        assertEquals(pinger1a, pinger1b);
        assertEquals(pinger1b, pinger1a);
        assertEquals(pinger1a.hashCode(), pinger1b.hashCode());
        assertNotEquals(pinger1a, pinger2a);
        assertNotEquals(pinger1a, pinger2b);
        assertNotEquals(pinger1b, pinger2a);
        assertNotEquals(pinger1b, pinger2b);

        assertEquals(pinger2a, pinger2b);
        assertEquals(pinger2b, pinger2a);
        assertEquals(pinger2a.hashCode(), pinger2b.hashCode());
        assertNotEquals(pinger2a, pinger1a);
        assertNotEquals(pinger2a, pinger1b);
        assertNotEquals(pinger2b, pinger1a);
        assertNotEquals(pinger2b, pinger1b);
    }
}
