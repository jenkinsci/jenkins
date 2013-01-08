package jenkins.slaves;

import hudson.Extension;
import hudson.TcpSlaveAgentListener.ConnectionFromCurrentPeer;
import hudson.Util;
import hudson.remoting.Channel;
import hudson.remoting.Engine;
import hudson.slaves.SlaveComputer;
import hudson.util.IOException2;
import jenkins.model.Jenkins;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

/**
 * {@link JnlpSlaveAgentProtocol} Version 2.
 *
 * <p>
 * This protocol extends the version 1 protocol by adding a per-client cookie,
 * so that we can detect a reconnection from the slave and take appropriate action,
 * when the connection disappered without the master noticing.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.467
 */
@Extension
public class JnlpSlaveAgentProtocol2 extends JnlpSlaveAgentProtocol {
    @Override
    public String getName() {
        return "JNLP2-connect";
    }

    @Override
    public void handle(Socket socket) throws IOException, InterruptedException {
        new Handler2(socket).run();
    }

    protected static class Handler2 extends Handler {
        public Handler2(Socket socket) throws IOException {
            super(socket);
        }

        /**
         * Handles JNLP slave agent connection request (v2 protocol)
         */
        @Override
        protected void run() throws IOException, InterruptedException {
            Properties request = new Properties();
            request.load(new ByteArrayInputStream(in.readUTF().getBytes("UTF-8")));

            final String nodeName = request.getProperty("Node-Name");

            if(!SLAVE_SECRET.mac(nodeName).equals(request.getProperty("Secret-Key"))) {
                error(out, "Unauthorized access");
                return;
            }

            SlaveComputer computer = (SlaveComputer) Jenkins.getInstance().getComputer(nodeName);
            if(computer==null) {
                error(out, "No such slave: "+nodeName);
                return;
            }

            Channel ch = computer.getChannel();
            if(ch !=null) {
                String c = request.getProperty("Cookie");
                if (c!=null && c.equals(ch.getProperty(COOKIE_NAME))) {
                    // we think we are currently connected, but this request proves that it's from the party
                    // we are supposed to be communicating to. so let the current one get disconnected
                    LOGGER.info("Disconnecting "+nodeName+" as we are reconnected from the current peer");
                    try {
                        computer.disconnect(new ConnectionFromCurrentPeer()).get(15, TimeUnit.SECONDS);
                    } catch (ExecutionException e) {
                        throw new IOException2("Failed to disconnect the current client",e);
                    } catch (TimeoutException e) {
                        throw new IOException2("Failed to disconnect the current client",e);
                    }
                } else {
                    error(out, nodeName + " is already connected to this master. Rejecting this connection.");
                    return;
                }
            }

            out.println(Engine.GREETING_SUCCESS);

            Properties response = new Properties();
            String cookie = generateCookie();
            response.put("Cookie",cookie);
            writeResponseHeaders(out, response);

            ch = jnlpConnect(computer);

            ch.setProperty(COOKIE_NAME, cookie);
        }

        private void writeResponseHeaders(PrintWriter out, Properties response) {
            for (Entry<Object, Object> e : response.entrySet()) {
                out.println(e.getKey()+": "+e.getValue());
            }
            out.println(); // empty line to conclude the response header
        }

        private String generateCookie() {
            byte[] cookie = new byte[32];
            new SecureRandom().nextBytes(cookie);
            return Util.toHexString(cookie);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(JnlpSlaveAgentProtocol2.class.getName());

    private static final String COOKIE_NAME = JnlpSlaveAgentProtocol2.class.getName()+".cookie";
}
