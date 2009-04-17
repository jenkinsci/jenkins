package hudson.plugins.sshslaves;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.trilead.ssh2.Connection;
import hudson.Plugin;
import hudson.slaves.ComputerLauncher;

/**
 * Entry point of ssh-slaves plugin.
 *
 * @author Stephen Connolly
 * @plugin
 */
public class PluginImpl extends Plugin {

    /**
     * The connections to close when the plugin is stopped.
     */
    private static final List<Connection> activeConnections = new ArrayList<Connection>();

    /**
     * {@inheritDoc}
     */
    public void start() throws Exception {
        LOGGER.log(Level.FINE, "Starting SSH Slaves plugin");
        ComputerLauncher.LIST.add(SSHLauncher.DESCRIPTOR);
    }

    /**
     * {@inheritDoc}
     */
    public void stop() throws Exception {
        LOGGER.log(Level.FINE, "Stopping SSH Slaves plugin.");
        closeRegisteredConnections();
        LOGGER.log(Level.FINE, "SSH Slaves plugin stopped.");
    }

    /**
     * Closes all the registered connections.
     */
    private static synchronized void closeRegisteredConnections() {
        for (Connection connection : activeConnections) {
            LOGGER.log(Level.INFO, "Forcing connection to {0}:{1} closed.",
                    new Object[]{connection.getHostname(), connection.getPort()});
            // force closed just in case
            connection.close();
        }
        activeConnections.clear();
    }

    /**
     * Registers a connection for cleanup when the plugin is stopped.
     *
     * @param connection The connection.
     */
    public static synchronized void register(Connection connection) {
        if (!activeConnections.contains(connection)) {
            activeConnections.add(connection);
        }
    }

    /**
     * Unregisters a connection for cleanup when the plugin is stopped.
     *
     * @param connection The connection.
     */
    public static synchronized void unregister(Connection connection) {
        activeConnections.remove(connection);
    }

    /**
     * The logger for this class.
     */
    private static final java.util.logging.Logger LOGGER = Logger.getLogger(PluginImpl.class.getName());
}
