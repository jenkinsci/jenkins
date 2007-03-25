package hudson.scm.browsers;

import hudson.model.Descriptor;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SubversionChangeLogSet.LogEntry;
import hudson.scm.SubversionChangeLogSet.Path;
import hudson.scm.SubversionRepositoryBrowser;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;

/**
 * {@link RepositoryBrowser} for FishEye SVN.
 *
 * @author Kohsuke Kawaguchi
 */
public class FishEyeSVN extends SubversionRepositoryBrowser {
    /**
     * The URL of the FishEye repository.
     *
     * This is normally like <tt>http://fisheye5.cenqua.com/browse/glassfish/</tt>
     * Normalized to have '/' at the tail.
     */
    public final URL url;

    /**
     * @stapler-constructor
     */
    public FishEyeSVN(URL url) throws MalformedURLException {
        if(!url.toExternalForm().endsWith("/"))
            url = new URL(url.toExternalForm()+"/");
        this.url = url;
    }

    @Override
    public URL getDiffLink(Path path) throws IOException {
        int r = path.getLogEntry().getRevision();
        return new URL(url,trimHeadSlash(path.getValue())+
            MessageFormat.format("?r1={0}&r2={1}",r-1,r));
    }

    @Override
    public URL getFileLink(Path path) throws IOException {
        return new URL(url,trimHeadSlash(path.getValue()));
    }

    /**
     * Pick up "FOOBAR" from "http://site/browse/FOOBAR/"
     */
    private String getProjectName() {
        String p = url.getPath();
        if(p.endsWith("/")) p = p.substring(0,p.length()-1);

        int idx = p.lastIndexOf('/');
        return p.substring(idx+1);
    }

    @Override
    public URL getChangeSetLink(LogEntry changeSet) throws IOException {
        return new URL(url,"../../changelog/"+getProjectName()+"/?cs="+changeSet.getRevision());
    }

    public Descriptor<RepositoryBrowser<?>> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final Descriptor<RepositoryBrowser<?>> DESCRIPTOR = new Descriptor<RepositoryBrowser<?>>(FishEyeSVN.class) {
        public String getDisplayName() {
            return "FishEye";
        }

        public FishEyeSVN newInstance(StaplerRequest req) throws FormException {
            return req.bindParameters(FishEyeSVN.class,"fisheye.svn.");
        }
    };
}
