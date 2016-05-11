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

import hudson.FilePath.TarCompression;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Describable;
import hudson.model.Job;
import hudson.model.TaskListener;
import hudson.util.io.ArchiverFactory;
import jenkins.model.Jenkins;
import hudson.model.listeners.RunListener;
import hudson.scm.SCM;
import org.jenkinsci.Symbol;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Prepares and provisions workspaces for {@link AbstractProject}s.
 *
 * <p>
 *
 *
 * <p>
 * STILL A WORK IN PROGRESS. SUBJECT TO CHANGE! DO NOT EXTEND.
 *
 * TODO: is this per {@link Computer}? Per {@link Job}?
 *   -> probably per agent.
 *
 * <h2>Design Problems</h2>
 * <ol>
 * <li>
 * Garbage collection of snapshots. When do we discard snapshots?
 * In one use case, it would be convenient to keep the snapshot of the
 * last promoted/successful build. So we need to define a mechanism
 * to veto GC of snapshot? like an interface that Action can implement?
 *
 * Snapshot should be obtained per user's direction. That would be a good
 * moment for the user to specify the retention policy.
 *
 * <li>
 * Configuration mechanism. Should we auto-detect FileSystemProvisioner
 * per OS? (but for example, zfs support would require the root access.)
 * People probably needs to be able to disable this feature, which means
 * one more configuration option. It's especially tricky because
 * during the configuration we don't know the OS type.
 *
 * OTOH special agent type like the ones for network.com grid can
 * hide this.
 * </ol>
 *
 *
 * <h2>Recap</h2>
 *
 * To recap,
 *
 * - when an agent connects, we auto-detect the file system provisioner.
 *   (for example, ZFS FSP would check the agent root user prop
 *   and/or attempt to "pfexec zfs create" and take over.)
 *
 * - the user may configure jobs for snapshot collection, along with
 *   the retention policy.
 *
 * - keep workspace snapshots that correspond to the permalinks
 *   In ZFS, use a user property to remember the build and the job.
 *
 * Can't the 2nd step happen automatically, when someone else depends on
 * the workspace snapshot of the upstream? Yes, by using {@link RunListener}.
 * So this becomes like a special SCM type.
 *
 *
 *
 * <h2>Design take 2</h2>
 * <p>
 * The first piece of this is the custom {@link SCM}, which inherits the
 * workspace of another job. When this executes, it picks up
 * {@link WorkspaceSnapshot} from the other job and use it to obtain the workspace.
 *
 * <p>
 * Then there's {@link RunListener}, which creates a snapshot if
 * someone else is interested in using a snapshot later.
 *
 * <h3>TODOs</h3>
 * <ul>
 * <li>
 * Garbage collection of workspace snapshots. 
 *
 * </ul>
 *
 * @author Kohsuke Kawaguchi
 * @since 1.235
 */
public abstract class FileSystemProvisioner implements ExtensionPoint, Describable<FileSystemProvisioner> {
    /**
     * Called very early in the build (before a build places any files
     * in the workspace, such as SCM checkout) to provision a workspace
     * for the build.
     *
     * <p>
     * This method can prepare the underlying file system in preparation
     * for the later {@link FileSystemProvisioner.Default#snapshot(AbstractBuild, FilePath, TaskListener)}.
     *
     * TODO : the method needs to be able to see the snapshot would
     * be later needed. In fact, perhaps we should only call this method
     * when Hudson knows that a snapshot is later needed?
     *
     * @param ws
     *      New workspace should be prepared in this location. This is the same value as
     *      {@code build.getProject().getWorkspace()} but passed separately for convenience.
     */
    public abstract void prepareWorkspace(AbstractBuild<?,?> build, FilePath ws, TaskListener listener) throws IOException, InterruptedException;

    /**
     * When a project is deleted, this method is called to undo the effect of
     * {@link #prepareWorkspace(AbstractBuild, FilePath, TaskListener)}.
     *
     * @param project
     *      Project whose workspace is being discarded.
     * @param ws
     *      Workspace to be discarded. This workspace is on the node
     *      this {@link FileSystemProvisioner} is provisioned for.
     */
    public abstract void discardWorkspace(AbstractProject<?, ?> project, FilePath ws) throws IOException, InterruptedException;

//    public abstract void moveWorkspace(AbstractProject<?,?> project, File oldWorkspace, File newWorkspace) throws IOException;

    /**
     * Obtains the snapshot of the workspace of the given build.
     *
     * <p>
     * The state of the build when this method is invoked depends on
     * the project type. Most would call this at the end of the build,
     * but for example {@code MatrixBuild} would call this after
     * SCM check out so that the state of the fresh workspace
     * can be then propagated to elsewhere.
     *
     * <p>
     * If the implementation of this method needs to store data in a file system,
     * do so under {@link AbstractBuild#getRootDir()}, since the lifecycle of
     * the snapshot is tied to the life cycle of a build.
     *
     * @param ws
     *      New workspace should be prepared in this location. This is the same value as
     *      {@code build.getWorkspace()} but passed separately for convenience.
     * @param glob
     *      Ant-style file glob for files to include in the snapshot. May not be pertinent for all
     *      implementations.
     */
    public abstract WorkspaceSnapshot snapshot(AbstractBuild<?,?> build, FilePath ws, String glob, TaskListener listener) throws IOException, InterruptedException;

    public FileSystemProvisionerDescriptor getDescriptor() {
        return (FileSystemProvisionerDescriptor) Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    /**
     * Default implementation.
     */
    public static final FileSystemProvisioner DEFAULT = new Default();

    /**
     * Returns all the registered {@link FileSystemProvisioner} descriptors.
     */
    public static DescriptorExtensionList<FileSystemProvisioner,FileSystemProvisionerDescriptor> all() {
        return Jenkins.getInstance().<FileSystemProvisioner,FileSystemProvisionerDescriptor>getDescriptorList(FileSystemProvisioner.class);
    }

    /**
     * Default implementation that doesn't rely on any file system specific capability,
     * and thus can be used anywhere that Hudson runs.
     */
    public static final class Default extends FileSystemProvisioner {
        public void prepareWorkspace(AbstractBuild<?, ?> build, FilePath ws, TaskListener listener) throws IOException, InterruptedException {
        }

        public void discardWorkspace(AbstractProject<?, ?> project, FilePath ws) throws IOException, InterruptedException {
        }

        /**
         * @deprecated as of 1.350
         */
        @Deprecated
        public WorkspaceSnapshot snapshot(AbstractBuild<?, ?> build, FilePath ws, TaskListener listener) throws IOException, InterruptedException {
            return snapshot(build, ws, "**/*", listener);
        }
        
        /**
         * Creates a tar ball.
         */
        public WorkspaceSnapshot snapshot(AbstractBuild<?, ?> build, FilePath ws, String glob, TaskListener listener) throws IOException, InterruptedException {
            File wss = new File(build.getRootDir(),"workspace.tgz");
            OutputStream os = new BufferedOutputStream(new FileOutputStream(wss));
            try {
                ws.archive(ArchiverFactory.TARGZ,os,glob);
            } finally {
                os.close();
            }
            return new WorkspaceSnapshotImpl();
        }

        public static final class WorkspaceSnapshotImpl extends WorkspaceSnapshot {
            public void restoreTo(AbstractBuild<?,?> owner, FilePath dst, TaskListener listener) throws IOException, InterruptedException {
                File zip = new File(owner.getRootDir(),"workspace.zip");
                if (zip.exists()) {// we used to keep it in zip
                    new FilePath(zip).unzip(dst);
                } else {// but since 1.456 we do tgz
                    File tgz = new File(owner.getRootDir(),"workspace.tgz");
                    new FilePath(tgz).untar(dst, TarCompression.GZIP);
                }
            }
        }

        @Extension @Symbol("standard")
        public static final class DescriptorImpl extends FileSystemProvisionerDescriptor {
            public boolean discard(FilePath ws, TaskListener listener) throws IOException, InterruptedException {
                // the default provisioner does not do anything special,
                // so allow other types to manage it
                return false;
            }

            public String getDisplayName() {
                return "Default";
            }
        }
    }
}
