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
 * {@link RepositoryBrowser} for Subversion.
 * 
 * @author Kohsuke Kawaguchi
 * @since 1.90
 */
// See http://viewvc.tigris.org/source/browse/*checkout*/viewvc/trunk/docs/url-reference.html
public class ViewSVN extends SubversionRepositoryBrowser {
    /**
     * The URL of the top of the site.
     *
     * Normalized to ends with '/', like <tt>http://svn.apache.org/viewvc/</tt>
     * It may contain a query parameter like <tt>?root=foobar</tt>, so relative URL
     * construction needs to be done with care.
     */
    public final URL url;

    @DataBoundConstructor
    public ViewSVN(URL url) throws MalformedURLException {
        this.url = normalizeToEndWithSlash(url);
    }

    @Override
    public URL getDiffLink(Path path) throws IOException {
        if(path.getEditType()!= EditType.EDIT)
            return null;    // no diff if this is not an edit change
        int r = path.getLogEntry().getRevision();
        return new URL(url,trimHeadSlash(path.getValue())+param().add("r1="+(r-1)).add("r2="+r));
    }

    @Override
    public URL getFileLink(Path path) throws IOException {
        return new URL(url,trimHeadSlash(path.getValue())+param());
    }

    @Override
    public URL getChangeSetLink(SubversionChangeLogSet.LogEntry changeSet) throws IOException {
        return new URL(url,"."+param().add("view=rev").add("rev="+changeSet.getRevision()));
    }

    private QueryBuilder param() {
        return new QueryBuilder(url.getQuery());
    }

    public DescriptorImpl getDescriptor() {
        return DescriptorImpl.INSTANCE;
    }

    private static final long serialVersionUID = 1L;

    public static final class DescriptorImpl extends Descriptor<RepositoryBrowser<?>> {
        public static final DescriptorImpl INSTANCE = new DescriptorImpl();

        public String getDisplayName() {
            return "ViewSVN";
        }
    }
}
