package hudson.model;

import hudson.model.Descriptor.FormException;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildTrigger;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrappers;
import hudson.tasks.Builder;
import hudson.tasks.Fingerprinter;
import hudson.tasks.Publisher;
import hudson.triggers.Trigger;
import hudson.FilePath;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

/**
 * Buildable software project.
 *
 * @author Kohsuke Kawaguchi
 */
public class Project extends AbstractProject<Project,Build> implements TopLevelItem {

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

    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);

        if(buildWrappers==null)
            // it didn't exist in < 1.64
            buildWrappers = new Vector<BuildWrapper>();

        updateTransientActions();
    }

    @Override
    public Hudson getParent() {
        return Hudson.getInstance();
    }

    @Override
    public FilePath getWorkspace() {
        Node node = getLastBuiltOn();
        if(node==null)  node = getParent();
        return node.getWorkspaceFor(this);
    }

    @Override
    public BallColor getIconColor() {
        if(isDisabled())
            // use grey to indicate that the build is disabled
            return BallColor.GREY;
        else
            return super.getIconColor();
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

    @Override
    protected Build loadBuild(File dir) throws IOException {
        return new Build(this,dir);
    }

    protected void buildDependencyGraph(DependencyGraph graph) {
        BuildTrigger buildTrigger = (BuildTrigger) getPublishers().get(BuildTrigger.DESCRIPTOR);
        if(buildTrigger!=null)
             graph.addDependency(this,buildTrigger.getChildProjects());
    }

    @Override
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

        Set<AbstractProject> upstream = Collections.emptySet();

        synchronized(this) {
            try {
                if(!Hudson.adminCheck(req,rsp))
                    return;

                req.setCharacterEncoding("UTF-8");

                buildDescribable(req, BuildWrappers.WRAPPERS, buildWrappers, "wrapper");
                buildDescribable(req, BuildStep.BUILDERS, builders, "builder");
                buildDescribable(req, BuildStep.PUBLISHERS, publishers, "publisher");

                super.doConfigSubmit(req,rsp);

                updateTransientActions();
            } catch (FormException e) {
                sendError(e,req,rsp);
            }
        }

        if(req.getParameter("pseudoUpstreamTrigger")!=null) {
            upstream = new HashSet<AbstractProject>(Items.fromNameList(req.getParameter("upstreamProjects"),AbstractProject.class));
        }

        // this needs to be done after we release the lock on this,
        // or otherwise we could dead-lock
        for (Project p : Hudson.getInstance().getProjects()) {
            boolean isUpstream = upstream.contains(p);
            synchronized(p) {
                List<AbstractProject> newChildProjects = new ArrayList<AbstractProject>(p.getDownstreamProjects());

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

        save();

        // notify the queue as the project might be now tied to different node
        Hudson.getInstance().getQueue().scheduleMaintenance();

        // dependency setting might have been changed by the user, so rebuild.
        Hudson.getInstance().rebuildDependencyGraph();
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

    /**
     * @deprecated
     *      left for legacy config file compatibility
     */
    @Deprecated
    private transient String slave;

    public TopLevelItemDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    public static final TopLevelItemDescriptor DESCRIPTOR = new TopLevelItemDescriptor(Project.class) {
        public String getDisplayName() {
            return "Building a software project";
        }

        public Project newInstance(String name) {
            return new Project(Hudson.getInstance(),name);
        }
    };
}