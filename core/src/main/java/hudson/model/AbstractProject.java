package hudson.model;

import hudson.maven.MavenJob;
import hudson.FilePath;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.File;
import java.util.SortedMap;

/**
 * Base implementation of {@link Job}s that build software.
 *
 * For now this is primarily the common part of {@link Project} and {@link MavenJob}.
 *
 * @author Kohsuke Kawaguchi
 * @see AbstractBuild
 */
public abstract class AbstractProject<P extends AbstractProject<P,R>,R extends AbstractBuild<P,R>> extends Job<P,R> {
    /**
     * All the builds keyed by their build number.
     */
    protected transient /*almost final*/ RunMap<R> builds = new RunMap<R>();

    /**
     * The quiet period. Null to delegate to the system default.
     */
    private Integer quietPeriod = null;

    /**
     * If this project is configured to be only built on a certain node,
     * this value will be set to that node. Null to indicate the affinity
     * with the master node.
     *
     * see #canRoam
     */
    private String assignedNode;

    /**
     * True if this project can be built on any node.
     *
     * <p>
     * This somewhat ugly flag combination is so that we can migrate
     * existing Hudson installations nicely.
     */
    private boolean canRoam;

    /**
     * True to suspend new builds.
     */
    private boolean disabled;

    /**
     * Identifies {@link JDK} to be used.
     * Null if no explicit configuration is required.
     *
     * <p>
     * Can't store {@link JDK} directly because {@link Hudson} and {@link Project}
     * are saved independently.
     *
     * @see Hudson#getJDK(String)
     */
    private String jdk;

    private boolean enableRemoteTrigger = false;

    private String authToken = null;

    protected AbstractProject(Hudson parent, String name) {
        super(parent, name);

        if(!parent.getSlaves().isEmpty()) {
            // if a new job is configured with Hudson that already has slave nodes
            // make it roamable by default
            canRoam = true;
        }
    }

    /**
     * If this project is configured to be always built on this node,
     * return that {@link Node}. Otherwise null.
     */
    public Node getAssignedNode() {
        if(canRoam)
            return null;

        if(assignedNode ==null)
            return Hudson.getInstance();
        return getParent().getSlave(assignedNode);
    }

    /**
     * Gets the directory where the module is checked out.
     */
    public FilePath getWorkspace() {
        Node node = getLastBuiltOn();

        if(node==null)
            node = getParent();

        if(node instanceof Slave)
            return ((Slave)node).getWorkspaceRoot().child(getName());
        else
            return new FilePath(new File(getRootDir(),"workspace"));
    }

    public int getQuietPeriod() {
        return quietPeriod!=null ? quietPeriod : getParent().getQuietPeriod();
    }

    // ugly name because of EL
    public boolean getHasCustomQuietPeriod() {
        return quietPeriod!=null;
    }

    public final boolean isBuildable() {
        return true;
    }

    public boolean isDisabled() {
        return disabled;
    }

    /**
     * Schedules a build of this project.
     */
    public void scheduleBuild() {
        if(!disabled)
            getParent().getQueue().add(this);
    }

    /**
     * Returns true if the build is in the queue.
     */
    @Override
    public boolean isInQueue() {
        return getParent().getQueue().contains(this);
    }

    public JDK getJDK() {
        return getParent().getJDK(jdk);
    }

    /**
     * Overwrites the JDK setting.
     */
    public synchronized void setJDK(JDK jdk) throws IOException {
        this.jdk = jdk.getName();
        save();
    }

    public boolean isEnableRemoteTrigger() {
        // no need to enable this option if security disabled
        return (Hudson.getInstance().isUseSecurity())
                && enableRemoteTrigger;
    }

    public String getAuthToken() {
            return authToken;
    }

    public SortedMap<Integer, ? extends R> _getRuns() {
        return builds.getView();
    }

    public void removeRun(R run) {
        this.builds.remove(run);
    }

    /**
     * Creates a new build of this project for immediate execution.
     */
    protected abstract R newBuild() throws IOException;

    /**
     * Gets the {@link Node} where this project was last built on.
     *
     * @return
     *      null if no information is available (for example,
     *      if no build was done yet.)
     */
    public Node getLastBuiltOn() {
        // where was it built on?
        AbstractBuild b = getLastBuild();
        if(b==null)
            return null;
        else
            return b.getBuiltOn();
    }

//
//
// actions
//
//
    /**
     * Schedules a new build command.
     */
    public void doBuild( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        if (authorizedToStartBuild(req, rsp)) {
            scheduleBuild();
            rsp.forwardToPreviousPage(req);
        }
    }

    private boolean authorizedToStartBuild(StaplerRequest req, StaplerResponse rsp) throws IOException {

        if (isEnableRemoteTrigger()) {
            String providedToken = req.getParameter("token");
            if (providedToken != null && providedToken.equals(getAuthToken())) {
                return true;
            }
        }

         return Hudson.adminCheck(req, rsp);
    }

    /**
     * Cancels a scheduled build.
     */
    public void doCancelQueue( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        if(!Hudson.adminCheck(req,rsp))
            return;

        getParent().getQueue().cancel(this);
        rsp.forwardToPreviousPage(req);
    }

    public synchronized void doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        super.doConfigSubmit(req, rsp);

        disabled = req.getParameter("disable")!=null;

        jdk = req.getParameter("jdk");
        if(req.getParameter("hasCustomQuietPeriod")!=null) {
            quietPeriod = Integer.parseInt(req.getParameter("quiet_period"));
        } else {
            quietPeriod = null;
        }

        if(req.getParameter("hasSlaveAffinity")!=null) {
            canRoam = false;
            assignedNode = req.getParameter("slave");
            if(assignedNode !=null) {
                if(Hudson.getInstance().getSlave(assignedNode)==null) {
                    assignedNode = null;   // no such slave
                }
            }
        } else {
            canRoam = true;
            assignedNode = null;
        }

        if (req.getParameter("pseudoRemoteTrigger") != null) {
            authToken = req.getParameter("authToken");
            enableRemoteTrigger = true;
        } else {
            enableRemoteTrigger = false;
        }
    }

    /**
     * Serves the workspace files.
     */
    public void doWs( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, InterruptedException {
        FilePath ws = getWorkspace();
        if(!ws.exists()) {
            // if there's no workspace, report a nice error message
            rsp.forward(this,"noWorkspace",req);
        } else {
            serveFile(req, rsp, ws, "folder.gif", true);
        }
    }
}
