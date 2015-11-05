/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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
package hudson.os.solaris;

import jenkins.MasterToSlaveFileCallable;
import hudson.FileSystemProvisioner;
import hudson.FilePath;
import hudson.WorkspaceSnapshot;
import hudson.FileSystemProvisionerDescriptor;
import hudson.Extension;
import hudson.remoting.VirtualChannel;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.model.AbstractProject;
import hudson.model.Node;

import java.io.IOException;
import java.io.File;
import java.io.Serializable;

import org.jvnet.solaris.libzfs.LibZFS;
import org.jvnet.solaris.libzfs.ZFSFileSystem;

/**
 * {@link FileSystemProvisioner} for ZFS.
 *
 * @author Kohsuke Kawaguchi
 */
public class ZFSProvisioner extends FileSystemProvisioner implements Serializable {
    private static final LibZFS libzfs = new LibZFS();
    private final String rootDataset;

    public ZFSProvisioner(Node node) throws IOException, InterruptedException {
        rootDataset = node.getRootPath().act(new MasterToSlaveFileCallable<String>() {
            private static final long serialVersionUID = -2142349338699797436L;

            public String invoke(File f, VirtualChannel channel) throws IOException {
                ZFSFileSystem fs = libzfs.getFileSystemByMountPoint(f);
                if(fs!=null)    return fs.getName();

                // TODO: for now, only support slaves that are already on ZFS.
                throw new IOException("Not on ZFS");
            }
        });
    }

    public void prepareWorkspace(AbstractBuild<?,?> build, FilePath ws, final TaskListener listener) throws IOException, InterruptedException {
        final String name = build.getProject().getFullName();
        
        ws.act(new MasterToSlaveFileCallable<Void>() {
            private static final long serialVersionUID = 2129531727963121198L;

            public Void invoke(File f, VirtualChannel channel) throws IOException {
                ZFSFileSystem fs = libzfs.getFileSystemByMountPoint(f);
                if(fs!=null)    return null;    // already on ZFS

                // nope. create a file system
                String fullName = rootDataset + '/' + name;
                listener.getLogger().println("Creating a ZFS file system "+fullName+" at "+f);
                fs = libzfs.create(fullName, ZFSFileSystem.class);
                fs.setMountPoint(f);
                fs.mount();
                return null;
            }
        });
    }

    public void discardWorkspace(AbstractProject<?, ?> project, FilePath ws) throws IOException, InterruptedException {
        ws.act(new MasterToSlaveFileCallable<Void>() {
            private static final long serialVersionUID = 1916618107019257530L;

            public Void invoke(File f, VirtualChannel channel) throws IOException {
                ZFSFileSystem fs = libzfs.getFileSystemByMountPoint(f);
                if(fs!=null)
                    fs.destory(true);
                return null;
            }
        });
    }

    /**
     * @deprecated as of 1.350
     */
    @Deprecated
    public WorkspaceSnapshot snapshot(AbstractBuild<?, ?> build, FilePath ws, TaskListener listener) throws IOException, InterruptedException {
        throw new UnsupportedOperationException();
    }

    public WorkspaceSnapshot snapshot(AbstractBuild<?, ?> build, FilePath ws, String glob, TaskListener listener) throws IOException, InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Extension
    public static final class DescriptorImpl extends FileSystemProvisionerDescriptor {
        public boolean discard(FilePath ws, TaskListener listener) throws IOException, InterruptedException {
            // TODO
            return false;
        }

        public String getDisplayName() {
            return "ZFS";
        }
    }

    private static final long serialVersionUID = 1L;
}
