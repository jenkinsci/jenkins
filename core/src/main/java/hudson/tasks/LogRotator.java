/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Martin Eigenbrodt
 * Copyright (c) 2019 Intel Corporation
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

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINER;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.Run;
import hudson.util.RunList;
import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.model.BuildDiscarder;
import jenkins.model.BuildDiscarderDescriptor;
import jenkins.util.io.CompositeIOException;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

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

    /** @deprecated Replaced by more generic {@link CompositeIOException}. */
    @Deprecated
    public static class CollatedLogRotatorException extends IOException {
        private static final long serialVersionUID = 5944233808072651101L;

        public final Collection<Exception> collated;

        public CollatedLogRotatorException(String msg, Exception... collated) {
            super(msg);
            if (collated == null || collated.length == 0) {
                this.collated = Collections.emptyList();
            } else {
                this.collated = Arrays.asList(collated);
            }
        }

        public CollatedLogRotatorException(String msg, Collection<Exception> values) {
            super(msg);
            this.collated = values != null ? values : Collections.emptyList();
        }
    }

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
     * If enabled also remove last successful build.
     * @since 2.474
     */
    private boolean removeLastBuild;

    @DataBoundConstructor
    public LogRotator(String daysToKeepStr, String numToKeepStr, String artifactDaysToKeepStr, String artifactNumToKeepStr) {
        this (parse(daysToKeepStr), parse(numToKeepStr),
              parse(artifactDaysToKeepStr), parse(artifactNumToKeepStr));
    }

    public static int parse(String p) {
        if (p == null)     return -1;
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
    @Deprecated
    public LogRotator(int daysToKeep, int numToKeep) {
        this(daysToKeep, numToKeep, -1, -1);
    }

    public LogRotator(int daysToKeep, int numToKeep, int artifactDaysToKeep, int artifactNumToKeep) {
        this.daysToKeep = daysToKeep;
        this.numToKeep = numToKeep;
        this.artifactDaysToKeep = artifactDaysToKeep;
        this.artifactNumToKeep = artifactNumToKeep;

    }

    @DataBoundSetter
    public void setRemoveLastBuild(boolean removeLastBuild) {
        this.removeLastBuild = removeLastBuild;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void perform(Job<?, ?> job) throws IOException, InterruptedException {
        //Exceptions thrown by the deletion submethods are collated and reported
        Map<Run<?, ?>, Set<IOException>> exceptionMap = new HashMap<>();

        LOGGER.log(FINE, "Running the log rotation for {0} with numToKeep={1} daysToKeep={2} artifactNumToKeep={3} artifactDaysToKeep={4}", new Object[] {job, numToKeep, daysToKeep, artifactNumToKeep, artifactDaysToKeep});

        // if configured keep the last successful and the last stable builds
        Run lsb = removeLastBuild ? null : job.getLastSuccessfulBuild();
        Run lstb = removeLastBuild ? null : job.getLastStableBuild();

        if (numToKeep != -1) {
            // Note that RunList.size is deprecated, and indeed here we are loading all the builds of the job.
            // However we would need to load the first numToKeep anyway, just to skip over them;
            // and we would need to load the rest anyway, to delete them.
            // (Using RunMap.headMap would not suffice, since we do not know if some recent builds have been deleted for other reasons,
            // so simply subtracting numToKeep from the currently last build number might cause us to delete too many.)
            RunList<? extends Run<?, ?>> builds = job.getBuilds();
            for (Run r : builds.subList(Math.min(builds.size(), numToKeep), builds.size())) {
                if (shouldKeepRun(r, lsb, lstb)) {
                    continue;
                }
                LOGGER.log(FINE, "{0} is to be removed", r);
                try { r.delete(); }
                catch (IOException ex) { exceptionMap.computeIfAbsent(r, key -> new HashSet<>()).add(ex); }
            }
        }

        if (daysToKeep != -1) {
            Calendar cal = new GregorianCalendar();
            cal.add(Calendar.DAY_OF_YEAR, -daysToKeep);
            Run r = job.getFirstBuild();
            while (r != null) {
                if (tooNew(r, cal)) {
                    break;
                }
                if (!shouldKeepRun(r, lsb, lstb)) {
                    LOGGER.log(FINE, "{0} is to be removed", r);
                    try { r.delete(); }
                    catch (IOException ex) { exceptionMap.computeIfAbsent(r, key -> new HashSet<>()).add(ex); }
                }
                r = r.getNextBuild();
            }
        }

        if (artifactNumToKeep != null && artifactNumToKeep != -1) {
            RunList<? extends Run<?, ?>> builds = job.getBuilds();
            for (Run r : builds.subList(Math.min(builds.size(), artifactNumToKeep), builds.size())) {
                if (shouldKeepRun(r, lsb, lstb)) {
                    continue;
                }
                LOGGER.log(FINE, "{0} is to be purged of artifacts", r);
                try { r.deleteArtifacts(); }
                catch (IOException ex) { exceptionMap.computeIfAbsent(r, key -> new HashSet<>()).add(ex); }
            }
        }

        if (artifactDaysToKeep != null && artifactDaysToKeep != -1) {
            Calendar cal = new GregorianCalendar();
            cal.add(Calendar.DAY_OF_YEAR, -artifactDaysToKeep);
            Run r = job.getFirstBuild();
            while (r != null) {
                if (tooNew(r, cal)) {
                    break;
                }
                if (!shouldKeepRun(r, lsb, lstb)) {
                    LOGGER.log(FINE, "{0} is to be purged of artifacts", r);
                    try { r.deleteArtifacts(); }
                    catch (IOException ex) { exceptionMap.computeIfAbsent(r, key -> new HashSet<>()).add(ex); }
                }
                r = r.getNextBuild();
            }
        }

        if (!exceptionMap.isEmpty()) {
            //Collate all encountered exceptions into a single exception and throw that
            String msg = String.format(
                    "Failed to rotate logs for [%s]",
                    exceptionMap.keySet().stream().map(Object::toString).collect(Collectors.joining(", "))
            );
            throw new CompositeIOException(msg, exceptionMap.values().stream().flatMap(Collection::stream).collect(Collectors.toList()));
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

    public boolean isRemoveLastBuild() {
        return removeLastBuild;
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
        return i == null ? -1 : i;
    }

    private String toString(Integer i) {
        if (i == null || i == -1)   return "";
        return String.valueOf(i);
    }

    @Extension @Symbol("logRotator")
    public static final class LRDescriptor extends BuildDiscarderDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return "Log Rotation";
        }
    }

    private static final Logger LOGGER = Logger.getLogger(LogRotator.class.getName());
}
