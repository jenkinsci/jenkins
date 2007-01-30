package hudson.maven;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.DependencyGraph;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.TaskListener;
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
public class MavenModuleSet extends AbstractProject<MavenModuleSet,MavenModuleSetBuild> implements TopLevelItem, ItemGroup<MavenModule> {
    /**
     * All {@link MavenModule}s, keyed by their {@link MavenModule#getModuleName()} module name}s.
     */
    transient /*final*/ Map<ModuleName,MavenModule> modules = new CopyOnWriteMap.Tree<ModuleName,MavenModule>();

    /**
     * Name of the top-level module.
     */
    private ModuleName rootModule;

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
        // TODO: support roaming and etc
        return Hudson.getInstance().getWorkspaceFor(this);
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

    protected void buildDependencyGraph(DependencyGraph graph) {
        // no dependency for this.
    }

    /**
     * Obtains a workspace.
     */
    public boolean checkout(Launcher launcher, TaskListener listener) throws IOException {
        try {
            FilePath workspace = getWorkspace();
            workspace.mkdirs();
            return getScm().checkout(launcher, workspace, listener);
        } catch (InterruptedException e) {
            e.printStackTrace(listener.fatalError("SCM check out aborted"));
            return false;
        }
    }

    public MavenModule getRootModule() {
        return modules.get(rootModule);
    }

    /*package*/ void setRootModule(ModuleName rootModule) throws IOException {
        if(this.rootModule!=null && this.rootModule.equals(rootModule))
            return; // no change
        this.rootModule = rootModule;
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

        super.doConfigSubmit(req,rsp);

        save();
        // SCM setting might have changed. Reparse POMs.
        scheduleBuild();
    }

    public TopLevelItemDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    public static final TopLevelItemDescriptor DESCRIPTOR = new TopLevelItemDescriptor(MavenModuleSet.class) {
        public String getDisplayName() {
            return "Building a maven2 project";
        }

        public MavenModuleSet newInstance(String name) {
            return new MavenModuleSet(name);
        }
    };

    static {
        Items.XSTREAM.alias("maven2-module-set", MavenModule.class);
    }
}
