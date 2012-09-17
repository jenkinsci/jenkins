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
package hudson.matrix;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Build;
import hudson.model.Node;
import hudson.slaves.WorkspaceList;
import hudson.slaves.WorkspaceList.Lease;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * Execution of {@link MatrixConfiguration}.
 *
 * @author Kohsuke Kawaguchi
 */
public class MatrixRun extends Build<MatrixConfiguration,MatrixRun> {
    public MatrixRun(MatrixConfiguration job) throws IOException {
        super(job);
    }

    public MatrixRun(MatrixConfiguration job, Calendar timestamp) {
        super(job, timestamp);
    }

    public MatrixRun(MatrixConfiguration project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    @Override
    public String getUpUrl() {
        StaplerRequest req = Stapler.getCurrentRequest();
        if(req!=null) {
            List<Ancestor> ancs = req.getAncestors();
            for( int i=1; i<ancs.size(); i++) {
                if(ancs.get(i).getObject()==this) {
                    Object parentObj = ancs.get(i-1).getObject();
                    if(parentObj instanceof MatrixBuild || parentObj instanceof MatrixConfiguration) {
                        return ancs.get(i-1).getUrl()+'/';
                    }
                }
            }
        }
        return super.getDisplayName();
    }

    /**
     * Gets the {@link MatrixBuild} that has the same build number.
     *
     * @return
     *      null if no such build exists, which happens when the module build
     *      is manually triggered.
     */
    public MatrixBuild getParentBuild() {
        return getParent().getParent().getBuildByNumber(getNumber());
    }

    /**
     * The same as {@link #getParentBuild()}.
     */
    @Override
    public AbstractBuild<?, ?> getRootBuild() {
        return getParentBuild();
    }

    @Override
    public String getDisplayName() {
        StaplerRequest req = Stapler.getCurrentRequest();
        if(req!=null) {
            List<Ancestor> ancs = req.getAncestors();
            for( int i=1; i<ancs.size(); i++) {
                if(ancs.get(i).getObject()==this) {
                    if(ancs.get(i-1).getObject() instanceof MatrixBuild) {
                        return getParent().getCombination().toCompactString(getParent().getParent().getAxes());
                    }
                }
            }
        }
        return super.getDisplayName();
    }

    @Override
    public Map<String,String> getBuildVariables() {
        Map<String,String> r = super.getBuildVariables();
        // pick up user axes
        AxisList axes = getParent().getParent().getAxes();
        for (Map.Entry<String,String> e : getParent().getCombination().entrySet()) {
            Axis a = axes.find(e.getKey());
            if (a!=null)
                a.addBuildVariable(e.getValue(),r);
            else
                r.put(e.getKey(), e.getValue());
        }
        return r;
    }

    /**
     * If the parent {@link MatrixBuild} is kept, keep this record too.
     */
    @Override
    public String getWhyKeepLog() {
        MatrixBuild pb = getParentBuild();
        if(pb!=null && pb.getWhyKeepLog()!=null)
            return Messages.MatrixRun_KeptBecauseOfParent(pb);
        return super.getWhyKeepLog();
    }

    @Override
    public MatrixConfiguration getParent() {// don't know why, but javac wants this
        return super.getParent();
    }

    @Override
    public void run() {
        execute(new MatrixRunExecution());
    }

    private class MatrixRunExecution extends BuildExecution {
        protected Lease getParentWorkspaceLease(Node n, WorkspaceList wsl) throws InterruptedException, IOException {
            MatrixProject mp = getParent().getParent();

            String customWorkspace = mp.getCustomWorkspace();
            if (customWorkspace != null) {
                // we allow custom workspaces to be concurrently used between jobs.
                return Lease.createDummyLease(n.getRootPath().child(getEnvironment(listener).expand(customWorkspace)));
            }
            return wsl.allocate(n.getWorkspaceFor(mp), getParentBuild());
        }

        @Override
        protected Lease decideWorkspace(Node n, WorkspaceList wsl) throws InterruptedException, IOException {
            MatrixProject mp = getParent().getParent();

            // lock is done at the parent level, so that concurrent MatrixProjects get respective workspace,
            // but within MatrixConfigurations that belong to the same MatrixBuild.
            // if MatrixProject is configured with custom workspace, we assume that the user knows what he's doing
            // and try not to append unique random suffix.
            Lease baseLease = getParentWorkspaceLease(n,wsl);

            // resolve the relative path against the parent workspace, which needs locking
            FilePath baseDir = baseLease.path;

            // prepare variables that can be used in the child workspace setting
            EnvVars env = getEnvironment(listener);
            env.put("COMBINATION",getParent().getCombination().toString('/','/'));  // e.g., "axis1/a/axis2/b"
            env.put("SHORT_COMBINATION",getParent().getDigestName());               // e.g., "0fbcab35"
            env.put("PARENT_WORKSPACE",baseDir.getRemote());
            env.putAll(getBuildVariables());

            // child workspace need no individual locks, whether or not we use custom workspace
            String childWs = mp.getChildCustomWorkspace();
            return Lease.createLinkedDummyLease(baseDir.child(env.expand(childWs)),baseLease);
        }
    }
}
