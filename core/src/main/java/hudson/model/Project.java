package hudson.model;

import hudson.Util;
import hudson.model.Descriptor.FormException;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrappers;
import hudson.tasks.Builder;
import hudson.tasks.Fingerprinter;
import hudson.tasks.Publisher;
import hudson.tasks.Maven;
import hudson.tasks.Maven.ProjectWithMaven;
import hudson.tasks.Maven.MavenInstallation;
import hudson.triggers.Trigger;
import hudson.util.DescribableList;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Buildable software project.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class Project<P extends Project<P,B>,B extends Build<P,B>>
    extends AbstractProject<P,B> implements SCMedItem, DescribableList.Owner, ProjectWithMaven {

    /**
     * List of active {@link Builder}s configured for this project.
     */
    private DescribableList<Builder,Descriptor<Builder>> builders =
            new DescribableList<Builder,Descriptor<Builder>>(this);

    /**
     * List of active {@link Publisher}s configured for this project.
     */
    private DescribableList<Publisher,Descriptor<Publisher>> publishers =
            new DescribableList<Publisher,Descriptor<Publisher>>(this);

    /**
     * List of active {@link BuildWrapper}s configured for this project.
     */
    private DescribableList<BuildWrapper,Descriptor<BuildWrapper>> buildWrappers =
            new DescribableList<BuildWrapper,Descriptor<BuildWrapper>>(this);

    /**
     * Creates a new project.
     */
    public Project(ItemGroup parent,String name) {
        super(parent,name);
    }

    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);

        if(buildWrappers==null)
            // it didn't exist in < 1.64
            buildWrappers = new DescribableList<BuildWrapper, Descriptor<BuildWrapper>>(this);
        builders.setOwner(this);
        publishers.setOwner(this);
        buildWrappers.setOwner(this);
    }

    public AbstractProject<?, ?> asProject() {
        return this;
    }

    public List<Builder> getBuilders() {
        return builders.toList();
    }

    public Map<Descriptor<Publisher>,Publisher> getPublishers() {
        return publishers.toMap();
    }

    public DescribableList<Builder,Descriptor<Builder>> getBuildersList() {
        return builders;
    }
    
    public DescribableList<Publisher,Descriptor<Publisher>> getPublishersList() {
        return publishers;
    }

    public Map<Descriptor<BuildWrapper>,BuildWrapper> getBuildWrappers() {
        return buildWrappers.toMap();
    }

    @Override
    protected Set<ResourceActivity> getResourceActivities() {
        final Set<ResourceActivity> activities = new HashSet<ResourceActivity>();

        activities.addAll(super.getResourceActivities());
        activities.addAll(Util.filter(builders,ResourceActivity.class));
        activities.addAll(Util.filter(publishers,ResourceActivity.class));
        activities.addAll(Util.filter(buildWrappers,ResourceActivity.class));

        return activities;
    }

    /**
     * Adds a new {@link BuildStep} to this {@link Project} and saves the configuration.
     */
    public void addPublisher(Publisher buildStep) throws IOException {
        publishers.add(buildStep);
    }

    /**
     * Removes a publisher from this project, if it's active.
     */
    public void removePublisher(Descriptor<Publisher> descriptor) throws IOException {
        publishers.remove(descriptor);
    }

    public Publisher getPublisher(Descriptor<Publisher> descriptor) {
        for (Publisher p : publishers) {
            if(p.getDescriptor()==descriptor)
                return p;
        }
        return null;
    }

    protected void buildDependencyGraph(DependencyGraph graph) {
        publishers.buildDependencyGraph(this,graph);
        builders.buildDependencyGraph(this,graph);
        buildWrappers.buildDependencyGraph(this,graph);
    }

    @Override
    public boolean isFingerprintConfigured() {
        for (Publisher p : publishers) {
            if(p instanceof Fingerprinter)
                return true;
        }
        return false;
    }

    @Override
    public MavenInstallation inferMavenInstallation() {
        for (Builder builder : builders) {
            if (builder instanceof Maven)
                return ((Maven) builder).getMaven();
        }
        return null;
    }

    //
//
// actions
//
//
    @Override
    protected void submit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, FormException {
        super.submit(req,rsp);

        req.setCharacterEncoding("UTF-8");
    JSONObject json = req.getSubmittedForm();

        buildWrappers.rebuild(req,json, BuildWrappers.getFor(this), "wrapper");
        builders.rebuildHetero(req,json, BuildStep.BUILDERS, "builder");
        publishers.rebuild(req, json, BuildStepDescriptor.filter(BuildStep.PUBLISHERS, this.getClass()), "publisher");
        updateTransientActions(); // to pick up transient actions from builder, publisher, etc.
    }

    protected void updateTransientActions() {
        synchronized(transientActions) {
            super.updateTransientActions();

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
            for (BuildWrapper step : buildWrappers) {
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

    /**
     * @deprecated
     *      left for legacy config file compatibility
     */
    @Deprecated
    private transient String slave;
}