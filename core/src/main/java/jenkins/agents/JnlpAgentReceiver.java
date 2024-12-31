package jenkins.agents;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.model.Agent;
import java.security.SecureRandom;
import jenkins.agents.WebSocketAgents;
import jenkins.security.HMACConfidentialKey;
import org.jenkinsci.remoting.engine.JnlpClientDatabase;
import org.jenkinsci.remoting.engine.JnlpConnectionStateListener;

/**
 * Receives incoming agents connecting through the likes of {@link JnlpAgentProtocol4} or {@link WebSocketAgents}.
 *
 * <p>
 * This is useful to establish the communication with other JVMs and use them
 * for different purposes outside {@link Agent}s.

 * <ul>
 * <li> When the {@link jenkins.agents.JnlpAgentReceiver#exists(String)} method is invoked for an agent,
 *      the {@link jenkins.agents.JnlpAgentReceiver#owns(String)} method is called on all the extension points:
 *      if no owner is found an exception is thrown.</li>
 * <li> If owner is found, then the {@link org.jenkinsci.remoting.engine.JnlpConnectionState} lifecycle methods are invoked for all registered {@link JnlpConnectionStateListener}
 *      until the one which changes the state of {@link org.jenkinsci.remoting.engine.JnlpConnectionState} by setting an approval or rejected state is found.
 *      When found, that listener will be set as the owner of the incoming connection event. </li>
 * <li> Subsequent steps of the connection lifecycle are only called on the {@link JnlpAgentReceiver} implementation owner for that connection event.</li>
 * </ul>
 *
 *
 * @author Kohsuke Kawaguchi
 * @since 1.561
 */
public abstract class JnlpAgentReceiver extends JnlpConnectionStateListener implements ExtensionPoint {

    /**
     * This secret value is used as a seed for agents.
     */
    public static final HMACConfidentialKey SLAVE_SECRET =
            new HMACConfidentialKey(JnlpAgentProtocol.class, "secret");

    private static final SecureRandom secureRandom = new SecureRandom();

    public static final JnlpClientDatabase DATABASE = new JnlpAgentDatabase();

    public static ExtensionList<JnlpAgentReceiver> all() {
        return ExtensionList.lookup(JnlpAgentReceiver.class);
    }

    public static boolean exists(String clientName) {
        for (JnlpAgentReceiver receiver : all()) {
            if (receiver.owns(clientName)) {
                return true;
            }
        }
        return false;
    }

    protected abstract boolean owns(String clientName);

    public static String generateCookie() {
        byte[] cookie = new byte[32];
        secureRandom.nextBytes(cookie);
        return Util.toHexString(cookie);
    }

    private static class JnlpAgentDatabase extends JnlpClientDatabase {
        @Override
        public boolean exists(String clientName) {
            return JnlpAgentReceiver.exists(clientName);
        }

        @Override
        public String getSecretOf(@NonNull String clientName) {
            return SLAVE_SECRET.mac(clientName);
        }
    }
}
