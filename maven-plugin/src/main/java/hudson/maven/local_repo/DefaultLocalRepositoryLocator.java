package hudson.maven.local_repo;

import hudson.Extension;
import hudson.FilePath;
import hudson.maven.AbstractMavenBuild;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Uses Maven's default local repository, which is actually <tt>~/.m2/repository</tt>
 *
 * @author Kohsuke Kawaguchi
 */
public class DefaultLocalRepositoryLocator extends LocalRepositoryLocator {
    @DataBoundConstructor
    public DefaultLocalRepositoryLocator() {
    }

    @Override
    public FilePath locate(AbstractMavenBuild build) {
        return null;
    }

    @Extension
    public static class DescriptorImpl extends LocalRepositoryLocatorDescriptor {
        @Override
        public String getDisplayName() {
            return "Default (~/.m2/repository)";
        }
    }
}
