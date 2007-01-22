package hudson.maven;

import hudson.FilePath;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.util.DescribableList;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.apache.maven.project.MavenProject;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.List;

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

    /*package*/ MavenModule(MavenModuleSet parent, PomInfo pom) {
        super(parent, pom.name.toFileSystemName());
        this.displayName = pom.displayName;
        this.relativePath = pom.relativePath;
    }

    protected void doSetName(String name) {
        super.doSetName(name);
        moduleName = ModuleName.fromFileSystemName(name);
    }

    @Override
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);
        if(reporters==null)
            reporters = new DescribableList<MavenReporter, Descriptor<MavenReporter>>(this);
        reporters.setOwner(this);
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

    public List<MavenModule> getDownstreamProjects() {
        // TODO
        throw new UnsupportedOperationException();
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
    }
}
