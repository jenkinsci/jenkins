package hudson.maven.local_repo;

import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.maven.AbstractMavenBuild;
import hudson.model.AbstractDescribableImpl;

/**
 * Strategy pattern that decides the location of the Maven local repository for a build.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.448
 * @see LocalRepositoryLocatorDescriptor
 */
public abstract class LocalRepositoryLocator extends AbstractDescribableImpl<LocalRepositoryLocator> implements ExtensionPoint {
    /**
     * Called during the build on the master to determine the location of the local Maven repository.
     *
     * @return
     *      null to let Maven uses its default location. Otherwise this must be located on the same
     *      node as {@link AbstractMavenBuild#getWorkspace()} does.
     */
    public abstract FilePath locate(AbstractMavenBuild build);

    @Override
    public LocalRepositoryLocatorDescriptor getDescriptor() {
        return (LocalRepositoryLocatorDescriptor)super.getDescriptor();
    }
}
