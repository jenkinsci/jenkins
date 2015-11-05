/*
 * The MIT License
 *
 * Copyright (c) 2013, CloudBees, Intl., Nicolas De loof
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

import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.slaves.WorkspaceList;

import javax.annotation.CheckForNull;

/**
 * Allows to access a workspace as an alternative to online build node.
 * <p>
 * Primary use case is {@link hudson.slaves.Cloud} implementations that don't keep the slave
 * node online to browse workspace, but maintain a copy of node workspace on master.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 * @since 1.502
 */
public abstract class WorkspaceBrowser implements ExtensionPoint {

    private final WorkspaceList workspaceList = new WorkspaceList();

    /**
     * Provide access to job's workspace
     * @param job
     * @return <code>null</code> if this WorkspaceBrowser don't have a workspace for this job
     */
    public abstract @CheckForNull FilePath getWorkspace(Job job);

    /**
     * Gets the object that coordinates the workspace allocation.
     */
    /* package */ final WorkspaceList getWorkspaceList() {
        return workspaceList;
    }
}
