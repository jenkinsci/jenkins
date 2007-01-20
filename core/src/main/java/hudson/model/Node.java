package hudson.model;

import hudson.Launcher;
import hudson.util.EnumConverter;
import org.apache.commons.beanutils.ConvertUtils;

/**
 * Commonality between {@link Slave} and master {@link Hudson}.
 *
 * @author Kohsuke Kawaguchi
 */
public interface Node {
    /**
     * PluginName of this node.
     *
     * @return
     *      "" if this is master
     */
    String getNodeName();

    /**
     * Human-readable description of this node.
     */
    String getNodeDescription();

    /**
     * Returns a {@link Launcher} for executing programs on this node.
     */
    Launcher createLauncher(TaskListener listener);

    /**
     * Returns the number of {@link Executor}s.
     *
     * This may be different from <code>getExecutors().size()</code>
     * because it takes time to adjust the number of executors.
     */
    int getNumExecutors();

    /**
     * Returns true if this node is only available
     * for those jobs that exclusively specifies this node
     * as the assigned node.
     */
    Mode getMode();

    Computer createComputer();

    public enum Mode {
        NORMAL("Utilize this slave as much as possible"),
        EXCLUSIVE("Leave this machine for tied jobs only");

        private final String description;

        public String getDescription() {
            return description;
        }

        public String getName() {
            return name();
        }

        Mode(String description) {
            this.description = description;
        }

        static {
            ConvertUtils.register(new EnumConverter(),Mode.class);
        }
    }
}
