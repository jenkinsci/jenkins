package hudson.scm;

import java.io.IOException;
import java.net.URL;

/**
 * {@link RepositoryBrowser} for Subversion.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class SubversionRepositoryBrowser extends RepositoryBrowser<SubversionChangeLogSet.LogEntry> {
    /**
     * Determines the link to the diff between the version
     * in the specified revision of {@link SubversionChangeLogSet.Path} to its previous version.
     *
     * @return
     *      null if the browser doesn't have any URL for diff.
     */
    public abstract URL getDiffLink(SubversionChangeLogSet.Path path) throws IOException;

    /**
     * Determines the link to a single file under Subversion.
     * This page should display all the past revisions of this file, etc.
     *
     * @return
     *      null if the browser doesn't have any suitable URL.
     */
    public abstract URL getFileLink(SubversionChangeLogSet.Path path) throws IOException;

    private static final long serialVersionUID = 1L;
}
