package hudson.model;

import hudson.FilePath;
import hudson.Launcher;
import hudson.FeedAdapter;
import hudson.maven.MavenModule;
import hudson.model.Descriptor.FormException;
import hudson.model.Fingerprint.RangeSet;
import hudson.model.RunMap.Constructor;
import hudson.scm.ChangeLogSet;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import hudson.scm.SCMS;
import hudson.scm.ChangeLogSet.Entry;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.triggers.Triggers;
import hudson.util.EditDistance;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Vector;
import java.util.Calendar;

/**
 * Base implementation of {@link Job}s that build software.
 *
 * For now this is primarily the common part of {@link Project} and {@link MavenModule}.
 *
 * @author Kohsuke Kawaguchi
 * @see AbstractBuild
 */
public abstract class AbstractProject<P extends AbstractProject<P,R>,R extends AbstractBuild<P,R>> extends Job<P,R> implements BuildableItem {

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
    protected boolean disabled;

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
     * @deprecated
     */
    private transient boolean enableRemoteTrigger;

    private BuildAuthorizationToken authToken = null;

    /**
     * List of all {@link Trigger}s for this project.
     */
    protected List<Trigger<?>> triggers = new Vector<Trigger<?>>();

    /**
     * {@link Action}s contributed from subsidiary objects associated with
     * {@link AbstractProject}, such as from triggers, builders, publishers, etc.
     *
     * We don't want to persist them separately, and these actions
     * come and go as configuration change, so it's kept separate.
     */
    protected transient /*final*/ List<Action> transientActions = new Vector<Action>();

    protected AbstractProject(ItemGroup parent, String name) {
        super(parent,name);

        if(!Hudson.getInstance().getSlaves().isEmpty()) {
            // if a new job is configured with Hudson that already has slave nodes
            // make it roamable by default
            canRoam = true;
        }
    }

    @Override
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);

        this.builds = new RunMap<R>();
        this.builds.load(this,new Constructor<R>() {
            public R create(File dir) throws IOException {
                return loadBuild(dir);
            }
        });

        if(triggers==null)
            // it didn't exist in < 1.28
            triggers = new Vector<Trigger<?>>();
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
        return Hudson.getInstance().getSlave(assignedNode);
    }

    /**
     * Gets the directory where the module is checked out.
     */
    public abstract FilePath getWorkspace();

    /**
     * Returns the root directory of the checked-out module.
     * <p>
     * This is usually where <tt>pom.xml</tt>, <tt>build.xml</tt>
     * and so on exists. 
     */
    public FilePath getModuleRoot() {
        return getScm().getModuleRoot(getWorkspace());
    }

    public int getQuietPeriod() {
        return quietPeriod!=null ? quietPeriod : Hudson.getInstance().getQuietPeriod();
    }

    // ugly name because of EL
    public boolean getHasCustomQuietPeriod() {
        return quietPeriod!=null;
    }

    public final boolean isBuildable() {
        return !isDisabled();
    }

    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public BallColor getIconColor() {
        if(isDisabled())
            // use grey to indicate that the build is disabled
            return BallColor.GREY;
        else
            return super.getIconColor();
    }
    
    /**
     * Schedules a build of this project.
     *
     * @return
     *      true if the project is actually added to the queue.
     *      false if the queue contained it and therefore the add()
     *      was noop
     */
    public boolean scheduleBuild() {
        if(isDisabled())    return false;
        return Hudson.getInstance().getQueue().add(this);
    }

    /**
     * Returns true if the build is in the queue.
     */
    @Override
    public boolean isInQueue() {
        return Hudson.getInstance().getQueue().contains(this);
    }

    @Override
    public Queue.Item getQueueItem() {
        return Hudson.getInstance().getQueue().getItem(this);
    }

    /**
     * Returns true if a build of this project is in progress.
     */
    public boolean isBuilding() {
        R b = getLastBuild();
        return b!=null && b.isBuilding();
    }

    public JDK getJDK() {
        return Hudson.getInstance().getJDK(jdk);
    }

    /**
     * Overwrites the JDK setting.
     */
    public synchronized void setJDK(JDK jdk) throws IOException {
        this.jdk = jdk.getName();
        save();
    }

    public BuildAuthorizationToken getAuthToken() {
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

    public synchronized List<Action> getActions() {
        // add all the transient actions, too
        List<Action> actions = new Vector<Action>(super.getActions());
        actions.addAll(transientActions);
        return actions;
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
        AbstractBuild b = getLastBuild();
        if(b==null)
            return null;
        else
            return b.getBuiltOn();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * A project must be blocked if its own previous build is in progress,
     * but derived classes can also check other conditions.
     */
    public boolean isBuildBlocked() {
        return isBuilding();
    }

    public String getWhyBlocked() {
        AbstractBuild<?, ?> build = getLastBuild();
        Executor e = build.getExecutor();
        String eta="";
        if(e!=null)
            eta = " (ETA:"+e.getEstimatedRemainingTime()+")";
        int lbn = build.getNumber();
        return "Build #"+lbn+" is already in progress"+eta;
    }

    public final long getEstimatedDuration() {
        AbstractBuild b = getLastSuccessfulBuild();
        if(b==null)     return -1;

        long duration = b.getDuration();
        if(duration==0) return -1;

        return duration;
    }

    public void execute() throws IOException {
        newBuild().run();
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
            return false;
        }
        if(isDisabled()) {
            listener.getLogger().println("Build disabled");
            return false;
        }

        try {
            FilePath workspace = getWorkspace();
            if(!workspace.exists()) {
                // no workspace. build now, or nothing will ever be built
                listener.getLogger().println("No workspace is available, so can't check for updates.");
                listener.getLogger().println("Scheduling a new build to get a workspace.");
                return true;
            }

            return scm.pollChanges(this, workspace.createLauncher(listener), workspace, listener );
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
    public void addTrigger(Trigger<?> trigger) throws IOException {
        addToList(trigger,triggers);
    }

    public void removeTrigger(TriggerDescriptor trigger) throws IOException {
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

    public synchronized Map<TriggerDescriptor,Trigger> getTriggers() {
        return (Map)Descriptor.toMap(triggers);
    }

//
//
// fingerprint related
//
//
    /**
     * True if the builds of this project produces {@link Fingerprint} records.
     */
    public abstract boolean isFingerprintConfigured();

    /**
     * Gets the other {@link AbstractProject}s that should be built
     * when a build of this project is completed.
     */
    public final List<AbstractProject> getDownstreamProjects() {
        return Hudson.getInstance().getDependencyGraph().getDownstream(this);
    }

    public final List<AbstractProject> getUpstreamProjects() {
        return Hudson.getInstance().getDependencyGraph().getUpstream(this);
    }

    /**
     * Gets the dependency relationship map between this project (as the source)
     * and that project (as the sink.)
     *
     * @return
     *      can be empty but not null. build number of this project to the build
     *      numbers of that project.
     */
    public SortedMap<Integer, RangeSet> getRelationship(AbstractProject that) {
        TreeMap<Integer,RangeSet> r = new TreeMap<Integer,RangeSet>(REVERSE_INTEGER_COMPARATOR);

        checkAndRecord(that, r, this.getBuilds());
        // checkAndRecord(that, r, that.getBuilds());

        return r;
    }

    /**
     * Helper method for getDownstreamRelationship.
     *
     * For each given build, find the build number range of the given project and put that into the map.
     */
    private void checkAndRecord(AbstractProject that, TreeMap<Integer, RangeSet> r, Collection<R> builds) {
        for (R build : builds) {
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
     * Builds the dependency graph.
     * @see DependencyGraph
     */
    protected abstract void buildDependencyGraph(DependencyGraph graph);

//
//
// actions
//
//
    /**
     * Schedules a new build command.
     */
    public void doBuild( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        BuildAuthorizationToken.startBuildIfAuthorized(authToken,this,req,rsp);
    }

    /**
     * Cancels a scheduled build.
     */
    public void doCancelQueue( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        if(!Hudson.adminCheck(req,rsp))
            return;

        Hudson.getInstance().getQueue().cancel(this);
        rsp.forwardToPreviousPage(req);
    }

    @Override
    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, FormException {
        super.submit(req,rsp);

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

        authToken = BuildAuthorizationToken.create(req);

        setScm(SCMS.parseSCM(req));

        for (Trigger t : triggers)
            t.stop();
        buildDescribable(req, Triggers.getApplicableTriggers(this), triggers, "trigger");
        for (Trigger t : triggers)
            t.start(this,true);
    }

    protected final <T extends Describable<T>> void buildDescribable(StaplerRequest req, List<? extends Descriptor<T>> descriptors, List<T> result, String prefix)
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
            new DirectoryBrowserSupport(this).serveFile(req, rsp, ws, "folder.gif", true);
        }
    }

    /**
     * RSS feed for changes in this project.
     */
    public void doRssChangelog(  StaplerRequest req, StaplerResponse rsp  ) throws IOException, ServletException {
        class FeedItem {
            ChangeLogSet.Entry e;
            int idx;

            public FeedItem(Entry e, int idx) {
                this.e = e;
                this.idx = idx;
            }

            AbstractBuild<?,?> getBuild() {
                return e.getParent().build;
            }
        }

        List<FeedItem> entries = new ArrayList<FeedItem>();

        for(R r=getLastBuild(); r!=null; r=r.getPreviousBuild()) {
            int idx=0;
            for( ChangeLogSet.Entry e : r.getChangeSet())
                entries.add(new FeedItem(e,idx++));
        }

        RSS.forwardToRss(
            getDisplayName()+' '+scm.getDescriptor().getDisplayName()+" changes",
            getUrl()+"changes",
            entries, new FeedAdapter<FeedItem>() {
                public String getEntryTitle(FeedItem item) {
                    return "#"+item.getBuild().number+' '+item.e.getMsg()+" ("+item.e.getAuthor()+")";
                }

                public String getEntryUrl(FeedItem item) {
                    return item.getBuild().getUrl()+"changes#detail"+item.idx;
                }

                public String getEntryID(FeedItem item) {
                    return getEntryUrl(item);
                }

                public String getEntryDescription(FeedItem item) {
                    StringBuilder buf = new StringBuilder();
                    for(String path : item.e.getAffectedPaths())
                        buf.append(path).append('\n');
                    return buf.toString();
                }

                public Calendar getEntryTimestamp(FeedItem item) {
                    return item.getBuild().getTimestamp();
                }
            },
            req, rsp );
    }

    /**
     * Finds a {@link AbstractProject} that has the name closest to the given name.
     */
    public static AbstractProject findNearest(String name) {
        List<AbstractProject> projects = Hudson.getInstance().getAllItems(AbstractProject.class);
        String[] names = new String[projects.size()];
        for( int i=0; i<projects.size(); i++ )
            names[i] = projects.get(i).getName();

        String nearest = EditDistance.findNearest(name, names);
        return (AbstractProject)Hudson.getInstance().getItem(nearest);
    }

    private static final Comparator<Integer> REVERSE_INTEGER_COMPARATOR = new Comparator<Integer>() {
        public int compare(Integer o1, Integer o2) {
            return o2-o1;
        }
    };
}
