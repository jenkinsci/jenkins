package hudson.model;

import hudson.EnvVars;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.util.DaemonThreadFactory;
import hudson.util.RunList;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Represents a set of {@link Executor}s on the same computer.
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
public abstract class Computer implements ModelObject {
    private final List<Executor> executors = new ArrayList<Executor>();

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

    /**
     * Gets the channel that can be used to run a program on this computer.
     *
     * @return
     *      never null when {@link #isOffline()}==false.
     */
    public abstract VirtualChannel getChannel();

    /**
     * If {@link #getChannel()}==null, attempts to relaunch the slave agent.
     */
    public abstract void doLaunchSlaveAgent( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException;

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
     * Returns the {@link Node} that this computer represents.
     */
    public Node getNode() {
        if(nodeName==null)
            return Hudson.getInstance();
        return Hudson.getInstance().getSlave(nodeName);
    }

    public boolean isOffline() {
        return temporarilyOffline || getChannel()==null;
    }

    /**
     * Returns true if this computer is supposed to be launched via JNLP.
     */
    public boolean isJnlpAgent() {
        return false;
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
    public boolean isTemporarilyOffline() {
        return temporarilyOffline;
    }

    public void setTemporarilyOffline(boolean temporarilyOffline) {
        this.temporarilyOffline = temporarilyOffline;
        Hudson.getInstance().getQueue().scheduleMaintenance();
    }

    public String getIcon() {
        if(isOffline())
            return "computer-x.gif";
        else
            return "computer.gif";
    }

    public String getDisplayName() {
        return nodeName;
    }

    public String getUrl() {
        return "computer/"+getDisplayName()+"/";
    }

    /**
     * Returns projects that are tied on this node.
     */
    public List<Project> getTiedJobs() {
        List<Project> r = new ArrayList<Project>();
        for( Project p : Hudson.getInstance().getProjects() ) {
            if(p.getAssignedNode()==getNode())
                r.add(p);
        }
        return r;
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
            if(e.getCurrentBuild()==null)
                e.interrupt();

        // if the number is increased, add new ones
        while(executors.size()<numExecutors)
            executors.add(new Executor(this));
    }

    /**
     * Returns the number of idle {@link Executor}s that can start working immediately.
     */
    public synchronized int countIdle() {
        int n = 0;
        for (Executor e : executors) {
            if(e.isIdle())
                n++;
        }
        return n;
    }

    /**
     * Gets the read-only view of all {@link Executor}s.
     */
    public synchronized List<Executor> getExecutors() {
        return new ArrayList<Executor>(executors);
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
    public synchronized void interrupt() {
        for (Executor e : executors) {
            e.interrupt();
        }
    }

    /**
     * Gets the system properties of the JVM on this computer.
     * If this is the master, it returns the system property of the master computer.
     */
    public Map<Object,Object> getSystemProperties() throws IOException, InterruptedException {
        return getChannel().call(new GetSystemProperties());
    }

    private static final class GetSystemProperties implements Callable<Map<Object,Object>,RuntimeException> {
        public Map<Object,Object> call() {
            return new TreeMap<Object,Object>(System.getProperties());
        }
        private static final long serialVersionUID = 1L;
    }

    /**
     * Gets the environment variables of the JVM on this computer.
     * If this is the master, it returns the system property of the master computer.
     */
    public Map<String,String> getEnvVars() throws IOException, InterruptedException {
        VirtualChannel channel = getChannel();
        if(channel==null)
            return Collections.singletonMap("N/A","N/A");
        return channel.call(new GetEnvVars());
    }

    private static final class GetEnvVars implements Callable<Map<String,String>,RuntimeException> {
        public Map<String,String> call() {
            return new TreeMap<String,String>(EnvVars.masterEnvVars);
        }
        private static final long serialVersionUID = 1L;
    }


    public static final ExecutorService threadPoolForRemoting = Executors.newCachedThreadPool(new DaemonThreadFactory());

//
//
// UI
//
//
    public void doRssAll( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        rss(req, rsp, " all builds", new RunList(getTiedJobs()));
    }
    public void doRssFailed( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        rss(req, rsp, " failed builds", new RunList(getTiedJobs()).failureOnly());
    }
    private void rss(StaplerRequest req, StaplerResponse rsp, String suffix, RunList runs) throws IOException, ServletException {
        RSS.forwardToRss(getDisplayName()+ suffix, getUrl(),
            runs.newBuilds(), Run.FEED_ADAPTER, req, rsp );
    }        

    public void doToggleOffline( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        if(!Hudson.adminCheck(req,rsp))
            return;

        setTemporarilyOffline(!temporarilyOffline);
        rsp.forwardToPreviousPage(req);
    }
}
