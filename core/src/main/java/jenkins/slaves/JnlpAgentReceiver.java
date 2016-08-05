package jenkins.slaves;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.model.Slave;
import java.security.SecureRandom;
import javax.annotation.Nonnull;
import org.jenkinsci.remoting.engine.JnlpClientDatabase;
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
        public String getSecretOf(@Nonnull String clientName) {
            return JnlpSlaveAgentProtocol.SLAVE_SECRET.mac(clientName);
        }
    }
}
