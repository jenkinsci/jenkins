package hudson.scm;

import hudson.model.Descriptor;

import java.util.List;
import java.util.Collections;

/**
 * {@link Descriptor} for {@link SCM}.
 *
 * @param <T>
 *      The 'self' type that represents the type of {@link SCM} that
 *      this descriptor describes.
 * @author Kohsuke Kawaguchi
 */
public abstract class SCMDescriptor<T extends SCM> extends Descriptor<SCM> {
    /**
     * If this SCM has corresponding {@link RepositoryBrowser},
     * that type. Otherwise this SCM will not have any repository browser.
     */
    public final Class<? extends RepositoryBrowser> repositoryBrowser;

    /**
     * Incremented every time a new {@link SCM} instance is created from this descriptor. 
     * This is used to invalidate cache. Due to the lack of synchronization and serialization,
     * this field doesn't really count the # of instances created to date,
     * but it's good enough for the cache invalidation.
     */
    public volatile int generation = 1;

    protected SCMDescriptor(Class<T> clazz, Class<? extends RepositoryBrowser> repositoryBrowser) {
        super(clazz);
        this.repositoryBrowser = repositoryBrowser;
    }

    /**
     * Optional method used by the automatic SCM browser inference.
     *
     * <p>
     * Implementing this method allows Hudson to reuse {@link RepositoryBrowser}
     * configured for one project to be used for other "compatible" projects.
     * 
     * @return
     *      true if the two given SCM configurations are similar enough
     *      that they can reuse {@link RepositoryBrowser} between them.
     */
    public boolean isBrowserReusable(T x, T y) {
        return false;
    }

    /**
     * Returns the list of {@link RepositoryBrowser} {@link Descriptor}
     * that can be used with this SCM.
     *
     * @return
     *      can be empty but never null.
     */
    public List<Descriptor<RepositoryBrowser<?>>> getBrowserDescriptors() {
        if(repositoryBrowser==null)     return Collections.emptyList();
        return RepositoryBrowsers.filter(repositoryBrowser);
    }
}
