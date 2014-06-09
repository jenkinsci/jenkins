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

import hudson.model.AbstractProject;
import jenkins.model.Jenkins;

/**
 * Maintains the automatically inferred {@link RepositoryBrowser} instance.
 *
 * <p>
 * To reduce the user's work, Hudson tries to infer applicable {@link RepositoryBrowser}
 * from configurations of other jobs. But this needs caution &mdash; for example,
 * such inferred {@link RepositoryBrowser} must be recalculated whenever
 * a job configuration changes somewhere.
 *
 * <p>
 * This class makes such tracking easy by hiding this logic.
 */
final class AutoBrowserHolder {
    private int cacheGeneration;
    private RepositoryBrowser cache;
    private SCM owner;

    public AutoBrowserHolder(SCM owner) {
        this.owner = owner;
    }

    public RepositoryBrowser get() {
        if (cacheGeneration == -1) {
            return cache;
        }
        SCMDescriptor<?> d = owner.getDescriptor();
        RepositoryBrowser<?> dflt = owner.guessBrowser();
        if (dflt != null) {
            cache = dflt;
            cacheGeneration = -1;
            return cache;
        }
        int g = d.generation;
        if(g!=cacheGeneration) {
            cacheGeneration = g;
            cache = infer();
        }
        return cache;
    }

    /**
     * Picks up a {@link RepositoryBrowser} that matches the
     * given {@link SCM} from existing other jobs.
     *
     * @return
     *      null if no applicable configuration was found.
     */
    private RepositoryBrowser infer() {
        for( AbstractProject p : Jenkins.getInstance().getAllItems(AbstractProject.class) ) {
            SCM scm = p.getScm();
            if (scm!=null && scm.getClass()==owner.getClass() && scm.getBrowser()!=null &&
                    ((SCMDescriptor)scm.getDescriptor()).isBrowserReusable(scm,owner)) {
                return scm.getBrowser();
            }
        }
        return null;
    }
}
