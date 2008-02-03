package hudson.scm.browsers;

import hudson.model.Descriptor;
import hudson.scm.EditType;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SubversionChangeLogSet;
import hudson.scm.SubversionRepositoryBrowser;
import java.io.IOException;
import java.net.URL;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * {@link RepositoryBrowser} implementation for CollabNet hosted Subversion repositories.
 * This enables Hudson to integrate with the repository browsers built-in to CollabNet-powered
 * sites such as Java.net and Tigris.org.
 * @author Daniel Dyer
 */
public class CollabNetSVN extends SubversionRepositoryBrowser
{
    public static final Descriptor<RepositoryBrowser<?>> DESCRIPTOR
        = new Descriptor<RepositoryBrowser<?>>(CollabNetSVN.class) {
        public String getDisplayName() {
            return "CollabNet";
        }


        @Override
        public RepositoryBrowser<?> newInstance(StaplerRequest req,
                                                JSONObject formData) throws FormException {
            return req.bindParameters(CollabNetSVN.class, "collabnet.svn.");
        }
    };


    public final URL url;


    /**
     * @param url The repository browser URL for the root of the project.
     * For example, a Java.net project called "myproject" would use
     * https://myproject.dev.java.net/source/browse/myproject
     */
    @DataBoundConstructor
    public CollabNetSVN(URL url) {
        this.url = normalizeToEndWithSlash(url);
    }


    /**
     * {@inheritDoc}
     */
    public URL getDiffLink(SubversionChangeLogSet.Path path) throws IOException {
        if (path.getEditType() != EditType.EDIT) {
            // No diff if the file is being added or deleted.
            return null;
        }

        int revision = path.getLogEntry().getRevision();
        QueryBuilder query = new QueryBuilder(null);
        query.add("r1=" + (revision - 1));
        query.add("r2=" + revision);
        return new URL(url, trimHeadSlash(path.getValue()) + query);
    }


    /**
     * {@inheritDoc}
     */    
    public URL getFileLink(SubversionChangeLogSet.Path path) throws IOException {
        int revision = path.getLogEntry().getRevision();
        QueryBuilder query = new QueryBuilder(null);
        query.add("rev=" + revision);
        query.add("view=log");
        return new URL(url, trimHeadSlash(path.getValue()) + query);
    }


    /**
     * {@inheritDoc}
     */    
    public URL getChangeSetLink(SubversionChangeLogSet.LogEntry changeSet) throws IOException {
        int revision = changeSet.getRevision();
        QueryBuilder query = new QueryBuilder(null);
        query.add("rev=" + revision);
        query.add("view=rev");
        return new URL(url, query.toString());
    }


    /**
     * {@inheritDoc}
     */    
    public Descriptor<RepositoryBrowser<?>> getDescriptor() {
        return DESCRIPTOR;
    }
}
