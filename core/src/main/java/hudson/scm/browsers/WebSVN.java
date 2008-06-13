package hudson.scm.browsers;

import hudson.model.Descriptor;

import hudson.scm.EditType;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SubversionChangeLogSet;

import hudson.scm.SubversionChangeLogSet.Path;

import hudson.scm.SubversionRepositoryBrowser;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

import java.net.MalformedURLException;
import java.net.URL;


/**
 * {@link RepositoryBrowser} for Subversion.  Assumes that WebSVN is
 * configured with Multiviews enabled.
 *
 * @author jasonchaffee at dev.java.net
 * @since 1.139
 */
public class WebSVN extends SubversionRepositoryBrowser {

    public static final Descriptor<RepositoryBrowser<?>> DESCRIPTOR =
        new Descriptor<RepositoryBrowser<?>>(WebSVN.class) {
            public String getDisplayName() {
                return "WebSVN";
            }
        };


    private static final long serialVersionUID = 1L;

    /**
     * The URL of the top of the site.
     *
     * <p>Normalized to ends with '/', like <tt>http://svn.apache.org/wsvn/</tt>
     * It may contain a query parameter like <tt>?root=foobar</tt>, so relative
     * URL construction needs to be done with care.</p>
     */
    public final URL url;

    /**
     * Creates a new WebSVN object.
     *
     * @param                url  DOCUMENT ME!
     *
     * @throws               MalformedURLException  DOCUMENT ME!
     */
    @DataBoundConstructor
    public WebSVN(URL url) throws MalformedURLException {
        this.url = normalizeToEndWithSlash(url);
    }

    /**
     * Returns the diff link value.
     *
     * @param   path  the given path value.
     *
     * @return  the diff link value.
     *
     * @throws  IOException  DOCUMENT ME!
     */
    @Override public URL getDiffLink(Path path) throws IOException {
        if (path.getEditType() != EditType.EDIT) {
            return null; // no diff if this is not an edit change
        }

        int r = path.getLogEntry().getRevision();

        return new URL(url,
                       trimHeadSlash(path.getValue()) +
                       param().add("op=diff").add("rev=" + r));
    }

    /**
     * Returns the file link value.
     *
     * @param   path  the given path value.
     *
     * @return  the file link value.
     *
     * @throws  IOException  DOCUMENT ME!
     */
    @Override public URL getFileLink(Path path) throws IOException {
        return new URL(url, trimHeadSlash(path.getValue()) + param());
    }

    /**
     * Returns the change set link value.
     *
     * @param   changeSet  the given changeSet value.
     *
     * @return  the change set link value.
     *
     * @throws  IOException  DOCUMENT ME!
     */
    @Override public URL getChangeSetLink(SubversionChangeLogSet.LogEntry changeSet)
                                   throws IOException {
        return new URL(url,
                       "." +
                       param().add("rev=" + changeSet.getRevision()).add("sc=1"));
    }

    private QueryBuilder param() {
        return new QueryBuilder(url.getQuery());
    }

    /**
     * Returns the descriptor value.
     *
     * @return  the descriptor value.
     */
    public Descriptor<RepositoryBrowser<?>> getDescriptor() {
        return DESCRIPTOR;
    }
}
