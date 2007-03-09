package hudson.maven;

import hudson.FilePath;
import hudson.Util;
import hudson.triggers.Trigger;
import hudson.tasks.Maven;
import hudson.tasks.Maven.MavenInstallation;
import hudson.model.AbstractProject;
import hudson.model.DependencyGraph;
import hudson.model.Executor;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.SCMedItem;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.util.CopyOnWriteMap;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

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
public final class MavenModuleSet extends AbstractProject<MavenModuleSet,MavenModuleSetBuild> implements TopLevelItem, ItemGroup<MavenModule>, SCMedItem {
    /**
     * All {@link MavenModule}s, keyed by their {@link MavenModule#getModuleName()} module name}s.
     */
    transient /*final*/ Map<ModuleName,MavenModule> modules = new CopyOnWriteMap.Tree<ModuleName,MavenModule>();

    /**
     * Name of the top-level module.
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
    public Collection<MavenModule> getDisabledModules(boolean disabled) {
        List<MavenModule> r = new ArrayList<MavenModule>();
        for (MavenModule m : modules.values()) {
            if(m.isDisabled()==disabled)
                r.add(m);
        }
        return r;
    }

    public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        if(ModuleName.isValid(token))
            return getModule(token);
        return super.getDynamic(token,req,rsp);
    }

    public File getRootDirFor(MavenModule child) {
        return new File(new File(getRootDir(),"modules"),child.getModuleName().toFileSystemName());
    }

    public Collection<Job> getAllJobs() {
        Set<Job> jobs = new HashSet<Job>(getItems());
        jobs.add(this);
        return jobs;
    }

    @Override
    protected boolean isBuildBlocked() {
        if(super.isBuildBlocked())
            return true;
        // updating the workspace (=our build) cannot be done
        // if someone else is still touching the workspace.
        for (MavenModule m : modules.values()) {
            if(m.isBuilding())
                return true;
        }
        return false;
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
    public MavenModuleSetBuild newBuild() throws IOException {
        MavenModuleSetBuild lastBuild = new MavenModuleSetBuild(this);
        builds.put(lastBuild);
        return lastBuild;
    }

    @Override
    protected MavenModuleSetBuild loadBuild(File dir) throws IOException {
        return new MavenModuleSetBuild(this,dir);
    }

    @Override
    public boolean isFingerprintConfigured() {
        return true;
    }

    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);

        File modulesDir = new File(getRootDir(),"modules");
        modulesDir.mkdirs(); // make sure it exists

        File[] subdirs = modulesDir.listFiles(new FileFilter() {
            public boolean accept(File child) {
                return child.isDirectory();
            }
        });
        modules = new CopyOnWriteMap.Tree<ModuleName,MavenModule>();
        for (File subdir : subdirs) {
            try {
                MavenModule item = (MavenModule) Items.load(this,subdir);
                modules.put(item.getModuleName(), item);
            } catch (IOException e) {
                e.printStackTrace(); // TODO: logging
            }
        }
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

    public synchronized int getNextBuildNumber() {
        try {
            updateNextBuildNumber();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE,"Failed to save the next build number",e);
        }
        return nextBuildNumber;
    }

    private void updateNextBuildNumber() throws IOException {
        int next = this.nextBuildNumber;
        for (MavenModule m : modules.values())
            next = Math.max(next,m.getNextBuildNumber());

        if(this.nextBuildNumber!=next) {
            this.nextBuildNumber=next;
            this.saveNextBuildNumber();
        }

        for (MavenModule m : modules.values())
            m.updateNextBuildNumber(next);
    }

    protected void buildDependencyGraph(DependencyGraph graph) {
        // no dependency for this.
    }

    public MavenModule getRootModule() {
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
        if(this.rootModule!=null && this.rootModule.equals(rootModule))
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

    public synchronized void doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        if(!Hudson.adminCheck(req,rsp))
            return;

        rootPOM = Util.fixEmpty(req.getParameter("rootPOM").trim());
        if(rootPOM.equals("pom.xml"))   rootPOM=null;   // normalization

        goals = Util.fixEmpty(req.getParameter("goals").trim());
        mavenName = req.getParameter("maven_version");

        super.doConfigSubmit(req,rsp);

        save();
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
            return "Build a maven2 project (alpha)";
        }

        public MavenModuleSet newInstance(String name) {
            return new MavenModuleSet(name);
        }
        
        public Maven.DescriptorImpl getMavenDescriptor() {
            return Maven.DESCRIPTOR;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(MavenModuleSet.class.getName());
}
