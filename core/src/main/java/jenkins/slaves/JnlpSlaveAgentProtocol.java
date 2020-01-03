package jenkins.slaves;

import java.io.IOException;
import java.net.Socket;
import jenkins.AgentProtocol;
import jenkins.security.HMACConfidentialKey;

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
public class JnlpSlaveAgentProtocol extends AgentProtocol {
    /**
     * This secret value is used as a seed for agents.
     */
    public static final HMACConfidentialKey SLAVE_SECRET =
            new HMACConfidentialKey(JnlpSlaveAgentProtocol.class, "secret");

    @Override
    public String getName() {
        return null;
    }

    @Override
    public void handle(Socket socket) throws IOException, InterruptedException {

    }
}
