package hudson.maven;

import hudson.model.AbstractProject;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.Launcher;
import hudson.maven.reporters.MavenArtifactRecord;

import java.io.IOException;

/**
 * {@link Publisher} for Maven projects to deploy artifacts to a Maven repository
 * after the fact.
 *
 * <p>
 * When a build breaks in the middle, this is a convenient way to prevent
 * modules from being deployed partially. This can be combined with promoted builds
 * plugin to deploy artifacts after testing, for example. 
 *
 * @author Kohsuke Kawaguchi
 */
public class MavenRedeployer extends Publisher {
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        MavenArtifactRecord mar = build.getAction(MavenArtifactRecord.class);
        if(mar==null) {
            if(build.getResult().isBetterThan(Result.FAILURE)) {
                listener.getLogger().println("There's no record of artifact information. Is this really a Maven build?");
                build.setResult(Result.FAILURE);
            }
            // failed
            return true;
        }

        listener.getLogger().println("TODO");
        
        return true;
    }

    public BuildStepDescriptor<Publisher> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return AbstractMavenProject.class.isAssignableFrom(jobType);
        }

        public String getDisplayName() {
            return Messages.MavenRedeployer_DisplayName();
        }
    }
}
