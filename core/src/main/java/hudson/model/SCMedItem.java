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
package hudson.model;

import hudson.scm.PollingResult;
import hudson.scm.SCM;
import jenkins.triggers.SCMTriggerItem;

/**
 * @deprecated Implement {@link SCMTriggerItem} instead.
 */
@Deprecated
public interface SCMedItem extends BuildableItem {
    /**
     * Gets the {@link SCM} for this item.
     *
     * @return
     *      may return null for indicating "no SCM".
     */
    SCM getScm();

    /**
     * {@link SCMedItem} needs to be an instance of
     * {@link AbstractProject}.
     *
     * <p>
     * This method must be always implemented as {@code (AbstractProject)this}, but
     * defining this method emphasizes the fact that this cast must be doable.
     */
    AbstractProject<?,?> asProject();

    /**
     * Checks if there's any update in SCM, and returns true if any is found.
     *
     * @deprecated as of 1.346
     *      Use {@link #poll(TaskListener)} instead.
     */
    @Deprecated
    boolean pollSCMChanges( TaskListener listener );

    /**
     * Checks if there's any update in SCM, and returns true if any is found.
     *
     * <p>
     * The implementation is responsible for ensuring mutual exclusion between polling and builds
     * if necessary.
     *
     * @return never null.
     *
     * @since 1.345
     */
    PollingResult poll(TaskListener listener);
}
