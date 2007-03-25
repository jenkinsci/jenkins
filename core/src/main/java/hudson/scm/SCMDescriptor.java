package hudson.scm;

import hudson.model.Descriptor;

import java.util.List;
import java.util.Collections;

/**
 * {@link Descriptor} for {@link SCM}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class SCMDescriptor extends Descriptor<SCM> {
    /**
     * If this SCM has corresponding {@link RepositoryBrowser},
     * that type. Otherwise this SCM will not have any repository browser.
     */
    public final Class<? extends RepositoryBrowser> repositoryBrowser;

    protected SCMDescriptor(Class<? extends SCM> clazz, Class<? extends RepositoryBrowser> repositoryBrowser) {
        super(clazz);
        this.repositoryBrowser = repositoryBrowser;
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
