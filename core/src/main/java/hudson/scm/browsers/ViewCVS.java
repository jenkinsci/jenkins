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

import hudson.model.Descriptor;
import hudson.scm.CVSChangeLogSet.CVSChangeLog;
import hudson.scm.CVSChangeLogSet.File;
import hudson.scm.CVSChangeLogSet.Revision;
import hudson.scm.CVSRepositoryBrowser;
import hudson.scm.RepositoryBrowser;
import hudson.Extension;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * {@link RepositoryBrowser} for CVS.
 * @author Kohsuke Kawaguchi
 */
// See http://viewvc.tigris.org/source/browse/*checkout*/viewvc/trunk/docs/url-reference.html
public final class ViewCVS extends CVSRepositoryBrowser {
    /**
     * The URL of the top of the site.
     *
     * Normalized to ends with '/', like <tt>http://isscvs.cern.ch/cgi-bin/viewcvs-all.cgi/</tt>
     * It may contain a query parameter like <tt>?cvsroot=foobar</tt>, so relative URL
     * construction needs to be done with care.
     */
    public final URL url;

    @DataBoundConstructor
    public ViewCVS(URL url) throws MalformedURLException {
        this.url = normalizeToEndWithSlash(url);
    }

    public URL getFileLink(File file) throws IOException {
        return new URL(url,trimHeadSlash(file.getFullName())+param());
    }

    public URL getDiffLink(File file) throws IOException {
        Revision r = new Revision(file.getRevision());
        Revision p = r.getPrevious();
        if(p==null) return null;

        return new URL(getFileLink(file), file.getSimpleName()+".diff"+param().add("r1="+p).add("r2="+r));
    }

    /**
     * No changeset support in ViewCVS.
     */
    public URL getChangeSetLink(CVSChangeLog changeSet) throws IOException {
        return null;
    }

    private QueryBuilder param() {
        return new QueryBuilder(url.getQuery());
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "ViewCVS";
        }
    }

    private static final long serialVersionUID = 1L;
}
