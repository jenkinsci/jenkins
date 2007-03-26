package hudson.scm;

import hudson.scm.CVSChangeLogSet.CVSChangeLog;

import java.io.IOException;
import java.net.URL;

/**
 * {@link RepositoryBrowser} for CVS.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class CVSRepositoryBrowser extends RepositoryBrowser<CVSChangeLog> {
    /**
     * Determines the link to the diff between the version
     * in the {@link CVSChangeLogSet.File} to its previous version.
     *
     * @return
     *      null if the browser doesn't have any URL for diff.
     */
    public abstract URL getDiffLink(CVSChangeLogSet.File file) throws IOException;

    /**
     * Determines the link to a single file under CVS.
     * This page should display all the past revisions of this file, etc. 
     *
     * @return
     *      null if the browser doesn't have any suitable URL.
     */
    public abstract URL getFileLink(CVSChangeLogSet.File file) throws IOException;

    private static final long serialVersionUID = 1L;
}
