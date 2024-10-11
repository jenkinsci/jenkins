/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Seiji Sogabe, Stephen Connolly
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

import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.BulkChange;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.FileSystemProvisioner;
import hudson.Launcher;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.Descriptor.FormException;
import hudson.model.Queue.Task;
import hudson.model.labels.LabelAtom;
import hudson.model.listeners.SaveableListener;
import hudson.model.queue.CauseOfBlockage;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.slaves.Cloud;
import hudson.slaves.ComputerListener;
import hudson.slaves.EphemeralNode;
import hudson.slaves.NodeDescriptor;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.OfflineCause;
import hudson.util.ClockDifference;
import hudson.util.DescribableList;
import hudson.util.EnumConverter;
import hudson.util.TagCloud;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.model.Nodes;
import jenkins.util.Listeners;
import jenkins.util.SystemProperties;
import jenkins.util.io.OnMaster;
import net.sf.json.JSONObject;
import org.jvnet.localizer.Localizable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.accmod.restrictions.ProtectedExternally;
import org.kohsuke.stapler.BindInterceptor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.springframework.security.core.Authentication;

/**
 * Base type of Jenkins agents (although in practice, you probably extend {@link Slave} to define a new agent type).
 *
 * <p>
 * As a special case, {@link Jenkins} extends from here.
 *
 * <p>
 * Nodes are persisted objects that capture user configurations, and instances get thrown away and recreated whenever
 * the configuration changes. Running state of nodes are captured by {@link Computer}s.
 *
 * <p>
 * There is no URL binding for {@link Node}. {@link Computer} and {@link TransientComputerActionFactory} must
 * be used to associate new {@link Action}s to agents.
 *
 * @author Kohsuke Kawaguchi
 * @see NodeDescriptor
 * @see Computer
 */
@ExportedBean
public abstract class Node extends AbstractModelObject implements ReconfigurableDescribable<Node>, ExtensionPoint, AccessControlled, OnMaster, PersistenceRoot {

    private static final Logger LOGGER = Logger.getLogger(Node.class.getName());

    /** @see <a href="https://issues.jenkins.io/browse/JENKINS-46652">JENKINS-46652</a> */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static /* not final */ boolean SKIP_BUILD_CHECK_ON_FLYWEIGHTS = SystemProperties.getBoolean(Node.class.getName() + ".SKIP_BUILD_CHECK_ON_FLYWEIGHTS", true);

    /**
     * Newly copied agents get this flag set, so that Jenkins doesn't try to start/remove this node until its configuration
     * is saved once.
     */
    protected transient volatile boolean holdOffLaunchUntilSave;

    private transient Nodes parent;

    @Override
    public String getDisplayName() {
        return getNodeName(); // default implementation
    }

    @Override
    public String getSearchUrl() {
        Computer c = toComputer();
        if (c != null) {
            return c.getUrl();
        }
        return "computer/" + Util.rawEncode(getNodeName());
    }

    public boolean isHoldOffLaunchUntilSave() {
        return holdOffLaunchUntilSave;
    }

    /**
     * @since 1.635.
     */
    @Override
    public void save() throws IOException {
        if (parent == null) return;
        if (this instanceof EphemeralNode) {
            Util.deleteRecursive(getRootDir());
            return;
        }
        if (BulkChange.contains(this))   return;
        getConfigFile().write(this);
        SaveableListener.fireOnChange(this, getConfigFile());
    }

    protected XmlFile getConfigFile() {
        return parent.getConfigFile(this);
    }

    /**
     * Name of this node.
     *
     * @return
     *      "" if this is master
     */
    @Exported(visibility = 999)
    @NonNull
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
    @Deprecated
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
     * This may be different from {@code getExecutors().size()}
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
    @CheckForNull
    public final Computer toComputer() {
        AbstractCIBase ciBase = Jenkins.get();
        return ciBase.getComputer(this);
    }

    /**
     * Gets the current channel, if the node is connected and online, or null.
     *
     * This is just a convenience method for {@link Computer#getChannel()} with null check.
     */
    @CheckForNull
    public final VirtualChannel getChannel() {
        Computer c = toComputer();
        return c == null ? null : c.getChannel();
    }

    /**
     * Creates a new {@link Computer} object that acts as the UI peer of this {@link Node}.
     *
     * Nobody but {@link Jenkins#updateComputerList()} should call this method.
     * @return Created instance of the computer.
     *         Can be {@code null} if the {@link Node} implementation does not support it (e.g. {@link Cloud} agent).
     */
    @CheckForNull
    @Restricted(ProtectedExternally.class)
    protected abstract Computer createComputer();

    /**
     * Returns {@code true} if the node is accepting tasks. Needed to allow agents programmatic suspension of task
     * scheduling that does not overlap with being offline. Called by {@link Computer#isAcceptingTasks()}.
     * This method is distinct from {@link Computer#isAcceptingTasks()} as sometimes the {@link Node} concrete
     * class may not have control over the {@link hudson.model.Computer} concrete class associated with it.
     *
     * @return {@code true} if the node is accepting tasks.
     * @see Computer#isAcceptingTasks()
     * @since 1.586
     */
    public boolean isAcceptingTasks() {
        return true;
    }

    public void onLoad(Nodes parent, String name) {
        this.parent = parent;
        setNodeName(name);
    }

    public boolean isTemporarilyOffline() {
        return temporaryOfflineCause != null;
    }

    private volatile OfflineCause temporaryOfflineCause;

    /**
     * Enable a {@link Computer} to inform its node when it is taken
     * temporarily offline.
     */
    void setTemporaryOfflineCause(OfflineCause cause) {
        try {
            if (temporaryOfflineCause != cause) {
                temporaryOfflineCause = cause;
                save();
            }
            if (temporaryOfflineCause != null) {
                Listeners.notify(ComputerListener.class, false, l -> l.onTemporarilyOffline(toComputer(), temporaryOfflineCause));
            } else {
                Listeners.notify(ComputerListener.class, false, l -> l.onTemporarilyOnline(toComputer()));
            }
        } catch (java.io.IOException e) {
            LOGGER.warning("Unable to complete save, temporary offline status will not be persisted: " + e.getMessage());
        }
    }

    /**
     * Get the cause if temporary offline.
     *
     * @return null if not temporary offline or there was no cause given.
     * @since 2.340
     */
    public OfflineCause getTemporaryOfflineCause() {
        return temporaryOfflineCause;
    }

    /**
     * Return the possibly empty tag cloud for the labels of this node.
     */
    public TagCloud<LabelAtom> getLabelCloud() {
        return new TagCloud<>(getAssignedLabels(), Label::getTiedJobCount);
    }

    /**
     * @return An immutable set of LabelAtom associated with the current node label.
     */
    @NonNull
    @Restricted(NoExternalUse.class)
    protected Set<LabelAtom> getLabelAtomSet() {
        // Default implementation doesn't cache, since we can't hook on label updates.
        return Collections.unmodifiableSet(Label.parse(getLabelString()));
    }

    /**
     * Returns the possibly empty set of labels that are assigned to this node,
     * including the automatic {@link #getSelfLabel() self label}, manually
     * assigned labels and dynamically assigned labels via the
     * {@link LabelFinder} extension point.
     *
     * This method has a side effect of updating the hudson-wide set of labels
     * and should be called after events that will change that - e.g. an agent
     * connecting.
     */

    @Exported
    public Set<LabelAtom> getAssignedLabels() {
        Set<LabelAtom> r = new HashSet<>(getLabelAtomSet());
        r.add(getSelfLabel());
        r.addAll(getDynamicLabels());
        return Collections.unmodifiableSet(r);
    }

    /**
     * Return all the labels assigned dynamically to this node.
     * This calls all the LabelFinder implementations with the node converts
     * the results into Labels.
     */
    private HashSet<LabelAtom> getDynamicLabels() {
        HashSet<LabelAtom> result = new HashSet<>();
        for (LabelFinder labeler : LabelFinder.all()) {
            // Filter out any bad(null) results from plugins
            // for compatibility reasons, findLabels may return LabelExpression and not atom.
            for (Label label : labeler.findLabels(this))
                if (label instanceof LabelAtom) result.add((LabelAtom) label);
        }
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
     * Sets the label string for a node. This value will be returned by {@link #getLabelString()}.
     *
     * @param labelString
     *      The new label string to use.
     * @since 1.477
     */
    public void setLabelString(String labelString) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * Gets the special label that represents this node itself.
     */
    @NonNull
    @WithBridgeMethods(Label.class)
    public LabelAtom getSelfLabel() {
        return LabelAtom.get(getNodeName());
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
     * @deprecated as of 1.413
     *      Use {@link #canTake(Queue.BuildableItem)}
     */
    @Deprecated
    public CauseOfBlockage canTake(Task task) {
        return null;
    }

    /**
     * Called by the {@link Queue} to determine whether or not this node can
     * take the given task. The default checks include whether or not this node
     * is part of the task's assigned label, whether this node is in
     * {@link Mode#EXCLUSIVE} mode if it is not in the task's assigned label,
     * and whether or not any of this node's {@link NodeProperty}s say that the
     * task cannot be run.
     *
     * @since 1.413
     */
    public CauseOfBlockage canTake(Queue.BuildableItem item) {
        Label l = item.getAssignedLabel();
        if (l != null && !l.contains(this))
            return CauseOfBlockage.fromMessage(Messages._Node_LabelMissing(getDisplayName(), l));   // the task needs to be executed on label that this node doesn't have.

        if (l == null && getMode() == Mode.EXCLUSIVE) {
            // flyweight tasks need to get executed somewhere, if every node
            if (!(item.task instanceof Queue.FlyweightTask && (
                    this instanceof Jenkins
                            // TODO Why is the next operator a '||' instead of a '&&'?
                            || Jenkins.get().getNumExecutors() < 1
                            || Jenkins.get().getMode() == Mode.EXCLUSIVE)
            )) {
                return CauseOfBlockage.fromMessage(Messages._Node_BecauseNodeIsReserved(getDisplayName()));   // this node is reserved for tasks that are tied to it
            }
        }

        Authentication identity = item.authenticate2();
        if (!(SKIP_BUILD_CHECK_ON_FLYWEIGHTS && item.task instanceof Queue.FlyweightTask) && !hasPermission2(identity, Computer.BUILD)) {
            // doesn't have a permission
            return CauseOfBlockage.fromMessage(Messages._Node_LackingBuildPermission(identity.getName(), getDisplayName()));
        }

        // Check each NodeProperty to see whether they object to this node
        // taking the task
        for (NodeProperty prop : getNodeProperties()) {
            CauseOfBlockage c;
            try {
                c = prop.canTake(item);
            } catch (Throwable t) {
                // We cannot guarantee the task can be taken by this node because something wrong happened
                LOGGER.log(Level.WARNING, t, () -> String.format("Exception evaluating if the node '%s' can take the task '%s'", getNodeName(), item.task.getName()));
                c = CauseOfBlockage.fromMessage(Messages._Queue_ExceptionCanTake());
            }
            if (c != null)    return c;
        }

        if (!isAcceptingTasks()) {
            return new CauseOfBlockage.BecauseNodeIsNotAcceptingTasks(this);
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
    public abstract @CheckForNull FilePath getWorkspaceFor(TopLevelItem item);

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
    public abstract @CheckForNull FilePath getRootPath();

    /**
     * Gets the {@link FilePath} on this node.
     */
    public @CheckForNull FilePath createPath(String absolutePath) {
        VirtualChannel ch = getChannel();
        if (ch == null)    return null;    // offline
        return new FilePath(ch, absolutePath);
    }

    @Deprecated
    public FileSystemProvisioner getFileSystemProvisioner() {
        return FileSystemProvisioner.DEFAULT;
    }

    /**
     * Gets the {@link NodeProperty} instances configured for this {@link Node}.
     */
    public abstract @NonNull DescribableList<NodeProperty<?>, NodePropertyDescriptor> getNodeProperties();

    /**
     * Gets the specified property or null if the property is not configured for this Node.
     *
     * @param clazz the type of the property
     *
     * @return null if the property is not configured
     *
     * @since 2.37
     */
    @CheckForNull
    public <T extends NodeProperty> T getNodeProperty(Class<T> clazz)
    {
        for (NodeProperty p : getNodeProperties()) {
            if (clazz.isInstance(p)) {
                return clazz.cast(p);
            }
        }
        return null;
    }

    /**
     * Gets the property from the given classname or null if the property
     * is not configured for this Node.
     *
     * @param className The classname of the property
     *
     * @return null if the property is not configured
     *
     * @since 2.37
     */
    @CheckForNull
    public NodeProperty getNodeProperty(String className)
    {
        for (NodeProperty p : getNodeProperties()) {
            if (p.getClass().getName().equals(className)) {
                return p;
            }
        }
        return null;
    }

    // used in the Jelly script to expose descriptors
    public List<NodePropertyDescriptor> getNodePropertyDescriptors() {
        return NodeProperty.for_(this);
    }

    @NonNull
    @Override
    public ACL getACL() {
        return Jenkins.get().getAuthorizationStrategy().getACL(this);
    }

    @Override
    public Node reconfigure(@NonNull final StaplerRequest2 req, JSONObject form) throws FormException {
        if (Util.isOverridden(Node.class, getClass(), "reconfigure", StaplerRequest.class, JSONObject.class)) {
            return reconfigure(StaplerRequest.fromStaplerRequest2(req), form);
        } else {
            return reconfigureImpl(req, form);
        }
    }

    /**
     * @deprecated use {@link #reconfigure(StaplerRequest2, JSONObject)}
     */
    @Deprecated
    @Override
    public Node reconfigure(@NonNull final StaplerRequest req, JSONObject form) throws FormException {
        return reconfigureImpl(StaplerRequest.toStaplerRequest2(req), form);
    }

    private Node reconfigureImpl(@NonNull final StaplerRequest2 req, JSONObject form) throws FormException {
        if (form == null)     return null;

        final JSONObject jsonForProperties = form.optJSONObject("nodeProperties");
        final AtomicReference<BindInterceptor> old = new AtomicReference<>();
        old.set(req.setBindInterceptor(new BindInterceptor() {
            @Override
            public Object onConvert(Type targetType, Class targetTypeErasure, Object jsonSource) {
                if (jsonForProperties != jsonSource) {
                    return old.get().onConvert(targetType, targetTypeErasure, jsonSource);
                }

                try {
                    DescribableList<NodeProperty<?>, NodePropertyDescriptor> tmp = new DescribableList<>(Saveable.NOOP, getNodeProperties().toList());
                    tmp.rebuild(req, jsonForProperties, NodeProperty.all());
                    return tmp.toList();
                } catch (FormException | IOException e) {
                    throw new IllegalArgumentException(e);
                }
            }
        }));

        try {
            return getDescriptor().newInstance(req, form);
        } finally {
            req.setBindListener(old.get());
        }
    }

    @Override
    public abstract NodeDescriptor getDescriptor();

    /**
     * Estimates the clock difference with this agent.
     *
     * @return
     *      always non-null.
     * @throws InterruptedException
     *      if the operation is aborted.
     */
    public ClockDifference getClockDifference() throws IOException, InterruptedException {
        VirtualChannel channel = getChannel();
        if (channel == null)
            throw new IOException(getNodeName() + " is offline");

        return channel.call(getClockDifferenceCallable());
    }

    /**
     * Returns a {@link Callable} that when run on the channel, estimates the clock difference.
     *
     * @return
     *      always non-null.
     * @since 1.522
     */
    public abstract Callable<ClockDifference, IOException> getClockDifferenceCallable();

    /**
     * Constants that control how Hudson allocates jobs to agents.
     */
    public enum Mode {
        NORMAL(Messages._Node_Mode_NORMAL()),
        EXCLUSIVE(Messages._Node_Mode_EXCLUSIVE());

        private final Localizable description;

        public String getDescription() {
            return description.toString();
        }

        public String getName() {
            return name();
        }

        Mode(Localizable description) {
            this.description = description;
        }

        static {
            Stapler.CONVERT_UTILS.register(new EnumConverter(), Mode.class);
        }
    }

    @Override
    public File getRootDir() {
        return getParent().getRootDirFor(this);
    }

    @NonNull
    private Nodes getParent() {
        if (parent == null) {
            throw new IllegalStateException("no parent set on " + getClass().getName() + "[" + getNodeName() + "]");
        }
        return parent;
    }
}
