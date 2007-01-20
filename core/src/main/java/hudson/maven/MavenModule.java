package hudson.maven;

import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Hudson;
import hudson.model.Items;
import hudson.model.Job;
import hudson.util.DescribableList;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

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

    public MavenModule(Hudson parent, String name) {
        super(parent, name);
    }

    @Override
    public void onLoad(String name) throws IOException {
        super.onLoad(name);
        if(reporters==null)
            reporters = new DescribableList<MavenReporter, Descriptor<MavenReporter>>(this);
        reporters.setOwner(this);
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

    static {
        Items.XSTREAM.alias("maven2", MavenModule.class);
    }
}
