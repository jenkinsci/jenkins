package jenkins.slaves;

import hudson.AbortException;
import hudson.Extension;
import hudson.Util;
import hudson.model.Computer;
import hudson.remoting.Channel;
import hudson.remoting.ChannelBuilder;
import hudson.slaves.SlaveComputer;
import jenkins.AgentProtocol;
import jenkins.model.Jenkins;
import jenkins.security.ChannelConfigurator;
import org.jenkinsci.remoting.engine.JnlpServer3Handshake;
import org.jenkinsci.remoting.nio.NioChannelHub;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.SystemProperties;

/**
 * Master-side implementation for JNLP3-connect protocol.
 *
 * <p>@see {@link org.jenkinsci.remoting.engine.JnlpProtocol3} for more details.
 *
 * @author Akshay Dayal
 * @since 1.XXX
 */
@Extension
public class JnlpSlaveAgentProtocol3 extends AgentProtocol {
    @Inject
    NioChannelSelector hub;

    @Override
    public String getName() {
        if (ENABLED)    return "JNLP3-connect";
        else            return "JNLP3-disabled";
    }

    @Override
    public void handle(Socket socket) throws IOException, InterruptedException {
        new Handler(hub.getHub(), socket).run();
    }

    static class Handler extends JnlpServer3Handshake {
        private SlaveComputer computer;
        private PrintWriter logw;
        private OutputStream log;

        public Handler(NioChannelHub hub, Socket socket) throws IOException {
            super(hub, Computer.threadPoolForRemoting, socket);
        }

        protected void run() throws IOException, InterruptedException {
            try {
                Channel channel = connect();

                computer.setChannel(channel, log,
                        new Channel.Listener() {
                            @Override
                            public void onClosed(Channel channel, IOException cause) {
                                if (cause != null)
                                    LOGGER.log(Level.WARNING,
                                            Thread.currentThread().getName() + " for + " +
                                                    getNodeName() + " terminated", cause);
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    // Do nothing.
                                }
                            }
                        });
            } catch (AbortException e) {
                logw.println(e.getMessage());
                logw.println("Failed to establish the connection with the agent");
                throw e;
            } catch (IOException e) {
                logw.println("Failed to establish the connection with the agent " + getNodeName());
                e.printStackTrace(logw);
                throw e;
            }
        }

        @Override
        public ChannelBuilder createChannelBuilder(String nodeName) {
            log = computer.openLogFile();
            logw = new PrintWriter(log,true);
            logw.println("JNLP agent connected from " + socket.getInetAddress());

            ChannelBuilder cb = super.createChannelBuilder(nodeName).withHeaderStream(log);

            for (ChannelConfigurator cc : ChannelConfigurator.all()) {
                cc.onChannelBuilding(cb, computer);
            }

            return cb;
        }

        @Override
        protected String getNodeSecret(String nodeName) throws Failure {
            computer = (SlaveComputer) Jenkins.getInstance().getComputer(nodeName);
            if (computer == null) {
                throw new Failure("Agent trying to register for invalid node: " + nodeName);
            }
            return computer.getJnlpMac();
        }

    }

    private static final Logger LOGGER = Logger.getLogger(JnlpSlaveAgentProtocol3.class.getName());

    /**
     * Flag to control the activation of JNLP3 protocol.
     * This feature is being A/B tested right now.
     *
     * <p>
     * Once this will be on by default, the flag and this field will disappear. The system property is
     * an escape hatch for those who hit any issues and those who are trying this out.
     */
    @Restricted(NoExternalUse.class)
    public static boolean ENABLED;

    static {
        String propName = JnlpSlaveAgentProtocol3.class.getName() + ".enabled";
        String propertyString = SystemProperties.getString(propName);
        if (propertyString != null)
            ENABLED = SystemProperties.getBoolean(propName);
        else {
            byte hash = Util.fromHexString(Jenkins.getActiveInstance().getLegacyInstanceId())[0];
            ENABLED = (hash%10)==0;
        }
    }
}
