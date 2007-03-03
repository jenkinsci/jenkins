package hudson.maven;

import hudson.CopyOnWrite;
import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.DependencyGraph;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Node;
import hudson.triggers.Trigger;
import hudson.util.DescribableList;
import org.apache.maven.project.MavenProject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

/**
 * {@link Job} that builds projects based on Maven2.
 * 
 * @author Kohsuke Kawaguchi
 */
public final class MavenModule extends AbstractProject<MavenModule,MavenBuild> implements DescribableList.Owner {
    private DescribableList<MavenReporter,Descriptor<MavenReporter>> reporters =
        new DescribableList<MavenReporter,Descriptor<MavenReporter>>(this);

    /**
     * Name taken from {@link MavenProject#getName()}.
     */
    private String displayName;

    private transient ModuleName moduleName;

    private String relativePath;

    /**
     * If this module has goals specified by itself.
     * Otherwise leave it null to use the default goals specified in the parent.
     */
    private String goals;

    /**
     * List of modules that this module declares direct dependencies on.
     */
    @CopyOnWrite
    private Set<ModuleName> dependencies;

    /**
     * {@link Action}s contributed from {@link #triggers} and {@link MavenBuild#projectActionReporters}
     * from the last build.
     *
     * We don't want to persist them separately, and these actions
     * come and go as configuration change, so it's kept separate.
     */
    private transient /*final*/ List<Action> transientActions = new Vector<Action>();

    /*package*/ MavenModule(MavenModuleSet parent, PomInfo pom, int firstBuildNumber) throws IOException {
        super(parent, pom.name.toFileSystemName());
        reconfigure(pom);
        updateNextBuildNumber(firstBuildNumber);
    }

    /**
     * Called to update the module with the new POM.
     * <p>
     * This method is invoked on {@link MavenModule} that has the matching
     * {@link ModuleName}.
     */
    /*package*/ final void reconfigure(PomInfo pom) {
        this.displayName = pom.displayName;
        this.relativePath = pom.relativePath;
        this.dependencies = pom.dependencies;
    }

    protected void doSetName(String name) {
        moduleName = ModuleName.fromFileSystemName(name);
        super.doSetName(moduleName.toString());
    }

    @Override
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent,name);
        if(reporters==null)
            reporters = new DescribableList<MavenReporter, Descriptor<MavenReporter>>(this);
        reporters.setOwner(this);
        if(dependencies==null)
            dependencies = Collections.emptySet();
        updateTransientActions();
    }

    /**
     * Relative path to this module's root directory
     * from {@link MavenModuleSet#getWorkspace()}.
     *
     * The path separator is normalized to '/'.
     */
    public String getRelativePath() {
        return relativePath;
    }

    /**
     * Gets the list of goals to execute for this module.
     */
    public String getGoals() {
        if(goals!=null) return goals;
        return getParent().getGoals();
    }

    /**
     * Gets the list of goals specified by the user,
     * without taking inheritance and POM default goals
     * into account.
     *
     * <p>
     * This is only used to present the UI screen, and in
     * all the other cases {@link #getGoals()} should be used.
     */
    public String getUserConfiguredGoals() {
        return goals;
    }

    @Override
    public FilePath getWorkspace() {
        return getParent().getModuleRoot().child(relativePath);
    }

    public ModuleName getModuleName() {
        return moduleName;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    public MavenModuleSet getParent() {
        return (MavenModuleSet)super.getParent();
    }

    /*package*/ void updateNextBuildNumber(int next) throws IOException {
        if(next>nextBuildNumber) {
            this.nextBuildNumber = next;
            saveNextBuildNumber();
        }
    }

    /**
     * {@link MavenModule} uses the workspace of the {@link MavenModuleSet},
     * so it always needs to be built on the same slave as the parent.
     */
    public Node getAssignedNode() {
        return getParent().getLastBuiltOn();
    }

    @Override
    public MavenBuild newBuild() throws IOException {
        MavenBuild lastBuild = new MavenBuild(this);
        builds.put(lastBuild);
        return lastBuild;
    }

    @Override
    protected MavenBuild loadBuild(File dir) throws IOException {
        return new MavenBuild(this,dir);
    }

    @Override
    protected boolean isBuildBlocked() {
        if(super.isBuildBlocked())
            return true;

        // if the module set's new build is planned or in progress,
        // don't start a new build. Otherwise on a busy maven project
        // MavenModuleSet will never get a chance to run.
        MavenModuleSet p = getParent();
        return p.isBuilding() || p.isInQueue();
    }

    @Override
    public boolean isFingerprintConfigured() {
        return true;
    }

    protected void buildDependencyGraph(DependencyGraph graph) {
        Map<ModuleName,MavenModule> modules = new HashMap<ModuleName,MavenModule>();

        for (MavenModule m : Hudson.getInstance().getAllItems(MavenModule.class))
            modules.put(m.getModuleName(),m);

        for (ModuleName d : dependencies) {
            MavenModule src = modules.get(d);
            if(src!=null)
                graph.addDependency(src,this);
        }
    }

    public synchronized List<Action> getActions() {
        // add all the transient actions, too
        List<Action> actions = new Vector<Action>(super.getActions());
        actions.addAll(transientActions);
        return actions;
    }

    /*package*/ void updateTransientActions() {
        if(transientActions==null)
            transientActions = new Vector<Action>();    // happens when loaded from disk
        synchronized(transientActions) {
            transientActions.clear();

            MavenBuild lb = getLastBuild();
            if(lb==null)    return;
            
            List<MavenReporter> list = lb.projectActionReporters;
            if(list!=null)
                for (MavenReporter step : list) {
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
     * List of active {@link MavenReporter}s configured for this project.
     */
    public DescribableList<MavenReporter, Descriptor<MavenReporter>> getReporters() {
        return reporters;
    }

    public void doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        super.doConfigSubmit(req, rsp);

        try {
            reporters.rebuild(req,MavenReporters.getConfigurableList(),"reporter");
        } catch (FormException e) {
            sendError(e,req,rsp);
        }

        goals = Util.fixEmpty(req.getParameter("goals").trim());

        save();

        // dependency setting might have been changed by the user, so rebuild.
        Hudson.getInstance().rebuildDependencyGraph();
    }

    /**
     * Marks this build as disabled.
     */
    public void disable() throws IOException {
        if(!disabled) {
            disabled = true;
            save();
        }
    }
}
