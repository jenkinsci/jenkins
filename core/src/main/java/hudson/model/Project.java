package hudson.model;

import hudson.FilePath;
import hudson.model.Descriptor.FormException;
import hudson.model.Fingerprint.RangeSet;
import hudson.model.RunMap.Constructor;
import hudson.scm.SCMS;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildTrigger;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrappers;
import hudson.tasks.Builder;
import hudson.tasks.Fingerprinter;
import hudson.tasks.Publisher;
import hudson.triggers.Trigger;
import hudson.triggers.Triggers;
import hudson.util.EditDistance;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
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
public class Project extends AbstractProject<Project,Build> {

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
     * List of active {@link BuildWrapper}s configured for this project.
     */
    private List<BuildWrapper> buildWrappers = new Vector<BuildWrapper>();

    /**
     * {@link Action}s contributed from {@link #triggers}, {@link #builders},
     * and {@link #publishers}.
     *
     * We don't want to persist them separately, and these actions
     * come and go as configuration change, so it's kept separate.
     */
    private transient /*final*/ List<Action> transientActions = new Vector<Action>();

    /**
     * Creates a new project.
     */
    public Project(Hudson parent,String name) {
        super(parent,name);
    }

    protected void onLoad(Hudson root, String name) throws IOException {
        super.onLoad(root, name);

        if(triggers==null)
            // it didn't exist in < 1.28
            triggers = new Vector<Trigger>();
        if(buildWrappers==null)
            // it didn't exist in < 1.64
            buildWrappers = new Vector<BuildWrapper>();

        this.builds = new RunMap<Build>();
        this.builds.load(this,new Constructor<Build>() {
            public Build create(File dir) throws IOException {
                return new Build(Project.this,dir);
            }
        });

        for (Trigger t : triggers)
            t.start(this,false);

        updateTransientActions();
    }

    @Override
    public BallColor getIconColor() {
        if(isDisabled())
            // use grey to indicate that the build is disabled
            return BallColor.GREY;
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

    public synchronized Map<Descriptor<BuildWrapper>,BuildWrapper> getBuildWrappers() {
        return Descriptor.toMap(buildWrappers);
    }

    private synchronized <T extends Describable<T>>
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

    private synchronized <T extends Describable<T>>
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

    /**
     * Adds a new {@link Trigger} to this {@link Project} if not active yet.
     */
    public void addTrigger(Trigger trigger) throws IOException {
        addToList(trigger,triggers);
    }

    public void removeTrigger(Descriptor<Trigger> trigger) throws IOException {
        removeFromList(trigger,triggers);
    }

    /**
     * Adds a new {@link BuildStep} to this {@link Project} and saves the configuration.
     */
    private void addPublisher(Publisher buildStep) throws IOException {
        addToList(buildStep,publishers);
    }

    /**
     * Removes a publisher from this project, if it's active.
     */
    private void removePublisher(Descriptor<Publisher> descriptor) throws IOException {
        removeFromList(descriptor, publishers);
    }

    @Override
    public Build newBuild() throws IOException {
        Build lastBuild = new Build(this);
        builds.put(lastBuild);
        return lastBuild;
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
     * Accepts submission from the configuration page.
     */
    public void doConfigSubmit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {

        Set<Project> upstream = Collections.emptySet();

        synchronized(this) {
            try {
                if(!Hudson.adminCheck(req,rsp))
                    return;

                req.setCharacterEncoding("UTF-8");

                int scmidx = Integer.parseInt(req.getParameter("scm"));
                setScm(SCMS.SCMS.get(scmidx).newInstance(req));

                buildDescribable(req, BuildWrappers.WRAPPERS, buildWrappers, "wrapper");
                buildDescribable(req, BuildStep.BUILDERS, builders, "builder");
                buildDescribable(req, BuildStep.PUBLISHERS, publishers, "publisher");

                for (Trigger t : triggers)
                    t.stop();
                buildDescribable(req, Triggers.TRIGGERS, triggers, "trigger");
                for (Trigger t : triggers)
                    t.start(this,true);

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
     * @deprecated
     *      left for legacy config file compatibility
     */
    @Deprecated
    private transient String slave;

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

    /**
     * Finds a {@link Project} that has the name closest to the given name.
     */
    public static Project findNearest(String name) {
        List<Project> projects = Hudson.getInstance().getProjects();
        String[] names = new String[projects.size()];
        for( int i=0; i<projects.size(); i++ )
            names[i] = projects.get(i).getName();

        String nearest = EditDistance.findNearest(name, names);
        return (Project)Hudson.getInstance().getJob(nearest);
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