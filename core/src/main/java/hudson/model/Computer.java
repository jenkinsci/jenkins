package hudson.model;

import hudson.EnvVars;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.RetentionStrategy;
import hudson.node_monitors.NodeMonitor;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Publisher;
import hudson.util.DaemonThreadFactory;
import hudson.util.RemotingDiagnostics;
import hudson.util.RunList;
import hudson.util.ExceptionCatchingThreadFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.LogRecord;

import javax.servlet.ServletException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Represents the running state of a remote computer that holds {@link Executor}s.
 *
 * <p>
 * {@link Executor}s on one {@link Computer} are transparently interchangeable
 * (that is the definition of {@link Computer}.)
 *
 * <p>
 * This object is related to {@link Node} but they have some significant difference.
 * {@link Computer} primarily works as a holder of {@link Executor}s, so
 * if a {@link Node} is configured (probably temporarily) with 0 executors,
 * you won't have a {@link Computer} object for it.
 *
 * Also, even if you remove a {@link Node}, it takes time for the corresponding
 * {@link Computer} to be removed, if some builds are already in progress on that
 * node.
 *
 * <p>
 * This object also serves UI (since {@link Node} is an interface and can't have
 * related side pages.)
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public abstract class Computer extends AbstractModelObject implements AccessControlled, ExecutorListener {

	private final CopyOnWriteArrayList<Executor> executors = new CopyOnWriteArrayList<Executor>();

    private int numExecutors;

    /**
     * True if Hudson shouldn't start new builds on this node.
     */
    private boolean temporarilyOffline;

    /**
     * {@link Node} object may be created and deleted independently
     * from this object.
     */
    protected String nodeName;

    public Computer(Node node) {
        assert node.getNumExecutors()!=0 : "Computer created with 0 executors";
        setNode(node);
    }

    public ACL getACL() {
        return Hudson.getInstance().getAuthorizationStrategy().getACL(this);
    }

    public void checkPermission(Permission permission) {
        getACL().checkPermission(permission);
    }

    public boolean hasPermission(Permission permission) {
        return getACL().hasPermission(permission);
    }

    /**
     * Gets the channel that can be used to run a program on this computer.
     *
     * @return
     *      never null when {@link #isOffline()}==false.
     */
    public abstract VirtualChannel getChannel();

    /**
     * Gets the logs recorded by this slave.
     */
    public abstract List<LogRecord> getLogRecords() throws IOException, InterruptedException;

    /**
     * If {@link #getChannel()}==null, attempts to relaunch the slave agent.
     */
    public abstract void doLaunchSlaveAgent( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException;

    /**
     * Do the same as {@link #doLaunchSlaveAgent(StaplerRequest, StaplerResponse)}
     * but outside the context of serving a request.
     *
     * If already connected, no-op.
     */
    public abstract void launch();

    /**
     * Disconnect this computer.
     *
     * If this is the master, no-op
     */
    public void disconnect() { }

    /**
     * Number of {@link Executor}s that are configured for this computer.
     *
     * <p>
     * When this value is decreased, it is temporarily possible
     * for {@link #executors} to have a larger number than this.
     */
    // ugly name to let EL access this
    public int getNumExecutors() {
        return numExecutors;
    }

    /**
     * Returns the name of the node.
     */
    public String getName() {
        return nodeName;
    }

    /**
     * Returns the {@link Node} that this computer represents.
     */
    public Node getNode() {
        if(nodeName==null)
            return Hudson.getInstance();
        return Hudson.getInstance().getSlave(nodeName);
    }

    /**
     * {@inheritDoc}
     */
    public void taskAccepted(Executor executor, Queue.Task task) {
        // dummy implementation
    }

    /**
     * {@inheritDoc}
     */
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        // dummy implementation
    }

    /**
     * {@inheritDoc}
     */
    public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        // dummy implementation
    }

    @Exported
    public boolean isOffline() {
        return temporarilyOffline || getChannel()==null;
    }

    /**
     * Returns true if this computer is supposed to be launched via JNLP.
     * @deprecated see {@linkplain #isLaunchSupported()} and {@linkplain ComputerLauncher}
     */
    @Exported
    @Deprecated
    public boolean isJnlpAgent() {
        return false;
    }

    /**
     * Returns true if this computer can be launched by Hudson.
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
     * returns true if the slave agent failed to launch.
     *
     * @deprecated
     *      You should almost always want {@link #isOffline()}.
     *      This method is marked as deprecated to warn people when they
     *      accidentally call this method.
     */
    @Exported
    public boolean isTemporarilyOffline() {
        return temporarilyOffline;
    }

    public void setTemporarilyOffline(boolean temporarilyOffline) {
        this.temporarilyOffline = temporarilyOffline;
        Hudson.getInstance().getQueue().scheduleMaintenance();
    }

    @Exported
    public String getIcon() {
        if(isOffline())
            return "computer-x.gif";
        else
            return "computer.gif";
    }

    @Exported
    public String getDisplayName() {
        return nodeName;
    }

    public String getCaption() {
        return Messages.Computer_Caption(nodeName);
    }

    public String getUrl() {
        return "computer/"+getDisplayName()+"/";
    }

    /**
     * Returns projects that are tied on this node.
     */
    public List<AbstractProject> getTiedJobs() {
        return getNode().getSelfLabel().getTiedJobs();
    }

    public RunList getBuilds() {
    	return new RunList(Hudson.getInstance().getAllItems(Job.class)).node(getNode());
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
    }

    /**
     * Called to notify {@link Computer} that it will be discarded.
     */
    protected void kill() {
        setNumExecutors(0);
    }

    private synchronized void setNumExecutors(int n) {
        this.numExecutors = n;

        // send signal to all idle executors to potentially kill them off
        for( Executor e : executors )
            if(e.isIdle())
                e.interrupt();

        // if the number is increased, add new ones
        while(executors.size()<numExecutors)
            executors.add(new Executor(this));
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
     * Gets the read-only snapshot view of all {@link Executor}s.
     */
    public List<Executor> getExecutors() {
        return new ArrayList<Executor>(executors);
    }

    /**
     * Returns true if all the executors of this computer is idle.
     */
    public final boolean isIdle() {
        for (Executor e : executors)
            if(!e.isIdle())
                return false;
        return true;
    }

    /**
     * Returns the time when this computer first became idle.
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
        for (Queue.BuildableItem item : Hudson.getInstance().getQueue().getBuildableItems(this)) {
            firstDemand = Math.min(item.buildableStartMilliseconds, firstDemand);
        }
        return firstDemand;
    }

    /**
     * Called by {@link Executor} to kill excessive executors from this computer.
     */
    /*package*/ synchronized void removeExecutor(Executor e) {
        executors.remove(e);
        if(executors.isEmpty())
            Hudson.getInstance().removeComputer(this);
    }

    /**
     * Interrupt all {@link Executor}s.
     */
    public void interrupt() {
        for (Executor e : executors) {
            e.interrupt();
        }
    }

    public String getSearchUrl() {
        return "computer/"+nodeName;
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
     * Gets the environment variables of the JVM on this computer.
     * If this is the master, it returns the system property of the master computer.
     */
    public Map<String,String> getEnvVars() throws IOException, InterruptedException {
        return EnvVars.getRemote(getChannel());
    }

    /**
     * Gets the thread dump of the slave JVM.
     * @return
     *      key is the thread name, and the value is the pre-formatted dump.
     */
    public Map<String,String> getThreadDump() throws IOException, InterruptedException {
        return RemotingDiagnostics.getThreadDump(getChannel());
    }

    public static final ExecutorService threadPoolForRemoting = Executors.newCachedThreadPool(new ExceptionCatchingThreadFactory(new DaemonThreadFactory()));

//
//
// UI
//
//
    public void doRssAll( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        rss(req, rsp, " all builds", getBuilds());
    }
    public void doRssFailed( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        rss(req, rsp, " failed builds", getBuilds().failureOnly());
    }
    private void rss(StaplerRequest req, StaplerResponse rsp, String suffix, RunList runs) throws IOException, ServletException {
        RSS.forwardToRss(getDisplayName()+ suffix, getUrl(),
            runs.newBuilds(), Run.FEED_ADAPTER, req, rsp );
    }

    public void doToggleOffline( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        checkPermission(Hudson.ADMINISTER);

        setTemporarilyOffline(!temporarilyOffline);
        rsp.forwardToPreviousPage(req);
    }

    public Api getApi() {
        return new Api(this);
    }

    /**
     * Dumps the contents of the export table.
     */
    public void doDumpExportTable( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        // this is a debug probe and may expose sensitive information
        checkPermission(Hudson.ADMINISTER);

        rsp.setContentType("text/plain");
        rsp.setCharacterEncoding("UTF-8");
        PrintWriter w = new PrintWriter(rsp.getCompressedWriter(req));
        ((Channel)getChannel()).dumpExportTable(w);
        w.close();
    }

    public void doScript( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        // ability to run arbitrary script is dangerous,
        // so tie it to the admin access
        checkPermission(Hudson.ADMINISTER);

        String text = req.getParameter("script");
        if(text!=null) {
            try {
                req.setAttribute("output",
                RemotingDiagnostics.executeGroovy(text,getChannel()));
            } catch (InterruptedException e) {
                throw new ServletException(e);
            }
        }

        req.getView(this,"_script.jelly").forward(req,rsp);
    }

    /**
     * Gets the current {@link Computer} that the build is running.
     * This method only works when called during a build, such as by
     * {@link Publisher}, {@link BuildWrapper}, etc.
     */
    public static Computer currentComputer() {
        return Executor.currentExecutor().getOwner();
    }

    /**
     * Returns {@code true} if the computer is accepting tasks. Needed to allow slaves programmatic suspension of task
     * scheduling that does not overlap with being offline.
     *
     * @return {@code true} if the computer is accepting tasks
     */
    public boolean isAcceptingTasks() {
        return true;
    }
}
