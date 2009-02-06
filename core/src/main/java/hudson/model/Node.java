/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Seiji Sogabe, Stephen Connolly
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.model;

import hudson.FilePath;
import hudson.Launcher;
import hudson.ExtensionPoint;
import hudson.security.AccessControlled;
import hudson.slaves.NodeDescriptor;
import hudson.node_monitors.NodeMonitor;
import hudson.util.ClockDifference;
import hudson.util.EnumConverter;
import org.kohsuke.stapler.Stapler;

import java.io.IOException;
import java.util.Set;

/**
 * Base type of Hudson slaves (although in practice, you probably extend {@link Slave} to define a new slave type.)
 *
 * <p>
 * As a special case, {@link Hudson} extends from here.
 *
 * @author Kohsuke Kawaguchi
 * @see NodeMonitor
 * @see NodeDescriptor
 */
public interface Node extends Describable<Node>, ExtensionPoint, AccessControlled {
    /**
     * Name of this node.
     *
     * @return
     *      "" if this is master
     */
    String getNodeName();

    /**
     * When the user clones a {@link Node}, Hudson uses this method to change the node name right after
     * the cloned {@link Node} object is instantiated.
     *
     * <p>
     * This method is never used for any other purpose, and as such for all practical intents and purposes,
     * the node name should be treated like immutable.
     *
     * @deprecated to indicate that this method isn't really meant to be called by random code.
     */
    void setNodeName(String name);

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
     *      this method can return null if there's no {@link Computer} object for this node,
     *      such as when this node has no executors at all.
     */
    Computer toComputer();

    /**
     * Creates a new {@link Computer} object that acts as the UI peer of this {@link Node}.
     * Nobody but {@link Hudson#updateComputerList()} should call this method.
     */
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

    NodeDescriptor getDescriptor();

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
