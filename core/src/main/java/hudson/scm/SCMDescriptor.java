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

import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Job;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import static java.util.logging.Level.WARNING;
import java.util.logging.Logger;

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
    public transient final Class<? extends RepositoryBrowser> repositoryBrowser;

    /**
     * Incremented every time a new {@link SCM} instance is created from this descriptor. 
     * This is used to invalidate cache of {@link SCM#getEffectiveBrowser}. Due to the lack of synchronization and serialization,
     * this field doesn't really count the # of instances created to date,
     * but it's good enough for the cache invalidation.
     * @deprecated No longer used by default.
     */
    @Deprecated
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

    // work around HUDSON-4514. The repositoryBrowser field was marked as non-transient until 1.325,
    // causing the field to be persisted and overwritten on the load method.
    @SuppressWarnings({"ConstantConditions"})
    @Override
    public void load() {
        Class<? extends RepositoryBrowser> rb = repositoryBrowser;
        super.load();
        if (repositoryBrowser!=rb) { // XStream may overwrite even the final field.
            try {
                Field f = SCMDescriptor.class.getDeclaredField("repositoryBrowser");
                f.setAccessible(true);
                f.set(this,rb);
            } catch (NoSuchFieldException e) {
                LOGGER.log(WARNING, "Failed to overwrite the repositoryBrowser field",e);
            } catch (IllegalAccessException e) {
                LOGGER.log(WARNING, "Failed to overwrite the repositoryBrowser field",e);
            }
        }
    }

    /**
     * Optional method used by the automatic SCM browser inference.
     *
     * <p>
     * Implementing this method allows Hudson to reuse {@link RepositoryBrowser}
     * configured for one project to be used for other "compatible" projects.
     * <p>{@link SCM#guessBrowser} is more robust since it does not require another project.
     * @return
     *      true if the two given SCM configurations are similar enough
     *      that they can reuse {@link RepositoryBrowser} between them.
     * @deprecated No longer used by default. {@link SCM#getKey} could be used to implement similar features if needed.
     */
    @Deprecated
    public boolean isBrowserReusable(T x, T y) {
        return false;
    }

    /**
     * Allows {@link SCMDescriptor}s to choose which projects it wants to be configurable against.
     *
     * <p>
     * When this method returns false, this {@link SCM} will not appear in the configuration screen
     * for the given project. The default is true for {@link AbstractProject} but false for {@link Job}.
     *
     * @since 1.568
     */
    public boolean isApplicable(Job project) {
        if (project instanceof AbstractProject) {
            return isApplicable((AbstractProject) project);
        } else {
            return false;
        }
    }

    @Deprecated
    public boolean isApplicable(AbstractProject project) {
        if (Util.isOverridden(SCMDescriptor.class, getClass(), "isApplicable", Job.class)) {
            return isApplicable((Job) project);
        } else {
            return true;
        }
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

    private static final Logger LOGGER = Logger.getLogger(SCMDescriptor.class.getName());
}
