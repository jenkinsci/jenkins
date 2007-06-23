package hudson.model;

import hudson.FilePath;
import hudson.model.Descriptor.FormException;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildTrigger;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrappers;
import hudson.tasks.Builder;
import hudson.tasks.Fingerprinter;
import hudson.tasks.Publisher;
import hudson.triggers.Trigger;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
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
public class Project extends AbstractProject<Project,Build> implements TopLevelItem, SCMedItem {

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

    public AbstractProject<?, ?> asProject() {
        return this;
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
    protected Class<Build> getBuildClass() {
        return Build.class;
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

    public Publisher getPublisher(Descriptor<Publisher> descriptor) {
        for (Publisher p : publishers) {
            if(p.getDescriptor()==descriptor)
                return p;
        }
        return null;
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
    @Override
    protected void submit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, FormException {
        super.submit(req,rsp);

        if(!Hudson.adminCheck(req,rsp))
            return;

        req.setCharacterEncoding("UTF-8");

        buildDescribable(req, BuildWrappers.WRAPPERS, buildWrappers, "wrapper");
        buildDescribable(req, BuildStep.BUILDERS, builders, "builder");
        buildDescribable(req, BuildStep.PUBLISHERS, publishers, "publisher");

        updateTransientActions();
    }

    @Override
    public void doConfigSubmit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        super.doConfigSubmit(req,rsp);

        Set<AbstractProject> upstream = Collections.emptySet();
        if(req.getParameter("pseudoUpstreamTrigger")!=null) {
            upstream = new HashSet<AbstractProject>(Items.fromNameList(req.getParameter("upstreamProjects"),AbstractProject.class));
        }

        // dependency setting might have been changed by the user, so rebuild.
        Hudson.getInstance().rebuildDependencyGraph();

        // reflect the submission of the pseudo 'upstream build trriger'.
        // this needs to be done after we release the lock on 'this',
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
                    BuildTrigger existing = (BuildTrigger)p.getPublisher(BuildTrigger.DESCRIPTOR);
                    p.addPublisher(new BuildTrigger(newChildProjects,
                        existing==null?Result.SUCCESS:existing.getThreshold()));
                }
            }
        }

        // notify the queue as the project might be now tied to different node
        Hudson.getInstance().getQueue().scheduleMaintenance();

        // this is to reflect the upstream build adjustments done above
        Hudson.getInstance().rebuildDependencyGraph();
    }

    private void updateTransientActions() {
        if(transientActions==null)
            transientActions = new Vector<Action>();    // happens when loaded from disk
        synchronized(transientActions) {
            transientActions.clear();
            for (JobProperty<? super Project> p : properties) {
                Action a = p.getJobAction(this);
                if(a!=null)
                    transientActions.add(a);
            }
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

    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends TopLevelItemDescriptor {
        private DescriptorImpl() {
            super(Project.class);
        }

        public String getDisplayName() {
            return "Build a free-style software project";
        }

        public Project newInstance(String name) {
            return new Project(Hudson.getInstance(),name);
        }
    }
}