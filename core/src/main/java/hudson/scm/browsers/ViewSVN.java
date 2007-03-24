package hudson.scm.browsers;

import hudson.scm.SubversionRepositoryBrowser;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SubversionChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.scm.SubversionChangeLogSet.Path;
import hudson.model.Descriptor;

import java.net.URL;
import java.io.IOException;
import java.text.MessageFormat;

import org.kohsuke.stapler.StaplerRequest;
import static hudson.scm.browsers.ViewCVS.trimHeadSlash;

/**
 * {@link RepositoryBrowser} for Subversion.
 * 
 * @author Kohsuke Kawaguchi
 * @since 1.90
 */
public class ViewSVN extends SubversionRepositoryBrowser {
    /**
     * The URL of the top of the site.
     *
     * Normalized to ends with '/', like <tt>http://svn.apache.org/viewvc/</tt>
     */
    public final URL url;

    /**
     * @stapler-constructor
     */
    public ViewSVN(URL url) {
        this.url = url;
    }

    @Override
    public URL getDiffLink(Path path) throws IOException {
        int r = path.getLogEntry().getRevision();
        return new URL(getFileLink(path),
            MessageFormat.format("?r1={0}&r2={1}",r-1,r));
    }

    @Override
    public URL getFileLink(Path path) throws IOException {
        return new URL(url,trimHeadSlash(path.getValue()));
    }

    @Override
    public URL getChangeSetLink(Entry changeSet) throws IOException {
        return new URL(url,"?view=rev&revision="+
            ((SubversionChangeLogSet.LogEntry)changeSet).getRevision());
    }

    public Descriptor<RepositoryBrowser> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final Descriptor<RepositoryBrowser> DESCRIPTOR = new Descriptor<RepositoryBrowser>(ViewSVN.class) {
        public String getDisplayName() {
            return "ViewSVN";
        }

        public ViewSVN newInstance(StaplerRequest req) throws FormException {
            return req.bindParameters(ViewSVN.class,"viewsvn.");
        }
    };
}
