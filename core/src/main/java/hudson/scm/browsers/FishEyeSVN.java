/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.scm.EditType;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SubversionChangeLogSet.LogEntry;
import hudson.scm.SubversionChangeLogSet.Path;
import hudson.scm.SubversionRepositoryBrowser;
import hudson.util.FormValidation;
import hudson.util.FormValidation.URLCheck;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

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
     * Root SVN module name (like 'foo/bar' &mdash; normalized to
     * have no leading nor trailing slash.) Can be empty.
     */
    private final String rootModule;

    @DataBoundConstructor
    public FishEyeSVN(URL url, String rootModule) throws MalformedURLException {
        this.url = normalizeToEndWithSlash(url);

        // normalize
        rootModule = rootModule.trim();
        if(rootModule.startsWith("/"))
            rootModule = rootModule.substring(1);
        if(rootModule.endsWith("/"))
            rootModule = rootModule.substring(0,rootModule.length()-1);

        this.rootModule = rootModule;
    }

    public String getRootModule() {
        if(rootModule==null)
            return "";  // compatibility
        return rootModule;
    }

    @Override
    public URL getDiffLink(Path path) throws IOException {
        if(path.getEditType()!= EditType.EDIT)
            return null;    // no diff if this is not an edit change
        int r = path.getLogEntry().getRevision();
        return new URL(url, getPath(path)+String.format("?r1=%d&r2=%d",r-1,r));
    }

    @Override
    public URL getFileLink(Path path) throws IOException {
        return new URL(url, getPath(path));
    }

    /**
     * Trims off the root module portion to compute the path within FishEye.
     */
    private String getPath(Path path) {
        String s = trimHeadSlash(path.getValue());
        if(s.startsWith(rootModule)) // this should be always true, but be defensive
            s = trimHeadSlash(s.substring(rootModule.length()));
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
        return new URL(url,"../../changelog/"+getProjectName()+"/?cs="+changeSet.getRevision());
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "FishEye";
        }

        /**
         * Performs on-the-fly validation of the URL.
         */
        public FormValidation doCheck(@QueryParameter(fixEmpty=true) String value) throws IOException, ServletException {
            if(value==null) // nothing entered yet
                return FormValidation.ok();

            if(!value.endsWith("/")) value+='/';
            if(!URL_PATTERN.matcher(value).matches())
                return FormValidation.errorWithMarkup("The URL should end like <tt>.../browse/foobar/</tt>");

            // Connect to URL and check content only if we have admin permission
            if (!Hudson.getInstance().hasPermission(Hudson.ADMINISTER))
                return FormValidation.ok();

            final String finalValue = value;
            return new URLCheck() {
                @Override
                protected FormValidation check() throws IOException, ServletException {
                    try {
                        if(findText(open(new URL(finalValue)),"FishEye")) {
                            return FormValidation.ok();
                        } else {
                            return FormValidation.error("This is a valid URL but it doesn't look like FishEye");
                        }
                    } catch (IOException e) {
                        return handleIOException(finalValue,e);
                    }
                }
            }.check();
        }

        private static final Pattern URL_PATTERN = Pattern.compile(".+/browse/[^/]+/");
    }

    private static final long serialVersionUID = 1L;
}
