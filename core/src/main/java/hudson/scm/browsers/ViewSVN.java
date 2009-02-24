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
import hudson.scm.EditType;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SubversionChangeLogSet;
import hudson.scm.SubversionChangeLogSet.Path;
import hudson.scm.SubversionRepositoryBrowser;
import hudson.Extension;
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

    private static final long serialVersionUID = 1L;

    @Extension
    public static final class DescriptorImpl extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "ViewSVN";
        }
    }
}
