/*
 * The MIT License
 * 
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Red Hat, Inc., Tom Huybrechts
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

import hudson.AbortException;
import hudson.Functions;
import hudson.Util;
import hudson.console.ModelHyperlinkNote;
import hudson.matrix.MatrixConfiguration.ParentBuildAction;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Executor;
import hudson.model.Fingerprint;
import hudson.model.Queue;
import hudson.model.Queue.Item;
import hudson.model.Result;
import hudson.util.HttpResponses;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletException;

/**
 * Build of {@link MatrixProject}.
 *
 * @author Kohsuke Kawaguchi
 */
public class MatrixBuild extends AbstractBuild<MatrixProject,MatrixBuild> {
    private AxisList axes;

    /**
     * If non-null, the {@link MatrixBuild} originates from the given build number.
     */
    private Integer baseBuild;

    public MatrixBuild(MatrixProject job) throws IOException {
        super(job);
    }

    public MatrixBuild(MatrixProject job, Calendar timestamp) {
        super(job, timestamp);
    }

    public MatrixBuild(MatrixProject project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    public Object readResolve() {
        // MatrixBuild.axes added in 1.285; default to parent axes for old data
        if (axes==null)
            axes = getParent().getAxes();
        return this;
    }

    /**
     * Deletes the build and all matrix configurations in this build when the button is pressed.
     */
    @RequirePOST
    public void doDoDeleteAll( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        checkPermission(DELETE);

        // We should not simply delete the build if it has been explicitly
        // marked to be preserved, or if the build should not be deleted
        // due to dependencies!
        String why = getWhyKeepLog();
        if (why!=null) {
            sendError(hudson.model.Messages.Run_UnableToDelete(toString(),why),req,rsp);
            return;
        }
        
        List<MatrixRun> runs = getExactRuns();
        for(MatrixRun run : runs){
        	why = run.getWhyKeepLog();
            if (why!=null) {
                sendError(hudson.model.Messages.Run_UnableToDelete(toString(),why),req,rsp);
                return;
            }
        	run.delete();
        }
        delete();
        rsp.sendRedirect2(req.getContextPath()+'/' + getParent().getUrl());
    }

    
    /**
     * Used by view to render a ball for {@link MatrixRun}.
     */
    public final class RunPtr {
        public final Combination combination;
        private RunPtr(Combination c) { this.combination=c; }

        public MatrixRun getRun() {
            return MatrixBuild.this.getRun(combination);
        }
        
        /**
         * Return the URL to the run that this pointer references.
         *
         * In the typical case, this creates {@linkplain #getShortUrl() a very short relative url}.
         * If the referenced run is a nearest previous build, this method returns a longer URL to that exact build.
         * {@link MatrixRun} which belongs to a given build {@link MatrixBuild}.
         * If there is no run which belongs to the build, return url of run, which belongs to the nearest previous build.
         */
        public String getNearestRunUrl() {
            MatrixRun r = getRun();
            if (r==null)    return null;
            if (getNumber()==r.getNumber())
                return getShortUrl()+'/';
            else
                return Stapler.getCurrentRequest().getContextPath()+'/'+r.getUrl();
        }

        public String getShortUrl() {
            return Util.rawEncode(combination.toString());
        }

        public String getTooltip() {
            MatrixRun r = getRun();
            if(r!=null) return r.getIconColor().getDescription();
            Queue.Item item = Jenkins.getInstance().getQueue().getItem(getParent().getItem(combination));
            if(item!=null)
                return item.getWhy();
            return null;    // fall back
        }
    }

    public Layouter<RunPtr> getLayouter() {
        // axes can be null if build page is access right when build starts
        return axes == null ? null : new Layouter<RunPtr>(axes) {
            protected RunPtr getT(Combination c) {
                return new RunPtr(c);
            }
        };
    }

    /**
     * Sets the base build from which this build is derived.
     * @since 1.416
     */
    public void setBaseBuild(MatrixBuild baseBuild) {
    	this.baseBuild = (baseBuild==null || baseBuild==getPreviousBuild()) ? null : baseBuild.getNumber();
    }

    /**
     * Returns the base {@link MatrixBuild} that this build originates from.
     * <p>
     * If this build is a partial build, unexecuted {@link MatrixRun}s are delegated to this build number.
     */
    public MatrixBuild getBaseBuild() {
        return baseBuild==null ? getPreviousBuild() : getParent().getBuildByNumber(baseBuild);
    }

    /**
     * Gets the {@link MatrixRun} in this build that corresponds
     * to the given combination.
     */
    public MatrixRun getRun(Combination c) {
        MatrixConfiguration config = getParent().getItem(c);
        if(config==null)    return null;
        return getRunForConfiguration(config);
    }

    /**
     * Like {@link #getRun(Combination)}, but do not approximate the result by earlier execution
     * of the given combination (which is done for partial rebuild of the matrix.)
     */
    public MatrixRun getExactRun(Combination c) {
        MatrixConfiguration config = getParent().getItem(c);
        if(config==null)    return null;
        return config.getBuildByNumber(getNumber());
    }
    
    /**
     * Returns all {@link MatrixRun}s for this {@link MatrixBuild}.
     */
    @Exported
    public List<MatrixRun> getRuns() {
        List<MatrixRun> r = new ArrayList<MatrixRun>();
        for(MatrixConfiguration c : getParent().getItems()) {
            MatrixRun b = getRunForConfiguration(c);
            if (b != null) r.add(b);
        }
        return r;
    }
    
    private MatrixRun getRunForConfiguration(MatrixConfiguration c) {
        for (MatrixBuild b=this; b!=null; b=b.getBaseBuild()) {
            MatrixRun r = c.getBuildByNumber(b.getNumber());
            if (r!=null)    return r;
        }
        return null;
    }

    /**
     * Returns all {@link MatrixRun}s for exactly this {@link MatrixBuild}.
     * <p>
     * Unlike {@link #getRuns()}, this method excludes those runs
     * that didn't run and got inherited.
     * @since 1.413
     */
    public List<MatrixRun> getExactRuns() {
        List<MatrixRun> r = new ArrayList<MatrixRun>();
        for(MatrixConfiguration c : getParent().getItems()) {
            MatrixRun b = c.getBuildByNumber(getNumber());
            if (b != null) r.add(b);
        }
        return r;
    }

    @Override
    public String getWhyKeepLog() {
        MatrixBuild b = getNextBuild();
        if (isLinkedBy(b))
            return Messages.MatrixBuild_depends_on_this(b.getDisplayName());
        return super.getWhyKeepLog();
    }

    /** 
     * @return True if another {@link MatrixBuild} build (passed as a parameter) depends on this build.
     * @since 1.481 
     */
    public boolean isLinkedBy(MatrixBuild b) {
    	if(null == b)
    		return false;
    	for(MatrixConfiguration c : b.getParent().getActiveConfigurations()) {
            MatrixRun r = c.getNearestOldBuild(b.getNumber());
            if (r != null && r.getNumber()==getNumber())
                return true;
        }
        return false;
    }
    
    /**
     * True if this build didn't do a full build and it is depending on the result of the previous build.
     */
    public boolean isPartial() {
        for(MatrixConfiguration c : getParent().getActiveConfigurations()) {
            MatrixRun b = c.getNearestOldBuild(getNumber());
            if (b != null && b.getNumber()!=getNumber())
                return true;
        }
        return false;
    }

    @Override
    public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        try {
            MatrixRun item = getRun(Combination.fromString(token));
            if(item!=null) {
                if (item.getNumber()==this.getNumber())
                    return item;
                else {
                    // redirect the user to the correct URL
                    String url = Functions.joinPath(item.getUrl(), req.getRestOfPath());
                    String qs = req.getQueryString();
                    if (qs!=null)   url+='?'+qs;
                    throw HttpResponses.redirectViaContextPath(url);
                }
            }
        } catch (IllegalArgumentException _) {
            // failed to parse the token as Combination. Must be something else
        }
        return super.getDynamic(token,req,rsp);
    }

    @Override
    public void run() {
        execute(new MatrixBuildExecution());
    }

    @Override
    public Fingerprint.RangeSet getDownstreamRelationship(AbstractProject that) {
        Fingerprint.RangeSet rs = super.getDownstreamRelationship(that);
        for(MatrixRun run : getRuns())
            rs.add(run.getDownstreamRelationship(that));
        return rs;
    }

    /**
     * Object that lives from the start of {@link MatrixBuild} execution to its end.
     *
     * Used to keep track of things that are needed only during the build.
     */
    public class MatrixBuildExecution extends AbstractBuildExecution {
        private final List<MatrixAggregator> aggregators = new ArrayList<MatrixAggregator>();
        private Set<MatrixConfiguration> activeConfigurations;

        /**
         * Snapshot of {@link MatrixProject#getActiveConfigurations()} to ensure
         * that the build will use a consistent view of it.
         */
        public Set<MatrixConfiguration> getActiveConfigurations() {
            return activeConfigurations;
        }

        /**
         * Aggregators attached to this build execution, that are notified
         * of every start/end of {@link MatrixRun}.
         */
        public List<MatrixAggregator> getAggregators() {
            return aggregators;
        }

        protected Result doRun(BuildListener listener) throws Exception {
            MatrixProject p = getProject();
            PrintStream logger = listener.getLogger();

            // give axes a chance to rebuild themselves
            activeConfigurations = p.rebuildConfigurations(this);

            // list up aggregators
            listUpAggregators(p.getPublishers().values());
            listUpAggregators(p.getProperties().values());
            listUpAggregators(p.getBuildWrappers().values());

            axes = p.getAxes();

            try {
                return p.getExecutionStrategy().run(this);
            } catch( InterruptedException e ) {
                logger.println("Aborted");
                Executor x = Executor.currentExecutor();
                x.recordCauseOfInterruption(MatrixBuild.this, listener);
                return x.abortResult();
            } catch (AbortException e) {
                logger.println(e.getMessage());
                return Result.FAILURE;
            } finally {
                // if the build was aborted in the middle. Cancel all the configuration builds.
                Queue q = Jenkins.getInstance().getQueue();
                synchronized(q) {// avoid micro-locking in q.cancel.
                    final int n = getNumber();
                    for (MatrixConfiguration c : activeConfigurations) {
                        for (Item i : q.getItems(c)) {
                            ParentBuildAction a = i.getAction(ParentBuildAction.class);
                            if (a!=null && a.parent==getBuild()) {
                                q.cancel(i);
                                logger.println(Messages.MatrixBuild_Cancelled(ModelHyperlinkNote.encodeTo(c)));
                            }
                        }
                        MatrixRun b = c.getBuildByNumber(n);
                        if(b!=null && b.isBuilding()) {// executor can spend some time in post production state, so only cancel in-progress builds.
                            Executor exe = b.getExecutor();
                            if(exe!=null) {
                                logger.println(Messages.MatrixBuild_Interrupting(ModelHyperlinkNote.encodeTo(b)));
                                exe.interrupt();
                            }
                        }
                    }
                }
            }
        }

        private void listUpAggregators(Collection<?> values) {
            for (Object v : values) {
                if (v instanceof MatrixAggregatable) {
                    MatrixAggregatable ma = (MatrixAggregatable) v;
                    MatrixAggregator a = ma.createAggregator(MatrixBuild.this, launcher, listener);
                    if(a!=null)
                        aggregators.add(a);
                }
            }
        }

        public void post2(BuildListener listener) throws Exception {
            for (MatrixAggregator a : aggregators)
                a.endBuild();
        }
    }
}
