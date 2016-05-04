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

import hudson.model.Descriptor;
import hudson.model.TaskListener;

import java.io.IOException;

/**
 * {@link Descriptor} for {@link FileSystemProvisioner}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class FileSystemProvisionerDescriptor extends Descriptor<FileSystemProvisioner> implements ExtensionPoint {
    /**
     * Called to clean up a workspace that may potentially belong to this {@link FileSystemProvisioner}.
     *
     * <p>
     * Because users may modify the file system behind Hudson, and agents may come and go when
     * configuration changes happen, in general case Hudson is unable to keep track of which jobs
     * have workspaces in which agents.
     *
     * <p>
     * So instead we rey on a garbage collection mechanism, to look at workspaces left in the file system
     * without the contextual information of the owner project, and try to clean that up.
     *
     * <p>
     * This method is called to do this, after Hudson determines that the workspace should be deleted
     * to reclaim disk space. The implementation of this method is expected to sniff the contents of
     * the workspace, and if it looks like the one created by {@link FileSystemProvisioner#prepareWorkspace(AbstractBuild, FilePath, TaskListener)},
     * perform the necessary deletion operation, and return <tt>true</tt>.
     *
     * <p>
     * If the workspace isn't the one created by this {@link FileSystemProvisioner}, or if the
     * workspace can be simply deleted by {@link FilePath#deleteRecursive()}, then simply
     * return <tt>false</tt> to give other {@link FileSystemProvisionerDescriptor}s a chance to
     * discard them.
     *
     * @param ws
     *      The workspace directory to be removed.
     * @param listener
     *      The status of the operation, error message, etc., should go here.
     * @return
     *      true if this {@link FileSystemProvisionerDescriptor} is responsible for de-alocating the workspace.
     *      false otherwise, in which case the other {@link FileSystemProvisionerDescriptor}s are asked to
     *      clean up the workspace.
     */
    public abstract boolean discard(FilePath ws, TaskListener listener) throws IOException, InterruptedException;
}
