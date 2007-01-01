package hudson.model;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.RunMap.Constructor;
import hudson.model.Descriptor.FormException;
import hudson.triggers.Trigger;
import hudson.triggers.Triggers;
import hudson.Launcher.LocalLauncher;
import hudson.maven.MavenJob;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.SortedMap;
import java.util.List;
import java.util.Vector;
import java.util.Map;

/**
 * Base implementation of {@link Job}s that build software.
 *
 * For now this is primarily the common part of {@link Project} and {@link MavenJob}.
 *
 * @author Kohsuke Kawaguchi
 * @see AbstractBuild
 */
public abstract class AbstractProject<P extends AbstractProject<P,R>,R extends AbstractBuild<P,R>> extends Job<P,R> {

    private SCM scm = new NullSCM();

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

    /**
     * List of all {@link Trigger}s for this project.
     */
    protected List<Trigger> triggers = new Vector<Trigger>();

    protected AbstractProject(Hudson parent, String name) {
        super(parent, name);

        if(!parent.getSlaves().isEmpty()) {
            // if a new job is configured with Hudson that already has slave nodes
            // make it roamable by default
            canRoam = true;
        }
    }

    @Override
    protected void onLoad(Hudson root, String name) throws IOException {
        super.onLoad(root,name);

        this.builds = new RunMap<R>();
        this.builds.load(this,new Constructor<R>() {
            public R create(File dir) throws IOException {
                return loadBuild(dir);
            }
        });

        if(triggers==null)
            // it didn't exist in < 1.28
            triggers = new Vector<Trigger>();
        for (Trigger t : triggers)
            t.start(this,false);
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
     * Loads an existing build record from disk.
     */
    protected abstract R loadBuild(File dir) throws IOException;


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

    public boolean checkout(AbstractBuild build, Launcher launcher, BuildListener listener, File changelogFile) throws IOException {
        if(scm==null)
            return true;    // no SCM

        try {
            FilePath workspace = getWorkspace();
            workspace.mkdirs();

            return scm.checkout(build, launcher, workspace, listener, changelogFile);
        } catch (InterruptedException e) {
            e.printStackTrace(listener.fatalError("SCM check out aborted"));
            return false;
        }
    }

    /**
     * Checks if there's any update in SCM, and returns true if any is found.
     *
     * <p>
     * The caller is responsible for coordinating the mutual exclusion between
     * a build and polling, as both touches the workspace.
     */
    public boolean pollSCMChanges( TaskListener listener ) {
        if(scm==null) {
            listener.getLogger().println("No SCM");
            return false;   // no SCM
        }

        try {
            FilePath workspace = getWorkspace();
            if(!workspace.exists()) {
                // no workspace. build now, or nothing will ever be built
                listener.getLogger().println("No workspace is available, so can't check for updates.");
                listener.getLogger().println("Scheduling a new build to get a workspace.");
                return true;
            }

            // TODO: do this by using the right slave
            return scm.pollChanges(this, new LocalLauncher(listener), workspace, listener );
        } catch (IOException e) {
            e.printStackTrace(listener.fatalError(e.getMessage()));
            return false;
        } catch (InterruptedException e) {
            e.printStackTrace(listener.fatalError("SCM polling aborted"));
            return false;
        }
    }

    public SCM getScm() {
        return scm;
    }

    public void setScm(SCM scm) {
        this.scm = scm;
    }

    /**
     * Adds a new {@link Trigger} to this {@link Project} if not active yet.
     */
    public void addTrigger(Trigger trigger) throws IOException {
        addToList(trigger,triggers);
    }

    public void removeTrigger(Descriptor<Trigger> trigger) throws IOException {
        removeFromList(trigger,triggers);
    }

    protected final synchronized <T extends Describable<T>>
    void addToList( T item, List<T> collection ) throws IOException {
        for( int i=0; i<collection.size(); i++ ) {
            if(collection.get(i).getDescriptor()==item.getDescriptor()) {
                // replace
                collection.set(i,item);
                save();
                return;
            }
        }
        // add
        collection.add(item);
        save();
    }

    protected final synchronized <T extends Describable<T>>
    void removeFromList(Descriptor<T> item, List<T> collection) throws IOException {
        for( int i=0; i< collection.size(); i++ ) {
            if(collection.get(i).getDescriptor()==item) {
                // found it
                collection.remove(i);
                save();
                return;
            }
        }
    }

    public synchronized Map<Descriptor<Trigger>,Trigger> getTriggers() {
        return Descriptor.toMap(triggers);
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

        try {
            for (Trigger t : triggers)
                t.stop();
            buildDescribable(req, Triggers.TRIGGERS, triggers, "trigger");
            for (Trigger t : triggers)
                t.start(this,true);
        } catch (FormException e) {
            throw new ServletException(e);
        }
    }

    protected final <T extends Describable<T>> void buildDescribable(StaplerRequest req, List<Descriptor<T>> descriptors, List<T> result, String prefix)
        throws FormException {

        result.clear();
        for( int i=0; i< descriptors.size(); i++ ) {
            if(req.getParameter(prefix +i)!=null) {
                T instance = descriptors.get(i).newInstance(req);
                result.add(instance);
            }
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
