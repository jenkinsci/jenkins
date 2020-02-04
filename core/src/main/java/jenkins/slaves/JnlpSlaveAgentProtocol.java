package jenkins.slaves;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Computer;
import java.io.IOException;
import java.net.Socket;
import java.util.Collections;
import java.util.logging.Logger;
import javax.inject.Inject;
import jenkins.AgentProtocol;
import jenkins.security.HMACConfidentialKey;
import org.jenkinsci.Symbol;
import org.jenkinsci.remoting.engine.JnlpConnectionState;
import org.jenkinsci.remoting.engine.JnlpProtocol1Handler;

/**
 * {@link AgentProtocol} that accepts connection from agents.
 *
 * <h2>Security</h2>
 * <p>
 * Once connected, remote agents can send in commands to be
 * executed on the master, so in a way this is like an rsh service.
 * Therefore, it is important that we reject connections from
 * unauthorized remote agents.
 *
 * <p>
 * We do this by computing HMAC of the agent name.
 * This code is sent to the agent inside the {@code .jnlp} file
 * (this file itself is protected by HTTP form-based authentication that
 * we use everywhere else in Jenkins), and the agent sends this
 * token back when it connects to the master.
 * Unauthorized agents can't access the protected {@code .jnlp} file,
 * so it can't impersonate a valid agent.
 *
 * <p>
 * We don't want to force the inbound agents to be restarted
 * whenever the server restarts, so right now this secret master key
 * is generated once and used forever, which makes this whole scheme
 * less secure.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.467
 */
@Extension
@Symbol("jnlp")
public class JnlpSlaveAgentProtocol extends AgentProtocol {
    /**
     * Our logger
     */
    private static final Logger LOGGER = Logger.getLogger(JnlpSlaveAgentProtocol.class.getName());
    /**
     * This secret value is used as a seed for agents.
     */
    public static final HMACConfidentialKey SLAVE_SECRET =
            new HMACConfidentialKey(JnlpSlaveAgentProtocol.class, "secret");

    private NioChannelSelector hub;

    private JnlpProtocol1Handler handler;

    @Inject
    public void setHub(NioChannelSelector hub) {
        this.hub = hub;
        this.handler = new JnlpProtocol1Handler(JnlpAgentReceiver.DATABASE, Computer.threadPoolForRemoting,
                hub.getHub(), true);
    }

    @Override
    public boolean isOptIn() {
        return true;
    }

    @Override
    public boolean isDeprecated() {
        return true;
    }

    @Override
    public String getName() {
        return handler.isEnabled() ? handler.getName() : null;
    }

    @Override
    public String getDisplayName() {
        return Messages.JnlpSlaveAgentProtocol_displayName();
    }

    @Override
    public void handle(Socket socket) throws IOException, InterruptedException {
        handler.handle(socket,
                Collections.singletonMap(JnlpConnectionState.COOKIE_KEY, JnlpAgentReceiver.generateCookie()),
                ExtensionList.lookup(JnlpAgentReceiver.class));
    }


}
