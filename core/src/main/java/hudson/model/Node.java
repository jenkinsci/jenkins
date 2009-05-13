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

import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.FileSystemProvisioner;
import hudson.Launcher;
import hudson.node_monitors.NodeMonitor;
import hudson.remoting.VirtualChannel;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.slaves.NodeDescriptor;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.util.ClockDifference;
import hudson.util.DescribableList;
import hudson.util.EnumConverter;

import java.io.IOException;
import java.util.Set;
import java.util.List;

import org.kohsuke.stapler.Stapler;

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
public abstract class Node extends AbstractModelObject implements Describable<Node>, ExtensionPoint, AccessControlled {

    public String getDisplayName() {
        return getNodeName(); // default implementation
    }

    public String getSearchUrl() {
        return "computer/"+getNodeName();
    }

    /**
     * Name of this node.
     *
     * @return
     *      "" if this is master
     */
    public abstract String getNodeName();

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
    public abstract void setNodeName(String name);

    /**
     * Human-readable description of this node.
     */
    public abstract String getNodeDescription();

    /**
     * Returns a {@link Launcher} for executing programs on this node.
     *
     * <p>
     * The callee must call {@link Launcher#decorateFor(Node)} before returning to complete the decoration. 
     */
    public abstract Launcher createLauncher(TaskListener listener);

    /**
     * Returns the number of {@link Executor}s.
     *
     * This may be different from <code>getExecutors().size()</code>
     * because it takes time to adjust the number of executors.
     */
    public abstract int getNumExecutors();

    /**
     * Returns {@link Mode#EXCLUSIVE} if this node is only available
     * for those jobs that exclusively specifies this node
     * as the assigned node.
     */
    public abstract Mode getMode();

    /**
     * Gets the corresponding {@link Computer} object.
     *
     * @return
     *      this method can return null if there's no {@link Computer} object for this node,
     *      such as when this node has no executors at all.
     */
    public final Computer toComputer() {
        return Hudson.getInstance().getComputer(this);
    }

    /**
     * Creates a new {@link Computer} object that acts as the UI peer of this {@link Node}.
     * Nobody but {@link Hudson#updateComputerList()} should call this method.
     */
    protected abstract Computer createComputer();

    /**
     * Returns the possibly empty set of labels that are assigned to this node,
     * including the automatic {@link #getSelfLabel() self label}.
     */
    public abstract Set<Label> getAssignedLabels();

    /**
     * The same as {@link #getAssignedLabels()} but returns labels as a single text.
     * Mainly for form binding.
     */
    public abstract String getLabelString();

    /*
     * Returns the possibly empty set of labels that it has been determined as supported by this node.
     * @see hudson.tasks.LabelFinder
     */
    public abstract Set<Label> getDynamicLabels();

    /**
     * Gets the special label that represents this node itself.
     */
    public Label getSelfLabel() {
        return Hudson.getInstance().getLabel(getNodeName());
    }

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
    public abstract FilePath getWorkspaceFor(TopLevelItem item);

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
    public abstract FilePath getRootPath();

    /**
     * Gets the {@link FilePath} on this node.
     */
    public FilePath createPath(String absolutePath) {
        Computer computer = toComputer();
        if (computer==null) return null; // offline
        VirtualChannel ch = computer.getChannel();
        if(ch==null)    return null;    // offline
        return new FilePath(ch,absolutePath);
    }

    public FileSystemProvisioner getFileSystemProvisioner() {
        // TODO: make this configurable or auto-detectable or something else
        return FileSystemProvisioner.DEFAULT;
    }

    /**
     * Gets the {@link NodeProperty} instances configured for this {@link Node}.
     */
    public abstract DescribableList<NodeProperty<?>, NodePropertyDescriptor> getNodeProperties();

    // used in the Jelly script to expose descriptors
    public List<NodePropertyDescriptor> getNodePropertyDescriptors() {
        return NodeProperty.for_(this);
    }
    
    public ACL getACL() {
        return Hudson.getInstance().getAuthorizationStrategy().getACL(this);
    }
    
    public final void checkPermission(Permission permission) {
        getACL().checkPermission(permission);
    }

    public final boolean hasPermission(Permission permission) {
        return getACL().hasPermission(permission);
    }

    public abstract NodeDescriptor getDescriptor();

    /**
     * Estimates the clock difference with this slave.
     *
     * @return
     *      always non-null.
     * @throws InterruptedException
     *      if the operation is aborted.
     */
    public abstract ClockDifference getClockDifference() throws IOException, InterruptedException;

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
