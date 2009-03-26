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

import hudson.model.Descriptor;
import hudson.model.Describable;
import hudson.model.AbstractProject;

import java.util.List;
import java.util.Collections;

/**
 * {@link Descriptor} for {@link SCM}.
 *
 * @param <T>
 *      The 'self' type that represents the type of {@link SCM} that
 *      this descriptor describes.
 * @author Kohsuke Kawaguchi
 */
public abstract class SCMDescriptor<T extends SCM> extends Descriptor<SCM> {
    /**
     * If this SCM has corresponding {@link RepositoryBrowser},
     * that type. Otherwise this SCM will not have any repository browser.
     */
    public final Class<? extends RepositoryBrowser> repositoryBrowser;

    /**
     * Incremented every time a new {@link SCM} instance is created from this descriptor. 
     * This is used to invalidate cache. Due to the lack of synchronization and serialization,
     * this field doesn't really count the # of instances created to date,
     * but it's good enough for the cache invalidation.
     */
    public volatile int generation = 1;

    protected SCMDescriptor(Class<T> clazz, Class<? extends RepositoryBrowser> repositoryBrowser) {
        super(clazz);
        this.repositoryBrowser = repositoryBrowser;
    }

    /**
     * Infers the type of the corresponding {@link SCM} from the outer class.
     * This version works when you follow the common convention, where a descriptor
     * is written as the static nested class of the describable class.
     *
     * @since 1.278
     */
    protected SCMDescriptor(Class<? extends RepositoryBrowser> repositoryBrowser) {
        this.repositoryBrowser = repositoryBrowser;
    }

    /**
     * Optional method used by the automatic SCM browser inference.
     *
     * <p>
     * Implementing this method allows Hudson to reuse {@link RepositoryBrowser}
     * configured for one project to be used for other "compatible" projects.
     * 
     * @return
     *      true if the two given SCM configurations are similar enough
     *      that they can reuse {@link RepositoryBrowser} between them.
     */
    public boolean isBrowserReusable(T x, T y) {
        return false;
    }

    /**
     * Allows {@link SCMDescriptor}s to choose which projects it wants to be configurable against.
     *
     * <p>
     * When this method returns false, this {@link SCM} will not appear in the configuration screen
     * for the given project. The default method always return true.
     *
     * @since 1.294
     */
    public boolean isApplicable(AbstractProject project) {
        return true;
    }

    /**
     * Returns the list of {@link RepositoryBrowser} {@link Descriptor}
     * that can be used with this SCM.
     *
     * @return
     *      can be empty but never null.
     */
    public List<Descriptor<RepositoryBrowser<?>>> getBrowserDescriptors() {
        if(repositoryBrowser==null)     return Collections.emptyList();
        return RepositoryBrowsers.filter(repositoryBrowser);
    }
}
