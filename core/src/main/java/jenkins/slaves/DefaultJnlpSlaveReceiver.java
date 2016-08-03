package jenkins.slaves;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.TcpSlaveAgentListener.ConnectionFromCurrentPeer;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Slave;
import hudson.remoting.Channel;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import java.io.OutputStream;
import java.io.PrintWriter;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import jenkins.security.ChannelConfigurator;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.remoting.engine.JnlpConnectionState;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.remoting.protocol.impl.ConnectionRefusalException;

/**
 * Match the name against the agent name and route the incoming JNLP agent as {@link Slave}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.561  
 * @since 1.614 handle() returns true on handshake error as it required in JnlpAgentReceiver.
 */
@Extension
public class DefaultJnlpSlaveReceiver extends JnlpAgentReceiver {

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public boolean owns(String clientName) {
        Computer computer = Jenkins.getInstance().getComputer(clientName);
        return computer != null;
    }

    @Override
    public void afterProperties(@NonNull JnlpConnectionState event) {
        String clientName = event.getProperty(JnlpConnectionState.CLIENT_NAME_KEY);
        SlaveComputer computer = (SlaveComputer) Jenkins.getInstance().getComputer(clientName);
        if (computer == null || !(computer.getLauncher() instanceof JNLPLauncher)) {
            event.reject(new ConnectionRefusalException(String.format("%s is not a JNLP agent", clientName)));
            return;
        }
        Channel ch = computer.getChannel();
        if (ch != null) {
            String cookie = event.getProperty(JnlpConnectionState.COOKIE_KEY);
            if (cookie != null && cookie.equals(ch.getProperty(COOKIE_NAME))) {
                // we think we are currently connected, but this request proves that it's from the party
                // we are supposed to be communicating to. so let the current one get disconnected
                LOGGER.log(Level.INFO, "Disconnecting {0} as we are reconnected from the current peer", clientName);
                try {
                    computer.disconnect(new ConnectionFromCurrentPeer()).get(15, TimeUnit.SECONDS);
                } catch (ExecutionException | TimeoutException | InterruptedException e) {
                    event.reject(new ConnectionRefusalException("Failed to disconnect the current client", e));
                    return;
                }
            } else {
                event.reject(new ConnectionRefusalException(String.format(
                        "%s is already connected to this master. Rejecting this connection.", clientName)));
                return;
            }
        }
        event.approve();
        event.setStash(new State(computer));
    }

    @Override
    public void beforeChannel(@NonNull JnlpConnectionState event) {
        DefaultJnlpSlaveReceiver.State state = event.getStash(DefaultJnlpSlaveReceiver.State.class);
        final SlaveComputer computer = state.getNode();
        final OutputStream log = computer.openLogFile();
        state.setLog(log);
        PrintWriter logw = new PrintWriter(log, true);
        logw.println("JNLP agent connected from " + event.getSocket().getInetAddress());
        for (ChannelConfigurator cc : ChannelConfigurator.all()) {
            cc.onChannelBuilding(event.getChannelBuilder(), computer);
        }
        event.getChannelBuilder().withHeaderStream(log);
        String cookie = event.getProperty(JnlpConnectionState.COOKIE_KEY);
        if (cookie != null) {
            event.getChannelBuilder().withProperty(COOKIE_NAME, cookie);
        }
    }

    @Override
    public void afterChannel(@NonNull JnlpConnectionState event) {
        DefaultJnlpSlaveReceiver.State state = event.getStash(DefaultJnlpSlaveReceiver.State.class);
        final SlaveComputer computer = state.getNode();
        try {
            computer.setChannel(event.getChannel(), state.getLog(), null);
        } catch (IOException | InterruptedException e) {
            PrintWriter logw = new PrintWriter(state.getLog(), true);
            e.printStackTrace(logw);
            IOUtils.closeQuietly(event.getChannel());
        }
    }

    @Override
    public void channelClosed(@NonNull JnlpConnectionState event) {
        final String nodeName = event.getProperty(JnlpConnectionState.CLIENT_NAME_KEY);
        IOException cause = event.getCloseCause();
        if (cause != null) {
            LOGGER.log(Level.WARNING, Thread.currentThread().getName() + " for " + nodeName + " terminated",
                    cause);
        }
    }

    private static class State implements JnlpConnectionState.ListenerState {
        @Nonnull
        private final SlaveComputer node;
        @CheckForNull
        private OutputStream log;

        public State(@Nonnull SlaveComputer node) {
            this.node = node;
        }

        @Nonnull
        public SlaveComputer getNode() {
            return node;
        }

        @CheckForNull
        public OutputStream getLog() {
            return log;
        }

        public void setLog(@Nonnull OutputStream log) {
            this.log = log;
        }
    }

    private String generateCookie() {
        byte[] cookie = new byte[32];
        secureRandom.nextBytes(cookie);
        return Util.toHexString(cookie);
    }

    private static final Logger LOGGER = Logger.getLogger(DefaultJnlpSlaveReceiver.class.getName());

    private static final String COOKIE_NAME = JnlpSlaveAgentProtocol2.class.getName()+".cookie";
}
