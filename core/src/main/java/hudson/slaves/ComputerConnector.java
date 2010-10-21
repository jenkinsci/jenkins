package hudson.slaves;

import hudson.model.AbstractDescribableImpl;
import hudson.model.TaskListener;

import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class ComputerConnector extends AbstractDescribableImpl<ComputerConnector> {
    /**
     * Creates a {@link ComputerLauncher} for connecting to the given host.
     *
     * @param host
     *      The host name / IP address of the machine to connect to.
     * @param listener
     *      If 
     */
    public abstract ComputerLauncher launch(String host, TaskListener listener) throws IOException, InterruptedException;
}
