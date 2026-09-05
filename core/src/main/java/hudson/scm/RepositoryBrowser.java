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

package hudson.scm;

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Connects Hudson to repository browsers like ViewCVS or FishEye,
 * so that Hudson can generate links to them.
 *
 * <p>
 * {@link RepositoryBrowser} instance is normally created as
 * a result of job configuration, and  stores immutable
 * configuration information (such as the URL of the FishEye site).
 *
 * <p>
 * {@link RepositoryBrowser} is persisted with {@link SCM}.
 *
 * <p>
 * To have Hudson recognize {@link RepositoryBrowser}, put {@link Extension} on your {@link Descriptor}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.89
 * @see RepositoryBrowsers
 */
@ExportedBean
public abstract class RepositoryBrowser<E extends ChangeLogSet.Entry> implements Describable<RepositoryBrowser<?>>, ExtensionPoint, Serializable {
    /**
     * Determines the link to the given change set.
     *
     * @return
     *      null if this repository browser doesn't have any meaningful
     *      URL for a change set (for example, ViewCVS doesn't have
     *      any page for a change set, whereas FishEye does.)
     */
    public abstract URL getChangeSetLink(E changeSet) throws IOException;

    /**
     * If the given string starts with '/', return a string that removes it.
     */
    protected static String trimHeadSlash(String s) {
        if (s.startsWith("/"))   return s.substring(1);
        return s;
    }

    /**
     * Normalize the URL so that it ends with '/'.
     * <p>
     * An attention is paid to preserve the query parameters in URL if any.
     */
    protected static URL normalizeToEndWithSlash(URL url) {
        if (url.getPath().endsWith("/"))
            return url;

        // normalize
        String q = url.getQuery();
        q = q != null ? '?' + q : "";
        try {
            return new URL(url, url.getPath() + '/' + q);
        } catch (MalformedURLException e) {
            // impossible
            throw new Error(e);
        }
    }

    /**
     * Returns all the registered {@link RepositoryBrowser} descriptors.
     */
    public static DescriptorExtensionList<RepositoryBrowser<?>, Descriptor<RepositoryBrowser<?>>> all() {
        return (DescriptorExtensionList) Jenkins.get().getDescriptorList(RepositoryBrowser.class);
    }

    private static final long serialVersionUID = 1L;
}
