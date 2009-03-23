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
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.scm.CVSChangeLogSet;
import hudson.scm.CVSChangeLogSet.File;
import hudson.scm.CVSChangeLogSet.Revision;
import hudson.scm.CVSRepositoryBrowser;
import hudson.scm.RepositoryBrowser;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.URL;
import java.util.regex.Pattern;

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

    @Extension
    public static class DescriptorImpl extends Descriptor<RepositoryBrowser<?>> {
        @Override
        public String getDisplayName() {
            return "FishEye";
        }

        public FormValidation doCheck(@QueryParameter String value) throws IOException, ServletException {
            value = Util.fixEmpty(value);
            if (value == null) return FormValidation.ok();
            if (!value.endsWith("/"))   value += '/';
            if (!URL_PATTERN.matcher(value).matches())
                return FormValidation.errorWithMarkup("The URL should end like <tt>.../browse/foobar/</tt>");

            // Connect to URL and check content only if we have admin permission
            if (!Hudson.getInstance().hasPermission(Hudson.ADMINISTER))
                return FormValidation.ok();
            
            final String finalValue = value;
            return new FormValidation.URLCheck() {
                @Override
                protected FormValidation check() throws IOException, ServletException {
                    try {
                        if (findText(open(new URL(finalValue)), "FishEye")) {
                            return FormValidation.ok();
                        } else {
                            return FormValidation.error("This is a valid URL but it doesn't look like FishEye");
                        }
                    } catch (IOException e) {
                        return handleIOException(finalValue, e);
                    }
                }
            }.check();
        }

        private static final Pattern URL_PATTERN = Pattern.compile(".+/browse/[^/]+/");

    }

    private static final long serialVersionUID = 1L;

}
