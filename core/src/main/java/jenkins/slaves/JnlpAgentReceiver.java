package jenkins.slaves;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Slave;
import org.jenkinsci.remoting.engine.JnlpConnectionStateListener;

/**
 * Receives incoming agents connecting through {@link JnlpSlaveAgentProtocol2}, {@link JnlpSlaveAgentProtocol3}, {@link JnlpSlaveAgentProtocol4}.
 *
 * <p>
 * This is useful to establish the communication with other JVMs and use them
 * for different purposes outside {@link Slave}s.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.561
 */
public abstract class JnlpAgentReceiver extends JnlpConnectionStateListener implements ExtensionPoint {

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
}
