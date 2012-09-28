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
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.Run;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.logging.Logger;

import static java.util.logging.Level.*;

/**
 * Deletes old builds.
 * 
 * Historically this is called LogRotator, though it deletes the whole build including all artifacts.
 * 
 * Since 1.350 it has also the option to keep the build, but delete its recorded artifacts.
 * 
 * @author Kohsuke Kawaguchi
 */
public class LogRotator implements Describable<LogRotator> {

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

    @DataBoundConstructor
    public LogRotator (String logrotate_days, String logrotate_nums, String logrotate_artifact_days, String logrotate_artifact_nums) {
        this (parse(logrotate_days),parse(logrotate_nums),
              parse(logrotate_artifact_days),parse(logrotate_artifact_nums));
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
     *      Use {@link #LogRotator(int, int, int, int)}
     */
    public LogRotator(int daysToKeep, int numToKeep) {
        this(daysToKeep, numToKeep, -1, -1);
    }
    
    public LogRotator(int daysToKeep, int numToKeep, int artifactDaysToKeep, int artifactNumToKeep) {
        this.daysToKeep = daysToKeep;
        this.numToKeep = numToKeep;
        this.artifactDaysToKeep = artifactDaysToKeep;
        this.artifactNumToKeep = artifactNumToKeep;
        
    }

    @SuppressWarnings("rawtypes")
    public void perform(Job<?,?> job) throws IOException, InterruptedException {
        LOGGER.log(FINE,"Running the log rotation for "+job.getFullDisplayName());
        
        // always keep the last successful and the last stable builds
        Run lsb = job.getLastSuccessfulBuild();
        Run lstb = job.getLastStableBuild();

        if(numToKeep!=-1) {
            List<? extends Run<?,?>> builds = job.getBuilds();
            for (Run r : copy(builds.subList(Math.min(builds.size(), numToKeep), builds.size()))) {
                if (r.isKeepLog()) {
                    LOGGER.log(FINER,r.getFullDisplayName()+" is not GC-ed because it's marked as a keeper");
                    continue;
                }
                if (r==lsb) {
                    LOGGER.log(FINER,r.getFullDisplayName()+" is not GC-ed because it's the last successful build");
                    continue;
                }
                if (r==lstb) {
                    LOGGER.log(FINER,r.getFullDisplayName()+" is not GC-ed because it's the last stable build");
                    continue;
                }
                LOGGER.log(FINER,r.getFullDisplayName()+" is to be removed");
                r.delete();
            }
        }

        if(daysToKeep!=-1) {
            Calendar cal = new GregorianCalendar();
            cal.add(Calendar.DAY_OF_YEAR,-daysToKeep);
            for( Run r : copy(job.getBuilds()) ) {
                if (r.isKeepLog()) {
                    LOGGER.log(FINER,r.getFullDisplayName()+" is not GC-ed because it's marked as a keeper");
                    continue;
                }
                if (r==lsb) {
                    LOGGER.log(FINER,r.getFullDisplayName()+" is not GC-ed because it's the last successful build");
                    continue;
                }
                if (r==lstb) {
                    LOGGER.log(FINER,r.getFullDisplayName()+" is not GC-ed because it's the last stable build");
                    continue;
                }
                if (!r.getTimestamp().before(cal)) {
                    LOGGER.log(FINER,r.getFullDisplayName()+" is not GC-ed because it's still new");
                    continue;
                }
                LOGGER.log(FINER,r.getFullDisplayName()+" is to be removed");
                r.delete();
            }
        }

        if(artifactNumToKeep!=null && artifactNumToKeep!=-1) {
            List<? extends Run<?,?>> builds = job.getBuilds();
            for (Run r : copy(builds.subList(Math.min(builds.size(), artifactNumToKeep), builds.size()))) {
                if (r.isKeepLog()) {
                    LOGGER.log(FINER,r.getFullDisplayName()+" is not purged of artifacts because it's marked as a keeper");
                    continue;
                }
                if (r==lsb) {
                    LOGGER.log(FINER,r.getFullDisplayName()+" is not purged of artifacts because it's the last successful build");
                    continue;
                }
                if (r==lstb) {
                    LOGGER.log(FINER,r.getFullDisplayName()+" is not purged of artifacts because it's the last stable build");
                    continue;
                }
                r.deleteArtifacts();
            }
        }

        if(artifactDaysToKeep!=null && artifactDaysToKeep!=-1) {
            Calendar cal = new GregorianCalendar();
            cal.add(Calendar.DAY_OF_YEAR,-artifactDaysToKeep);
            for( Run r : copy(job.getBuilds())) {
                if (r.isKeepLog()) {
                    LOGGER.log(FINER,r.getFullDisplayName()+" is not purged of artifacts because it's marked as a keeper");
                    continue;
                }
                if (r==lsb) {
                    LOGGER.log(FINER,r.getFullDisplayName()+" is not purged of artifacts because it's the last successful build");
                    continue;
                }
                if (r==lstb) {
                    LOGGER.log(FINER,r.getFullDisplayName()+" is not purged of artifacts because it's the last stable build");
                    continue;
                }
                if (!r.getTimestamp().before(cal)) {
                    LOGGER.log(FINER,r.getFullDisplayName()+" is not purged of artifacts because it's still new");
                    continue;
                }
                r.deleteArtifacts();
            }
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

    private int unbox(Integer i) {
        return i==null ? -1: i;
    }

    private String toString(Integer i) {
        if (i==null || i==-1)   return "";
        return String.valueOf(i);
    }


    public LRDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    public static final LRDescriptor DESCRIPTOR = new LRDescriptor();

    public static final class LRDescriptor extends Descriptor<LogRotator> {
        public String getDisplayName() {
            return "Log Rotation";
        }
    }

    private static final Logger LOGGER = Logger.getLogger(LogRotator.class.getName());
}
