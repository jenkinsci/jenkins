package hudson.maven;

import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.JobDescriptor;
import hudson.model.Descriptor.FormException;
import hudson.util.DescribableList;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;

/**
 * {@link Job} that builds projects based on Maven2.
 * 
 * @author Kohsuke Kawaguchi
 */
public final class MavenJob extends AbstractProject<MavenJob,MavenBuild> implements DescribableList.Owner {
    private DescribableList<MavenReporter,Descriptor<MavenReporter>> reporters =
        new DescribableList<MavenReporter,Descriptor<MavenReporter>>(this);

    public MavenJob(Hudson parent, String name) {
        super(parent, name);
    }

    @Override
    protected void onLoad(Hudson root, String name) throws IOException {
        super.onLoad(root, name);
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

    public JobDescriptor<MavenJob,MavenBuild> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final JobDescriptor<MavenJob,MavenBuild> DESCRIPTOR = new JobDescriptor<MavenJob,MavenBuild>(MavenJob.class) {
        public String getDisplayName() {
            return "Building Maven2 project (alpha)";
        }

        public MavenJob newInstance(String name) {
            return new MavenJob(Hudson.getInstance(),name);
        }
    };

    static {
        XSTREAM.alias("maven2", MavenJob.class);
    }
}
