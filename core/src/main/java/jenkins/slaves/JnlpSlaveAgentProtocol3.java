package jenkins.slaves;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.Computer;
import java.io.IOException;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import jenkins.AgentProtocol;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import org.jenkinsci.Symbol;
import org.jenkinsci.remoting.engine.JnlpClientDatabase;
import org.jenkinsci.remoting.engine.JnlpConnectionState;
import org.jenkinsci.remoting.engine.JnlpProtocol3Handler;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Master-side implementation for JNLP3-connect protocol.
 *
 * <p>@see {@link org.jenkinsci.remoting.engine.JnlpProtocol3Handler} for more details.
 *
 * @since 1.653
 */
@Deprecated
@Extension
@Symbol("jnlp3")
public class JnlpSlaveAgentProtocol3 extends AgentProtocol {
    private NioChannelSelector hub;

    private JnlpProtocol3Handler handler;

    @Inject
    public void setHub(NioChannelSelector hub) {
        this.hub = hub;
        this.handler = new JnlpProtocol3Handler(JnlpAgentReceiver.DATABASE, Computer.threadPoolForRemoting,
                hub.getHub(), true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isOptIn() {
        return true ;
    }

    @Override
    public String getName() {
        return handler.isEnabled() ? handler.getName() : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        return Messages.JnlpSlaveAgentProtocol3_displayName();
    }

    @Override
    public boolean isDeprecated() {
        return true;
    }
    
    @Override
    public void handle(Socket socket) throws IOException, InterruptedException {
        handler.handle(socket,
                Collections.singletonMap(JnlpConnectionState.COOKIE_KEY, JnlpAgentReceiver.generateCookie()),
                ExtensionList.lookup(JnlpAgentReceiver.class));
    }

}
