package hudson.maven.local_repo;

import hudson.Extension;
import hudson.FilePath;
import hudson.maven.AbstractMavenBuild;
import hudson.maven.MavenBuild;
import hudson.maven.MavenModuleSetBuild;
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
        if (build instanceof MavenBuild) {
            MavenModuleSetBuild parentBuild = ((MavenBuild) build).getModuleSetBuild();
            if (parentBuild != null) {
                build = parentBuild;
            }
        }
        FilePath ws = build.getWorkspace();
        if (ws == null) {
            return null;
        }
        return ws.child(".repository");
    }

    @Extension
    public static class DescriptorImpl extends LocalRepositoryLocatorDescriptor {
        @Override
        public String getDisplayName() {
            return "Local to the workspace";
        }
    }
}
