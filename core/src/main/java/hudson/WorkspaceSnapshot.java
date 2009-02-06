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
package hudson;

import hudson.model.Action;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;

import java.io.IOException;

/**
 * Represents a workspace snapshot created by {@link FileSystemProvisioner}.
 *
 * <p>
 * This class encapsulates a logic to use the snapshot elsewhere.
 * The instance will be persisted with the {@link AbstractBuild} object
 * as an {@link Action}.
 *
 * <p>
 * TODO: how to garbage-collect this object, especially for zfs?
 * perhaps when a new build is started?
 *
 * @see FileSystemProvisioner
 * @author Kohsuke Kawaguchi
 */
public abstract class WorkspaceSnapshot implements Action {
    /**
     * Restores the snapshot to the given file system location.
     *
     * @param owner
     *      The build that owns this action. It's always the same value for any given {@link WorkspaceSnapshot},
     *      but passed in separately so that implementations don't need to keep them in fields.
     * @param dst
     *      The file path to which the snapshot shall be restored to.
     * @param listener
     *      Send the progress of the restoration to this listener. Never null.
     */
    public abstract void restoreTo(AbstractBuild<?,?> owner, FilePath dst, TaskListener listener) throws IOException, InterruptedException;

    public String getIconFileName() {
        // by default, hide from the UI
        return null;
    }

    public String getDisplayName() {
        return "Workspace";
    }

    public String getUrlName() {
        return "workspace";
    }
}
