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
 * @since 1.XXX
 */
@Deprecated
@Extension
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
        return !ENABLED;
    }

    @Override
    public String getName() {
        // we only want to force the protocol off for users that have explicitly banned it via system property
        // everyone on the A/B test will just have the opt-in flag toggled
        // TODO strip all this out and hardcode OptIn==TRUE once JENKINS-36871 is merged
        return forceEnabled != Boolean.FALSE ? handler.getName() : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        return Messages.JnlpSlaveAgentProtocol3_displayName();
    }

    @Override
    public void handle(Socket socket) throws IOException, InterruptedException {
        handler.handle(socket,
                Collections.singletonMap(JnlpConnectionState.COOKIE_KEY, JnlpAgentReceiver.generateCookie()),
                ExtensionList.lookup(JnlpAgentReceiver.class));
    }

    /**
     * Flag to control the activation of JNLP3 protocol.
     * This feature is being A/B tested right now.
     *
     * <p>
     * Once this will be on by default, the flag and this field will disappear. The system property is
     * an escape hatch for those who hit any issues and those who are trying this out.
     */
    @Restricted(NoExternalUse.class)
    @SuppressFBWarnings(value = "MS_SHOULD_BE_REFACTORED_TO_BE_FINAL",
            justification = "Part of the administrative API for System Groovy scripts.")
    public static boolean ENABLED;
    private static final Boolean forceEnabled;

    static {
        forceEnabled = SystemProperties.optBoolean(JnlpSlaveAgentProtocol3.class.getName() + ".enabled");
        if (forceEnabled != null)
            ENABLED = forceEnabled;
        else {
            byte hash = Util.fromHexString(Jenkins.getActiveInstance().getLegacyInstanceId())[0];
            ENABLED = (hash % 10) == 0;
        }
    }
}
