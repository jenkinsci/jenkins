package jenkins.slaves;

import hudson.Extension;
import hudson.TcpSlaveAgentListener.ConnectionFromCurrentPeer;
import hudson.Util;
import hudson.model.Slave;
import hudson.remoting.Channel;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

/**
 * Match the name against the slave name and route the incoming JNLP agent as {@link Slave}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.561
 */
@Extension
public class DefaultJnlpSlaveReceiver extends JnlpAgentReceiver {
    @Override
    public boolean handle(String nodeName, JnlpSlaveHandshake handshake) throws IOException, InterruptedException {
        SlaveComputer computer = (SlaveComputer) Jenkins.getInstance().getComputer(nodeName);

        if(computer==null) {
            return false;
        }

        // Validate the slave secret matches.
        if (!computer.getJnlpMac().equals(handshake.getRequestProperty("Secret-Key"))) {
            return false;
        }

        Channel ch = computer.getChannel();
        if(ch !=null) {
            String c = handshake.getRequestProperty("Cookie");
            if (c!=null && c.equals(ch.getProperty(COOKIE_NAME))) {
                // we think we are currently connected, but this request proves that it's from the party
                // we are supposed to be communicating to. so let the current one get disconnected
                LOGGER.info("Disconnecting "+nodeName+" as we are reconnected from the current peer");
                try {
                    computer.disconnect(new ConnectionFromCurrentPeer()).get(15, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    throw new IOException("Failed to disconnect the current client",e);
                } catch (TimeoutException e) {
                    throw new IOException("Failed to disconnect the current client",e);
                }
            } else {
                handshake.error(nodeName + " is already connected to this master. Rejecting this connection.");
                return false;
            }
        }

        Properties response = new Properties();
        String cookie = generateCookie();
        response.put("Cookie",cookie);
        handshake.success(response);

        // this cast is leaking abstraction
        JnlpSlaveAgentProtocol2.Handler handler = (JnlpSlaveAgentProtocol2.Handler)handshake;

        ch = handler.jnlpConnect(computer);

        ch.setProperty(COOKIE_NAME, cookie);

        return true;
    }

    private String generateCookie() {
        byte[] cookie = new byte[32];
        new SecureRandom().nextBytes(cookie);
        return Util.toHexString(cookie);
    }

    private static final Logger LOGGER = Logger.getLogger(DefaultJnlpSlaveReceiver.class.getName());

    private static final String COOKIE_NAME = JnlpSlaveAgentProtocol2.class.getName()+".cookie";
}
