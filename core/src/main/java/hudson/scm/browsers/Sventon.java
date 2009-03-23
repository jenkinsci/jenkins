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

import hudson.model.Descriptor;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SubversionChangeLogSet.LogEntry;
import hudson.scm.SubversionChangeLogSet.Path;
import hudson.scm.SubversionRepositoryBrowser;
import hudson.scm.EditType;
import hudson.util.FormValidation;
import hudson.util.FormValidation.URLCheck;
import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.AncestorInPath;

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
        public FormValidation doCheckUrl(@AncestorInPath AbstractProject project, @QueryParameter(fixEmpty=true) final String value) throws IOException, ServletException {
            if(!project.hasPermission(Item.CONFIGURE))  return FormValidation.ok(); // can't check
            if(value==null) // nothing entered yet
                return FormValidation.ok();

            return new URLCheck() {
                protected FormValidation check() throws IOException, ServletException {
                    String v = value;
                    if(!v.endsWith("/")) v+='/';

                    try {
                        if(findText(open(new URL(v)),"sventon")) {
                            return FormValidation.ok();
                        } else {
                            return FormValidation.error("This is a valid URL but it doesn't look like Sventon");
                        }
                    } catch (IOException e) {
                        return handleIOException(v,e);
                    }
                }
            }.check();
        }
    }

    private static final long serialVersionUID = 1L;
}
