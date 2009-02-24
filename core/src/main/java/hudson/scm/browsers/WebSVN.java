/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Daniel Dyer
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
 * {@link RepositoryBrowser} for Subversion.  Assumes that WebSVN is
 * configured with Multiviews enabled.
 *
 * @author jasonchaffee at dev.java.net
 * @since 1.139
 */
public class WebSVN extends SubversionRepositoryBrowser {

    @Extension
    public static class DescriptorImpl extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "WebSVN";
        }
    }


    private static final long serialVersionUID = 1L;

    /**
     * The URL of the top of the site.
     *
     * <p>Normalized to ends with '/', like <tt>http://svn.apache.org/wsvn/</tt>
     * It may contain a query parameter like <tt>?root=foobar</tt>, so relative
     * URL construction needs to be done with care.</p>
     */
    public final URL url;

    /**
     * Creates a new WebSVN object.
     *
     * @param                url  DOCUMENT ME!
     *
     * @throws               MalformedURLException  DOCUMENT ME!
     */
    @DataBoundConstructor
    public WebSVN(URL url) throws MalformedURLException {
        this.url = normalizeToEndWithSlash(url);
    }

    /**
     * Returns the diff link value.
     *
     * @param   path  the given path value.
     *
     * @return  the diff link value.
     *
     * @throws  IOException  DOCUMENT ME!
     */
    @Override public URL getDiffLink(Path path) throws IOException {
        if (path.getEditType() != EditType.EDIT) {
            return null; // no diff if this is not an edit change
        }

        int r = path.getLogEntry().getRevision();

        return new URL(url,
                       trimHeadSlash(path.getValue()) +
                       param().add("op=diff").add("rev=" + r));
    }

    /**
     * Returns the file link value.
     *
     * @param   path  the given path value.
     *
     * @return  the file link value.
     *
     * @throws  IOException  DOCUMENT ME!
     */
    @Override public URL getFileLink(Path path) throws IOException {
        return new URL(url, trimHeadSlash(path.getValue()) + param());
    }

    /**
     * Returns the change set link value.
     *
     * @param   changeSet  the given changeSet value.
     *
     * @return  the change set link value.
     *
     * @throws  IOException  DOCUMENT ME!
     */
    @Override public URL getChangeSetLink(SubversionChangeLogSet.LogEntry changeSet)
                                   throws IOException {
        return new URL(url,
                       "." +
                       param().add("rev=" + changeSet.getRevision()).add("sc=1"));
    }

    private QueryBuilder param() {
        return new QueryBuilder(url.getQuery());
    }
}
