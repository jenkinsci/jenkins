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
import hudson.model.Queue.Task;
import hudson.model.queue.CauseOfBlockage;
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
import hudson.util.TagCloud;
import hudson.util.TagCloud.WeightFunction;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Exported;

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
@ExportedBean
public abstract class Node extends AbstractModelObject implements Describable<Node>, ExtensionPoint, AccessControlled {
    /**
     * Newly copied slaves get this flag set, so that Hudson doesn't try to start this node until its configuration
     * is saved once.
     */
    protected volatile transient boolean holdOffLaunchUntilSave;

    public String getDisplayName() {
        return getNodeName(); // default implementation
    }

    public String getSearchUrl() {
        return "computer/"+getNodeName();
    }

    public boolean isHoldOffLaunchUntilSave() {
        return holdOffLaunchUntilSave;
    }

    /**
     * Name of this node.
     *
     * @return
     *      "" if this is master
     */
    @Exported(visibility=999)
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
    @Exported
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
    @Exported
    public abstract int getNumExecutors();

    /**
     * Returns {@link Mode#EXCLUSIVE} if this node is only available
     * for those jobs that exclusively specifies this node
     * as the assigned node.
     */
    @Exported
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
     * Gets the current channel, if the node is connected and online, or null.
     *
     * This is just a convenience method for {@link Computer#getChannel()} with null check. 
     */
    public final VirtualChannel getChannel() {
        Computer c = toComputer();
        return c==null ? null : c.getChannel();
    }

    /**
     * Creates a new {@link Computer} object that acts as the UI peer of this {@link Node}.
     * Nobody but {@link Hudson#updateComputerList()} should call this method.
     */
    protected abstract Computer createComputer();

    /**
     * Return the possibly empty tag cloud for the labels of this node.
     */
    public TagCloud<Label> getLabelCloud() {
        return new TagCloud<Label>(getAssignedLabels(),new WeightFunction<Label>() {
            public float weight(Label item) {
                return item.getTiedJobs().size();
            }
        });
    }
    /**
     * Returns the possibly empty set of labels that are assigned to this node,
     * including the automatic {@link #getSelfLabel() self label}, manually
     * assigned labels and dynamically assigned labels via the
     * {@link LabelFinder} extension point.
     *
     * This method has a side effect of updating the hudson-wide set of labels
     * and should be called after events that will change that - e.g. a slave
     * connecting.
     */
    @Exported
    public Set<Label> getAssignedLabels() {
         Set<Label> r = Label.parse(getLabelString());
        r.add(getSelfLabel());
        r.addAll(getDynamicLabels());
        return Collections.unmodifiableSet(r);
    }

    /**
     * Return all the labels assigned dynamically to this node.
     * This calls all the LabelFinder implementations with the node converts
     * the results into Labels.
     * @return HashSet<Label>.
     */
    private final HashSet<Label> getDynamicLabels() {
        HashSet<Label> result = new HashSet<Label>();
        for (LabelFinder labeler : LabelFinder.all())
            // Filter out any bad(null) results from plugins
            for (Label label : labeler.findLabels(this))
                if (label != null) result.add(label);
        return result;
    }


    /**
     * Returns the manually configured label for a node. The list of assigned
     * and dynamically determined labels is available via 
     * {@link #getAssignedLabels()} and includes all labels that have been
     * manually configured.
     * 
     * Mainly for form binding.
     */
    public abstract String getLabelString();

    /**
     * Gets the special label that represents this node itself.
     */
    public Label getSelfLabel() {
        return Label.get(getNodeName());
    }

    /**
     * Called by the {@link Queue} to determine whether or not this node can
     * take the given task. The default checks include whether or not this node
     * is part of the task's assigned label, whether this node is in
     * {@link Mode#EXCLUSIVE} mode if it is not in the task's assigned label,
     * and whether or not any of this node's {@link NodeProperty}s say that the
     * task cannot be run.
     *
     * @since 1.360
     */
    public CauseOfBlockage canTake(Task task) {
        Label l = task.getAssignedLabel();
        if(l!=null && !l.contains(this))
            return CauseOfBlockage.fromMessage(Messages._Node_LabelMissing(getNodeName(),l));   // the task needs to be executed on label that this node doesn't have.

        if(l==null && getMode()== Mode.EXCLUSIVE)
            return CauseOfBlockage.fromMessage(Messages._Node_BecauseNodeIsReserved(getNodeName()));   // this node is reserved for tasks that are tied to it

        // Check each NodeProperty to see whether they object to this node
        // taking the task
        for (NodeProperty prop: getNodeProperties()) {
            CauseOfBlockage c = prop.canTake(task);
            if (c!=null)    return c;
        }

        // Looks like we can take the task
        return null;
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
    // TODO: should this be modified now that getWorkspace is moved from AbstractProject to AbstractBuild?
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
        VirtualChannel ch = getChannel();
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
