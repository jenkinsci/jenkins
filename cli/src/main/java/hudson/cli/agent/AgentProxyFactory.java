package hudson.cli.agent;

import com.jcraft.jsch.agentproxy.AgentProxyException;
import com.jcraft.jsch.agentproxy.Connector;
import com.jcraft.jsch.agentproxy.ConnectorFactory;
import com.jcraft.jsch.agentproxy.TrileadAgentProxy;

import java.util.logging.Logger;

/**
 * @author reynald
 */
public class AgentProxyFactory {

    private enum AgentProxy {
        INSTANCE;

        private TrileadAgentProxy agentProxy;

        private AgentProxy() {
            try {
                final Connector con = ConnectorFactory.getDefault().createConnector();

                agentProxy = new TrileadAgentProxy(con);

                LOGGER.fine("Connected to SSH agent '" + con.getName() + "'");
            } catch (AgentProxyException e) {
                agentProxy = null;
            }
        }
    }

    public static final TrileadAgentProxy getAgentProxy() {
        return AgentProxy.INSTANCE.agentProxy;
    }

    private static final Logger LOGGER = Logger.getLogger(AgentProxyFactory.class.getName());
}
