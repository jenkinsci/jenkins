/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Red Hat, Inc., Seiji Sogabe, Stephen Connolly, Thomas J. Black, Tom Huybrechts,
 * CloudBees, Inc., Christopher Simons
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

import edu.umd.cs.findbugs.annotations.OverrideMustInvoke;
import edu.umd.cs.findbugs.annotations.When;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher.ProcStarter;
import jenkins.util.SystemProperties;
import hudson.Util;
import hudson.cli.declarative.CLIMethod;
import hudson.cli.declarative.CLIResolver;
import hudson.console.AnnotatedLargeText;
import hudson.init.Initializer;
import hudson.model.Descriptor.FormException;
import hudson.model.Queue.FlyweightTask;
import hudson.model.labels.LabelAtom;
import hudson.model.queue.WorkUnit;
import hudson.node_monitors.NodeMonitor;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.ComputerListener;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.WorkspaceList;
import hudson.slaves.OfflineCause;
import hudson.slaves.OfflineCause.ByCLI;
import hudson.util.DaemonThreadFactory;
import hudson.util.EditDistance;
import hudson.util.ExceptionCatchingThreadFactory;
import hudson.util.RemotingDiagnostics;
import hudson.util.RemotingDiagnostics.HeapDump;
import hudson.util.RunList;
import hudson.util.Futures;
import hudson.util.NamingThreadFactory;
import jenkins.model.Jenkins;
import jenkins.util.ContextResettingExecutorService;
import jenkins.security.MasterToSlaveCallable;

import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.args4j.Option;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.concurrent.GuardedBy;
import javax.servlet.ServletException;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.logging.LogRecord;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.nio.charset.Charset;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Inet4Address;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static javax.servlet.http.HttpServletResponse.*;

/**
 * Represents the running state of a remote computer that holds {@link Executor}s.
 *
 * <p>
 * {@link Executor}s on one {@link Computer} are transparently interchangeable
 * (that is the definition of {@link Computer}).
 *
 * <p>
 * This object is related to {@link Node} but they have some significant differences.
 * {@link Computer} primarily works as a holder of {@link Executor}s, so
 * if a {@link Node} is configured (probably temporarily) with 0 executors,
 * you won't have a {@link Computer} object for it (except for the master node,
 * which always gets its {@link Computer} in case we have no static executors and
 * we need to run a {@link FlyweightTask} - see JENKINS-7291 for more discussion.)
 *
 * Also, even if you remove a {@link Node}, it takes time for the corresponding
 * {@link Computer} to be removed, if some builds are already in progress on that
 * node. Or when the node configuration is changed, unaffected {@link Computer} object
 * remains intact, while all the {@link Node} objects will go away.
 *
 * <p>
 * This object also serves UI (unlike {@link Node}), and can be used along with
 * {@link TransientComputerActionFactory} to add {@link Action}s to {@link Computer}s.
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public /*transient*/ abstract class Computer extends Actionable implements AccessControlled, ExecutorListener {

    private final CopyOnWriteArrayList<Executor> executors = new CopyOnWriteArrayList<Executor>();
    // TODO:
    private final CopyOnWriteArrayList<OneOffExecutor> oneOffExecutors = new CopyOnWriteArrayList<OneOffExecutor>();

    private int numExecutors;

    /**
     * Contains info about reason behind computer being offline.
     */
    protected volatile OfflineCause offlineCause;

    private long connectTime = 0;

    /**
     * True if Jenkins shouldn't start new builds on this node.
     */
    private boolean temporarilyOffline;

    /**
     * {@link Node} object may be created and deleted independently
     * from this object.
     */
    protected String nodeName;

    /**
     * @see #getHostName()
     */
    private volatile String cachedHostName;
    private volatile boolean hostNameCached;

    /**
     * @see #getEnvironment()
     */
    private volatile EnvVars cachedEnvironment;


    private final WorkspaceList workspaceList = new WorkspaceList();

    protected transient List<Action> transientActions;

    protected final Object statusChangeLock = new Object();

    /**
     * Keeps track of stack traces to track the tremination requests for this computer.
     *
     * @since 1.607
     * @see Executor#resetWorkUnit(String)
     */
    private transient final List<TerminationRequest> terminatedBy = Collections.synchronizedList(new ArrayList
            <TerminationRequest>());

    /**
     * This method captures the information of a request to terminate a computer instance. Method is public as
     * it needs to be called from {@link AbstractCloudSlave} and {@link jenkins.model.Nodes}. In general you should
     * not need to call this method directly, however if implementing a custom node type or a different path
     * for removing nodes, it may make sense to call this method in order to capture the originating request.
     *
     * @since 1.607
     */
    public void recordTermination() {
        StaplerRequest request = Stapler.getCurrentRequest();
        if (request != null) {
            terminatedBy.add(new TerminationRequest(
                    String.format("Termination requested at %s by %s [id=%d] from HTTP request for %s",
                            new Date(),
                            Thread.currentThread(),
                            Thread.currentThread().getId(),
                            request.getRequestURL()
                    )
            ));
        } else {
            terminatedBy.add(new TerminationRequest(
                    String.format("Termination requested at %s by %s [id=%d]",
                            new Date(),
                            Thread.currentThread(),
                            Thread.currentThread().getId()
                    )
            ));
        }
    }

    /**
     * Returns the list of captured termination requests for this Computer. This method is used by {@link Executor}
     * to provide details on why a Computer was removed in-between work being scheduled against the {@link Executor}
     * and the {@link Executor} starting to execute the task.
     *
     * @return the (possibly empty) list of termination requests.
     * @see Executor#resetWorkUnit(String)
     * @since 1.607
     */
    public List<TerminationRequest> getTerminatedBy() {
        return new ArrayList<TerminationRequest>(terminatedBy);
    }

    public Computer(Node node) {
        setNode(node);
    }

     /**
     * Returns list of all boxes {@link ComputerPanelBox}s.
     */
    public List<ComputerPanelBox> getComputerPanelBoxs(){
        return ComputerPanelBox.all(this);
    }

    /**
     * Returns the transient {@link Action}s associated with the computer.
     */
    @SuppressWarnings("deprecation")
    public List<Action> getActions() {
    	List<Action> result = new ArrayList<Action>();
    	result.addAll(super.getActions());
    	synchronized (this) {
    		if (transientActions == null) {
    			transientActions = TransientComputerActionFactory.createAllFor(this);
    		}
    		result.addAll(transientActions);
    	}
    	return Collections.unmodifiableList(result);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void addAction(Action a) {
        if(a==null) throw new IllegalArgumentException();
        super.getActions().add(a);
    }

    /**
     * This is where the log from the remote agent goes.
     * The method also creates a log directory if required.
     * @see #getLogDir(), #relocateOldLogs()
     */
    public @Nonnull File getLogFile() {
        return new File(getLogDir(),"slave.log");
    }

    /**
     * Directory where rotated agent logs are stored.
     *
     * The method also creates a log directory if required.
     *
     * @since 1.613
     */
    protected @Nonnull File getLogDir() {
        File dir = new File(Jenkins.getInstance().getRootDir(),"logs/slaves/"+nodeName);
        if (!dir.exists() && !dir.mkdirs()) {
            LOGGER.severe("Failed to create agent log directory " + dir.getAbsolutePath());
        }
        return dir;
    }

    /**
     * Gets the object that coordinates the workspace allocation on this computer.
     */
    public WorkspaceList getWorkspaceList() {
        return workspaceList;
    }

    /**
     * Gets the string representation of the agent log.
     */
    public String getLog() throws IOException {
        return Util.loadFile(getLogFile());
    }

    /**
     * Used to URL-bind {@link AnnotatedLargeText}.
     */
    public AnnotatedLargeText<Computer> getLogText() {
        return new AnnotatedLargeText<Computer>(getLogFile(), Charset.defaultCharset(), false, this);
    }

    public ACL getACL() {
        return Jenkins.getInstance().getAuthorizationStrategy().getACL(this);
    }

    public void checkPermission(Permission permission) {
        getACL().checkPermission(permission);
    }

    public boolean hasPermission(Permission permission) {
        return getACL().hasPermission(permission);
    }

    /**
     * If the computer was offline (either temporarily or not),
     * this method will return the cause.
     *
     * @return
     *      null if the system was put offline without given a cause.
     */
    @Exported
    public OfflineCause getOfflineCause() {
        return offlineCause;
    }

    /**
     * If the computer was offline (either temporarily or not),
     * this method will return the cause as a string (without user info).
     *
     * @return
     *      empty string if the system was put offline without given a cause.
     */
    @Exported
    public String getOfflineCauseReason() {
        if (offlineCause == null) {
            return "";
        }
        // fetch the localized string for "Disconnected By"
        String gsub_base = hudson.slaves.Messages.SlaveComputer_DisconnectedBy("","");
        // regex to remove commented reason base string
        String gsub1 = "^" + gsub_base + "[\\w\\W]* \\: ";
        // regex to remove non-commented reason base string
        String gsub2 = "^" + gsub_base + "[\\w\\W]*";

        String newString = offlineCause.toString().replaceAll(gsub1, "");
        return newString.replaceAll(gsub2, "");
    }

    /**
     * Gets the channel that can be used to run a program on this computer.
     *
     * @return
     *      never null when {@link #isOffline()}==false.
     */
    public abstract @Nullable VirtualChannel getChannel();

    /**
     * Gets the default charset of this computer.
     *
     * @return
     *      never null when {@link #isOffline()}==false.
     */
    public abstract Charset getDefaultCharset();

    /**
     * Gets the logs recorded by this agent.
     */
    public abstract List<LogRecord> getLogRecords() throws IOException, InterruptedException;

    /**
     * If {@link #getChannel()}==null, attempts to relaunch the agent.
     */
    public abstract void doLaunchSlaveAgent( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException;

    /**
     * @deprecated since 2009-01-06.  Use {@link #connect(boolean)}
     */
    @Deprecated
    public final void launch() {
        connect(true);
    }

    /**
     * Do the same as {@link #doLaunchSlaveAgent(StaplerRequest, StaplerResponse)}
     * but outside the context of serving a request.
     *
     * <p>
     * If already connected or if this computer doesn't support proactive launching, no-op.
     * This method may return immediately
     * while the launch operation happens asynchronously.
     *
     * @see #disconnect()
     *
     * @param forceReconnect
     *      If true and a connect activity is already in progress, it will be cancelled and
     *      the new one will be started. If false, and a connect activity is already in progress, this method
     *      will do nothing and just return the pending connection operation.
     * @return
     *      A {@link Future} representing pending completion of the task. The 'completion' includes
     *      both a successful completion and a non-successful completion (such distinction typically doesn't
     *      make much sense because as soon as {@link Computer} is connected it can be disconnected by some other threads.)
     */
    public final Future<?> connect(boolean forceReconnect) {
    	connectTime = System.currentTimeMillis();
    	return _connect(forceReconnect);
    }

    /**
     * Allows implementing-classes to provide an implementation for the connect method.
     *
     * <p>
     * If already connected or if this computer doesn't support proactive launching, no-op.
     * This method may return immediately
     * while the launch operation happens asynchronously.
     *
     * @see #disconnect()
     *
     * @param forceReconnect
     *      If true and a connect activity is already in progress, it will be cancelled and
     *      the new one will be started. If false, and a connect activity is already in progress, this method
     *      will do nothing and just return the pending connection operation.
     * @return
     *      A {@link Future} representing pending completion of the task. The 'completion' includes
     *      both a successful completion and a non-successful completion (such distinction typically doesn't
     *      make much sense because as soon as {@link Computer} is connected it can be disconnected by some other threads.)
     */
    protected abstract Future<?> _connect(boolean forceReconnect);

    /**
     * @deprecated Implementation of CLI command "connect-node" moved to {@link hudson.cli.ConnectNodeCommand}.
     *
     * @param force
     *      If true cancel any currently pending connect operation and retry from scratch
     */
    @Deprecated
    public void cliConnect(boolean force) throws ExecutionException, InterruptedException {
        checkPermission(CONNECT);
        connect(force).get();
    }

    /**
     * Gets the time (since epoch) when this computer connected.
     *
     * @return The time in ms since epoch when this computer last connected.
     */
    public final long getConnectTime() {
    	return connectTime;
    }

    /**
     * Disconnect this computer.
     *
     * If this is the master, no-op. This method may return immediately
     * while the launch operation happens asynchronously.
     *
     * @param cause
     *      Object that identifies the reason the node was disconnected.
     *
     * @return
     *      {@link Future} to track the asynchronous disconnect operation.
     * @see #connect(boolean)
     * @since 1.320
     */
    public Future<?> disconnect(OfflineCause cause) {
        recordTermination();
        offlineCause = cause;
        if (Util.isOverridden(Computer.class,getClass(),"disconnect"))
            return disconnect();    // legacy subtypes that extend disconnect().

        connectTime=0;
        return Futures.precomputed(null);
    }

    /**
     * Equivalent to {@code disconnect(null)}
     *
     * @deprecated as of 1.320.
     *      Use {@link #disconnect(OfflineCause)} and specify the cause.
     */
    @Deprecated
    public Future<?> disconnect() {
        recordTermination();
        if (Util.isOverridden(Computer.class,getClass(),"disconnect",OfflineCause.class))
            // if the subtype already derives disconnect(OfflineCause), delegate to it
            return disconnect(null);

        connectTime=0;
        return Futures.precomputed(null);
    }

    /**
     * @deprecated Implementation of CLI command "disconnect-node" moved to {@link hudson.cli.DisconnectNodeCommand}.
     *
     * @param cause
     *      Record the note about why you are disconnecting this node
     */
    @Deprecated
    public void cliDisconnect(String cause) throws ExecutionException, InterruptedException {
        checkPermission(DISCONNECT);
        disconnect(new ByCLI(cause)).get();
    }

    /**
     * @deprecated  Implementation of CLI command "offline-node" moved to {@link hudson.cli.OfflineNodeCommand}.
     *
     * @param cause
     *      Record the note about why you are disconnecting this node
     */
    @Deprecated
    public void cliOffline(String cause) throws ExecutionException, InterruptedException {
        checkPermission(DISCONNECT);
        setTemporarilyOffline(true, new ByCLI(cause));
    }

    /**
     * @deprecated Implementation of CLI command "online-node" moved to {@link hudson.cli.OnlineNodeCommand}.
     */
    @Deprecated
    public void cliOnline() throws ExecutionException, InterruptedException {
        checkPermission(CONNECT);
        setTemporarilyOffline(false, null);
    }

    /**
     * Number of {@link Executor}s that are configured for this computer.
     *
     * <p>
     * When this value is decreased, it is temporarily possible
     * for {@link #executors} to have a larger number than this.
     */
    // ugly name to let EL access this
    @Exported
    public int getNumExecutors() {
        return numExecutors;
    }

    /**
     * Returns {@link Node#getNodeName() the name of the node}.
     */
    public @Nonnull String getName() {
        return nodeName != null ? nodeName : "";
    }

    /**
     * True if this computer is a Unix machine (as opposed to Windows machine).
     *
     * @since 1.624
     * @return
     *      null if the computer is disconnected and therefore we don't know whether it is Unix or not.
     */
    public abstract @CheckForNull Boolean isUnix();

    /**
     * Returns the {@link Node} that this computer represents.
     *
     * @return
     *      null if the configuration has changed and the node is removed, yet the corresponding {@link Computer}
     *      is not yet gone.
     */
    @CheckForNull
    public Node getNode() {
        Jenkins j = Jenkins.getInstanceOrNull(); // TODO confirm safe to assume non-null and use getInstance()
        if (j == null) {
            return null;
        }
        if (nodeName == null) {
            return j;
        }
        return j.getNode(nodeName);
    }

    @Exported
    public LoadStatistics getLoadStatistics() {
        return LabelAtom.get(nodeName != null ? nodeName : Jenkins.getInstance().getSelfLabel().toString()).loadStatistics;
    }

    public BuildTimelineWidget getTimeline() {
        return new BuildTimelineWidget(getBuilds());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void taskAccepted(Executor executor, Queue.Task task) {
        // dummy implementation
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        // dummy implementation
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        // dummy implementation
    }

    @Exported
    public boolean isOffline() {
        return temporarilyOffline || getChannel()==null;
    }

    public final boolean isOnline() {
        return !isOffline();
    }

    /**
     * This method is called to determine whether manual launching of the agent is allowed at this point in time.
     * @return {@code true} if manual launching of the agent is allowed at this point in time.
     */
    @Exported
    public boolean isManualLaunchAllowed() {
        return getRetentionStrategy().isManualLaunchAllowed(this);
    }


    /**
     * Is a {@link #connect(boolean)} operation in progress?
     */
    public abstract boolean isConnecting();

    /**
     * Returns true if this computer is supposed to be launched via JNLP.
     * @deprecated since 2008-05-18.
     *     See {@linkplain #isLaunchSupported()} and {@linkplain ComputerLauncher}
     */
    @Exported
    @Deprecated
    public boolean isJnlpAgent() {
        return false;
    }

    /**
     * Returns true if this computer can be launched by Hudson proactively and automatically.
     *
     * <p>
     * For example, JNLP agents return {@code false} from this, because the launch process
     * needs to be initiated from the agent side.
     */
    @Exported
    public boolean isLaunchSupported() {
        return true;
    }

    /**
     * Returns true if this node is marked temporarily offline by the user.
     *
     * <p>
     * In contrast, {@link #isOffline()} represents the actual online/offline
     * state. For example, this method may return false while {@link #isOffline()}
     * returns true if the agent failed to launch.
     *
     * @deprecated
     *      You should almost always want {@link #isOffline()}.
     *      This method is marked as deprecated to warn people when they
     *      accidentally call this method.
     */
    @Exported
    @Deprecated
    public boolean isTemporarilyOffline() {
        return temporarilyOffline;
    }

    /**
     * @deprecated as of 1.320.
     *      Use {@link #setTemporarilyOffline(boolean, OfflineCause)}
     */
    @Deprecated
    public void setTemporarilyOffline(boolean temporarilyOffline) {
        setTemporarilyOffline(temporarilyOffline,null);
    }

    /**
     * Marks the computer as temporarily offline. This retains the underlying
     * {@link Channel} connection, but prevent builds from executing.
     *
     * @param cause
     *      If the first argument is true, specify the reason why the node is being put
     *      offline.
     */
    public void setTemporarilyOffline(boolean temporarilyOffline, OfflineCause cause) {
        offlineCause = temporarilyOffline ? cause : null;
        this.temporarilyOffline = temporarilyOffline;
        Node node = getNode();
        if (node != null) {
            node.setTemporaryOfflineCause(offlineCause);
        }
        synchronized (statusChangeLock) {
            statusChangeLock.notifyAll();
        }
        for (ComputerListener cl : ComputerListener.all()) {
            if (temporarilyOffline)     cl.onTemporarilyOffline(this,cause);
            else                        cl.onTemporarilyOnline(this);
        }
    }

    /**
     * Returns the icon for this computer.
     * 	 
     * It is both the recommended and default implementation to serve different icons based on {@link #isOffline}
     * 
     * @see #getIconClassName()
     */
    @Exported
    public String getIcon() {
        if(isOffline())
            return "computer-x.png";
        else
            return "computer.png";
    }

    /**
     * Returns the class name that will be used to lookup the icon.
     * 
     * This class name will be added as a class tag to the html img tags where the icon should
     * show up followed by a size specifier given by {@link Icon#toNormalizedIconSizeClass(String)}
     * The conversion of class tag to src tag is registered through {@link IconSet#addIcon(Icon)}
     * 
     * It is both the recommended and default implementation to serve different icons based on {@link #isOffline}
     * 
     * @see #getIcon()
     */
    @Exported
    public String getIconClassName() {
        if(isOffline())
            return "icon-computer-x";
        else
            return "icon-computer";
    }

    public String getIconAltText() {
        if(isOffline())
            return "[offline]";
        else
            return "[online]";
    }

    @Exported
    @Override public @Nonnull String getDisplayName() {
        return nodeName;
    }

    public String getCaption() {
        return Messages.Computer_Caption(nodeName);
    }

    public String getUrl() {
        return "computer/" + Util.rawEncode(getName()) + "/";
    }

    /**
     * Returns projects that are tied on this node.
     */
    public List<AbstractProject> getTiedJobs() {
        Node node = getNode();
        return (node != null) ? node.getSelfLabel().getTiedJobs() : Collections.EMPTY_LIST;
    }

    public RunList getBuilds() {
    	return new RunList(Jenkins.getInstance().getAllItems(Job.class)).node(getNode());
    }

    /**
     * Called to notify {@link Computer} that its corresponding {@link Node}
     * configuration is updated.
     */
    protected void setNode(Node node) {
        assert node!=null;
        if(node instanceof Slave)
            this.nodeName = node.getNodeName();
        else
            this.nodeName = null;

        setNumExecutors(node.getNumExecutors());
        if (this.temporarilyOffline) {
            // When we get a new node, push our current temp offline
            // status to it (as the status is not carried across
            // configuration changes that recreate the node).
            // Since this is also called the very first time this
            // Computer is created, avoid pushing an empty status
            // as that could overwrite any status that the Node
            // brought along from its persisted config data.
            node.setTemporaryOfflineCause(this.offlineCause);
        }
    }

    /**
     * Called by {@link Jenkins#updateComputerList()} to notify {@link Computer} that it will be discarded.
     *
     * <p>
     * Note that at this point {@link #getNode()} returns null.
     *
     * @see #onRemoved()
     */
    protected void kill() {
        // On most code paths, this should already be zero, and thus this next call becomes a no-op... and more
        // importantly it will not acquire a lock on the Queue... not that the lock is bad, more that the lock
        // may delay unnecessarily
        setNumExecutors(0);
    }

    /**
     * Called by {@link Jenkins#updateComputerList()} to notify {@link Computer} that it will be discarded.
     *
     * <p>
     * Note that at this point {@link #getNode()} returns null.
     *
     * <p>
     * Note that the Queue lock is already held when this method is called.
     *
     * @see #onRemoved()
     */
    @Restricted(NoExternalUse.class)
    @GuardedBy("hudson.model.Queue.lock")
    /*package*/ void inflictMortalWound() {
        setNumExecutors(0);
    }

    /**
     * Called by {@link Jenkins} when this computer is removed.
     *
     * <p>
     * This happens when list of nodes are updated (for example by {@link Jenkins#setNodes(List)} and
     * the computer becomes redundant. Such {@link Computer}s get {@linkplain #kill() killed}, then
     * after all its executors are finished, this method is called.
     *
     * <p>
     * Note that at this point {@link #getNode()} returns null.
     *
     * @see #kill()
     * @since 1.510
     */
    protected void onRemoved(){
    }

    private synchronized void setNumExecutors(int n) {
        this.numExecutors = n;
        final int diff = executors.size()-n;

        if (diff>0) {
            // we have too many executors
            // send signal to all idle executors to potentially kill them off
            // need the Queue maintenance lock held to prevent concurrent job assignment on the idle executors
            Queue.withLock(new Runnable() {
                @Override
                public void run() {
                    for( Executor e : executors )
                        if(e.isIdle())
                            e.interrupt();
                }
            });
        }

        if (diff<0) {
            // if the number is increased, add new ones
            addNewExecutorIfNecessary();
        }
    }

    private void addNewExecutorIfNecessary() {
        Set<Integer> availableNumbers  = new HashSet<Integer>();
        for (int i = 0; i < numExecutors; i++)
            availableNumbers.add(i);

        for (Executor executor : executors)
            availableNumbers.remove(executor.getNumber());

        for (Integer number : availableNumbers) {
            /* There may be busy executors with higher index, so only
               fill up until numExecutors is reached.
               Extra executors will call removeExecutor(...) and that
               will create any necessary executors from #0 again. */
            if (executors.size() < numExecutors) {
                Executor e = new Executor(this, number);
                executors.add(e);
            }
        }

    }

    /**
     * Returns the number of idle {@link Executor}s that can start working immediately.
     */
    public int countIdle() {
        int n = 0;
        for (Executor e : executors) {
            if(e.isIdle())
                n++;
        }
        return n;
    }

    /**
     * Returns the number of {@link Executor}s that are doing some work right now.
     */
    public final int countBusy() {
        return countExecutors()-countIdle();
    }

    /**
     * Returns the current size of the executor pool for this computer.
     * This number may temporarily differ from {@link #getNumExecutors()} if there
     * are busy tasks when the configured size is decreased.  OneOffExecutors are
     * not included in this count.
     */
    public final int countExecutors() {
        return executors.size();
    }

    /**
     * Gets the read-only snapshot view of all {@link Executor}s.
     */
    @Exported
    public List<Executor> getExecutors() {
        return new ArrayList<Executor>(executors);
    }

    /**
     * Gets the read-only snapshot view of all {@link OneOffExecutor}s.
     */
    @Exported
    public List<OneOffExecutor> getOneOffExecutors() {
        return new ArrayList<OneOffExecutor>(oneOffExecutors);
    }

    /**
     * Used to render the list of executors.
     * @return a snapshot of the executor display information
     * @since 1.607
     */
    @Restricted(NoExternalUse.class)
    public List<DisplayExecutor> getDisplayExecutors() {
        // The size may change while we are populating, but let's start with a reasonable guess to minimize resizing
        List<DisplayExecutor> result = new ArrayList<DisplayExecutor>(executors.size()+oneOffExecutors.size());
        int index = 0;
        for (Executor e: executors) {
            if (e.isDisplayCell()) {
                result.add(new DisplayExecutor(Integer.toString(index + 1), String.format("executors/%d", index), e));
            }
            index++;
        }
        index = 0;
        for (OneOffExecutor e: oneOffExecutors) {
            if (e.isDisplayCell()) {
                result.add(new DisplayExecutor("", String.format("oneOffExecutors/%d", index), e));
            }
            index++;
        }
        return result;
    }

    /**
     * Returns true if all the executors of this computer are idle.
     */
    @Exported
    public final boolean isIdle() {
        if (!oneOffExecutors.isEmpty())
            return false;
        for (Executor e : executors)
            if(!e.isIdle())
                return false;
        return true;
    }

    /**
     * Returns true if this computer has some idle executors that can take more workload.
     */
    public final boolean isPartiallyIdle() {
        for (Executor e : executors)
            if(e.isIdle())
                return true;
        return false;
    }

    /**
     * Returns the time when this computer last became idle.
     *
     * <p>
     * If this computer is already idle, the return value will point to the
     * time in the past since when this computer has been idle.
     *
     * <p>
     * If this computer is busy, the return value will point to the
     * time in the future where this computer will be expected to become free.
     */
    public final long getIdleStartMilliseconds() {
        long firstIdle = Long.MIN_VALUE;
        for (Executor e : oneOffExecutors) {
            firstIdle = Math.max(firstIdle, e.getIdleStartMilliseconds());
        }
        for (Executor e : executors) {
            firstIdle = Math.max(firstIdle, e.getIdleStartMilliseconds());
        }
        return firstIdle;
    }

    /**
     * Returns the time when this computer first became in demand.
     */
    public final long getDemandStartMilliseconds() {
        long firstDemand = Long.MAX_VALUE;
        for (Queue.BuildableItem item : Jenkins.getInstance().getQueue().getBuildableItems(this)) {
            firstDemand = Math.min(item.buildableStartMilliseconds, firstDemand);
        }
        return firstDemand;
    }

    /**
     * Called by {@link Executor} to kill excessive executors from this computer.
     */
    protected void removeExecutor(final Executor e) {
        final Runnable task = new Runnable() {
            @Override
            public void run() {
                synchronized (Computer.this) {
                    executors.remove(e);
                    addNewExecutorIfNecessary();
                    if (!isAlive()) {
                        AbstractCIBase ciBase = Jenkins.getInstanceOrNull();
                        if (ciBase != null) { // TODO confirm safe to assume non-null and use getInstance()
                            ciBase.removeComputer(Computer.this);
                        }
                    }
                }
            }
        };
        if (!Queue.tryWithLock(task)) {
            // JENKINS-28840 if we couldn't get the lock push the operation to a separate thread to avoid deadlocks
            threadPoolForRemoting.submit(Queue.wrapWithLock(task));
        }
    }

    /**
     * Returns true if any of the executors are {@linkplain Executor#isActive active}.
     *
     * @since 1.509
     */
    protected boolean isAlive() {
        for (Executor e : executors)
            if (e.isActive())
                return true;
        return false;
    }

    /**
     * Interrupt all {@link Executor}s.
     * Called from {@link Jenkins#cleanUp}.
     */
    public void interrupt() {
        Queue.withLock(new Runnable() {
            @Override
            public void run() {
                for (Executor e : executors) {
                    e.interruptForShutdown();
                }
            }
        });
    }

    public String getSearchUrl() {
        return getUrl();
    }

    /**
     * {@link RetentionStrategy} associated with this computer.
     *
     * @return
     *      never null. This method return {@code RetentionStrategy<? super T>} where
     *      {@code T=this.getClass()}.
     */
    public abstract RetentionStrategy getRetentionStrategy();

    /**
     * Expose monitoring data for the remote API.
     */
    @Exported(inline=true)
    public Map<String/*monitor name*/,Object> getMonitorData() {
        Map<String,Object> r = new HashMap<String, Object>();
        for (NodeMonitor monitor : NodeMonitor.getAll())
            r.put(monitor.getClass().getName(),monitor.data(this));
        return r;
    }

    /**
     * Gets the system properties of the JVM on this computer.
     * If this is the master, it returns the system property of the master computer.
     */
    public Map<Object,Object> getSystemProperties() throws IOException, InterruptedException {
        return RemotingDiagnostics.getSystemProperties(getChannel());
    }

    /**
     * @deprecated as of 1.292
     *      Use {@link #getEnvironment()} instead.
     */
    @Deprecated
    public Map<String,String> getEnvVars() throws IOException, InterruptedException {
        return getEnvironment();
    }

    /**
     * Returns cached environment variables (copy to prevent modification) for the JVM on this computer.
     * If this is the master, it returns the system property of the master computer.
     */
    public EnvVars getEnvironment() throws IOException, InterruptedException {
        EnvVars cachedEnvironment = this.cachedEnvironment;
        if (cachedEnvironment != null) {
            return new EnvVars(cachedEnvironment);
        }

        cachedEnvironment = EnvVars.getRemote(getChannel());
        this.cachedEnvironment = cachedEnvironment;
        return new EnvVars(cachedEnvironment);
    }

    /**
     * Creates an environment variable override to be used for launching processes on this node.
     *
     * @see ProcStarter#envs(Map)
     * @since 1.489
     */
    public @Nonnull EnvVars buildEnvironment(@Nonnull TaskListener listener) throws IOException, InterruptedException {
        EnvVars env = new EnvVars();

        Node node = getNode();
        if (node==null)     return env; // bail out

        for (NodeProperty nodeProperty: Jenkins.getInstance().getGlobalNodeProperties()) {
            nodeProperty.buildEnvVars(env,listener);
        }

        for (NodeProperty nodeProperty: node.getNodeProperties()) {
            nodeProperty.buildEnvVars(env,listener);
        }

        // TODO: hmm, they don't really belong
        String rootUrl = Jenkins.getInstance().getRootUrl();
        if(rootUrl!=null) {
            env.put("HUDSON_URL", rootUrl); // Legacy.
            env.put("JENKINS_URL", rootUrl);
        }

        return env;
    }

    /**
     * Gets the thread dump of the agent JVM.
     * @return
     *      key is the thread name, and the value is the pre-formatted dump.
     */
    public Map<String,String> getThreadDump() throws IOException, InterruptedException {
        return RemotingDiagnostics.getThreadDump(getChannel());
    }

    /**
     * Obtains the heap dump.
     */
    public HeapDump getHeapDump() throws IOException {
        return new HeapDump(this,getChannel());
    }

    /**
     * This method tries to compute the name of the host that's reachable by all the other nodes.
     *
     * <p>
     * Since it's possible that the agent is not reachable from the master (it may be behind a firewall,
     * connecting to master via JNLP), this method may return null.
     *
     * It's surprisingly tricky for a machine to know a name that other systems can get to,
     * especially between things like DNS search suffix, the hosts file, and YP.
     *
     * <p>
     * So the technique here is to compute possible interfaces and names on the agent,
     * then try to ping them from the master, and pick the one that worked.
     *
     * <p>
     * The computation may take some time, so it employs caching to make the successive lookups faster.
     *
     * @since 1.300
     * @return
     *      null if the host name cannot be computed (for example because this computer is offline,
     *      because the agent is behind the firewall, etc.)
     */
    public String getHostName() throws IOException, InterruptedException {
        if(hostNameCached)
            // in the worst case we end up having multiple threads computing the host name simultaneously, but that's not harmful, just wasteful.
            return cachedHostName;

        VirtualChannel channel = getChannel();
        if(channel==null)   return null; // can't compute right now

        for( String address : channel.call(new ListPossibleNames())) {
            try {
                InetAddress ia = InetAddress.getByName(address);
                if(!(ia instanceof Inet4Address)) {
                    LOGGER.log(Level.FINE, "{0} is not an IPv4 address", address);
                    continue;
                }
                if(!ComputerPinger.checkIsReachable(ia, 3)) {
                    LOGGER.log(Level.FINE, "{0} didn't respond to ping", address);
                    continue;
                }
                cachedHostName = ia.getCanonicalHostName();
                hostNameCached = true;
                return cachedHostName;
            } catch (IOException e) {
                // if a given name fails to parse on this host, we get this error
                LogRecord lr = new LogRecord(Level.FINE, "Failed to parse {0}");
                lr.setThrown(e);
                lr.setParameters(new Object[]{address});
                LOGGER.log(lr);
            }
        }

        // allow the administrator to manually specify the host name as a fallback. HUDSON-5373
        cachedHostName = channel.call(new GetFallbackName());
        hostNameCached = true;
        return cachedHostName;
    }

    /**
     * Starts executing a fly-weight task.
     */
    /*package*/ final void startFlyWeightTask(WorkUnit p) {
        OneOffExecutor e = new OneOffExecutor(this);
        e.start(p);
        oneOffExecutors.add(e);
    }

    /*package*/ final void remove(OneOffExecutor e) {
        oneOffExecutors.remove(e);
    }

    private static class ListPossibleNames extends MasterToSlaveCallable<List<String>,IOException> {
        /**
         * In the normal case we would use {@link Computer} as the logger's name, however to
         * do that we would have to send the {@link Computer} class over to the remote classloader
         * and then it would need to be loaded, which pulls in {@link Jenkins} and loads that
         * and then that fails to load as you are not supposed to do that. Another option
         * would be to export the logger over remoting, with increased complexity as a result.
         * Instead we just use a loger based on this class name and prevent any references to
         * other classes from being transferred over remoting.
         */
        private static final Logger LOGGER = Logger.getLogger(ListPossibleNames.class.getName());
        
        public List<String> call() throws IOException {
            List<String> names = new ArrayList<String>();

            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni =  nis.nextElement();
                LOGGER.log(Level.FINE, "Listing up IP addresses for {0}", ni.getDisplayName());
                Enumeration<InetAddress> e = ni.getInetAddresses();
                while (e.hasMoreElements()) {
                    InetAddress ia =  e.nextElement();
                    if(ia.isLoopbackAddress()) {
                        LOGGER.log(Level.FINE, "{0} is a loopback address", ia);
                        continue;
                    }

                    if(!(ia instanceof Inet4Address)) {
                        LOGGER.log(Level.FINE, "{0} is not an IPv4 address", ia);
                        continue;
                    }

                    LOGGER.log(Level.FINE, "{0} is a viable candidate", ia);
                    names.add(ia.getHostAddress());
                }
            }
            return names;
        }
        private static final long serialVersionUID = 1L;
    }

    private static class GetFallbackName extends MasterToSlaveCallable<String,IOException> {
        public String call() throws IOException {
            return SystemProperties.getString("host.name");
        }
        private static final long serialVersionUID = 1L;
    }

    public static final ExecutorService threadPoolForRemoting = new ContextResettingExecutorService(
            Executors.newCachedThreadPool(
                    new ExceptionCatchingThreadFactory(
                            new NamingThreadFactory(new DaemonThreadFactory(), "Computer.threadPoolForRemoting"))));

//
//
// UI
//
//
    public void doRssAll( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        rss(req, rsp, " all builds", getBuilds());
    }

    public void doRssFailed(StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        rss(req, rsp, " failed builds", getBuilds().failureOnly());
    }
    private void rss(StaplerRequest req, StaplerResponse rsp, String suffix, RunList runs) throws IOException, ServletException {
        RSS.forwardToRss(getDisplayName() + suffix, getUrl(),
                runs.newBuilds(), Run.FEED_ADAPTER, req, rsp);
    }

    @RequirePOST
    public HttpResponse doToggleOffline(@QueryParameter String offlineMessage) throws IOException, ServletException {
        if(!temporarilyOffline) {
            checkPermission(DISCONNECT);
            offlineMessage = Util.fixEmptyAndTrim(offlineMessage);
            setTemporarilyOffline(!temporarilyOffline,
                    new OfflineCause.UserCause(User.current(), offlineMessage));
        } else {
            checkPermission(CONNECT);
            setTemporarilyOffline(!temporarilyOffline,null);
        }
        return HttpResponses.redirectToDot();
    }

    @RequirePOST
    public HttpResponse doChangeOfflineCause(@QueryParameter String offlineMessage) throws IOException, ServletException {
        checkPermission(DISCONNECT);
        offlineMessage = Util.fixEmptyAndTrim(offlineMessage);
        setTemporarilyOffline(true,
                new OfflineCause.UserCause(User.current(), offlineMessage));
        return HttpResponses.redirectToDot();
    }

    public Api getApi() {
        return new Api(this);
    }

    /**
     * Dumps the contents of the export table.
     */
    public void doDumpExportTable( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, InterruptedException {
        // this is a debug probe and may expose sensitive information
        checkPermission(Jenkins.ADMINISTER);

        rsp.setContentType("text/plain");
        try (PrintWriter w = new PrintWriter(rsp.getCompressedWriter(req))) {
            VirtualChannel vc = getChannel();
            if (vc instanceof Channel) {
                w.println("Master to slave");
                ((Channel) vc).dumpExportTable(w);
                w.flush(); // flush here once so that even if the dump from the agent fails, the client gets some useful info

                w.println("\n\n\nSlave to master");
                w.print(vc.call(new DumpExportTableTask()));
            } else {
                w.println(Messages.Computer_BadChannel());
            }
        }
    }

    private static final class DumpExportTableTask extends MasterToSlaveCallable<String,IOException> {
        public String call() throws IOException {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            Channel.current().dumpExportTable(pw);
            pw.close();
            return sw.toString();
        }
    }

    /**
     * For system diagnostics.
     * Run arbitrary Groovy script.
     */
    public void doScript(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        _doScript(req, rsp, "_script.jelly");
    }

    /**
     * Run arbitrary Groovy script and return result as plain text.
     */
    public void doScriptText(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        _doScript(req, rsp, "_scriptText.jelly");
    }

    protected void _doScript(StaplerRequest req, StaplerResponse rsp, String view) throws IOException, ServletException {
        Jenkins._doScript(req, rsp, req.getView(this, view), getChannel(), getACL());
    }

    /**
     * Accepts the update to the node configuration.
     */
    @RequirePOST
    public void doConfigSubmit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, FormException {
        checkPermission(CONFIGURE);

        String proposedName = Util.fixEmptyAndTrim(req.getSubmittedForm().getString("name"));
        Jenkins.checkGoodName(proposedName);

        Node node = getNode();
        if (node == null) {
            throw new ServletException("No such node " + nodeName);
        }

        if ((!proposedName.equals(nodeName))
                && Jenkins.getActiveInstance().getNode(proposedName) != null) {
            throw new FormException(Messages.ComputerSet_SlaveAlreadyExists(proposedName), "name");
        }

        Node result = node.reconfigure(req, req.getSubmittedForm());
        Jenkins.getInstance().getNodesObject().replaceNode(this.getNode(), result);

        // take the user back to the agent top page.
        rsp.sendRedirect2("../" + result.getNodeName() + '/');
    }

    /**
     * Accepts <tt>config.xml</tt> submission, as well as serve it.
     */
    @WebMethod(name = "config.xml")
    public void doConfigDotXml(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException {

        if (req.getMethod().equals("GET")) {
            // read
            checkPermission(EXTENDED_READ);
            rsp.setContentType("application/xml");
            Node node = getNode();
            if (node == null) {
                throw HttpResponses.notFound();
            }
            Jenkins.XSTREAM2.toXMLUTF8(node, rsp.getOutputStream());
            return;
        }
        if (req.getMethod().equals("POST")) {
            // submission
            updateByXml(req.getInputStream());
            return;
        }

        // huh?
        rsp.sendError(SC_BAD_REQUEST);
    }

    /**
     * Updates Job by its XML definition.
     *
     * @since 1.526
     */
    public void updateByXml(final InputStream source) throws IOException, ServletException {
        checkPermission(CONFIGURE);
        Node result = (Node)Jenkins.XSTREAM2.fromXML(source);
        Jenkins.getInstance().getNodesObject().replaceNode(this.getNode(), result);
    }

    /**
     * Really deletes the agent.
     */
    @RequirePOST
    public HttpResponse doDoDelete() throws IOException {
        checkPermission(DELETE);
        Node node = getNode();
        if (node != null) {
            Jenkins.getInstance().removeNode(node);
        } else {
            AbstractCIBase app = Jenkins.getInstance();
            app.removeComputer(this);
        }
        return new HttpRedirect("..");
    }

    /**
     * Blocks until the node becomes online/offline.
     */
    public void waitUntilOnline() throws InterruptedException {
        synchronized (statusChangeLock) {
            while (!isOnline())
                statusChangeLock.wait(1000);
        }
    }

    public void waitUntilOffline() throws InterruptedException {
        synchronized (statusChangeLock) {
            while (!isOffline())
                statusChangeLock.wait(1000);
        }
    }

    /**
     * Handles incremental log.
     */
    public void doProgressiveLog( StaplerRequest req, StaplerResponse rsp) throws IOException {
        getLogText().doProgressText(req, rsp);
    }

    /**
     * Gets the current {@link Computer} that the build is running.
     * This method only works when called during a build, such as by
     * {@link hudson.tasks.Publisher}, {@link hudson.tasks.BuildWrapper}, etc.
     * @return the {@link Computer} associated with {@link Executor#currentExecutor}, or (consistently as of 1.591) null if not on an executor thread
     */
    public static @Nullable Computer currentComputer() {
        Executor e = Executor.currentExecutor();
        return e != null ? e.getOwner() : null;
    }

    /**
     * Returns {@code true} if the computer is accepting tasks. Needed to allow agents programmatic suspension of task
     * scheduling that does not overlap with being offline.
     *
     * @return {@code true} if the computer is accepting tasks
     * @see hudson.slaves.RetentionStrategy#isAcceptingTasks(Computer)
     * @see hudson.model.Node#isAcceptingTasks()
     */
    @OverrideMustInvoke(When.ANYTIME)
    public boolean isAcceptingTasks() {
        final Node node = getNode();
        return getRetentionStrategy().isAcceptingTasks(this) && (node == null || node.isAcceptingTasks());
    }

    /**
     * Used for CLI binding.
     */
    @CLIResolver
    public static Computer resolveForCLI(
            @Argument(required=true,metaVar="NAME",usage="Agent name, or empty string for master") String name) throws CmdLineException {
        Jenkins h = Jenkins.getInstance();
        Computer item = h.getComputer(name);
        if (item==null) {
            List<String> names = ComputerSet.getComputerNames();
            String adv = EditDistance.findNearest(name, names);
            throw new IllegalArgumentException(adv == null ?
                    hudson.model.Messages.Computer_NoSuchSlaveExistsWithoutAdvice(name) :
                    hudson.model.Messages.Computer_NoSuchSlaveExists(name, adv));
        }
        return item;
    }

    /**
     * Relocate log files in the old location to the new location.
     *
     * Files were used to be $JENKINS_ROOT/slave-NAME.log (and .1, .2, ...)
     * but now they are at $JENKINS_ROOT/logs/slaves/NAME/slave.log (and .1, .2, ...)
     *
     * @see #getLogFile()
     */
    @Initializer
    public static void relocateOldLogs() {
        relocateOldLogs(Jenkins.getInstance().getRootDir());
    }

    /*package*/ static void relocateOldLogs(File dir) {
        final Pattern logfile = Pattern.compile("slave-(.*)\\.log(\\.[0-9]+)?");
        File[] logfiles = dir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return logfile.matcher(name).matches();
            }
        });
        if (logfiles==null)     return;

        for (File f : logfiles) {
            Matcher m = logfile.matcher(f.getName());
            if (m.matches()) {
                File newLocation = new File(dir, "logs/slaves/" + m.group(1) + "/slave.log" + Util.fixNull(m.group(2)));
                newLocation.getParentFile().mkdirs();
                boolean relocationSuccessful=f.renameTo(newLocation);
                if (relocationSuccessful) { // The operation will fail if mkdir fails
                    LOGGER.log(Level.INFO, "Relocated log file {0} to {1}",new Object[] {f.getPath(),newLocation.getPath()});
                } else {
                    LOGGER.log(Level.WARNING, "Cannot relocate log file {0} to {1}",new Object[] {f.getPath(),newLocation.getPath()});
                }
            } else {
                assert false;
            }
        }
    }

    /**
     * A value class to provide a consistent snapshot view of the state of an executor to avoid race conditions
     * during rendering of the executors list.
     *
     * @since 1.607
     */
    @Restricted(NoExternalUse.class)
    public static class DisplayExecutor implements ModelObject {

        @Nonnull
        private final String displayName;
        @Nonnull
        private final String url;
        @Nonnull
        private final Executor executor;

        public DisplayExecutor(@Nonnull String displayName, @Nonnull String url, @Nonnull Executor executor) {
            this.displayName = displayName;
            this.url = url;
            this.executor = executor;
        }

        @Override
        @Nonnull
        public String getDisplayName() {
            return displayName;
        }

        @Nonnull
        public String getUrl() {
            return url;
        }

        @Nonnull
        public Executor getExecutor() {
            return executor;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("DisplayExecutor{");
            sb.append("displayName='").append(displayName).append('\'');
            sb.append(", url='").append(url).append('\'');
            sb.append(", executor=").append(executor);
            sb.append('}');
            return sb.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            DisplayExecutor that = (DisplayExecutor) o;

            if (!executor.equals(that.executor)) {
                return false;
            }

            return true;
        }

        @Extension(ordinal = Double.MAX_VALUE)
        @Restricted(DoNotUse.class)
        public static class InternalComputerListener extends ComputerListener {
            @Override
            public void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
                c.cachedEnvironment = null;
            }
        }

        @Override
        public int hashCode() {
            return executor.hashCode();
        }
    }

    /**
     * Used to trace requests to terminate a computer.
     *
     * @since 1.607
     */
    public static class TerminationRequest extends RuntimeException {
        private final long when;
        public TerminationRequest(String message) {
            super(message);
            this.when = System.currentTimeMillis();
        }

        /**
         * Returns the when the termination request was created.
         *
         * @return the difference, measured in milliseconds, between
         * the time of the termination request and midnight, January 1, 1970 UTC.
         */
        public long getWhen() {
            return when;
        }
    }

    public static final PermissionGroup PERMISSIONS = new PermissionGroup(Computer.class,Messages._Computer_Permissions_Title());
    public static final Permission CONFIGURE = new Permission(PERMISSIONS,"Configure", Messages._Computer_ConfigurePermission_Description(), Permission.CONFIGURE, PermissionScope.COMPUTER);
    /**
     * @since 1.532
     */
    public static final Permission EXTENDED_READ = new Permission(PERMISSIONS,"ExtendedRead", Messages._Computer_ExtendedReadPermission_Description(), CONFIGURE, SystemProperties.getBoolean("hudson.security.ExtendedReadPermission"), new PermissionScope[]{PermissionScope.COMPUTER});
    public static final Permission DELETE = new Permission(PERMISSIONS,"Delete", Messages._Computer_DeletePermission_Description(), Permission.DELETE, PermissionScope.COMPUTER);
    public static final Permission CREATE = new Permission(PERMISSIONS,"Create", Messages._Computer_CreatePermission_Description(), Permission.CREATE, PermissionScope.JENKINS);
    public static final Permission DISCONNECT = new Permission(PERMISSIONS,"Disconnect", Messages._Computer_DisconnectPermission_Description(), Jenkins.ADMINISTER, PermissionScope.COMPUTER);
    public static final Permission CONNECT = new Permission(PERMISSIONS,"Connect", Messages._Computer_ConnectPermission_Description(), DISCONNECT, PermissionScope.COMPUTER);
    public static final Permission BUILD = new Permission(PERMISSIONS, "Build", Messages._Computer_BuildPermission_Description(),  Permission.WRITE, PermissionScope.COMPUTER);

    private static final Logger LOGGER = Logger.getLogger(Computer.class.getName());
}
