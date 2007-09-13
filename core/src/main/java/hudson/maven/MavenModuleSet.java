package hudson.maven;

import hudson.CopyOnWrite;
import hudson.FilePath;
import hudson.Indenter;
import hudson.Util;
import hudson.matrix.MatrixConfiguration;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.DependencyGraph;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Executor;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import static hudson.model.ItemGroupMixIn.loadChildren;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Queue.Task;
import hudson.model.SCMedItem;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.tasks.Maven;
import hudson.tasks.Maven.MavenInstallation;
import hudson.util.CopyOnWriteMap;
import hudson.util.DescribableList;
import hudson.util.Function1;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

/**
 * Group of {@link MavenModule}s.
 *
 * <p>
 * This corresponds to the group of Maven POMs that constitute a single
 * tree of projects. This group serves as the grouping of those related
 * modules.
 *
 * @author Kohsuke Kawaguchi
 */
public final class MavenModuleSet extends AbstractMavenProject<MavenModuleSet,MavenModuleSetBuild> implements TopLevelItem, ItemGroup<MavenModule>, SCMedItem, DescribableList.Owner {
    /**
     * All {@link MavenModule}s, keyed by their {@link MavenModule#getModuleName()} module name}s.
     */
    transient /*final*/ Map<ModuleName,MavenModule> modules = new CopyOnWriteMap.Tree<ModuleName,MavenModule>();

    /**
     * Topologically sorted list of modules. This only includes live modules,
     * since archived ones usually don't have consistent history.
     */
    @CopyOnWrite
    transient List<MavenModule> sortedActiveModules;

    /**
     * Name of the top-level module. Null until the root module is determined.
     */
    private ModuleName rootModule;

    private String rootPOM;

    private String goals;

    /**
     * Default goals specified in POM. Can be null.
     */
    private String defaultGoals;

    /**
     * Identifies {@link MavenInstallation} to be used.
     * Null to indicate 'default' maven.
     */
    private String mavenName;

    /**
     * Equivalent of CLI <tt>MAVEN_OPTS</tt>. Can be null.
     */
    private String mavenOpts;

    /**
     * If true, the build will be aggregator style. False otherwise.
     *
     * @since 1.133
     */
    private boolean aggregatorStyleBuild = true;

    /**
     * Reporters configured at {@link MavenModuleSet} level. Applies to all {@link MavenModule} builds.
     */
    private DescribableList<MavenReporter,Descriptor<MavenReporter>> reporters =
        new DescribableList<MavenReporter,Descriptor<MavenReporter>>(this);

    public MavenModuleSet(String name) {
        super(Hudson.getInstance(),name);
    }

    public String getUrlChildPrefix() {
        // seemingly redundant "./" is used to make sure that ':' is not interpreted as the scheme identifier
        return ".";
    }

    public Hudson getParent() {
        return Hudson.getInstance();
    }

    public Collection<MavenModule> getItems() {
        return modules.values();
    }

    public Collection<MavenModule> getModules() {
        return getItems();
    }

    public MavenModule getItem(String name) {
        return modules.get(ModuleName.fromString(name));
    }

    public MavenModule getModule(String name) {
        return getItem(name);
    }

    protected void addTransientActionsFromBuild(MavenModuleSetBuild build, Set<Class> added) {
        if(build==null)    return;

        for (Action a : build.getActions())
            if(a instanceof MavenAggregatedReport)
                if(added.add(a.getClass()))
                    transientActions.add(((MavenAggregatedReport)a).getProjectAction(this));
    }

    /**
     * Called by {@link MavenModule#doDoDelete(StaplerRequest, StaplerResponse)}.
     * Real deletion is done by the caller, and this method only adjusts the
     * data structure the parent maintains.
     */
    /*package*/ void onModuleDeleted(MavenModule module) {
        modules.remove(module.getModuleName());
    }

    /**
     * Returns true if there's any disabled module.
     */
    public boolean hasDisabledModule() {
        for (MavenModule m : modules.values()) {
            if(m.isDisabled())
                return true;
        }
        return false;
    }

    /**
     * Possibly empty list of all disabled modules (if disabled==true)
     * or all enabeld modules (if disabled==false)
     */
    public List<MavenModule> getDisabledModules(boolean disabled) {
        if(sortedActiveModules!=null)
            return sortedActiveModules;

        List<MavenModule> r = new ArrayList<MavenModule>();
        for (MavenModule m : modules.values()) {
            if(m.isDisabled()==disabled)
                r.add(m);
        }
        return r;
    }

    public Indenter<MavenModule> createIndenter() {
        return new Indenter<MavenModule>() {
            protected int getNestLevel(MavenModule job) {
                return job.nestLevel;
            }
        };
    }

    public boolean isAggregatorStyleBuild() {
        return aggregatorStyleBuild;
    }

    /**
     * List of active {@link MavenReporter}s that should be applied to all module builds.
     */
    public DescribableList<MavenReporter, Descriptor<MavenReporter>> getReporters() {
        return reporters;
    }

    public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        if(ModuleName.isValid(token))
            return getModule(token);
        return super.getDynamic(token,req,rsp);
    }

    public File getRootDirFor(MavenModule child) {
        return new File(getModulesDir(),child.getModuleName().toFileSystemName());
    }

    public Collection<Job> getAllJobs() {
        Set<Job> jobs = new HashSet<Job>(getItems());
        jobs.add(this);
        return jobs;
    }

    /**
     * Gets the workspace of this job.
     */
    public FilePath getWorkspace() {
        Node node = getLastBuiltOn();
        if(node==null)  node = Hudson.getInstance();
        return node.getWorkspaceFor(this);
    }

    @Override
    protected Class<MavenModuleSetBuild> getBuildClass() {
        return MavenModuleSetBuild.class;
    }

    @Override
    public boolean isFingerprintConfigured() {
        return true;
    }

    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);

        modules = loadChildren(this, getModulesDir(),new Function1<ModuleName,MavenModule>() {
            public ModuleName call(MavenModule module) {
                return module.getModuleName();
            }
        });
        // update the transient nest level field.
        MavenModule root = getRootModule();
        if(root!=null && root.getChildren()!=null) {
            List<MavenModule> sortedList = new ArrayList<MavenModule>();
            Stack<MavenModule> q = new Stack<MavenModule>();
            root.nestLevel = 0;
            q.push(root);
            while(!q.isEmpty()) {
                MavenModule p = q.pop();
                sortedList.add(p);
                List<MavenModule> children = p.getChildren();
                if(children!=null) {
                    for (MavenModule m : children)
                        m.nestLevel = p.nestLevel+1;
                    for( int i=children.size()-1; i>=0; i--)    // add them in the reverse order
                        q.push(children.get(i));
                }
            }
            this.sortedActiveModules = sortedList;
        } else {
            this.sortedActiveModules = getDisabledModules(false);
        }

        if(reporters==null)
            reporters = new DescribableList<MavenReporter, Descriptor<MavenReporter>>(this);

        updateTransientActions();
    }

    private File getModulesDir() {
        return new File(getRootDir(),"modules");
    }

    /**
     * To make it easy to grasp relationship among modules
     * and the module set, we'll align the build numbers of
     * all the modules.
     *
     * <p>
     * This method is invoked from {@link Executor#run()},
     * and because of the mutual exclusion among {@link MavenModuleSetBuild}
     * and {@link MavenBuild}, we can safely touch all the modules.
     */
    public synchronized int assignBuildNumber() throws IOException {
        // determine the next value
        updateNextBuildNumber();

        return super.assignBuildNumber();
    }

    public void logRotate() throws IOException {
        super.logRotate();
        // perform the log rotation of modules
        for (MavenModule m : modules.values())
            m.logRotate();
    }

    /**
     * The next build of {@link MavenModuleSet} must have
     * the build number newer than any of the current module build.
     */
    /*package*/ void updateNextBuildNumber() throws IOException {
        int next = this.nextBuildNumber;
        for (MavenModule m : modules.values())
            next = Math.max(next,m.getNextBuildNumber());

        if(this.nextBuildNumber!=next) {
            this.nextBuildNumber=next;
            this.saveNextBuildNumber();
        }
    }

    protected void buildDependencyGraph(DependencyGraph graph) {
        // no dependency for this.
    }

    public MavenModule getRootModule() {
        if(rootModule==null)    return null;
        return modules.get(rootModule);
    }

    /**
     * Gets the location of top-level <tt>pom.xml</tt> relative to the workspace root.
     */
    public String getRootPOM() {
        if(rootPOM==null)   return "pom.xml";
        return rootPOM;
    }

    public AbstractProject<?,?> asProject() {
        return this;
    }

    /**
     * Gets the list of goals to execute.
     */
    public String getGoals() {
        if(goals==null) {
            if(defaultGoals!=null)  return defaultGoals;
            return "install";
        }
        return goals;
    }

    /**
     * Possibly null, whitespace-separated (including TAB, NL, etc) VM options
     * to be used to launch Maven process.
     */
    public String getMavenOpts() {
        return mavenOpts;
    }

    /**
     * Gets the Maven to invoke.
     * If null, we pick any random Maven installation.
     */
    public MavenInstallation getMaven() {
        for( MavenInstallation i : DESCRIPTOR.getMavenDescriptor().getInstallations() ) {
            if(mavenName==null || i.getName().equals(mavenName))
                return i;
        }
        return null;
    }

    /**
     * Returns the {@link MavenModule}s that are in the queue.
     */
    public List<Queue.Item> getQueueItems() {
        List<Queue.Item> r = new ArrayList<hudson.model.Queue.Item>();
        for( Queue.Item item : Hudson.getInstance().getQueue().getItems() ) {
            Task t = item.task;
            if((t instanceof MavenModule && ((MavenModule)t).getParent()==this) || t ==this)
                r.add(item);
        }
        return r;
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

    /*package*/ void reconfigure(PomInfo rootPom) throws IOException {
        if(this.rootModule!=null && this.rootModule.equals(rootPom.name))
            return; // no change
        this.rootModule = rootPom.name;
        this.defaultGoals = rootPom.defaultGoal;
        save();
    }

//
//
// Web methods
//
//

    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, FormException {
        super.submit(req,rsp);

        rootPOM = Util.fixEmpty(req.getParameter("rootPOM").trim());
        if(rootPOM!=null && rootPOM.equals("pom.xml"))   rootPOM=null;   // normalization

        goals = Util.fixEmpty(req.getParameter("goals").trim());
        mavenOpts = Util.fixEmpty(req.getParameter("mavenOpts").trim());
        mavenName = req.getParameter("maven_version");
        aggregatorStyleBuild = req.getParameter("maven.perModuleBuild")==null;

        reporters.rebuild(req,MavenReporters.getConfigurableList(),"reporter");
    }

    /**
     * Delete all disabled modules.
     */
    public void doDoDeleteAllDisabledModules(StaplerRequest req, StaplerResponse rsp) throws IOException {
        if(!Hudson.adminCheck(req,rsp))
            return;
        for( MavenModule m : getDisabledModules(true))
            m.delete();
        rsp.sendRedirect2(".");
    }

    public TopLevelItemDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends TopLevelItemDescriptor {
        private DescriptorImpl() {
            super(MavenModuleSet.class);
        }

        public String getDisplayName() {
            return "Build a maven2 project (beta)";
        }

        public MavenModuleSet newInstance(String name) {
            return new MavenModuleSet(name);
        }

        public Maven.DescriptorImpl getMavenDescriptor() {
            return Maven.DESCRIPTOR;
        }
    }
}
