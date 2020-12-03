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

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import java.io.IOException;
import jenkins.model.Jenkins;

/**
 * @deprecated Unused.
 */
@Deprecated
public abstract class FileSystemProvisioner implements Describable<FileSystemProvisioner> {
    public abstract void prepareWorkspace(AbstractBuild<?,?> build, FilePath ws, TaskListener listener) throws IOException, InterruptedException;

    public abstract void discardWorkspace(AbstractProject<?, ?> project, FilePath ws) throws IOException, InterruptedException;

    public abstract WorkspaceSnapshot snapshot(AbstractBuild<?,?> build, FilePath ws, String glob, TaskListener listener) throws IOException, InterruptedException;

    public Descriptor getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(getClass());
    }

    public static final FileSystemProvisioner DEFAULT = new Default();

    public static final class Default extends FileSystemProvisioner {
        public void prepareWorkspace(AbstractBuild<?, ?> build, FilePath ws, TaskListener listener) throws IOException, InterruptedException {
        }

        public void discardWorkspace(AbstractProject<?, ?> project, FilePath ws) throws IOException, InterruptedException {
        }

        public WorkspaceSnapshot snapshot(AbstractBuild<?, ?> build, FilePath ws, String glob, TaskListener listener) throws IOException, InterruptedException {
            throw new IOException("unimplemented");
        }

    }
}
