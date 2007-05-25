package hudson.model;

import hudson.Launcher;
import hudson.FilePath;
import hudson.util.EnumConverter;
import org.apache.commons.beanutils.ConvertUtils;

import java.util.Set;

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
     * Returns {@link Mode#EXCLUSIVE} if this node is only available
     * for those jobs that exclusively specifies this node
     * as the assigned node.
     */
    Mode getMode();

    Computer createComputer();

    /**
     * Returns the possibly empty set of labels that are assigned to this node,
     * including the automatic {@link #getSelfLabel() self label}.
     */
    Set<Label> getAssignedLabels();

    /**
     * Gets the special label that represents this node itself.
     */
    Label getSelfLabel();

    /**
     * Returns a "workspace" directory for the given {@link TopLevelItem}.
     *
     * <p>
     * Workspace directory is usually used for keeping out the checked out
     * source code, but it can be used for anything.
     */
    FilePath getWorkspaceFor(TopLevelItem item);

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

    enum JNLPSecurityMode {
        NORMAL("Launch only from Computer detail. Require Login if security enabled.", true, false, false),
        SECURE_PUBLIC("Launch from front page. Require Login if security enabled.", true, false, false),
        BYPASS("Launch only from Computer detail. Login never required.", false, false, false),
        PUBLIC("Launch from front page. Login never required.", false, true, false)/*,

        // stephenconnolly: holding off on these next changes until they are closer to ready
        DYNAMIC("Dynamic secure slave (experimental)", true, true, true),
        DYNAMIC("Dynamic public slave (experimental)", false, true, true)*/;

        private final String description;
        private final boolean enforceSecurity;
        private final boolean publicLaunch;
        private final boolean dynammicPool;

        public String getDescription() {
            return description;
        }

        public String getName() {
            return name();
        }

        public boolean isEnforceSecurity() {
            return enforceSecurity;
        }

        public boolean isPublicLaunch() {
            return publicLaunch;
        }

        public boolean isDynammicPool() {
            return dynammicPool;
        }

        JNLPSecurityMode(String description, boolean enforceSecurity, boolean publicLaunch, boolean dynammicPool) {
            this.description = description;
            this.enforceSecurity = enforceSecurity;
            this.publicLaunch = publicLaunch;
            this.dynammicPool = dynammicPool;
        }

        static {
            ConvertUtils.register(new EnumConverter(), JNLPSecurityMode.class);
        }
    }
}
