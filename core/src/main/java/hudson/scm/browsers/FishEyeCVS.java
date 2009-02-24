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

import hudson.Util;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Hudson;
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

    @Extension
    public static class DescriptorImpl extends Descriptor<RepositoryBrowser<?>> {
        @Override
        public String getDisplayName() {
            return "FishEye";
        }

        public void doCheck(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            // false==No permission needed for basic check
            new FormFieldValidator(req,rsp,false) {
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
                    // Connect to URL and check content only if we have admin permission
                    if (Hudson.getInstance().hasPermission(Hudson.ADMINISTER)) {
                        final String finalValue = value;
                        new FormFieldValidator.URLCheck(request,response) {
                            @Override
                            protected void check() throws IOException, ServletException {
                                try {
                                    if (findText(open(new URL(finalValue)), "FishEye")) {
                                        ok();
                                    } else {
                                        error("This is a valid URL but it doesn't look like FishEye");
                                    }
                                } catch (IOException e) {
                                    handleIOException(finalValue, e);
                                }
                            }
                        }.process();
                    } else {
                        ok();
                    }
                }
            }.process();
        }

        private static final Pattern URL_PATTERN = Pattern.compile(".+/browse/[^/]+/");

    }

    private static final long serialVersionUID = 1L;

}
