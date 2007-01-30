package hudson.maven;

import hudson.CopyOnWrite;
import hudson.FilePath;
import hudson.model.AbstractProject;
import hudson.model.DependencyGraph;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Node;
import hudson.util.DescribableList;
import org.apache.maven.project.MavenProject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

    /**
     * Relative path to this module's root directory
     * from {@link MavenModuleSet#getWorkspace()} 
     */
    private String relativePath;

    /**
     * List of modules that this module declares direct dependencies on.
     */
    @CopyOnWrite
    private Set<ModuleName> dependencies;

    /*package*/ MavenModule(MavenModuleSet parent, PomInfo pom) {
        super(parent, pom.name.toFileSystemName());
        reconfigure(pom);
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
    }

    @Override
    public FilePath getWorkspace() {
        return getParent().getWorkspace().child(relativePath);
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

    /**
     * List of active {@link MavenReporter}s configured for this project.
     */
    public DescribableList<MavenReporter, Descriptor<MavenReporter>> getReporters() {
        return reporters;
    }

    public void doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        super.doConfigSubmit(req, rsp);

        try {
            reporters.rebuild(req,MavenReporters.LIST,"reporter");
        } catch (FormException e) {
            sendError(e,req,rsp);
        }

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
