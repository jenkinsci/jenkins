/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.scm.browsers;

import static hudson.Util.fixEmpty;
import hudson.model.Descriptor;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SubversionChangeLogSet.LogEntry;
import hudson.scm.SubversionChangeLogSet.Path;
import hudson.scm.SubversionRepositoryBrowser;
import hudson.scm.EditType;
import hudson.util.FormFieldValidator;
import hudson.Extension;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

/**
 * {@link RepositoryBrowser} for Sventon.
 *
 * @author Stephen Connolly
 */
public class Sventon extends SubversionRepositoryBrowser {
    /**
     * The URL of the Sventon repository.
     *
     * This is normally like <tt>http://somehost.com/svn/</tt>
     * Normalized to have '/' at the tail.
     */
    public final URL url;

    /**
     * Repository instance. Cannot be empty
     */
    private final String repositoryInstance;

    @DataBoundConstructor
    public Sventon(URL url, String repositoryInstance) throws MalformedURLException {
        this.url = normalizeToEndWithSlash(url);

        // normalize
        repositoryInstance = repositoryInstance.trim();

        this.repositoryInstance = repositoryInstance;
    }

    public String getRepositoryInstance() {
        if(repositoryInstance==null)
            return "";  // compatibility
        return repositoryInstance;
    }

    @Override
    public URL getDiffLink(Path path) throws IOException {
        if(path.getEditType()!= EditType.EDIT)
            return null;    // no diff if this is not an edit change
        int r = path.getLogEntry().getRevision();
        return new URL(url, String.format("diffprev.svn?name=%s&commitrev=%d&committedRevision=%d&revision=%d&path=%s",
                repositoryInstance,r,r,r,URLEncoder.encode(getPath(path))));
    }

    @Override
    public URL getFileLink(Path path) throws IOException {
        if (path.getEditType() == EditType.DELETE)
           return null; // no file if it's gone
        int r = path.getLogEntry().getRevision();
        return new URL(url, String.format("goto.svn?name=%s&revision=%d&path=%s",
                repositoryInstance,r,URLEncoder.encode(getPath(path))));
    }

    /**
     * Trims off the root module portion to compute the path within FishEye.
     */
    private String getPath(Path path) {
        String s = trimHeadSlash(path.getValue());
        if(s.startsWith(repositoryInstance)) // this should be always true, but be defensive
            s = trimHeadSlash(s.substring(repositoryInstance.length()));
        return s;
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
        return new URL(url, String.format("revinfo.svn?name=%s&revision=%d",
                repositoryInstance,changeSet.getRevision()));
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "Sventon";
        }

        /**
         * Performs on-the-fly validation of the URL.
         */
        public void doCheck(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            // URLCheck requires Admin permission
            new FormFieldValidator.URLCheck(req,rsp) {
                protected void check() throws IOException, ServletException {
                    String value = fixEmpty(request.getParameter("value"));
                    if(value==null) {// nothing entered yet
                        ok();
                        return;
                    }

                    if(!value.endsWith("/")) value+='/';

                    try {
                        if(findText(open(new URL(value)),"sventon")) {
                            ok();
                        } else {
                            error("This is a valid URL but it doesn't look like Sventon");
                        }
                    } catch (IOException e) {
                        handleIOException(value,e);
                    }
                }
            }.process();
        }
    }

    private static final long serialVersionUID = 1L;
}
