/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Martin Eigenbrodt
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
package hudson.tasks;

import com.google.common.collect.Lists;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.Run;
import jenkins.model.BuildDiscarder;
import jenkins.model.BuildDiscarderDescriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINER;

/**
 * Default implementation of {@link BuildDiscarder}.
 *
 * For historical reason, this is called LogRotator, but it does not rotate logs :-)
 * 
 * Since 1.350 it has also the option to keep the build, but delete its recorded artifacts.
 * 
 * @author Kohsuke Kawaguchi
 */
public class LogRotator extends BuildDiscarder {

    /**
     * If not -1, history is only kept up to this days.
     */
    private final int daysToKeep;

    /**
     * If not -1, only this number of build logs are kept.
     */
    private final int numToKeep;

    /**
     * If not -1 nor null, artifacts are only kept up to this days.
     * Null handling is necessary to remain data compatible with old versions.
     * @since 1.350
     */
    private final Integer artifactDaysToKeep;

    /**
     * If not -1 nor null, only this number of builds have their artifacts kept.
     * Null handling is necessary to remain data compatible with old versions.
     * @since 1.350
     */
    private final Integer artifactNumToKeep;

    /**
     * If not -1 nor null, build logs are only kept up to this days.
     * Null handling is necessary to remain data compatible with old versions.
     * @since FIXME
     */
    private final Integer logDaysToKeep;

    /**
     * If not -1, only this number of build logs are kept.
     * Null handling is necessary to remain data compatible with old versions.
     * @since FIXME
     */
    private final Integer logNumToKeep;

    @DataBoundConstructor
    public LogRotator (String daysToKeepStr, String numToKeepStr, String artifactDaysToKeepStr, String artifactNumToKeepStr, String logDaysToKeepStr, String logNumToKeepStr) {
        this (parse(daysToKeepStr),parse(numToKeepStr),
              parse(artifactDaysToKeepStr),parse(artifactNumToKeepStr),
              parse(logDaysToKeepStr),parse(logNumToKeepStr));
    }

    public static int parse(String p) {
        if(p==null)     return -1;
        try {
            return Integer.parseInt(p);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * @deprecated since 1.350.
     *      Use {@link #LogRotator(int, int, int, int, int, int)}
     */
    @Deprecated
    public LogRotator(int daysToKeep, int numToKeep) {
        this(daysToKeep, numToKeep, -1, -1, -1, -1);
    }
    /**
     * @deprecated since FIXME
     *      Use {@link #LogRotator(int, int, int, int, int, int)}
     */
    @Deprecated
    public LogRotator(int daysToKeep, int numToKeep, int artifactDaysToKeep, int artifactNumToKeep) {
        this(daysToKeep, numToKeep, artifactDaysToKeep, artifactNumToKeep, -1, -1);
    }

    public LogRotator(int daysToKeep, int numToKeep, int artifactDaysToKeep, int artifactNumToKeep, int logDaysToKeep, int logNumToKeep) {
        this.daysToKeep = daysToKeep;
        this.numToKeep = numToKeep;
        this.artifactDaysToKeep = artifactDaysToKeep;
        this.artifactNumToKeep = artifactNumToKeep;
        this.logDaysToKeep = logDaysToKeep;
        this.logNumToKeep = logNumToKeep;

    }

    @SuppressWarnings("rawtypes")
    public void perform(Job<?,?> job) throws IOException, InterruptedException {
        LOGGER.log(FINE, "Running the log rotation for {0} with numToKeep={1} daysToKeep={2} artifactNumToKeep={3} " +
                "artifactDaysToKeep={4} logNumToKeep={5} logDaysToKeep={6}", new Object[] {job, numToKeep, daysToKeep,
                artifactNumToKeep, artifactDaysToKeep, logNumToKeep, logDaysToKeep});

        performDeletionOf("build", job, numToKeep, daysToKeep, Run::delete);
        performDeletionOf("artifacts", job, artifactNumToKeep, artifactDaysToKeep, Run::deleteArtifacts);
        performDeletionOf("log", job, logNumToKeep, logDaysToKeep, Run::deleteLog);
    }

    /**
     *  Deletes build metadata based on the parameters passed.
     *
     *  Each build or build metadata have different delete functions as specified in {@link Run}
     * @param buildMetaData String indicating whether builds, artifacts, or logs are being deleted (used for logging)
     * @param job the Jenkins job whose builds and/or metadata are to be deleted
     * @param numToKeep the number of the most recent builds to keep
     * @param daysToKeep the number of days to keep the most recent builds up until
     * @param runAction the deletion function passed
     * @throws IOException if fail to delete
     * @since FIXME
     */
    private void performDeletionOf(String buildMetaData, Job<?,?> job, Integer numToKeep, Integer daysToKeep,
            RunAction runAction) throws IOException{
        // always keep the last successful and the last stable builds
        Run lsb = job.getLastSuccessfulBuild();
        Run lstb = job.getLastStableBuild();

        if(numToKeep!=null && numToKeep!=-1) {
            // Note that RunList.size is deprecated, and indeed here we are loading all the builds of the job.
            // However we would need to load the first numToKeep anyway, just to skip over them;
            // and we would need to load the rest anyway, to delete them.
            // (Using RunMap.headMap would not suffice, since we do not know if some recent builds have been deleted for other reasons,
            // so simply subtracting numToKeep from the currently last build number might cause us to delete too many.)
            List<? extends Run<?,?>> builds = job.getBuilds();
            for (Run r : copy(builds.subList(Math.min(builds.size(), numToKeep), builds.size()))) {
                if (shouldKeepRun(r, lsb, lstb)) {
                    continue;
                }
                LOGGER.log(FINE, "{0}'s {1} to be removed", new Object[] {r, buildMetaData});
                runAction.call(r);
            }
        }

        if(daysToKeep!= null && daysToKeep!=-1) {
            Calendar cal = new GregorianCalendar();
            cal.add(Calendar.DAY_OF_YEAR,-daysToKeep);
            Run r = job.getFirstBuild();
            while (r != null) {
                if (tooNew(r, cal)) {
                    break;
                }
                if (!shouldKeepRun(r, lsb, lstb)) {
                    LOGGER.log(FINE, "{0}'s {1} to be removed", new Object[] {r, buildMetaData});
                    runAction.call(r);
                }
                r = r.getNextBuild();
            }
        }
    }

    private boolean shouldKeepRun(Run r, Run lsb, Run lstb) {
        if (r.isKeepLog()) {
            LOGGER.log(FINER, "{0} is not to be removed or purged of artifacts because it’s marked as a keeper", r);
            return true;
        }
        if (r == lsb) {
            LOGGER.log(FINER, "{0} is not to be removed or purged of artifacts because it’s the last successful build", r);
            return true;
        }
        if (r == lstb) {
            LOGGER.log(FINER, "{0} is not to be removed or purged of artifacts because it’s the last stable build", r);
            return true;
        }
        if (r.isBuilding()) {
            LOGGER.log(FINER, "{0} is not to be removed or purged of artifacts because it’s still building", r);
            return true;
        }
        return false;
    }

    private boolean tooNew(Run r, Calendar cal) {
        if (!r.getTimestamp().before(cal)) {
            LOGGER.log(FINER, "{0} is not to be removed or purged of artifacts because it’s still new", r);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Creates a copy since we'll be deleting some entries from them.
     */
    private <R> Collection<R> copy(Iterable<R> src) {
        return Lists.newArrayList(src);
    }

    public int getDaysToKeep() {
        return daysToKeep;
    }

    public int getNumToKeep() {
        return numToKeep;
    }

    public int getArtifactDaysToKeep() {
        return unbox(artifactDaysToKeep);
    }

    public int getArtifactNumToKeep() {
        return unbox(artifactNumToKeep);
    }

    public int getLogDaysToKeep() {
        return unbox(logDaysToKeep);
    }

    public int getLogNumToKeep() {
        return unbox(logNumToKeep);
    }

    public String getDaysToKeepStr() {
        return toString(daysToKeep);
    }

    public String getNumToKeepStr() {
        return toString(numToKeep);
    }

    public String getArtifactDaysToKeepStr() {
        return toString(artifactDaysToKeep);
    }

    public String getArtifactNumToKeepStr() {
        return toString(artifactNumToKeep);
    }

    public String getLogDaysToKeepStr() {
        return toString(logDaysToKeep);
    }

    public String getLogNumToKeepStr() {
        return toString(logNumToKeep);
    }

    private int unbox(Integer i) {
        return i==null ? -1: i;
    }

    private String toString(Integer i) {
        if (i==null || i==-1)   return "";
        return String.valueOf(i);
    }

    /**
     * Interface to allow for lambda functions to run a call on a  given run.
     * @since FIXME
     */
    @FunctionalInterface
    interface RunAction {
        void call(Run<?,?> r) throws IOException;
    }

    @Extension @Symbol("logRotator")
    public static final class LRDescriptor extends BuildDiscarderDescriptor {
        public String getDisplayName() {
            return "Log Rotation";
        }
    }

    private static final Logger LOGGER = Logger.getLogger(LogRotator.class.getName());
}
