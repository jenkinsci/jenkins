package jenkins.slaves;

import hudson.AbortException;
import hudson.Extension;
import hudson.model.Computer;
import hudson.remoting.Channel;
import hudson.remoting.ChannelBuilder;
import hudson.slaves.SlaveComputer;
import jenkins.AgentProtocol;
import jenkins.model.Jenkins;
import jenkins.security.ChannelConfigurator;
import org.jenkinsci.remoting.engine.JnlpServer3Handshake;
import org.jenkinsci.remoting.nio.NioChannelHub;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Master-side implementation for JNLP3-connect protocol.
 *
 * <p>@see {@link org.jenkinsci.remoting.engine.JnlpProtocol3} for more details.
 *
 * @author Akshay Dayal
 */
@Extension
public class JnlpSlaveAgentProtocol3 extends AgentProtocol {
    @Inject
    NioChannelSelector hub;

    @Override
    public String getName() {
        return "JNLP3-connect";
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
}
