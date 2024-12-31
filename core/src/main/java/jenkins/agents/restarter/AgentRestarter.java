package jenkins.agents.restarter;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import java.io.Serializable;
import java.util.logging.Logger;

/**
 * Extension point to control how to restart an inbound agent when it loses the connection with the master.
 *
 * <p>
 * Objects are instantiated on the master, then transferred to an agent via serialization.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AgentRestarter implements ExtensionPoint, Serializable {
    /**
     * Called on the agent to see if this restarter can work on this agent.
     */
    public abstract boolean canWork();

    /**
     * If {@link #canWork()} method returns true, this method is called later when
     * the connection is lost to restart the agent.
     *
     * <p>
     * Note that by the time this method is called, classloader is no longer capable of
     * loading any additional classes. Therefore {@link #canWork()} method must have
     * exercised enough of the actual restart process so that this call can proceed
     * without trying to load additional classes nor resources.
     *
     * <p>
     * This method is not expected to return, and the JVM should terminate before this call returns.
     * If the method returns normally, the agent will move on to the reconnection without restart.
     * If an exception is thrown, it is reported as an error and then the agent will move on to the
     * reconnection without restart.
     */
    public abstract void restart() throws Exception;

    public static ExtensionList<AgentRestarter> all() {
        return ExtensionList.lookup(AgentRestarter.class);
    }

    private static final Logger LOGGER = Logger.getLogger(AgentRestarter.class.getName());

    private static final long serialVersionUID = 1L;
}
