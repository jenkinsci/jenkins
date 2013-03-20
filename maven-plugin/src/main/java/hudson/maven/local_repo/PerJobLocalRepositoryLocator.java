package hudson.maven.local_repo;

import hudson.Extension;
import hudson.FilePath;
import hudson.maven.AbstractMavenBuild;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Uses a local repository isolated per job.
 *
 * @author Kohsuke Kawaguchi
 */
public class PerJobLocalRepositoryLocator extends LocalRepositoryLocator {
    @DataBoundConstructor
    public PerJobLocalRepositoryLocator() {
    }

    @Override
    public FilePath locate(AbstractMavenBuild build) {
        // XXX should this use ((MavenBuild) build).getParentBuild().getWorkspace() when instanceof MavenBuild?
        return build.getWorkspace().child(".repository");
    }

    @Extension
    public static class DescriptorImpl extends LocalRepositoryLocatorDescriptor {
        @Override
        public String getDisplayName() {
            return "Local to the workspace";
        }
    }
}
