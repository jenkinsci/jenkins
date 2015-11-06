package jenkins.slaves;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

import hudson.TcpSlaveAgentListener.ConnectionFromCurrentPeer;
import hudson.remoting.Channel;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Jenkins.class)
public class DefaultJnlpSlaveReceiverTest {

    @Mock private Jenkins mockJenkins;
    @Mock private SlaveComputer mockComputer;
    @Mock private Channel mockChannel;
    @Mock private JnlpSlaveAgentProtocol2.Handler mockHandshake;
    @Mock private Future mockFuture;

    private DefaultJnlpSlaveReceiver receiver;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mockStatic(Jenkins.class);
        when(Jenkins.getInstance()).thenReturn(mockJenkins);

        receiver = new DefaultJnlpSlaveReceiver();
    }

    @Test
    public void testHandle() throws Exception {
        when(mockJenkins.getComputer("node")).thenReturn(mockComputer);
        when(mockComputer.getChannel()).thenReturn(null);
        when(mockChannel.getProperty(any(String.class))).thenReturn("some cookie");
        when(mockHandshake.jnlpConnect(mockComputer)).thenReturn(mockChannel);

        assertTrue(receiver.handle("node", mockHandshake));
        verify(mockHandshake).success(any(Properties.class));
        verify(mockChannel).setProperty(any(String.class), any(String.class));
    }

    @Test
    public void testHandleWithInvalidNode() throws Exception {
        when(mockJenkins.getComputer("bogus-node")).thenReturn(null);

        assertFalse(receiver.handle("bogus-node", mockHandshake));
    }

    @Test
    public void testHandleTakeover() throws Exception {
        when(mockJenkins.getComputer("node")).thenReturn(mockComputer);
        when(mockComputer.getChannel()).thenReturn(mockChannel);
        when(mockHandshake.getRequestProperty(any(String.class))).thenReturn("some cookie");
        when(mockChannel.getProperty(any(String.class))).thenReturn("some cookie");
        when(mockComputer.disconnect(any(ConnectionFromCurrentPeer.class))).thenReturn(mockFuture);
        when(mockHandshake.jnlpConnect(mockComputer)).thenReturn(mockChannel);

        assertTrue(receiver.handle("node", mockHandshake));
        verify(mockFuture).get(15, TimeUnit.SECONDS);
        verify(mockHandshake).success(any(Properties.class));
        verify(mockChannel).setProperty(any(String.class), any(String.class));
    }

    @Test
    public void testHandleTakeoverFailedDisconnect() throws Exception {
        when(mockJenkins.getComputer("node")).thenReturn(mockComputer);
        when(mockComputer.getChannel()).thenReturn(mockChannel);
        when(mockHandshake.getRequestProperty(any(String.class))).thenReturn("some cookie");
        when(mockChannel.getProperty(any(String.class))).thenReturn("some cookie");
        when(mockComputer.disconnect(any(ConnectionFromCurrentPeer.class))).thenReturn(mockFuture);
        when(mockFuture.get(15, TimeUnit.SECONDS)).thenThrow(new ExecutionException(null));

        try {
            receiver.handle("node", mockHandshake);
            fail();
        } catch (IOException e) {
            // good
        }
    }

    @Test
    public void testHandleTakeoverTimedOut() throws Exception {
        when(mockJenkins.getComputer("node")).thenReturn(mockComputer);
        when(mockComputer.getChannel()).thenReturn(mockChannel);
        when(mockHandshake.getRequestProperty(any(String.class))).thenReturn("some cookie");
        when(mockChannel.getProperty(any(String.class))).thenReturn("some cookie");
        when(mockComputer.disconnect(any(ConnectionFromCurrentPeer.class))).thenReturn(mockFuture);
        when(mockFuture.get(15, TimeUnit.SECONDS)).thenThrow(new TimeoutException());

        try {
            receiver.handle("node", mockHandshake);
            fail();
        } catch (IOException e) {
            // good
        }
    }

    @Test
    public void testHandleAttemptTakeoverWithNullCookie() throws Exception {
        when(mockJenkins.getComputer("node")).thenReturn(mockComputer);
        when(mockComputer.getChannel()).thenReturn(mockChannel);
        when(mockHandshake.getRequestProperty(any(String.class))).thenReturn(null);
        when(mockChannel.getProperty(any(String.class))).thenReturn("some cookie");

        assertTrue(receiver.handle("node", mockHandshake));
        verify(mockHandshake).error(any(String.class));
    }

    @Test
    public void testHandleAttemptTakeoverWithInvalidCookie() throws Exception {
        when(mockJenkins.getComputer("node")).thenReturn(mockComputer);
        when(mockComputer.getChannel()).thenReturn(mockChannel);
        when(mockHandshake.getRequestProperty(any(String.class))).thenReturn("bogus cookie");
        when(mockChannel.getProperty(any(String.class))).thenReturn("some cookie");

        assertTrue(receiver.handle("node", mockHandshake));
        verify(mockHandshake).error(any(String.class));
    }
}
