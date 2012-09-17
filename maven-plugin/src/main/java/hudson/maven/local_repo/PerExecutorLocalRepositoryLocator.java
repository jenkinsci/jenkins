package hudson.maven.local_repo;

import hudson.Extension;
import hudson.FilePath;
import hudson.maven.AbstractMavenBuild;
import hudson.model.Executor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Kohsuke Kawaguchi
 */
public class PerExecutorLocalRepositoryLocator  extends LocalRepositoryLocator {
    @DataBoundConstructor
    public PerExecutorLocalRepositoryLocator() {}

    @Override
    public FilePath locate(AbstractMavenBuild build) {
        return build.getBuiltOn().getRootPath().child("maven-repositories/"+ Executor.currentExecutor().getNumber());
    }

    @Extension
    public static class DescriptorImpl extends LocalRepositoryLocatorDescriptor {
        @Override
        public String getDisplayName() {
            return "Local to the executor";
        }
    }
}
