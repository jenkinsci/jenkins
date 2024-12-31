package jenkins.agents;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Functions;
import hudson.TcpAgentListener.ConnectionFromCurrentPeer;
import hudson.model.Computer;
import hudson.model.Agent;
import hudson.remoting.Channel;
import hudson.remoting.ChannelClosedException;
import hudson.agents.ComputerLauncher;
import hudson.agents.ComputerLauncherFilter;
import hudson.agents.DelegatingComputerLauncher;
import hudson.agents.JNLPLauncher;
import hudson.agents.AgentComputer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.security.ChannelConfigurator;
import jenkins.util.SystemProperties;
import org.jenkinsci.remoting.engine.JnlpConnectionState;
import org.jenkinsci.remoting.protocol.impl.ConnectionRefusalException;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Match the name against the agent name and route the incoming agent as {@link Agent}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.561
 * @since 1.614 handle() returns true on handshake error as it required in {@link JnlpAgentReceiver}.
 */
@Extension
public class DefaultJnlpAgentReceiver extends JnlpAgentReceiver {

    /**
     * Disables strict verification of connections. Turn this on if you have plugins that incorrectly extend
     * {@link ComputerLauncher} when then should have extended {@link DelegatingComputerLauncher}
     *
     * @since 2.28
     */
    @Restricted(NoExternalUse.class)
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "Accessible via System Groovy Scripts")
    public static boolean disableStrictVerification =
            SystemProperties.getBoolean(DefaultJnlpAgentReceiver.class.getName() + ".disableStrictVerification");


    @Override
    public boolean owns(String clientName) {
        Computer computer = Jenkins.get().getComputer(clientName);
        return computer != null;
    }

    private static ComputerLauncher getDelegate(ComputerLauncher launcher) {
        try {
            Method getDelegate = launcher.getClass().getMethod("getDelegate");
            if (ComputerLauncher.class.isAssignableFrom(getDelegate.getReturnType())) {
                return (ComputerLauncher) getDelegate.invoke(launcher);
            }
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            // ignore
        }
        try {
            Method getLauncher = launcher.getClass().getMethod("getLauncher");
            if (ComputerLauncher.class.isAssignableFrom(getLauncher.getReturnType())) {
                return (ComputerLauncher) getLauncher.invoke(launcher);
            }
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            // ignore
        }
        return null;
    }

    @Override
    public void afterProperties(@NonNull JnlpConnectionState event) {
        String clientName = event.getProperty(JnlpConnectionState.CLIENT_NAME_KEY);
        AgentComputer computer = (AgentComputer) Jenkins.get().getComputer(clientName);
        if (computer == null) {
            event.reject(new ConnectionRefusalException(String.format("%s is not an inbound agent", clientName)));
            return;
        }
        ComputerLauncher launcher = computer.getLauncher();
        while (!(launcher instanceof JNLPLauncher)) {
            ComputerLauncher l;
            if (launcher instanceof DelegatingComputerLauncher) {
                launcher = ((DelegatingComputerLauncher) launcher).getLauncher();
            } else if (launcher instanceof ComputerLauncherFilter) {
                launcher = ((ComputerLauncherFilter) launcher).getCore();
            } else if (null != (l = getDelegate(launcher))) {  // TODO remove when all plugins are fixed
                LOGGER.log(Level.INFO, "Connecting {0} as an inbound agent where the launcher {1} does not mark "
                                + "itself correctly as being an inbound agent",
                        new Object[]{clientName, computer.getLauncher().getClass()});
                launcher = l;
            } else {
                if (disableStrictVerification) {
                    LOGGER.log(Level.WARNING, "Connecting {0} as an inbound agent where the launcher {1} does not mark "
                            + "itself correctly as being an inbound agent",
                            new Object[]{clientName, computer.getLauncher().getClass()});
                    break;
                } else {
                    LOGGER.log(Level.WARNING, "Rejecting connection to {0} from {1} as an inbound agent as the launcher "
                                    + "{2} does not extend JNLPLauncher or does not implement "
                                    + "DelegatingComputerLauncher with a delegation chain leading to a JNLPLauncher. "
                                    + "Set system property "
                                    + "jenkins.agents.DefaultJnlpAgentReceiver.disableStrictVerification=true to allow"
                                    + "connections until the plugin has been fixed.",
                            new Object[]{clientName, event.getRemoteEndpointDescription(), computer.getLauncher().getClass()});
                    event.reject(new ConnectionRefusalException(String.format("%s is not an inbound agent", clientName)));
                    return;
                }
            }
        }
        Channel ch = computer.getChannel();
        if (ch != null) {
            String cookie = event.getProperty(JnlpConnectionState.COOKIE_KEY);
            String channelCookie = (String) ch.getProperty(JnlpConnectionState.COOKIE_KEY);
            if (cookie != null && channelCookie != null && MessageDigest.isEqual(cookie.getBytes(StandardCharsets.UTF_8), channelCookie.getBytes(StandardCharsets.UTF_8))) {
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
                        "%s is already connected to this controller. Rejecting this connection.", clientName)));
                return;
            }
        }
        event.approve();
        event.setStash(new State(computer));
    }

    @Override
    @SuppressFBWarnings(value = "OS_OPEN_STREAM", justification = "Closed by hudson.agents.AgentComputer#kill")
    public void beforeChannel(@NonNull JnlpConnectionState event) {
        DefaultJnlpAgentReceiver.State state = event.getStash(DefaultJnlpAgentReceiver.State.class);
        final AgentComputer computer = state.getNode();
        final OutputStream log = computer.openLogFile();
        state.setLog(log);
        PrintWriter logw = new PrintWriter(new OutputStreamWriter(log, /* TODO switch agent logs to UTF-8 */ Charset.defaultCharset()), true); // Closed by hudson.agents.AgentComputer#kill
        logw.println("Inbound agent connected from " + event.getRemoteEndpointDescription());
        for (ChannelConfigurator cc : ChannelConfigurator.all()) {
            cc.onChannelBuilding(event.getChannelBuilder(), computer);
        }
        event.getChannelBuilder().withHeaderStream(log);
        String cookie = event.getProperty(JnlpConnectionState.COOKIE_KEY);
        if (cookie != null) {
            event.getChannelBuilder().withProperty(JnlpConnectionState.COOKIE_KEY, cookie);
        }
    }

    @Override
    public void afterChannel(@NonNull JnlpConnectionState event) {
        DefaultJnlpAgentReceiver.State state = event.getStash(DefaultJnlpAgentReceiver.State.class);
        final AgentComputer computer = state.getNode();
        try {
            computer.setChannel(event.getChannel(), state.getLog(), null);
        } catch (IOException | InterruptedException e) {
            PrintWriter logw = new PrintWriter(new OutputStreamWriter(state.getLog(), /* TODO switch agent logs to UTF-8 */ Charset.defaultCharset()), true);
            Functions.printStackTrace(e, logw);
            try {
                event.getChannel().close();
            } catch (IOException x) {
                LOGGER.log(Level.WARNING, null, x);
            }
        }
    }

    @Override
    public void channelClosed(@NonNull JnlpConnectionState event) {
        final String nodeName = event.getProperty(JnlpConnectionState.CLIENT_NAME_KEY);
        IOException cause = event.getCloseCause();
        if (cause instanceof ClosedChannelException || cause instanceof ChannelClosedException) {
            LOGGER.log(Level.INFO, "{0} for {1} terminated: {2}", new Object[] {Thread.currentThread().getName(), nodeName, cause});
        } else if (cause != null) {
            LOGGER.log(Level.WARNING, Thread.currentThread().getName() + " for " + nodeName + " terminated",
                    cause);
        }
    }

    private static class State implements JnlpConnectionState.ListenerState {
        @NonNull
        private final AgentComputer node;
        @CheckForNull
        private OutputStream log;

        State(@NonNull AgentComputer node) {
            this.node = node;
        }

        @NonNull
        public AgentComputer getNode() {
            return node;
        }

        @CheckForNull
        public OutputStream getLog() {
            return log;
        }

        public void setLog(@NonNull OutputStream log) {
            this.log = log;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(DefaultJnlpAgentReceiver.class.getName());

}
