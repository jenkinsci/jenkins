package jenkins.slaves;

import hudson.Extension;
import hudson.ExtensionComponent;
import hudson.ExtensionList;
import hudson.model.Computer;
import java.io.IOException;
import java.net.Socket;
import java.util.Collections;
import java.util.logging.Logger;
import javax.inject.Inject;

import jenkins.AgentProtocol;
import jenkins.ExtensionFilter;
import org.jenkinsci.Symbol;
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

    private static final Logger logger = Logger.getLogger(JnlpSlaveAgentProtocol3.class.getName());
    @Restricted(NoExternalUse.class)
    public static final String ALLOW_UNSAFE = JnlpSlaveAgentProtocol3.class.getName() + ".ALLOW_UNSAFE";
    private NioChannelSelector hub;

    private JnlpProtocol3Handler handler;

    @Inject
    public void setHub(NioChannelSelector hub) {
        this.hub = hub;
        this.handler = new JnlpProtocol3Handler(JnlpAgentReceiver.DATABASE, Computer.threadPoolForRemoting,
                hub.getHub(), true);
    }

    @Override
    public boolean isOptIn() {
        return true ;
    }

    @Override
    public String getName() {
        return handler.isEnabled() ? handler.getName() : null;
    }

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

    @Extension
    @Restricted(NoExternalUse.class)
    public static class Impl extends ExtensionFilter {
        @Override
        public <T> boolean allows(Class<T> type, ExtensionComponent<T> component) {
            if (Boolean.getBoolean(ALLOW_UNSAFE)) {
                return true;
            }
            if (!AgentProtocol.class.isAssignableFrom(type)) {
                return true;
            }
            boolean isJnlp3 = component.getInstance().getClass().isAssignableFrom(JnlpSlaveAgentProtocol3.class);
            if (isJnlp3) {
                logger.info("Inbound TCP Agent Protocol/3 has been forcibly disabled for additional security reasons. To enable it yet again set the system property " + ALLOW_UNSAFE);
            }
            return !isJnlp3;
        }
    }

}
