package hudson.slaves;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import hudson.remoting.Channel;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ChannelPingerTest {

    @Mock private Channel mockChannel;

    private Map<String, String> savedSystemProperties = new HashMap<>();

    private AutoCloseable mocks;

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @BeforeEach
    void preserveSystemProperties() {
        preserveSystemProperty("hudson.slaves.ChannelPinger.pingInterval");
        preserveSystemProperty("hudson.slaves.ChannelPinger.pingIntervalSeconds");
        preserveSystemProperty("hudson.slaves.ChannelPinger.pingTimeoutSeconds");
    }

    @AfterEach
    void restoreSystemProperties() {
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
    void testDefaults() throws IOException, InterruptedException {
        ChannelPinger channelPinger = new ChannelPinger();
        channelPinger.install(mockChannel, null);

        verify(mockChannel).call(eq(new ChannelPinger.SetUpRemotePing(ChannelPinger.PING_TIMEOUT_SECONDS_DEFAULT,
                                                                      ChannelPinger.PING_INTERVAL_SECONDS_DEFAULT)));
        ChannelPinger.setUpPingForChannel(mockChannel, null, ChannelPinger.PING_TIMEOUT_SECONDS_DEFAULT,
                                          ChannelPinger.PING_INTERVAL_SECONDS_DEFAULT, true);
    }

    @Test
    void testFromSystemProperties() throws IOException, InterruptedException {
        System.setProperty("hudson.slaves.ChannelPinger.pingTimeoutSeconds", "42");
        System.setProperty("hudson.slaves.ChannelPinger.pingIntervalSeconds", "73");

        ChannelPinger channelPinger = new ChannelPinger();
        channelPinger.install(mockChannel, null);

        verify(mockChannel).call(new ChannelPinger.SetUpRemotePing(42, 73));
        ChannelPinger.setUpPingForChannel(mockChannel, null, 42, 73, true);
    }

    @Test
    void testFromOldSystemProperty() throws IOException, InterruptedException {
        System.setProperty("hudson.slaves.ChannelPinger.pingInterval", "7");

        ChannelPinger channelPinger = new ChannelPinger();
        channelPinger.install(mockChannel, null);

        verify(mockChannel).call(eq(new ChannelPinger.SetUpRemotePing(ChannelPinger.PING_TIMEOUT_SECONDS_DEFAULT, 420)));
        ChannelPinger.setUpPingForChannel(mockChannel, null, ChannelPinger.PING_TIMEOUT_SECONDS_DEFAULT, 420, true);
    }

    @Test
    void testNewSystemPropertyTrumpsOld() throws IOException, InterruptedException {
        System.setProperty("hudson.slaves.ChannelPinger.pingIntervalSeconds", "73");
        System.setProperty("hudson.slaves.ChannelPinger.pingInterval", "7");

        ChannelPinger channelPinger = new ChannelPinger();
        channelPinger.install(mockChannel, null);

        verify(mockChannel).call(eq(new ChannelPinger.SetUpRemotePing(ChannelPinger.PING_TIMEOUT_SECONDS_DEFAULT, 73)));
        ChannelPinger.setUpPingForChannel(mockChannel, null, ChannelPinger.PING_TIMEOUT_SECONDS_DEFAULT, 73, true);
    }

    @Test
    void testSetUpRemotePingEquality() {
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
