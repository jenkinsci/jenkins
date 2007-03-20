package hudson.scm;

import hudson.ExtensionPoint;
import hudson.model.Describable;

import java.io.IOException;
import java.net.URL;

/**
 * Connects Hudson to repository browsers like ViewCVS or FishEye,
 * so that Hudson can generate links to them. 
 *
 * <p>
 * {@link RepositoryBrowser} instance is normally created as
 * a result of job configuration, and  stores immutable
 * configuration information (such as the URL of the FishEye site).
 *
 * <p>
 * {@link RepositoryBrowser} is persisted with {@link SCM}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.89
 * @see RepositoryBrowsers
 */
public abstract class RepositoryBrowser implements ExtensionPoint, Describable<RepositoryBrowser> {
    /**
     * Determines the link to the given change set.
     *
     * @return
     *      null if this repository browser doesn't have any meaningful
     *      URL for a change set (for example, ViewCVS doesn't have
     *      any page for a change set, whereas FishEye does.)
     */
    public abstract URL getChangeSetLink(ChangeLogSet.Entry changeSet) throws IOException;
}
