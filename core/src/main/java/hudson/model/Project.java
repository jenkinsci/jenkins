package hudson.model;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Descriptor.FormException;
import hudson.model.Fingerprint.RangeSet;
import hudson.model.RunMap.Constructor;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import hudson.scm.SCMS;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildTrigger;
import hudson.tasks.Builder;
import hudson.tasks.Fingerprinter;
import hudson.tasks.Publisher;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.triggers.Trigger;
import hudson.triggers.Triggers;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.Vector;

/**
 * Buildable software project.
 *
 * @author Kohsuke Kawaguchi
 */
public class Project extends Job<Project,Build> {

    /**
     * All the builds keyed by their build number.
     */
    private transient /*almost final*/ RunMap<Build> builds = new RunMap<Build>();

    private SCM scm = new NullSCM();

    /**
     * List of all {@link Trigger}s for this project.
     */
    private List<Trigger> triggers = new Vector<Trigger>();

    /**
     * List of active {@link Builder}s configured for this project.
     */
    private List<Builder> builders = new Vector<Builder>();

    /**
     * List of active {@link Publisher}s configured for this project.
     */
    private List<Publisher> publishers = new Vector<Publisher>();

    /**
     * {@link Action}s contributed from {@link #triggers}, {@link #builders},
     * and {@link #publishers}.
     *
     * We don't want to persist them separately, and these actions
     * come and go as configuration change, so it's kept separate.
     */
    private transient /*final*/ List<Action> transientActions = new Vector<Action>();

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
     * Creates a new project.
     */
    public Project(Hudson parent,String name) {
        super(parent,name);

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

    public JDK getJDK() {
        return getParent().getJDK(jdk);
    }

    public int getQuietPeriod() {
        return quietPeriod!=null ? quietPeriod : getParent().getQuietPeriod();
    }

    // ugly name because of EL
    public boolean getHasCustomQuietPeriod() {
        return quietPeriod!=null;
    }


    protected void onLoad(Hudson root, String name) throws IOException {
        super.onLoad(root, name);

        if(triggers==null)
            // it didn't exist in < 1.28
            triggers = new Vector<Trigger>();

        this.builds = new RunMap<Build>();
        this.builds.load(this,new Constructor<Build>() {
            public Build create(File dir) throws IOException {
                return new Build(Project.this,dir);
            }
        });

        for (Trigger t : triggers)
            t.start(this);

        updateTransientActions();
    }

    public boolean isBuildable() {
        return !isDisabled();
    }

    public boolean isDisabled() {
        return disabled;
    }

    public SCM getScm() {
        return scm;
    }

    public void setScm(SCM scm) {
        this.scm = scm;
    }

    @Override
    public String getIconColor() {
        if(isDisabled())
            // use grey to indicate that the build is disabled
            return "grey";
        else
            return super.getIconColor();
    }

    public synchronized Map<Descriptor<Trigger>,Trigger> getTriggers() {
        return Descriptor.toMap(triggers);
    }

    public synchronized Map<Descriptor<Builder>,Builder> getBuilders() {
        return Descriptor.toMap(builders);
    }

    public synchronized Map<Descriptor<Publisher>,Publisher> getPublishers() {
        return Descriptor.toMap(publishers);
    }

    /**
     * Adds a new {@link BuildStep} to this {@link Project} and saves the configuration.
     */
    private synchronized void addPublisher(Publisher buildStep) throws IOException {
        for( int i=0; i<publishers.size(); i++ ) {
            if(publishers.get(i).getDescriptor()==buildStep.getDescriptor()) {
                // replace
                publishers.set(i,buildStep);
                save();
                return;
            }
        }

        // add
        publishers.add(buildStep);
        save();
    }

    /**
     * Removes a publisher from this project, if it's active.
     */
    private void removePublisher(Descriptor<Publisher> descriptor) throws IOException {
        for( int i=0; i<publishers.size(); i++ ) {
            if(publishers.get(i).getDescriptor()==descriptor) {
                // found it
                publishers.remove(i);
                save();
                return;
            }
        }
    }

    public SortedMap<Integer, ? extends Build> _getRuns() {
        return builds.getView();
    }

    public void removeRun(Build run) {
        this.builds.remove(run);
    }

    /**
     * Creates a new build of this project for immediate execution.
     */
    public Build newBuild() throws IOException {
        Build lastBuild = new Build(this);
        builds.put(lastBuild);
        return lastBuild;
    }

    public boolean checkout(Build build, Launcher launcher, BuildListener listener, File changelogFile) throws IOException {
        if(scm==null)
            return true;    // no SCM

        FilePath workspace = getWorkspace();
        workspace.mkdirs();

        return scm.checkout(build, launcher, workspace, listener, changelogFile);
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


        FilePath workspace = getWorkspace();
        if(!workspace.exists()) {
            // no workspace. build now, or nothing will ever be built
            listener.getLogger().println("No workspace is available, so can't check for updates.");
            listener.getLogger().println("Scheduling a new build to get a workspace.");
            return true;
        }

        try {
            // TODO: do this by using the right slave
            return scm.pollChanges(this, new Launcher(listener), workspace, listener );
        } catch (IOException e) {
            e.printStackTrace(listener.fatalError(e.getMessage()));
            return false;
        }
    }

    /**
     * Gets the {@link Node} where this project was last built on.
     *
     * @return
     *      null if no information is available (for example,
     *      if no build was done yet.)
     */
    public Node getLastBuiltOn() {
        // where was it built on?
        Build b = getLastBuild();
        if(b==null)
            return null;
        else
            return b.getBuiltOn();
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

    /**
     * Returns the root directory of the checked-out module.
     *
     * @return
     *      When running remotely, this returns a remote fs directory.
     */
    public FilePath getModuleRoot() {
        return getScm().getModuleRoot(getWorkspace());
    }

    /**
     * Gets the dependency relationship map between this project (as the source)
     * and that project (as the sink.)
     *
     * @return
     *      can be empty but not null. build number of this project to the build
     *      numbers of that project.
     */
    public SortedMap<Integer,RangeSet> getRelationship(Project that) {
        TreeMap<Integer,RangeSet> r = new TreeMap<Integer,RangeSet>(REVERSE_INTEGER_COMPARATOR);

        checkAndRecord(that, r, this.getBuilds());
        // checkAndRecord(that, r, that.getBuilds());

        return r;
    }

    public List<Project> getDownstreamProjects() {
        BuildTrigger buildTrigger = (BuildTrigger) getPublishers().get(BuildTrigger.DESCRIPTOR);
        if(buildTrigger==null)
            return new ArrayList<Project>();
        else
            return buildTrigger.getChildProjects();
    }

    public List<Project> getUpstreamProjects() {
        List<Project> r = new ArrayList<Project>();
        for( Project p : Hudson.getInstance().getProjects() ) {
            synchronized(p) {
                for (BuildStep step : p.publishers) {
                    if (step instanceof BuildTrigger) {
                        BuildTrigger trigger = (BuildTrigger) step;
                        if(trigger.getChildProjects().contains(this))
                            r.add(p);
                    }
                }
            }
        }
        return r;
    }

    /**
     * Helper method for getDownstreamRelationship.
     *
     * For each given build, find the build number range of the given project and put that into the map.
     */
    private void checkAndRecord(Project that, TreeMap<Integer, RangeSet> r, Collection<? extends Build> builds) {
        for (Build build : builds) {
            RangeSet rs = build.getDownstreamRelationship(that);
            if(rs==null || rs.isEmpty())
                continue;

            int n = build.getNumber();

            RangeSet value = r.get(n);
            if(value==null)
                r.put(n,rs);
            else
                value.add(rs);
        }
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

    /**
     * Schedules the SCM polling. If a polling is already in progress
     * or a build is in progress, polling will take place after that.
     * Otherwise the polling will be started immediately on a separate thread.
     *
     * <p>
     * In any case this method returns immediately.
     */
    public void scheduleSCMPolling() {
        // TODO
    }

    /**
     * Returns true if the fingerprint record is configured in this project.
     */
    public boolean isFingerprintConfigured() {
        synchronized(publishers) {
            for (Publisher p : publishers) {
                if(p instanceof Fingerprinter)
                    return true;
            }
        }
        return false;
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
        scheduleBuild();
        rsp.forwardToPreviousPage(req);
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

    /**
     * Accepts submission from the configuration page.
     */
    public void doConfigSubmit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {

        Set<Project> upstream = Collections.EMPTY_SET;

        synchronized(this) {
            try {
                if(!Hudson.adminCheck(req,rsp))
                    return;

                req.setCharacterEncoding("UTF-8");

                int scmidx = Integer.parseInt(req.getParameter("scm"));
                scm = SCMS.SCMS.get(scmidx).newInstance(req);

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

                buildDescribable(req, BuildStep.BUILDERS, builders, "builder");
                buildDescribable(req, BuildStep.PUBLISHERS, publishers, "publisher");

                for (Trigger t : triggers)
                    t.stop();
                buildDescribable(req, Triggers.TRIGGERS, triggers, "trigger");
                for (Trigger t : triggers)
                    t.start(this);

                updateTransientActions();

                super.doConfigSubmit(req,rsp);
            } catch (FormException e) {
                sendError(e,req,rsp);
            }
        }

        if(req.getParameter("pseudoUpstreamTrigger")!=null) {
            upstream = new HashSet<Project>(Project.fromNameList(req.getParameter("upstreamProjects")));
        }

        // this needs to be done after we release the lock on this,
        // or otherwise we could dead-lock
        for (Project p : Hudson.getInstance().getProjects()) {
            boolean isUpstream = upstream.contains(p);
            synchronized(p) {
                List<Project> newChildProjects = p.getDownstreamProjects();

                if(isUpstream) {
                    if(!newChildProjects.contains(this))
                        newChildProjects.add(this);
                } else {
                    newChildProjects.remove(this);
                }

                if(newChildProjects.isEmpty()) {
                    p.removePublisher(BuildTrigger.DESCRIPTOR);
                } else {
                    p.addPublisher(new BuildTrigger(newChildProjects));
                }
            }
        }
    }

    private void updateTransientActions() {
        if(transientActions==null)
            transientActions = new Vector<Action>();    // happens when loaded from disk
        synchronized(transientActions) {
            transientActions.clear();
            for (BuildStep step : builders) {
                Action a = step.getProjectAction(this);
                if(a!=null)
                    transientActions.add(a);
            }
            for (BuildStep step : publishers) {
                Action a = step.getProjectAction(this);
                if(a!=null)
                    transientActions.add(a);
            }
            for (Trigger trigger : triggers) {
                Action a = trigger.getProjectAction();
                if(a!=null)
                    transientActions.add(a);
            }
        }
    }

    public synchronized List<Action> getActions() {
        // add all the transient actions, too
        List<Action> actions = new Vector<Action>(super.getActions());
        actions.addAll(transientActions);
        return actions;
    }

    public List<ProminentProjectAction> getProminentActions() {
        List<Action> a = getActions();
        List<ProminentProjectAction> pa = new Vector<ProminentProjectAction>();
        for (Action action : a) {
            if(action instanceof ProminentProjectAction)
                pa.add((ProminentProjectAction) action);
        }
        return pa;
    }

    private <T extends Describable<T>> void buildDescribable(StaplerRequest req, List<Descriptor<T>> descriptors, List<T> result, String prefix)
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
    public void doWs( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        File dir = getWorkspace().getLocal();
        if(!dir.exists()) {
            // if there's no workspace, report a nice error message
            rsp.forward(this,"noWorkspace",req);
        } else {
            serveFile(req, rsp, dir, "folder.gif", true);
        }
    }

    /**
     * Display the test result trend.
     */
    public void doTestResultTrend( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        Build b = getLastSuccessfulBuild();
        if(b!=null) {
            AbstractTestResultAction a = b.getTestResultAction();
            if(a!=null) {
                a.doGraph(req,rsp);
                return;
            }
        }

        // error
        rsp.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    /**
     * Changes the test result report display mode.
     */
    public void doFlipTestResultTrend( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        boolean failureOnly = false;

        // check the current preference value
        Cookie[] cookies = req.getCookies();
        if(cookies!=null) {
            for (Cookie cookie : cookies) {
                if(cookie.getName().equals(FAILURE_ONLY_COOKIE))
                    failureOnly = Boolean.parseBoolean(cookie.getValue());
            }
        }

        // flip!
        failureOnly = !failureOnly;

        // set the updated value
        Cookie cookie = new Cookie(FAILURE_ONLY_COOKIE,String.valueOf(failureOnly));
        List anc = req.getAncestors();
        Ancestor a = (Ancestor) anc.get(anc.size()-1); // last
        cookie.setPath(a.getUrl()); // just for this chart
        cookie.setMaxAge(Integer.MAX_VALUE);
        rsp.addCookie(cookie);

        // back to the project page
        rsp.sendRedirect(".");
    }

    /**
     * @deprecated
     *      left for legacy config file compatibility
     */
    private transient String slave;

    private static final String FAILURE_ONLY_COOKIE = "TestResultAction_failureOnly";

    /**
     * Converts a list of projects into a camma-separated names.
     */
    public static String toNameList(Collection<? extends Project> projects) {
        StringBuilder buf = new StringBuilder();
        for (Project project : projects) {
            if(buf.length()>0)
                buf.append(", ");
            buf.append(project.getName());
        }
        return buf.toString();
    }

    /**
     * Does the opposite of {@link #toNameList(Collection)}.
     */
    public static List<Project> fromNameList(String list) {
        Hudson hudson = Hudson.getInstance();

        List<Project> r = new ArrayList<Project>();
        StringTokenizer tokens = new StringTokenizer(list,",");
        while(tokens.hasMoreTokens()) {
            String projectName = tokens.nextToken().trim();
            Job job = hudson.getJob(projectName);
            if(!(job instanceof Project)) {
                continue; // ignore this token
            }
            r.add((Project) job);
        }
        return r;
    }

    private static final Comparator<Integer> REVERSE_INTEGER_COMPARATOR = new Comparator<Integer>() {
        public int compare(Integer o1, Integer o2) {
            return o2-o1;
        }
    };

    public JobDescriptor<Project,Build> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final JobDescriptor<Project,Build> DESCRIPTOR = new JobDescriptor<Project,Build>(Project.class) {
        public String getDisplayName() {
            return "Building a software project";
        }

        public Project newInstance(String name) {
            return new Project(Hudson.getInstance(),name);
        }
    };
}
