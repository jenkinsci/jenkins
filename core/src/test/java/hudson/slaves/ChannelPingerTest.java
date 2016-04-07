package hudson.slaves;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyNoMoreInteractions;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;

import com.google.common.testing.EqualsTester;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import hudson.remoting.Channel;

import java.util.Map;
import java.util.HashMap;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ ChannelPinger.class })
public class ChannelPingerTest {

    @Mock private Channel mockChannel;

    private Map<String, String> savedSystemProperties = new HashMap<String, String>();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mockStatic(ChannelPinger.class);
    }

    @Before
    public void preserveSystemProperties() throws Exception {
        preserveSystemProperty("hudson.slaves.ChannelPinger.pingInterval");
        preserveSystemProperty("hudson.slaves.ChannelPinger.pingIntervalSeconds");
        preserveSystemProperty("hudson.slaves.ChannelPinger.pingTimeoutSeconds");
    }

    @After
    public void restoreSystemProperties() throws Exception {
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
    public void testDefaults() throws Exception {
        ChannelPinger channelPinger = new ChannelPinger();
        channelPinger.install(mockChannel);

        verify(mockChannel).call(eq(new ChannelPinger.SetUpRemotePing(240, 300)));
        verifyStatic();
        ChannelPinger.setUpPingForChannel(mockChannel, 240, 300);
    }

    @Test
    public void testFromSystemProperties() throws Exception {
        System.setProperty("hudson.slaves.ChannelPinger.pingTimeoutSeconds", "42");
        System.setProperty("hudson.slaves.ChannelPinger.pingIntervalSeconds", "73");

        ChannelPinger channelPinger = new ChannelPinger();
        channelPinger.install(mockChannel);

        verify(mockChannel).call(new ChannelPinger.SetUpRemotePing(42, 73));
        verifyStatic();
        ChannelPinger.setUpPingForChannel(mockChannel, 42, 73);
    }

    @Test
    public void testFromOldSystemProperty() throws Exception {
        System.setProperty("hudson.slaves.ChannelPinger.pingInterval", "7");

        ChannelPinger channelPinger = new ChannelPinger();
        channelPinger.install(mockChannel);

        verify(mockChannel).call(eq(new ChannelPinger.SetUpRemotePing(240, 420)));
        verifyStatic();
        ChannelPinger.setUpPingForChannel(mockChannel, 240, 420);
    }

    @Test
    public void testNewSystemPropertyTrumpsOld() throws Exception {
        System.setProperty("hudson.slaves.ChannelPinger.pingIntervalSeconds", "73");
        System.setProperty("hudson.slaves.ChannelPinger.pingInterval", "7");

        ChannelPinger channelPinger = new ChannelPinger();
        channelPinger.install(mockChannel);

        verify(mockChannel).call(eq(new ChannelPinger.SetUpRemotePing(240, 73)));
        verifyStatic();
        ChannelPinger.setUpPingForChannel(mockChannel, 240, 73);
    }

    @Test
    public void testSetUpRemotePingEquality() {
         new EqualsTester()
             .addEqualityGroup(new ChannelPinger.SetUpRemotePing(1, 2), new ChannelPinger.SetUpRemotePing(1, 2))
             .addEqualityGroup(new ChannelPinger.SetUpRemotePing(2, 3), new ChannelPinger.SetUpRemotePing(2, 3))
             .testEquals();
    }
}
