package hudson.model;

import hudson.FilePath;
import hudson.Launcher;
import hudson.node_monitors.NodeMonitor;
import hudson.util.ClockDifference;
import hudson.util.EnumConverter;
import org.kohsuke.stapler.Stapler;

import java.io.IOException;
import java.util.Set;

/**
 * Commonality between {@link Slave} and master {@link Hudson}.
 *
 * @author Kohsuke Kawaguchi
 * @see NodeMonitor
 */
public interface Node {
    /**
     * Name of this node.
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

    /**
     * Gets the corresponding {@link Computer} object.
     *
     * @return
     *      never null.
     */
    Computer toComputer();

    Computer createComputer();

    /**
     * Returns the possibly empty set of labels that are assigned to this node,
     * including the automatic {@link #getSelfLabel() self label}.
     */
    Set<Label> getAssignedLabels();

    /**
     * Returns the possibly empty set of labels that it has been determined as supported by this node.
     * @see hudson.tasks.LabelFinder
     */
    Set<Label> getDynamicLabels();

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
     *
     * @return
     *      null if this node is not connected hence the path is not available
     */
    FilePath getWorkspaceFor(TopLevelItem item);

    /**
     * Gets the root directory of this node.
     *
     * <p>
     * Hudson always owns a directory on every node. This method
     * returns that.
     *
     * @return
     *      null if the node is offline and hence the {@link FilePath}
     *      object is not available.
     */
    FilePath getRootPath();

    /**
     * Gets the {@link FilePath} on this node.
     */
    FilePath createPath(String absolutePath);

    /**
     * Estimates the clock difference with this slave.
     *
     * @return
     *      always non-null.
     * @throws InterruptedException
     *      if the operation is aborted.
     */
    ClockDifference getClockDifference() throws IOException, InterruptedException;

    /**
     * Constants that control how Hudson allocates jobs to slaves.
     */
    public enum Mode {
        NORMAL(Messages.Node_Mode_NORMAL()),
        EXCLUSIVE(Messages.Node_Mode_EXCLUSIVE());

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
            Stapler.CONVERT_UTILS.register(new EnumConverter(), Mode.class);
        }
    }

}
