package hudson.maven;

import hudson.CopyOnWrite;
import hudson.FilePath;
import hudson.Util;
import hudson.maven.reporters.MavenMailer;
import hudson.model.Action;
import hudson.model.DependencyGraph;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.JDK;
import hudson.model.Job;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Resource;
import hudson.tasks.LogRotator;
import hudson.util.DescribableList;
import org.apache.maven.project.MavenProject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.AbstractList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;

/**
 * {@link Job} that builds projects based on Maven2.
 * 
 * @author Kohsuke Kawaguchi
 */
public final class MavenModule extends AbstractMavenProject<MavenModule,MavenBuild> implements DescribableList.Owner {
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
    private volatile Set<ModuleName> dependencies;

    /**
     * List of child modules as defined by &lt;module> POM element.
     * Used to determine parent/child relationship of modules.
     * <p>
     * For compatibility reason, this field may be null when loading data from old hudson. 
     */
    @CopyOnWrite
    private volatile List<ModuleName> children;

    /**
     * Nest level used to display this module in the module list.
     * The root module and orphaned module gets 0.
     */
    /*package*/ volatile transient int nestLevel;

    /*package*/ MavenModule(MavenModuleSet parent, PomInfo pom, int firstBuildNumber) throws IOException {
        super(parent, pom.name.toFileSystemName());
        reconfigure(pom);
        updateNextBuildNumber(firstBuildNumber);
    }

    /**
     * {@link MavenModule} follows the same log rotation schedule as its parent. 
     */
    @Override
    public LogRotator getLogRotator() {
        return getParent().getLogRotator();
    }

    /**
     * @deprecated
     *      Not allowed to configure log rotation per module.
     */
    @Override
    public void setLogRotator(LogRotator logRotator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsLogRotator() {
        return false;
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
        this.children = pom.children;
        this.nestLevel = pom.getNestLevel();
        disabled = false;

        if (pom.mailNotifier != null) {
            MavenReporter reporter = getReporters().get(MavenMailer.DescriptorImpl.DESCRIPTOR);
            if (reporter != null) {
                MavenMailer mailer = (MavenMailer) reporter;
                mailer.dontNotifyEveryUnstableBuild = !pom.mailNotifier.isSendOnFailure();
                String recipients = pom.mailNotifier.getConfiguration().getProperty("recipients");
                if (recipients != null) {
                    mailer.recipients = recipients;
                }
            }
        }
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

    @Override
    public JDK getJDK() {
        // share one setting for the whole module set.
        return getParent().getJDK();
    }

    @Override
    protected Class<MavenBuild> getBuildClass() {
        return MavenBuild.class;
    }

    public ModuleName getModuleName() {
        return moduleName;
    }

    @Override
    public String getShortUrl() {
        return moduleName.toFileSystemName()+'/';
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getPronoun() {
        return "Module";
    }

    @Override
    public boolean isNameEditable() {
        return false;
    }

    public MavenModuleSet getParent() {
        return (MavenModuleSet)super.getParent();
    }

    /**
     * Gets all the child modules (that are listed in the &lt;module> element in our POM.)
     * <p>
     * This method returns null if this information is not recorded. This happens
     * for compatibility reason.
     */
    public List<MavenModule> getChildren() {
        final List<ModuleName> l = children;    // take a snapshot
        if(l==null) return null;
        return new AbstractList<MavenModule>() {
            public int size() {
                return l.size();
            }
            public MavenModule get(int i) {
                return getParent().modules.get(l.get(i));
            }
        };
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
    public Label getAssignedLabel() {
        Node n = getParent().getLastBuiltOn();
        if(n==null) return null;
        return n.getSelfLabel();
    }

    /**
     * Workspace of a {@link MavenModule} is a part of the parent's workspace.
     * <p>
     * That is, {@Link MavenModuleSet} builds are incompatible with any {@link MavenModule}
     * builds, whereas {@link MavenModule} builds are compatible with each other.
     */
    @Override
    public Resource getWorkspaceResource() {
        return new Resource(getParent().getWorkspaceResource(),getDisplayName()+" workspace");
    }

    @Override
    public boolean isFingerprintConfigured() {
        return true;
    }

    protected void buildDependencyGraph(DependencyGraph graph) {
        if(isDisabled())        return;

        Map<ModuleName,MavenModule> modules = new HashMap<ModuleName,MavenModule>();

        for (MavenModule m : Hudson.getInstance().getAllItems(MavenModule.class)) {
            if(m.isDisabled())  continue;
            modules.put(m.getModuleName(),m);
        }

        // in case two modules with the same name is defined, modules in this MavenModuleSet
        // takes precedence

        for (MavenModule m : getParent().getModules()) {
            if(m.isDisabled())  continue;
            modules.put(m.getModuleName(),m);
        }

        for (ModuleName d : dependencies) {
            MavenModule src = modules.get(d);
            if(src!=null)
                graph.addDependency(src,this);
        }
    }

    @Override
    protected void addTransientActionsFromBuild(MavenBuild build, Set<Class> added) {
        if(build==null)    return;
        List<MavenReporter> list = build.projectActionReporters;
        if(list==null)   return;

        for (MavenReporter step : list) {
            if(!added.add(step.getClass()))     continue;   // already added
            Action a = step.getProjectAction(this);
            if(a!=null)
                transientActions.add(a);
        }
    }

    /**
     * List of active {@link MavenReporter}s configured for this module.
     */
    public DescribableList<MavenReporter, Descriptor<MavenReporter>> getReporters() {
        return reporters;
    }

    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, FormException {
        super.submit(req, rsp);

        reporters.rebuild(req,MavenReporters.getConfigurableList(),"reporter");

        goals = Util.fixEmpty(req.getParameter("goals").trim());

        // dependency setting might have been changed by the user, so rebuild.
        Hudson.getInstance().rebuildDependencyGraph();
    }

    protected void performDelete() throws IOException {
        super.performDelete();
        getParent().onModuleDeleted(this);
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

    /**
     * Creates a list of {@link MavenReporter}s to be used for a build of this project.
     */
    protected final List<MavenReporter> createReporters() {
        List<MavenReporter> reporters = new ArrayList<MavenReporter>();

        getReporters().addAllTo(reporters);
        getParent().getReporters().addAllTo(reporters);

        for (MavenReporterDescriptor d : MavenReporters.LIST) {
            if(getReporters().contains(d))
                continue;   // already configured
            MavenReporter auto = d.newAutoInstance(this);
            if(auto!=null)
                reporters.add(auto);
        }

        return reporters;
    }
}
