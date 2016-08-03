package jenkins.slaves;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Computer;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import jenkins.AgentProtocol;
import org.jenkinsci.Symbol;
import org.jenkinsci.remoting.engine.JnlpClientDatabase;
import org.jenkinsci.remoting.engine.JnlpProtocol2Handler;

/**
 * {@link JnlpSlaveAgentProtocol} Version 2.
 *
 * <p>
 * This protocol extends the version 1 protocol by adding a per-client cookie,
 * so that we can detect a reconnection from the agent and take appropriate action,
 * when the connection disappeared without the master noticing.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.467
 */
@Extension
@Symbol("jnlp2")
public class JnlpSlaveAgentProtocol2 extends AgentProtocol {
    private NioChannelSelector hub;

    private JnlpProtocol2Handler handler;

    @Inject
    public void setHub(NioChannelSelector hub) {
        this.hub = hub;
        this.handler = new JnlpProtocol2Handler(new JnlpClientDatabase() {
            @Override
            public boolean exists(String clientName) {
                return JnlpAgentReceiver.exists(clientName);
            }

            @Override
            public String getSecretOf(@Nonnull String clientName) {
                return JnlpSlaveAgentProtocol.SLAVE_SECRET.mac(clientName);
            }
        }, Computer.threadPoolForRemoting, hub.getHub());
    }

    @Override
    public String getName() {
        return handler.isEnabled() ? handler.getName() : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isOptIn() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        return Messages.JnlpSlaveAgentProtocol2_displayName();
    }

    @Override
    public void handle(Socket socket) throws IOException, InterruptedException {
        handler.handle(socket, new HashMap<String, String>(), ExtensionList.lookup(JnlpAgentReceiver.class));
    }

}
