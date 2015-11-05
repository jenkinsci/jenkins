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
package hudson.fsp;

import hudson.scm.PollingResult;
import hudson.scm.SCM;
import hudson.scm.ChangeLogParser;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.model.AbstractProject;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import jenkins.model.Jenkins;
import hudson.model.Result;
import hudson.model.PermalinkProjectAction.Permalink;
import hudson.Launcher;
import hudson.FilePath;
import hudson.WorkspaceSnapshot;
import hudson.PermalinkList;

import java.io.IOException;
import java.io.File;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * {@link SCM} that inherits the workspace from another build through {@link WorkspaceSnapshot}
 *
 * @author Kohsuke Kawaguchi
 */
public class WorkspaceSnapshotSCM extends SCM {
    /**
     * The job name from which we inherit the workspace.
     */
    public String jobName;
    /**
     * The permalink name indicating the build from which to inherit the workspace.
     */
    public String permalink;

    @DataBoundConstructor
    public WorkspaceSnapshotSCM(String jobName, String permalink) {
        this.jobName = jobName;
        this.permalink = permalink;
    }

    /**
     * {@link Exception} indicating that the resolution of the job/permalink failed.
     */
    private final class ResolvedFailedException extends Exception {
        private ResolvedFailedException(String message) {
            super(message);
        }
    }

    private static class Snapshot {
        final WorkspaceSnapshot snapshot;
        final AbstractBuild<?,?> owner;
        private Snapshot(WorkspaceSnapshot snapshot, AbstractBuild<?,?> owner) {
            this.snapshot = snapshot;
            this.owner = owner;
        }

        void restoreTo(FilePath dst,TaskListener listener) throws IOException, InterruptedException {
            snapshot.restoreTo(owner,dst,listener);
        }
    }
    /**
     * Obtains the {@link WorkspaceSnapshot} object that this {@link SCM} points to,
     * or throws {@link hudson.fsp.WorkspaceSnapshotSCM.ResolvedFailedException} upon failing.
     *
     * @return never null.
     */
    public Snapshot resolve() throws ResolvedFailedException {
        Jenkins h = Jenkins.getInstance();
        AbstractProject<?,?> job = h.getItemByFullName(jobName, AbstractProject.class);
        if(job==null) {
            if(h.getItemByFullName(jobName)==null) {
                AbstractProject nearest = AbstractProject.findNearest(jobName);
                throw new ResolvedFailedException(Messages.WorkspaceSnapshotSCM_NoSuchJob(jobName,nearest.getFullName()));
            } else
                throw new ResolvedFailedException(Messages.WorkspaceSnapshotSCM_IncorrectJobType(jobName));
        }

        PermalinkList permalinks = job.getPermalinks();
        Permalink p = permalinks.get(permalink);
        if(p==null)
            throw new ResolvedFailedException(Messages.WorkspaceSnapshotSCM_NoSuchPermalink(permalink,jobName));

        AbstractBuild<?,?> b = (AbstractBuild<?,?>)p.resolve(job);
        if(b==null)
            throw new ResolvedFailedException(Messages.WorkspaceSnapshotSCM_NoBuild(permalink,jobName));

        WorkspaceSnapshot snapshot = b.getAction(WorkspaceSnapshot.class);
        if(snapshot==null)
            throw new ResolvedFailedException(Messages.WorkspaceSnapshotSCM_NoWorkspace(jobName,permalink));

        return new Snapshot(snapshot,b);
    }

    @Override public SCMRevisionState calcRevisionsFromBuild(AbstractBuild<?, ?> build, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        return null;
    }

    @Override protected PollingResult compareRemoteRevisionWith(AbstractProject project, Launcher launcher, FilePath workspace, TaskListener listener, SCMRevisionState baseline) throws IOException, InterruptedException {
        return PollingResult.NO_CHANGES;
    }

    @Override public boolean checkout(AbstractBuild build, Launcher launcher, FilePath workspace, BuildListener listener, File changelogFile) throws IOException, InterruptedException {
        try {
            resolve().restoreTo(workspace,listener);
            return true;
        } catch (ResolvedFailedException e) {
            listener.error(e.getMessage()); // stack trace is meaningless
            build.setResult(Result.FAILURE);
            return false;
        }
    }

    @Override public ChangeLogParser createChangeLogParser() {
        return null;
    }

    @Override public SCMDescriptor<?> getDescriptor() {
        return null;
    }
}
