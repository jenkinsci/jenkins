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

import hudson.Util;
import hudson.console.HyperlinkNote;
import hudson.matrix.listeners.MatrixBuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Cause.UpstreamCause;
import hudson.model.Executor;
import hudson.model.Fingerprint;
import hudson.model.ParametersAction;
import hudson.model.Queue;
import hudson.model.Result;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

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
        
        List<MatrixRun> runs = getRuns();
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
        if (b!=null && b.isPartial())
            return b.getDisplayName()+" depends on this";
        return super.getWhyKeepLog();
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
            if(item!=null)
                return item;
        } catch (IllegalArgumentException _) {
            // failed to parse the token as Combination. Must be something else
        }
        return super.getDynamic(token,req,rsp);
    }

    @Override
    public void run() {
        run(new RunnerImpl());
    }

    @Override
    public Fingerprint.RangeSet getDownstreamRelationship(AbstractProject that) {
        Fingerprint.RangeSet rs = super.getDownstreamRelationship(that);
        for(MatrixRun run : getRuns())
            rs.add(run.getDownstreamRelationship(that));
        return rs;
    }

    private class RunnerImpl extends AbstractRunner {
        private final List<MatrixAggregator> aggregators = new ArrayList<MatrixAggregator>();
        /**
         * This method groups the active configurations according to the touchstone
         * combination list. The list must not be empty. Last element must  be the empty
         * string, so the last group of active combinations will always receive all
         * configurations not matched by any specific filter.
         *
         * @param p Matrix project
         * @param touchStoneFilterList contains a list of combination filters. Must not be empty,
         * must end with empty string to catch all remaining configurations
         * @return list of collections of active configurations
         */
        private List<Collection<MatrixConfiguration>>  getGroupedActiveConfigurations(MatrixProject p, List<String> touchStoneFilterList) {
            List<Collection<MatrixConfiguration>> groupedActiveConfigurations = new ArrayList<Collection<MatrixConfiguration>>();
            Collection<MatrixConfiguration> activeConfigurations = p.getActiveConfigurations();
            for (int i = 0; i<touchStoneFilterList.size(); i++) {
                groupedActiveConfigurations.add(new HashSet<MatrixConfiguration>());
            }

            for (MatrixConfiguration c: activeConfigurations) {
                Boolean groupFound = false;
                if (!MatrixBuildListener.buildConfiguration(MatrixBuild.this, c))
                    continue; // skip rebuild
                for (int i = 0; i < touchStoneFilterList.size() && !groupFound; i++) {
                    /**
                     * Since the last element of touchStoneFilterList is the
                     * empty string, it will catch all remainders.
                     */
                    if (c.getCombination().evalGroovyExpression(p.getAxes(), touchStoneFilterList.get(i))) {
                        groupedActiveConfigurations.get(i).add(c);
                        groupFound = true;
                        break; // exit for loop
                    }
                }
                /*
                 * The following assertion is justified because the last element
                 * of touchStoneFilterList is always "", which is catch all
                 */
                assert(groupFound);
            }
            return groupedActiveConfigurations;
        }
        /**
         * This method checks if touchstone configuration is active. If so, the
         * semicolon separated, not-empty combination filters are collected in a
         * list. Afterwards an empty string is appended to the list, making sure
         * it is never empty and all active configurations are matched by a
         * list element.
         * @param p
         * @return Non-empty string list where the last element is an empty string
         */
        private List<String> getTouchstoneFilterList(MatrixProject p) {
            Collection<MatrixConfiguration> activeConfigurations = p.getActiveConfigurations();
            List<String> touchStoneFilterList = new ArrayList<String>();
            String touchStoneFilterField = p.getTouchStoneCombinationFilter();
            if (null == touchStoneFilterField)
                touchStoneFilterField = "";
            for (String filter: touchStoneFilterField.split("\\s*;\\s*"))
                if (filter.length() > 0)
                    touchStoneFilterList.add(filter);
                else
                    break;

            touchStoneFilterList.add(""); // if last list item is not empty, add empty string as catch-all
            return touchStoneFilterList;
        }
        protected Result doRun(BuildListener listener) throws Exception {
            MatrixProject p = getProject();
            PrintStream logger = listener.getLogger();

            // list up aggregators
            listUpAggregators(listener, p.getPublishers().values());
            listUpAggregators(listener, p.getProperties().values());
            listUpAggregators(listener, p.getBuildWrappers().values());

            final int n = getNumber();
            Collection<MatrixConfiguration> activeConfigurations = p.getActiveConfigurations();
            List<String> touchStoneFilterList = getTouchstoneFilterList(p);
            List<Collection<MatrixConfiguration>> groupedActiveConfigurations = getGroupedActiveConfigurations(p,touchStoneFilterList);
            for (MatrixAggregator a : aggregators)
                if(!a.startBuild())
                    return Result.FAILURE;

            MatrixConfigurationSorter sorter = p.getSorter();

            if (sorter != null) {
                logger.print( "Start sorting..." );
                for ( int i = 0; i < groupedActiveConfigurations.size(); i++ ) {
                    groupedActiveConfigurations.set(
                            i,
                            createTreeSet(groupedActiveConfigurations.get(i),
                            sorter)
                    );
                }
            }

            try {
                Result r = Result.SUCCESS;
                for (int i = 0; i < groupedActiveConfigurations.size(); i++) {
                    if(!p.isRunSequentially())
                        for(MatrixConfiguration c : groupedActiveConfigurations.get(i) )
                            scheduleConfigurationBuild(logger, c);

                    for (MatrixConfiguration c : groupedActiveConfigurations.get(i) ) {
                        if(p.isRunSequentially())
                            scheduleConfigurationBuild(logger, c);
                        Result buildResult = waitForCompletion(listener, c);
                        r = r.combine(buildResult);
                    }
                
                    if (i < groupedActiveConfigurations.size() - 1 && p.getTouchStoneResultCondition() != null && r.isWorseThan(p.getTouchStoneResultCondition())) {
                        logger.printf("Touchstone configuration %d (%s) resulted in %s, so aborting...%n", i, touchStoneFilterList.get(i), r);
                        return r;
                    } else {
                        logger.printf("Touchstone configuration %d (%s) finished...%n", i, touchStoneFilterList.get(i));
                    }
                }
                return r;
            } catch( InterruptedException e ) {
                logger.println("Aborted");
                Executor x = Executor.currentExecutor();
                x.recordCauseOfInterruption(MatrixBuild.this, listener);
                return x.abortResult();
            } catch (AggregatorFailureException e) {
                return Result.FAILURE;
            }
            finally {
                // if the build was aborted in the middle. Cancel all the configuration builds.
                Queue q = Jenkins.getInstance().getQueue();
                synchronized(q) {// avoid micro-locking in q.cancel.
                    for (MatrixConfiguration c : activeConfigurations) {
                        if(q.cancel(c))
                            logger.println(Messages.MatrixBuild_Cancelled(HyperlinkNote.encodeTo('/'+ c.getUrl(),c.getDisplayName())));
                        MatrixRun b = c.getBuildByNumber(n);
                        if(b!=null && b.isBuilding()) {// executor can spend some time in post production state, so only cancel in-progress builds.
                            Executor exe = b.getExecutor();
                            if(exe!=null) {
                                logger.println(Messages.MatrixBuild_Interrupting(HyperlinkNote.encodeTo('/'+ b.getUrl(),b.getDisplayName())));
                                exe.interrupt();
                            }
                        }
                    }
                }
            }
        }

        private void listUpAggregators(BuildListener listener, Collection<?> values) {
            for (Object v : values) {
                if (v instanceof MatrixAggregatable) {
                    MatrixAggregatable ma = (MatrixAggregatable) v;
                    MatrixAggregator a = ma.createAggregator(MatrixBuild.this, launcher, listener);
                    if(a!=null)
                        aggregators.add(a);
                }
            }
        }

        private Result waitForCompletion(BuildListener listener, MatrixConfiguration c) throws InterruptedException, IOException, AggregatorFailureException {
            String whyInQueue = "";
            long startTime = System.currentTimeMillis();

            // wait for the completion
            int appearsCancelledCount = 0;
            while(true) {
                MatrixRun b = c.getBuildByNumber(getNumber());

                // two ways to get beyond this. one is that the build starts and gets done,
                // or the build gets cancelled before it even started.
                Result buildResult = null;
                if(b!=null && !b.isBuilding())
                    buildResult = b.getResult();
                Queue.Item qi = c.getQueueItem();
                if(b==null && qi==null)
                    appearsCancelledCount++;
                else
                    appearsCancelledCount = 0;

                if(appearsCancelledCount>=5) {
                    // there's conceivably a race condition in computating b and qi, as their computation
                    // are not synchronized. There are indeed several reports of Hudson incorrectly assuming
                    // builds being cancelled. See
                    // http://www.nabble.com/Master-slave-problem-tt14710987.html and also
                    // http://www.nabble.com/Anyone-using-AccuRev-plugin--tt21634577.html#a21671389
                    // because of this, we really make sure that the build is cancelled by doing this 5
                    // times over 5 seconds
                    listener.getLogger().println(Messages.MatrixBuild_AppearsCancelled(HyperlinkNote.encodeTo('/'+ c.getUrl(),c.getDisplayName())));
                    buildResult = Result.ABORTED;
                }

                if(buildResult!=null) {
                    for (MatrixAggregator a : aggregators)
                        if(!a.endRun(b))
                            throw new AggregatorFailureException();
                    return buildResult;
                } 

                if(qi!=null) {
                    // if the build seems to be stuck in the queue, display why
                    String why = qi.getWhy();
                    if(!why.equals(whyInQueue) && System.currentTimeMillis()-startTime>5000) {
                        listener.getLogger().println(HyperlinkNote.encodeTo('/'+ c.getUrl(),c.getDisplayName())+" is still in the queue: "+why);
                        whyInQueue = why;
                    }
                }
                
                Thread.sleep(1000);
            }
        }

        private void scheduleConfigurationBuild(PrintStream logger, MatrixConfiguration c) {
            logger.println(Messages.MatrixBuild_Triggering(HyperlinkNote.encodeTo('/'+ c.getUrl(),c.getDisplayName())));
            c.scheduleBuild(getAction(ParametersAction.class), new UpstreamCause(MatrixBuild.this));
        }

        public void post2(BuildListener listener) throws Exception {
            for (MatrixAggregator a : aggregators)
                a.endBuild();
        }
    }

    private <T> TreeSet<T> createTreeSet(Collection<T> items, Comparator<T> sorter) {
        TreeSet<T> r = new TreeSet<T>(sorter);
        r.addAll(items);
        return r;
    }

    /**
     * A private exception to help maintain the correct control flow after extracting the 'waitForCompletion' method
     */
    private static class AggregatorFailureException extends Exception {}

}
