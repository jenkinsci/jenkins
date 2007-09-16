package hudson.scm.browsers;

import hudson.Util;
import hudson.model.Descriptor;
import hudson.scm.CVSChangeLogSet;
import hudson.scm.CVSChangeLogSet.File;
import hudson.scm.CVSChangeLogSet.Revision;
import hudson.scm.CVSRepositoryBrowser;
import hudson.scm.RepositoryBrowser;
import hudson.util.FormFieldValidator;
import java.io.IOException;
import java.net.URL;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Repository browser for CVS in a FishEye server.
 */
public final class FishEyeCVS extends CVSRepositoryBrowser {

    /**
     * The URL of the FishEye repository, e.g.
     * <tt>http://deadlock.netbeans.org/fisheye/browse/netbeans/</tt>
     */
    public final URL url;

    @DataBoundConstructor
    public FishEyeCVS(URL url) {
        this.url = normalizeToEndWithSlash(url);
    }

    @Override
    public URL getDiffLink(File file) throws IOException {
        Revision r = new Revision(file.getRevision());
        Revision p = r.getPrevious();
        if (p == null) {
            return null;
        }
        return new URL(url, trimHeadSlash(file.getFullName()) + new QueryBuilder(url.getQuery()).add("r1=" + p).add("r2=" + r));
    }

    @Override
    public URL getFileLink(File file) throws IOException {
        return new URL(url, trimHeadSlash(file.getFullName()) + new QueryBuilder(url.getQuery()));
    }

    @Override
    public URL getChangeSetLink(CVSChangeLogSet.CVSChangeLog changeSet) throws IOException {
        return null;
    }

    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static class DescriptorImpl extends Descriptor<RepositoryBrowser<?>> {

        public DescriptorImpl() {
            super(FishEyeCVS.class);
        }

        @Override
        public String getDisplayName() {
            return "FishEye";
        }

        public void doCheck(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            new FormFieldValidator.URLCheck(req,rsp) {
                @Override
                protected void check() throws IOException, ServletException {
                    String value = Util.fixEmpty(request.getParameter("value"));
                    if (value == null) {
                        ok();
                        return;
                    }
                    if (!value.endsWith("/")) {
                        value += '/';
                    }
                    if (!URL_PATTERN.matcher(value).matches()) {
                        errorWithMarkup("The URL should end like <tt>.../browse/foobar/</tt>");
                        return;
                    }
                    try {
                        if (findText(open(new URL(value)), "FishEye")) {
                            ok();
                        } else {
                            error("This is a valid URL but it doesn't look like FishEye");
                        }
                    } catch (IOException e) {
                        handleIOException(value, e);
                    }
                }
            }.process();
        }

        @Override
        public FishEyeCVS newInstance(StaplerRequest req) throws FormException {
            return req.bindParameters(FishEyeCVS.class, "fisheye.cvs.");
        }

        private static final Pattern URL_PATTERN = Pattern.compile(".+/browse/[^/]+/");

    }

    private static final long serialVersionUID = 1L;

}
