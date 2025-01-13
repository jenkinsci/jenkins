/*
 * The MIT License
 *
 * Copyright (c) 2004-2012, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Daniel Dyer, Red Hat, Inc., Tom Huybrechts, Romain Seguy, Yahoo! Inc.,
 * Darek Ostolski, CloudBees, Inc.
 * Copyright (c) 2012, Martin Schroeder, Intel Mobile Communications GmbH
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

package hudson.model;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINER;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;

import com.thoughtworks.xstream.XStream;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.BulkChange;
import hudson.EnvVars;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.FeedAdapter;
import hudson.Functions;
import hudson.Util;
import hudson.XmlFile;
import hudson.cli.declarative.CLIMethod;
import hudson.console.AnnotatedLargeText;
import hudson.console.ConsoleLogFilter;
import hudson.console.ConsoleNote;
import hudson.console.ModelHyperlinkNote;
import hudson.console.PlainTextConsoleOutputStream;
import hudson.model.Descriptor.FormException;
import hudson.model.listeners.RunListener;
import hudson.model.listeners.SaveableListener;
import hudson.model.queue.SubTask;
import hudson.search.SearchIndexBuilder;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Fingerprinter.FingerprintAction;
import hudson.util.FormApply;
import hudson.util.LogTaskListener;
import hudson.util.ProcessTree;
import hudson.util.XStream2;
import io.jenkins.servlet.ServletExceptionWrapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import jenkins.console.ConsoleUrlProvider;
import jenkins.console.WithConsoleUrl;
import jenkins.model.ArtifactManager;
import jenkins.model.ArtifactManagerConfiguration;
import jenkins.model.ArtifactManagerFactory;
import jenkins.model.BuildDiscarder;
import jenkins.model.Detail;
import jenkins.model.DetailFactory;
import jenkins.model.HistoricalBuild;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import jenkins.model.RunAction2;
import jenkins.model.StandardArtifactManager;
import jenkins.model.details.DurationDetail;
import jenkins.model.details.TimestampDetail;
import jenkins.model.lazy.BuildReference;
import jenkins.model.lazy.LazyBuildMixIn;
import jenkins.security.MasterToSlaveCallable;
import jenkins.security.stapler.StaplerNotDispatchable;
import jenkins.util.SystemProperties;
import jenkins.util.VirtualFile;
import jenkins.util.io.OnMaster;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.jelly.XMLOutput;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.verb.POST;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

/**
 * A particular execution of {@link Job}.
 *
 * <p>
 * Custom {@link Run} type is always used in conjunction with
 * a custom {@link Job} type, so there's no separate registration
 * mechanism for custom {@link Run} types.
 *
 * @author Kohsuke Kawaguchi
 * @see RunListener
 */
@ExportedBean
public abstract class Run<JobT extends Job<JobT, RunT>, RunT extends Run<JobT, RunT>>
        extends Actionable implements ExtensionPoint, Comparable<RunT>, AccessControlled, PersistenceRoot, DescriptorByNameOwner, OnMaster, StaplerProxy, HistoricalBuild, WithConsoleUrl {

    /**
     * The original {@link Queue.Item#getId()} has not yet been mapped onto the {@link Run} instance.
     * @since 1.601
     */
    public static final long QUEUE_ID_UNKNOWN = -1;

    protected final transient @NonNull JobT project;

    /**
     * Build number.
     *
     * <p>
     * In earlier versions &lt; 1.24, this number is not unique nor continuous,
     * but going forward, it will, and this really replaces the build id.
     */
    public transient /*final*/ int number;

    /**
     * The original Queue task ID from where this Run instance originated.
     */
    private long queueId = Run.QUEUE_ID_UNKNOWN;

    /**
     * Previous build. Can be null.
     * TODO JENKINS-22052 this is not actually implemented any more
     *
     * External code should use {@link #getPreviousBuild()}
     */
    @Restricted(NoExternalUse.class)
    protected transient volatile RunT previousBuild;

    /**
     * Next build. Can be null.
     *
     * External code should use {@link #getNextBuild()}
     */
    @Restricted(NoExternalUse.class)
    protected transient volatile RunT nextBuild;

    /**
     * Pointer to the next younger build in progress. This data structure is lazily updated,
     * so it may point to the build that's already completed. This pointer is set to 'this'
     * if the computation determines that everything earlier than this build is already completed.
     */
    /* does not compile on JDK 7: private*/ transient volatile RunT previousBuildInProgress;

    /** ID as used for historical build records; otherwise null. */
    private @CheckForNull String id;

    /**
     * When the build is scheduled.
     */
    protected /*final*/ long timestamp;

    /**
     * When the build has started running.
     *
     * For historical reasons, 0 means no value is recorded.
     *
     * @see #getStartTimeInMillis()
     */
    private long startTime;

    /**
     * The build result.
     * This value may change while the state is in {@link Run.State#BUILDING}.
     */
    protected volatile Result result;

    /**
     * Human-readable description which is used on the main build page.
     * It can also be quite long, and it may use markup in a format defined by a {@link hudson.markup.MarkupFormatter}.
     * {@link #getTruncatedDescription()} may be used to retrieve a size-limited description,
     * but it implies some limitations.
     */
    @CheckForNull
    protected volatile String description;

    /**
     * Human-readable name of this build. Can be null.
     * If non-null, this text is displayed instead of "#NNN", which is the default.
     * @since 1.390
     */
    private volatile String displayName;

    /**
     * The current build state.
     */
    private transient volatile State state;

    private enum State {
        /**
         * Build is created/queued but we haven't started building it.
         */
        NOT_STARTED,
        /**
         * Build is in progress.
         */
        BUILDING,
        /**
         * Build is completed now, and the status is determined,
         * but log files are still being updated.
         *
         * The significance of this state is that Jenkins
         * will now see this build as completed. Things like
         * "triggering other builds" requires this as pre-condition.
         * See JENKINS-980.
         */
        POST_PRODUCTION,
        /**
         * Build is completed now, and log file is closed.
         */
        COMPLETED
    }

    /**
     * Number of milli-seconds it took to run this build.
     */
    protected long duration;

    /**
     * Charset in which the log file is written.
     * For compatibility reason, this field may be null.
     * For persistence, this field is string and not {@link Charset}.
     *
     * @see #getCharset()
     * @since 1.257
     */
    protected String charset;

    /**
     * Keeps this build.
     */
    private boolean keepLog;

    /**
     * If the build is in progress, remember {@link RunExecution} that's running it.
     * This field is not persisted.
     */
    private transient volatile RunExecution runner;

    /**
     * Artifact manager associated with this build, if any.
     * @since 1.532
     */
    private @CheckForNull ArtifactManager artifactManager;

    /**
     * If the build is pending delete.
     */
    private transient boolean isPendingDelete;

    /**
     * Creates a new {@link Run}.
     * @param job Owner job
     * @see LazyBuildMixIn#newBuild
     */
    protected Run(@NonNull JobT job) throws IOException {
        this(job, System.currentTimeMillis());
        this.number = project.assignBuildNumber();
        LOGGER.log(FINER, "new {0} @{1}", new Object[] {this, hashCode()});
    }

    /**
     * Constructor for creating a {@link Run} object in
     * an arbitrary state.
     * {@link #number} must be set manually.
     * <p>May be used in a {@link SubTask#createExecutable} (instead of calling {@link LazyBuildMixIn#newBuild}).
     * For example, {@code MatrixConfiguration.newBuild} does this
     * so that the {@link #timestamp} as well as {@link #number} are shared with the parent build.
     */
    protected Run(@NonNull JobT job, @NonNull Calendar timestamp) {
        this(job, timestamp.getTimeInMillis());
    }

    /** @see #Run(Job, Calendar) */
    protected Run(@NonNull JobT job, long timestamp) {
        this.project = job;
        this.timestamp = timestamp;
        this.state = State.NOT_STARTED;
    }

    /**
     * Loads a run from a log file.
     * @see LazyBuildMixIn#loadBuild
     */
    protected Run(@NonNull JobT project, @NonNull File buildDir) throws IOException {
        this.project = project;
        this.previousBuildInProgress = _this(); // loaded builds are always completed
        number = Integer.parseInt(buildDir.getName());
        reload();
    }

    /**
     * Reloads the build record from disk.
     *
     * @since 1.410
     */
    public void reload() throws IOException {
        this.state = State.COMPLETED;
        this.result = Result.ABORTED;  // defensive measure. value should be overwritten by unmarshal, but just in case the saved data is inconsistent
        getDataFile().unmarshal(this); // load the rest of the data

        if (state == State.COMPLETED) {
            LOGGER.log(FINER, "reload {0} @{1}", new Object[] {this, hashCode()});
        } else {
            LOGGER.log(WARNING, "reload {0} @{1} with anomalous state {2}", new Object[] {this, hashCode(), state});
        }

        // not calling onLoad upon reload. partly because we don't want to call that from Run constructor,
        // and partly because some existing use of onLoad isn't assuming that it can be invoked multiple times.
    }

    /**
     * Called after the build is loaded and the object is added to the build list.
     */
    @SuppressWarnings("deprecation")
    protected void onLoad() {
        for (Action a : getAllActions()) {
            if (a instanceof RunAction2) {
                try {
                    ((RunAction2) a).onLoad(this);
                } catch (RuntimeException x) {
                    LOGGER.log(WARNING, "failed to load " + a + " from " + getDataFile(), x);
                    removeAction(a); // if possible; might be in an inconsistent state
                }
            } else if (a instanceof RunAction) {
                ((RunAction) a).onLoad();
            }
        }
        if (artifactManager != null) {
            artifactManager.onLoad(this);
        }
    }

    /**
     * Return all transient actions associated with this build.
     *
     * @return the list can be empty but never null. read only.
     * @deprecated Use {@link #getAllActions} instead.
     */
    @Deprecated
    public List<Action> getTransientActions() {
        List<Action> actions = new ArrayList<>();
        for (TransientBuildActionFactory factory : TransientBuildActionFactory.all()) {
            for (Action created : factory.createFor(this)) {
                if (created == null) {
                    LOGGER.log(WARNING, "null action added by {0}", factory);
                    continue;
                }
                actions.add(created);
            }
        }
        return Collections.unmodifiableList(actions);
    }

    /**
     * {@inheritDoc}
     * A {@link RunAction2} is handled specially.
     */
    @SuppressWarnings("deprecation")
    @Override
    public void addAction(@NonNull Action a) {
        super.addAction(a);
        if (a instanceof RunAction2) {
            ((RunAction2) a).onAttached(this);
        } else if (a instanceof RunAction) {
            ((RunAction) a).onAttached(this);
        }
    }

    /**
     * Obtains 'this' in a more type safe signature.
     */
    @SuppressWarnings("unchecked")
    protected @NonNull RunT _this() {
        return (RunT) this;
    }

    /**
     * Ordering based on build numbers.
     * If numbers are equal order based on names of parent projects.
     */
    @Override
    public int compareTo(@NonNull RunT that) {
        final int res = this.number - that.number;
        if (res == 0)
            return this.getParent().getFullName().compareTo(that.getParent().getFullName());

        return res;
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.601
     */
    @Exported
    @Override
    public long getQueueId() {
        return queueId;
    }

    /**
     * Set the queue item ID.
     * <p>
     * Mapped from the {@link Queue.Item#getId()}.
     * @param queueId The queue item ID.
     */
    @Restricted(NoExternalUse.class)
    public void setQueueId(long queueId) {
        this.queueId = queueId;
    }

    @Exported
    @Override
    public @CheckForNull Result getResult() {
        return result;
    }

    /**
     * Sets the {@link #getResult} of this build.
     * Has no effect when the result is already set and worse than the proposed result.
     * May only be called after the build has started and before it has moved into post-production
     * (normally meaning both {@link #isInProgress} and {@link #isBuilding} are true).
     * @param r the proposed new result
     * @throws IllegalStateException if the build has not yet started, is in post-production, or is complete
     */
    public void setResult(@NonNull Result r) {
        if (state != State.BUILDING) {
            throw new IllegalStateException("cannot change build result while in " + state);
        }

        // result can only get worse
        if (result == null || r.isWorseThan(result)) {
            result = r;
            LOGGER.log(FINE, this + " in " + getRootDir() + ": result is set to " + r, LOGGER.isLoggable(Level.FINER) ? new Exception() : null);
        }
    }

    /**
     * Gets the subset of {@link #getActions()} that consists of {@link BuildBadgeAction}s.
     */
    @Override
    public @NonNull List<BuildBadgeAction> getBadgeActions() {
        List<BuildBadgeAction> r = getActions(BuildBadgeAction.class);
        if (isKeepLog()) {
            r = new ArrayList<>(r);
            r.add(new KeepLogBuildBadge());
        }
        return r;
    }

    @Exported
    @Override
    public boolean isBuilding() {
        return state.compareTo(State.POST_PRODUCTION) < 0;
    }

    /**
     * Determine whether the run is being build right now.
     * @return true if after started and before completed.
     * @since 1.538
     */
    @Exported
    public boolean isInProgress() {
        return state.equals(State.BUILDING) || state.equals(State.POST_PRODUCTION);
    }

    /**
     * Returns true if the log file is still being updated.
     */
    public boolean isLogUpdated() {
        return state.compareTo(State.COMPLETED) < 0;
    }

    /**
     * Gets the {@link Executor} building this job, if it's being built.
     * Otherwise null.
     *
     * This method looks for {@link Executor} who's {@linkplain Executor#getCurrentExecutable() assigned to this build},
     * and because of that this might not be necessarily in sync with the return value of {@link #isBuilding()} &mdash;
     * an executor holds on to {@link Run} some more time even after the build is finished (for example to
     * perform {@linkplain Run.State#POST_PRODUCTION post-production processing}.)
     * @see Executor#of
     */
    @Exported
    public @CheckForNull Executor getExecutor() {
        return this instanceof Queue.Executable ? Executor.of((Queue.Executable) this) : null;
    }

    /**
     * Gets the one off {@link Executor} building this job, if it's being built.
     * Otherwise null.
     * @since 1.433
     */
    public @CheckForNull Executor getOneOffExecutor() {
        for (Computer c : Jenkins.get().getComputers()) {
            for (Executor e : c.getOneOffExecutors()) {
                if (e.getCurrentExecutable() == this)
                    return e;
            }
        }
        return null;
    }

    /**
     * Gets the charset in which the log file is written.
     * @return never null.
     * @since 1.257
     */
    public final @NonNull Charset getCharset() {
        if (charset == null)   return Charset.defaultCharset();
        return Charset.forName(charset);
    }

    /**
     * Returns the {@link Cause}s that triggered a build.
     *
     * <p>
     * If a build sits in the queue for a long time, multiple build requests made during this period
     * are all rolled up into one build, hence this method may return a list.
     *
     * @return
     *      can be empty but never null. read-only.
     * @since 1.321
     */
    public @NonNull List<Cause> getCauses() {
        CauseAction a = getAction(CauseAction.class);
        if (a == null)    return Collections.emptyList();
        return Collections.unmodifiableList(a.getCauses());
    }

    /**
     * Returns a {@link Cause} of a particular type.
     *
     * @since 1.362
     */
    public @CheckForNull <T extends Cause> T getCause(Class<T> type) {
        for (Cause c : getCauses())
            if (type.isInstance(c))
                return type.cast(c);
        return null;
    }

    /**
     * Returns true if this build should be kept and not deleted.
     * (Despite the name, this refers to the entire build, not merely the log file.)
     * This is used as a signal to the {@link BuildDiscarder}.
     */
    @Exported
    public final boolean isKeepLog() {
        return getWhyKeepLog() != null;
    }

    /**
     * If {@link #isKeepLog()} returns true, returns a short, human-readable
     * sentence that explains why it's being kept.
     */
    public @CheckForNull String getWhyKeepLog() {
        if (keepLog)
            return Messages.Run_MarkedExplicitly();
        return null;    // not marked at all
    }

    /**
     * The project this build is for.
     */
    public @NonNull JobT getParent() {
        return project;
    }

    /**
     * {@inheritDoc}
     * @see #getStartTimeInMillis()
     */
    @Exported
    @Override
    public @NonNull Calendar getTimestamp() {
        GregorianCalendar c = new GregorianCalendar();
        c.setTimeInMillis(timestamp);
        return c;
    }

    /**
     * Same as {@link #getTimestamp()} but in a different type.
     */
    public final @NonNull Date getTime() {
        return new Date(timestamp);
    }

    /**
     * Same as {@link #getTimestamp()} but in a different type, that is since the time of the epoch.
     */
    public final long getTimeInMillis() {
        return timestamp;
    }

    /**
     * When the build has started running in an executor.
     *
     * For example, if a build is scheduled 1pm, and stayed in the queue for 1 hour (say, no idle agents),
     * then this method returns 2pm, which is the time the job moved from the queue to the building state.
     *
     * @see #getTimestamp()
     */
    public final long getStartTimeInMillis() {
        if (startTime == 0)   return timestamp;   // fallback: approximate by the queuing time
        return startTime;
    }

    @Exported
    @Override
    @CheckForNull
    public String getDescription() {
        return description;
    }

    /**
     * Gets the string that says how long since this build has started.
     *
     * @return
     *      string like "3 minutes" "1 day" etc.
     */
    public @NonNull String getTimestampString() {
        long duration = new GregorianCalendar().getTimeInMillis() - timestamp;
        return Util.getTimeSpanString(duration);
    }

    /**
     * Returns the timestamp formatted in xs:dateTime.
     */
    public @NonNull String getTimestampString2() {
        return Util.XS_DATETIME_FORMATTER2.format(Instant.ofEpochMilli(timestamp));
    }

    @Override
    public @NonNull String getDurationString() {
        if (hasntStartedYet()) {
            return Messages.Run_NotStartedYet();
        } else if (isBuilding()) {
            return Messages.Run_InProgressDuration(
                    Util.getTimeSpanString(System.currentTimeMillis() - startTime));
        }
        return Util.getTimeSpanString(duration);
    }

    /**
     * Gets the millisecond it took to build.
     */
    @Exported
    public long getDuration() {
        return duration;
    }

    @Override
    public @NonNull BallColor getIconColor() {
        if (!isBuilding()) {
            // already built
            return getResult().color;
        }

        // a new build is in progress
        BallColor baseColor;
        RunT pb = getPreviousBuild();
        if (pb == null)
            baseColor = BallColor.NOTBUILT;
        else
            baseColor = pb.getIconColor();

        return baseColor.anime();
    }

    /**
     * Returns true if the build is still queued and hasn't started yet.
     */
    public boolean hasntStartedYet() {
        return state == State.NOT_STARTED;
    }

    @SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification = "see JENKINS-45892")
    @Override
    public String toString() {
        if (project == null) {
            return "<broken data JENKINS-45892>";
        }
        return project.getFullName() + " #" + number;
    }

    @Exported
    @Override
    public String getFullDisplayName() {
        return project.getFullDisplayName() + ' ' + getDisplayName();
    }

    @Override
    @Exported
    public String getDisplayName() {
        return displayName != null ? displayName : "#" + number;
    }

    public boolean hasCustomDisplayName() {
        return displayName != null;
    }

    /**
     * @param value
     *      Set to null to revert back to the default "#NNN".
     */
    public void setDisplayName(String value) throws IOException {
        checkPermission(UPDATE);
        this.displayName = value;
        save();
    }

    @Exported(visibility = 2)
    @Override
    public int getNumber() {
        return number;
    }

    /**
     * Called by {@link RunMap} to obtain a reference to this run.
     * @return Reference to the build. Never null
     * @see jenkins.model.lazy.LazyBuildMixIn.RunMixIn#createReference
     * @since 1.556
     */
    protected @NonNull BuildReference<RunT> createReference() {
        return new BuildReference<>(getId(), _this());
    }

    /**
     * Called by {@link RunMap} to drop bi-directional links in preparation for
     * deleting a build.
     * @see jenkins.model.lazy.LazyBuildMixIn.RunMixIn#dropLinks
     * @since 1.556
     */
    protected void dropLinks() {
        if (nextBuild != null)
            nextBuild.previousBuild = previousBuild;
        if (previousBuild != null)
            previousBuild.nextBuild = nextBuild;
    }

    /**
     * @see jenkins.model.lazy.LazyBuildMixIn.RunMixIn#getPreviousBuild
     */
    public @CheckForNull RunT getPreviousBuild() {
        return previousBuild;
    }

    /**
     * Gets the most recent {@linkplain #isBuilding() completed} build excluding 'this' Run itself.
     */
    public final @CheckForNull RunT getPreviousCompletedBuild() {
        RunT r = getPreviousBuild();
        while (r != null && r.isBuilding())
            r = r.getPreviousBuild();
        return r;
    }

    /**
     * Obtains the next younger build in progress. It uses a skip-pointer so that we can compute this without
     * O(n) computation time. This method also fixes up the skip list as we go, in a way that's concurrency safe.
     *
     * <p>
     * We basically follow the existing skip list, and wherever we find a non-optimal pointer, we remember them
     * in 'fixUp' and update them later.
     */
    public final @CheckForNull RunT getPreviousBuildInProgress() {
        if (previousBuildInProgress == this)   return null;    // the most common case

        List<RunT> fixUp = new ArrayList<>();
        RunT r = _this(); // 'r' is the source of the pointer (so that we can add it to fix up if we find that the target of the pointer is inefficient.)
        RunT answer;
        while (true) {
            RunT n = r.previousBuildInProgress;
            if (n == null) { // no field computed yet.
                n = r.getPreviousBuild();
                fixUp.add(r);
            }
            if (r == n || n == null) {
                // this indicates that we know there's no build in progress beyond this point
                answer = null;
                break;
            }
            if (n.isBuilding()) {
                // we now know 'n' is the target we wanted
                answer = n;
                break;
            }

            fixUp.add(r);   // r contains the stale 'previousBuildInProgress' back pointer
            r = n;
        }

        // fix up so that the next look up will run faster
        for (RunT f : fixUp)
            f.previousBuildInProgress = answer == null ? f : answer;
        return answer;
    }

    /**
     * Returns the last build that was actually built - i.e., skipping any with Result.NOT_BUILT
     */
    public @CheckForNull RunT getPreviousBuiltBuild() {
        RunT r = getPreviousBuild();
        // in certain situations (aborted m2 builds) r.getResult() can still be null, although it should theoretically never happen
        while (r != null && (r.getResult() == null || r.getResult() == Result.NOT_BUILT))
            r = r.getPreviousBuild();
        return r;
    }

    /**
     * Returns the last build that didn't fail before this build.
     */
    public @CheckForNull RunT getPreviousNotFailedBuild() {
        RunT r = getPreviousBuild();
        while (r != null && r.getResult() == Result.FAILURE)
            r = r.getPreviousBuild();
        return r;
    }

    /**
     * Returns the last failed build before this build.
     */
    public @CheckForNull RunT getPreviousFailedBuild() {
        RunT r = getPreviousBuild();
        while (r != null && r.getResult() != Result.FAILURE)
            r = r.getPreviousBuild();
        return r;
    }

    /**
     * Returns the last successful build before this build.
     * @since 1.383
     */
    public @CheckForNull RunT getPreviousSuccessfulBuild() {
        RunT r = getPreviousBuild();
        while (r != null && r.getResult() != Result.SUCCESS)
            r = r.getPreviousBuild();
        return r;
    }

    /**
     * Returns the last {@code numberOfBuilds} builds with a build result ≥ {@code threshold}.
     *
     * @param numberOfBuilds the desired number of builds
     * @param threshold the build result threshold
     * @return a list with the builds (youngest build first).
     *   May be smaller than 'numberOfBuilds' or even empty
     *   if not enough builds satisfying the threshold have been found. Never null.
     * @since 1.383
     */
    public @NonNull List<RunT> getPreviousBuildsOverThreshold(int numberOfBuilds, @NonNull Result threshold) {
        RunT r = getPreviousBuild();
        if (r != null) {
            return r.getBuildsOverThreshold(numberOfBuilds, threshold);
        }
        return new ArrayList<>(numberOfBuilds);
    }

    /**
     * Returns the last {@code numberOfBuilds} builds with a build result ≥ {@code threshold}.
     *
     * @param numberOfBuilds the desired number of builds
     * @param threshold the build result threshold
     * @return a list with the builds (youngest build first).
     *   May be smaller than 'numberOfBuilds' or even empty
     *   if not enough builds satisfying the threshold have been found. Never null.
     * @since 2.202
     */
    protected @NonNull List<RunT> getBuildsOverThreshold(int numberOfBuilds, @NonNull Result threshold) {
        List<RunT> builds = new ArrayList<>(numberOfBuilds);

        RunT r = _this();
        while (r != null && builds.size() < numberOfBuilds) {
            if (!r.isBuilding() &&
                 r.getResult() != null && r.getResult().isBetterOrEqualTo(threshold)) {
                builds.add(r);
            }
            r = r.getPreviousBuild();
        }

        return builds;
    }

    /**
     * @see jenkins.model.lazy.LazyBuildMixIn.RunMixIn#getNextBuild
     */
    public @CheckForNull RunT getNextBuild() {
        return nextBuild;
    }


    @Override
    public @NonNull String getUrl() {

        // RUN may be accessed using permalinks, as "/lastSuccessful" or other, so try to retrieve this base URL
        // looking for "this" in the current request ancestors
        // @see also {@link AbstractItem#getUrl}
        StaplerRequest2 req = Stapler.getCurrentRequest2();
        if (req != null) {
            String seed = Functions.getNearestAncestorUrl(req, this);
            if (seed != null) {
                // trim off the context path portion and leading '/', but add trailing '/'
                return seed.substring(req.getContextPath().length() + 1) + '/';
            }
        }

        return project.getUrl() + getNumber() + '/';
    }

    /**
     * @see ConsoleUrlProvider#consoleUrlOf
     */
    @Override
    public String getConsoleUrl() {
        return ConsoleUrlProvider.consoleUrlOf(this);
    }

    /**
     * Obtains the absolute URL to this build.
     *
     * @deprecated
     *      This method shall <b>NEVER</b> be used during HTML page rendering, as it's too easy for
     *      misconfiguration to break this value, with network set up like Apache reverse proxy.
     *      This method is only intended for the remote API clients who cannot resolve relative references.
     */
    @Exported(visibility = 2, name = "url")
    @Deprecated
    public final @NonNull String getAbsoluteUrl() {
        return project.getAbsoluteUrl() + getNumber() + '/';
    }

    @Override
    public final @NonNull String getSearchUrl() {
        return getNumber() + "/";
    }

    /**
     * Unique ID of this build.
     * Usually the decimal form of {@link #number}, but may be a formatted timestamp for historical builds.
     */
    @Exported
    public @NonNull String getId() {
        return id != null ? id : Integer.toString(number);
    }

    /**
     * Get the root directory of this {@link Run} on the controller.
     * Files related to this {@link Run} should be stored below this directory.
     * @return Root directory of this {@link Run} on the controller. Never null
     */
    @Override
    public @NonNull File getRootDir() {
        return new File(project.getBuildDir(), Integer.toString(number));
    }

    @Override
    public List<ParameterValue> getParameterValues() {
        ParametersAction a = getAction(ParametersAction.class);
        return a != null ? a.getParameters() : List.of();
    }

    /**
     * Gets an object responsible for storing and retrieving build artifacts.
     * If {@link #pickArtifactManager} has previously been called on this build,
     * and a nondefault manager selected, that will be returned.
     * Otherwise (including if we are loading a historical build created prior to this feature) {@link StandardArtifactManager} is used.
     * <p>This method should be used when existing artifacts are to be loaded, displayed, or removed.
     * If adding artifacts, use {@link #pickArtifactManager} instead.
     * @return an appropriate artifact manager
     * @since 1.532
     */
    public final @NonNull ArtifactManager getArtifactManager() {
        return artifactManager != null ? artifactManager : new StandardArtifactManager(this);
    }

    /**
     * Selects an object responsible for storing and retrieving build artifacts.
     * The first time this is called on a running build, {@link ArtifactManagerConfiguration} is checked
     * to see if one will handle this build.
     * If so, that manager is saved in the build and it will be used henceforth.
     * If no manager claimed the build, {@link StandardArtifactManager} is used.
     * <p>This method should be used when a build step expects to archive some artifacts.
     * If only displaying existing artifacts, use {@link #getArtifactManager} instead.
     * @return an appropriate artifact manager
     * @throws IOException if a custom manager was selected but the selection could not be saved
     * @since 1.532
     */
    public final synchronized @NonNull ArtifactManager pickArtifactManager() throws IOException {
        if (artifactManager != null) {
            return artifactManager;
        } else {
            for (ArtifactManagerFactory f : ArtifactManagerConfiguration.get().getArtifactManagerFactories()) {
                ArtifactManager mgr = f.managerFor(this);
                if (mgr != null) {
                    artifactManager = mgr;
                    save();
                    return mgr;
                }
            }
            return new StandardArtifactManager(this);
        }
    }

    /**
     * Gets the directory where the artifacts are archived.
     * @deprecated Should only be used from {@link StandardArtifactManager} or subclasses.
     */
    @Deprecated
    public File getArtifactsDir() {
        return new File(getRootDir(), "archive");
    }

    /**
     * Gets the artifacts (relative to {@link #getArtifactsDir()}.
     * @return The list can be empty but never null
     */
    @Exported
    public @NonNull List<Artifact> getArtifacts() {
        return getArtifactsUpTo(Integer.MAX_VALUE);
    }

    /**
     * Gets the first N artifacts.
     * @return The list can be empty but never null
     */
    public @NonNull List<Artifact> getArtifactsUpTo(int artifactsNumber) {
        SerializableArtifactList sal;
        VirtualFile root = getArtifactManager().root();
        try {
            sal = root.run(new AddArtifacts(root, artifactsNumber));
        } catch (IOException x) {
            LOGGER.log(Level.WARNING, null, x);
            sal = new SerializableArtifactList();
        }
        ArtifactList r = new ArtifactList();
        r.updateFrom(sal);
        r.computeDisplayName();
        return r;
    }

    /**
     * Check if the {@link Run} contains artifacts.
     * The strange method name is so that we can access it from EL.
     * @return true if this run has any artifacts
     */
    public boolean getHasArtifacts() {
        return !getArtifactsUpTo(1).isEmpty();
    }

    private static final class AddArtifacts extends MasterToSlaveCallable<SerializableArtifactList, IOException> {
        private static final long serialVersionUID = 1L;
        private final VirtualFile root;
        private final int artifactsNumber;

        AddArtifacts(VirtualFile root, int artifactsNumber) {
            this.root = root;
            this.artifactsNumber = artifactsNumber;
        }

        @Override
        public SerializableArtifactList call() throws IOException {
            SerializableArtifactList sal = new SerializableArtifactList();
            addArtifacts(root, "", "", sal, null, artifactsNumber);
            return sal;
        }
    }

    private static int addArtifacts(@NonNull VirtualFile dir,
            @NonNull String path, @NonNull String pathHref,
            @NonNull SerializableArtifactList r, @CheckForNull SerializableArtifact parent, int upTo) throws IOException {
        VirtualFile[] kids = dir.list();
        Arrays.sort(kids);

        int n = 0;
        for (VirtualFile sub : kids) {
            String child = sub.getName();
            String childPath = path + child;
            String childHref = pathHref + Util.rawEncode(child);
            String length = sub.isFile() ? String.valueOf(sub.length()) : "";
            boolean collapsed = kids.length == 1 && parent != null;
            SerializableArtifact a;
            if (collapsed) {
                // Collapse single items into parent node where possible:
                a = new SerializableArtifact(parent.name + '/' + child, childPath,
                                 sub.isDirectory() ? null : childHref, length,
                                 parent.treeNodeId);
                r.tree.put(a, r.tree.remove(parent));
            } else {
                // Use null href for a directory:
                a = new SerializableArtifact(child, childPath,
                                 sub.isDirectory() ? null : childHref, length,
                                 "n" + ++r.idSeq);
                r.tree.put(a, parent != null ? parent.treeNodeId : null);
            }
            if (sub.isDirectory()) {
                n += addArtifacts(sub, childPath + '/', childHref + '/', r, a, upTo - n);
                if (n >= upTo) break;
            } else {
                // Don't store collapsed path in ArrayList (for correct data in external API)
                r.add(collapsed ? new SerializableArtifact(child, a.relativePath, a.href, length, a.treeNodeId) : a);
                if (++n >= upTo) break;
            }
        }
        return n;
    }

    /**
     * Maximum number of artifacts to list before using switching to the tree view.
     */
    public static final int LIST_CUTOFF = Integer.parseInt(SystemProperties.getString("hudson.model.Run.ArtifactList.listCutoff", "20"));

    // ..and then "too many"

    /** {@link Run.Artifact} without the implicit link to {@link Run} */
    private static final class SerializableArtifact implements Serializable {
        private static final long serialVersionUID = 1L;
        final String name;
        final String relativePath;
        final String href;
        final String length;
        final String treeNodeId;

        SerializableArtifact(String name, String relativePath, String href, String length, String treeNodeId) {
            this.name = name;
            this.relativePath = relativePath;
            this.href = href;
            this.length = length;
            this.treeNodeId = treeNodeId;
        }
    }

    /** {@link Run.ArtifactList} without the implicit link to {@link Run} */
    private static final class SerializableArtifactList extends ArrayList<SerializableArtifact> {
        private static final long serialVersionUID = 1L;
        private LinkedHashMap<SerializableArtifact, String> tree = new LinkedHashMap<>();
        private int idSeq = 0;
    }

    public final class ArtifactList extends ArrayList<Artifact> {
        private static final long serialVersionUID = 1L;
        /**
         * Map of Artifact to treeNodeId of parent node in tree view.
         * Contains Artifact objects for directories and files (the ArrayList contains only files).
         */
        private LinkedHashMap<Artifact, String> tree = new LinkedHashMap<>();

        void updateFrom(SerializableArtifactList clone) {
            Map<String, Artifact> artifacts = new HashMap<>(); // need to share objects between tree and list, since computeDisplayName mutates displayPath
            for (SerializableArtifact sa : clone) {
                Artifact a = new Artifact(sa);
                artifacts.put(a.relativePath, a);
                add(a);
            }
            tree = new LinkedHashMap<>();
            for (Map.Entry<SerializableArtifact, String> entry : clone.tree.entrySet()) {
                SerializableArtifact sa = entry.getKey();
                Artifact a = artifacts.get(sa.relativePath);
                if (a == null) {
                    a = new Artifact(sa);
                }
                tree.put(a, entry.getValue());
            }
        }

        public Map<Artifact, String> getTree() {
            return tree;
        }

        public void computeDisplayName() {
            if (size() > LIST_CUTOFF)   return; // we are not going to display file names, so no point in computing this

            int maxDepth = 0;
            int[] len = new int[size()];
            String[][] tokens = new String[size()][];
            for (int i = 0; i < tokens.length; i++) {
                tokens[i] = get(i).relativePath.split("[\\\\/]+");
                maxDepth = Math.max(maxDepth, tokens[i].length);
                len[i] = 1;
            }

            boolean collision;
            int depth = 0;
            do {
                collision = false;
                Map<String, Integer/*index*/> names = new HashMap<>();
                for (int i = 0; i < tokens.length; i++) {
                    String[] token = tokens[i];
                    String displayName = combineLast(token, len[i]);
                    Integer j = names.put(displayName, i);
                    if (j != null) {
                        collision = true;
                        if (j >= 0)
                            len[j]++;
                        len[i]++;
                        names.put(displayName, -1);  // occupy this name but don't let len[i] incremented with additional collisions
                    }
                }
            } while (collision && depth++ < maxDepth);

            for (int i = 0; i < tokens.length; i++)
                get(i).displayPath = combineLast(tokens[i], len[i]);
        }

        /**
         * Combines last N token into the "a/b/c" form.
         */
        private String combineLast(String[] token, int n) {
            StringBuilder buf = new StringBuilder();
            for (int i = Math.max(0, token.length - n); i < token.length; i++) {
                if (!buf.isEmpty())  buf.append('/');
                buf.append(token[i]);
            }
            return buf.toString();
        }
    }

    /**
     * A build artifact.
     */
    @ExportedBean
    public class Artifact {
        /**
         * Relative path name from artifacts root.
         */
        @Exported(visibility = 3)
        public final String relativePath;

        /**
         * Truncated form of {@link #relativePath} just enough
         * to disambiguate {@link Artifact}s.
         */
        /*package*/ String displayPath;

        /**
         * The filename of the artifact.
         * (though when directories with single items are collapsed for tree view, name may
         *  include multiple path components, like "dist/pkg/mypkg")
         */
        private String name;

        /**
         * Properly encoded relativePath for use in URLs.  This field is null for directories.
         */
        private String href;

        /**
         * Id of this node for use in tree view.
         */
        private String treeNodeId;

        /**
         * length of this artifact for files.
         */
        private String length;

        Artifact(SerializableArtifact clone) {
            this(clone.name, clone.relativePath, clone.href, clone.length, clone.treeNodeId);
        }

        /*package for test*/ Artifact(String name, String relativePath, String href, String len, String treeNodeId) {
            this.name = name;
            this.relativePath = relativePath;
            this.href = href;
            this.treeNodeId = treeNodeId;
            this.length = len;
        }

        /**
         * Gets the artifact file.
         * @deprecated May not be meaningful with custom artifact managers. Use {@link ArtifactManager#root} plus {@link VirtualFile#child} with {@link #relativePath} instead.
         */
        @Deprecated
        public @NonNull File getFile() {
            return new File(getArtifactsDir(), relativePath);
        }

        /**
         * Returns just the file name portion, without the path.
         */
        @Exported(visibility = 3)
        public String getFileName() {
            return name;
        }

        @Exported(visibility = 3)
        public String getDisplayPath() {
            return displayPath;
        }

        public String getHref() {
            return href;
        }

        public String getLength() {
            return length;
        }

        public long getFileSize() {
            try {
                return Long.decode(length);
            }
            catch (NumberFormatException e) {
                LOGGER.log(FINE, "Cannot determine file size of the artifact {0}. The length {1} is not a valid long value", new Object[] {this, length});
                return 0;
            }
        }

        public String getTreeNodeId() {
            return treeNodeId;
        }

        @Override
        public String toString() {
            return relativePath;
        }
    }

    /**
     * get the fingerprints associated with this build
     *
     * @return The fingerprints
     */
    @NonNull
    @Exported(name = "fingerprint", inline = true, visibility = -1)
    public Collection<Fingerprint> getBuildFingerprints() {
        FingerprintAction fingerprintAction = getAction(FingerprintAction.class);
        if (fingerprintAction != null) {
            return fingerprintAction.getFingerprints().values();
        }
        return Collections.emptyList();
    }

    /**
     * Returns the log file.
     * @return The file may reference both uncompressed or compressed logs
     * @deprecated Assumes file-based storage of the log, which is not necessarily the case for Pipelines after JEP-210.
     *     Use other methods giving various kinds of streams such as {@link Run#getLogReader()}, {@link Run#getLogInputStream()}, or {@link Run#getLogText()}.
     */
    @Deprecated
    public @NonNull File getLogFile() {
        File rawF = new File(getRootDir(), "log");
        if (rawF.isFile()) {
            return rawF;
        }
        File gzF = new File(getRootDir(), "log.gz");
        if (gzF.isFile()) {
            return gzF;
        }
        //If both fail, return the standard, uncompressed log file
        return rawF;
    }

    /**
     * Returns an input stream that reads from the log file.
     * It will use a gzip-compressed log file (log.gz) if that exists.
     *
     * @return An input stream from the log file.
     *   If the log file does not exist, the error message will be returned to the output.
     * @since 1.349
     */
    public @NonNull InputStream getLogInputStream() throws IOException {
        File logFile = getLogFile();

        if (logFile.exists()) {
            // Checking if a ".gz" file was return
            try {
                InputStream fis = Files.newInputStream(logFile.toPath());
                if (logFile.getName().endsWith(".gz")) {
                    return new GZIPInputStream(fis);
                } else {
                    return fis;
                }
            } catch (InvalidPathException e) {
                throw new IOException(e);
            }
        }

        String message = "No such file: " + logFile;
        return new ByteArrayInputStream(charset != null ? message.getBytes(charset) : message.getBytes(Charset.defaultCharset()));
    }

    public @NonNull Reader getLogReader() throws IOException {
        if (charset == null)  return new InputStreamReader(getLogInputStream(), Charset.defaultCharset());
        else                return new InputStreamReader(getLogInputStream(), charset);
    }

    /**
     * Used from {@code console.jelly} to write annotated log to the given output.
     *
     * @since 1.349
     */
    @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED", justification = "method signature does not permit plumbing through the return value")
    public void writeLogTo(long offset, @NonNull XMLOutput out) throws IOException {
        long start = offset;
        if (offset > 0) {
            try (BufferedInputStream bufferedInputStream = new BufferedInputStream(getLogInputStream())) {
                if (offset == bufferedInputStream.skip(offset)) {
                    int r;
                    do {
                        r = bufferedInputStream.read();
                        start = r == -1 ? 0 : start + 1;
                    } while (r != -1 && r != '\n');
                }
            }
        }
        getLogText().writeHtmlTo(start, out.asWriter());
    }

    /**
     * Writes the complete log from the start to finish to the {@link OutputStream}.
     *
     * If someone is still writing to the log, this method will not return until the whole log
     * file gets written out.
     * <p>
     * The method does not close the {@link OutputStream}.
     */
    public void writeWholeLogTo(@NonNull OutputStream out) throws IOException, InterruptedException {
        long pos = 0;
        AnnotatedLargeText logText;
        logText = getLogText();
        pos = logText.writeLogTo(pos, out);

        while (!logText.isComplete()) {
            // Instead of us hitting the log file as many times as possible, instead we get the information once every
            // second to avoid CPU usage getting very high.
            Thread.sleep(1000);
            logText = getLogText();
            pos = logText.writeLogTo(pos, out);
        }
    }

    /**
     * Used to URL-bind {@link AnnotatedLargeText}.
     * @return A {@link Run} log with annotations
     */
    public @NonNull AnnotatedLargeText getLogText() {
        return new AnnotatedLargeText(getLogFile(), getCharset(), !isLogUpdated(), this);
    }

    @Override
    protected @NonNull SearchIndexBuilder makeSearchIndex() {
        SearchIndexBuilder builder = super.makeSearchIndex()
                .add("console")
                .add("changes");
        for (Action a : getAllActions()) {
            if (a.getIconFileName() != null)
                builder.add(a.getUrlName());
        }
        return builder;
    }

    public @NonNull Api getApi() {
        return new Api(this);
    }

    @NonNull
    @Override
    public ACL getACL() {
        // for now, don't maintain ACL per run, and do it at project level
        return getParent().getACL();
    }

    /**
     * Deletes this build's artifacts.
     *
     * @throws IOException
     *      if we fail to delete.
     *
     * @since 1.350
     */
    public synchronized void deleteArtifacts() throws IOException {
        try {
            getArtifactManager().delete();
        } catch (InterruptedException x) {
            throw new IOException(x);
        }
    }

    /**
     * Deletes this build and its entire log
     *
     * @throws IOException
     *      if we fail to delete.
     */
    public void delete() throws IOException {
        if (isLogUpdated()) {
            throw new IOException("Unable to delete " + this + " because it is still running");
        }
        synchronized (this) {
            // Avoid concurrent delete. See https://issues.jenkins.io/browse/JENKINS-61687
            if (isPendingDelete) {
                return;
            }

            isPendingDelete = true;
        }

        File rootDir = getRootDir();
        if (!rootDir.isDirectory()) {
            //No root directory found to delete. Somebody seems to have nuked
            //it externally. Logging a warning before dropping the build
            LOGGER.warning(String.format(
                    "%s: %s looks to have already been deleted, assuming build dir was already cleaned up",
                    this, rootDir
            ));
            //Still firing the delete listeners; just no need to clean up rootDir
            RunListener.fireDeleted(this);
            SaveableListener.fireOnDeleted(this, getDataFile());
            synchronized (this) { // avoid holding a lock while calling plugin impls of onDeleted
                removeRunFromParent();
            }
            return;
        }

        //The root dir exists and is a directory that needs to be purged
        RunListener.fireDeleted(this);
        SaveableListener.fireOnDeleted(this, getDataFile());

        if (artifactManager != null) {
            deleteArtifacts();
        } // for StandardArtifactManager, deleting the whole build dir suffices

        synchronized (this) { // avoid holding a lock while calling plugin impls of onDeleted
            File tmp = new File(rootDir.getParentFile(), '.' + rootDir.getName());

            if (tmp.exists()) {
                Util.deleteRecursive(tmp);
            }
            try {
                Files.move(
                        Util.fileToPath(rootDir),
                        Util.fileToPath(tmp),
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (UnsupportedOperationException | SecurityException ex) {
                throw new IOException(rootDir + " is in use", ex);
            }

            Util.deleteRecursive(tmp);
            // some user reported that they see some left-over .xyz files in the workspace,
            // so just to make sure we've really deleted it, schedule the deletion on VM exit, too.
            if (tmp.exists()) {
                tmp.deleteOnExit();
            }
            LOGGER.log(FINE, "{0}: {1} successfully deleted", new Object[] {this, rootDir});
            removeRunFromParent();
        }
    }

    @SuppressWarnings("unchecked") // seems this is too clever for Java's type system?
    private void removeRunFromParent() {
        getParent().removeRun((RunT) this);
    }


    /**
     * @see CheckPoint#report()
     */
    /*package*/ static void reportCheckpoint(@NonNull CheckPoint id) {
        Run<?, ?>.RunExecution exec = RunnerStack.INSTANCE.peek();
        if (exec == null) {
            return;
        }
        exec.checkpoints.report(id);
    }

    /**
     * @see CheckPoint#block()
     */
    /*package*/ static void waitForCheckpoint(@NonNull CheckPoint id, @CheckForNull BuildListener listener, @CheckForNull String waiter) throws InterruptedException {
        while (true) {
            Run<?, ?>.RunExecution exec = RunnerStack.INSTANCE.peek();
            if (exec == null) {
                return;
            }
            Run b = exec.getBuild().getPreviousBuildInProgress();
            if (b == null)     return; // no pending earlier build
            Run.RunExecution runner = b.runner;
            if (runner == null) {
                // polled at the wrong moment. try again.
                Thread.sleep(0);
                continue;
            }
            if (runner.checkpoints.waitForCheckPoint(id, listener, waiter))
                return; // confirmed that the previous build reached the check point

            // the previous build finished without ever reaching the check point. try again.
        }
    }

    /**
     * @deprecated as of 1.467
     *      Please use {@link RunExecution}
     */
    @Deprecated
    protected abstract class Runner extends RunExecution {}

    /**
     * Object that lives while the build is executed, to keep track of things that
     * are needed only during the build.
     */
    public abstract class RunExecution {
        /**
         * Keeps track of the check points attained by a build, and abstracts away the synchronization needed to
         * maintain this data structure.
         */
        private final class CheckpointSet {
            /**
             * Stages of the builds that this runner has completed. This is used for concurrent {@link RunExecution}s to
             * coordinate and serialize their executions where necessary.
             */
            private final Set<CheckPoint> checkpoints = new HashSet<>();

            private boolean allDone;

            protected synchronized void report(@NonNull CheckPoint identifier) {
                checkpoints.add(identifier);
                notifyAll();
            }

            protected synchronized boolean waitForCheckPoint(@NonNull CheckPoint identifier, @CheckForNull BuildListener listener, @CheckForNull String waiter) throws InterruptedException {
                final Thread t = Thread.currentThread();
                final String oldName = t.getName();
                t.setName(oldName + " : waiting for " + identifier + " on " + getFullDisplayName() + " from " + waiter);
                try {
                    boolean first = true;
                    while (!allDone && !checkpoints.contains(identifier)) {
                        if (first && listener != null && waiter != null) {
                            listener.getLogger().println(Messages.Run__is_waiting_for_a_checkpoint_on_(waiter, getFullDisplayName()));
                        }
                        wait();
                        first = false;
                    }
                    return checkpoints.contains(identifier);
                } finally {
                    t.setName(oldName);
                }
            }

            /**
             * Notifies that the build is fully completed and all the checkpoint locks be released.
             */
            private synchronized void allDone() {
                allDone = true;
                notifyAll();
            }
        }

        private final CheckpointSet checkpoints = new CheckpointSet();

        private final Map<Object, Object> attributes = new HashMap<>();

        /**
         * Performs the main build and returns the status code.
         *
         * @throws Exception
         *      exception will be recorded and the build will be considered a failure.
         */
        public abstract @NonNull Result run(@NonNull BuildListener listener) throws Exception;

        /**
         * Performs the post-build action.
         * <p>
         * This method is called after {@linkplain #run(BuildListener) the main portion of the build is completed.}
         * This is a good opportunity to do notifications based on the result
         * of the build. When this method is called, the build is not really
         * finalized yet, and the build is still considered in progress --- for example,
         * even if the build is successful, this build still won't be picked up
         * by {@link Job#getLastSuccessfulBuild()}.
         */
        public abstract void post(@NonNull BuildListener listener) throws Exception;

        /**
         * Performs final clean up action.
         * <p>
         * This method is called after {@link #post(BuildListener)},
         * after the build result is fully finalized. This is the point
         * where the build is already considered completed.
         * <p>
         * Among other things, this is often a necessary pre-condition
         * before invoking other builds that depend on this build.
         */
        public abstract void cleanUp(@NonNull BuildListener listener) throws Exception;

        public @NonNull RunT getBuild() {
            return _this();
        }

        public @NonNull JobT getProject() {
            return _this().getParent();
        }

        /**
         * Bag of stuff to allow plugins to store state for the duration of a build
         * without persisting it.
         *
         * @since 1.473
         */
        public @NonNull Map<Object, Object> getAttributes() {
            return attributes;
        }
    }

    /**
     * Used in {@link Run.RunExecution#run} to indicates that a fatal error in a build
     * is reported to {@link BuildListener} and the build should be simply aborted
     * without further recording a stack trace.
     */
    public static final class RunnerAbortedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    /**
     * @deprecated as of 1.467
     *      Use {@link #execute(hudson.model.Run.RunExecution)}
     */
    @Deprecated
    protected final void run(@NonNull Runner job) {
        execute(job);
    }

    protected final void execute(@NonNull RunExecution job) {
        if (result != null)
            return;     // already built.

        OutputStream logger = null;
        StreamBuildListener listener = null;

        runner = job;
        onStartBuilding();
        try {
            // to set the state to COMPLETE in the end, even if the thread dies abnormally.
            // otherwise the queue state becomes inconsistent

            long start = System.currentTimeMillis();

            try {
                try {
                    Computer computer = Computer.currentComputer();
                    Charset charset = null;
                    if (computer != null) {
                        charset = computer.getDefaultCharset();
                        this.charset = charset.name();
                    }
                    logger = createLogger();
                    listener = createBuildListener(job, logger, charset);
                    listener.started(getCauses());

                    Authentication auth = Jenkins.getAuthentication2();
                    if (auth.equals(ACL.SYSTEM2)) {
                        listener.getLogger().println(Messages.Run_running_as_SYSTEM());
                    } else {
                        String id = auth.getName();
                        if (!auth.equals(Jenkins.ANONYMOUS2)) {
                            final User usr = User.getById(id, false);
                            if (usr != null) { // Encode user hyperlink for existing users
                                id = ModelHyperlinkNote.encodeTo(usr);
                            }
                        }
                        listener.getLogger().println(Messages.Run_running_as_(id));
                    }

                    RunListener.fireStarted(this, listener);

                    setResult(job.run(listener));

                    LOGGER.log(FINE, "{0} main build action completed: {1}", new Object[] {this, result});
                    CheckPoint.MAIN_COMPLETED.report();
                } catch (AbortException e) { // orderly abortion.
                    result = Result.FAILURE;
                    listener.error(e.getMessage());
                    LOGGER.log(FINE, "Build " + this + " aborted", e);
                } catch (RunnerAbortedException e) { // orderly abortion.
                    result = Result.FAILURE;
                    LOGGER.log(FINE, "Build " + this + " aborted", e);
                } catch (InterruptedException e) {
                    // aborted
                    result = Executor.currentExecutor().abortResult();
                    listener.getLogger().println(Messages.Run_BuildAborted());
                    Executor.currentExecutor().recordCauseOfInterruption(Run.this, listener);
                    LOGGER.log(Level.INFO, this + " aborted", e);
                } catch (Throwable e) {
                    handleFatalBuildProblem(listener, e);
                    result = Result.FAILURE;
                }

                // even if the main build fails fatally, try to run post build processing
                job.post(Objects.requireNonNull(listener));

            } catch (Throwable e) {
                handleFatalBuildProblem(listener, e);
                result = Result.FAILURE;
            } finally {
                long end = System.currentTimeMillis();
                duration = Math.max(end - start, 0);  // @see JENKINS-5844

                // advance the state.
                // the significance of doing this is that Jenkins
                // will now see this build as completed.
                // things like triggering other builds requires this as pre-condition.
                // see issue JENKINS-980.
                LOGGER.log(FINER, "moving into POST_PRODUCTION on {0}", this);
                state = State.POST_PRODUCTION;

                if (listener != null) {
                    RunListener.fireCompleted(this, listener);
                    try {
                        job.cleanUp(listener);
                    } catch (Exception e) {
                        handleFatalBuildProblem(listener, e);
                        // too late to update the result now
                    }
                    listener.finished(result);
                    listener.closeQuietly();
                }

                try {
                    save();
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed to save build record", e);
                }
            }
        } finally {
            onEndBuilding();
            if (logger != null) {
                try {
                    logger.close();
                } catch (IOException x) {
                    LOGGER.log(Level.WARNING, "failed to close log for " + Run.this, x);
                }
            }
        }
    }

    private OutputStream createLogger() throws IOException {
        // don't do buffering so that what's written to the listener
        // gets reflected to the file immediately, which can then be
        // served to the browser immediately
        try {
            return Files.newOutputStream(getLogFile().toPath(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (InvalidPathException e) {
            throw new IOException(e);
        }
    }

    private StreamBuildListener createBuildListener(@NonNull RunExecution job, OutputStream logger, Charset charset) throws IOException, InterruptedException {
        RunT build = job.getBuild();

        // Global log filters
        for (ConsoleLogFilter filter : ConsoleLogFilter.all()) {
            logger = filter.decorateLogger(build, logger);
        }

        // Project specific log filters
        if (project instanceof BuildableItemWithBuildWrappers && build instanceof AbstractBuild) {
            BuildableItemWithBuildWrappers biwbw = (BuildableItemWithBuildWrappers) project;
            for (BuildWrapper bw : biwbw.getBuildWrappersList()) {
                logger = bw.decorateLogger((AbstractBuild) build, logger);
            }
        }

        return new StreamBuildListener(logger, charset);
    }

    /**
     * @deprecated After JENKINS-37862 this no longer does anything.
     */
    @Deprecated
    public final void updateSymlinks(@NonNull TaskListener listener) throws InterruptedException {}

    /**
     * Handles a fatal build problem (exception) that occurred during the build.
     */
    private void handleFatalBuildProblem(@NonNull BuildListener listener, @NonNull Throwable e) {
        if (listener != null) {
            LOGGER.log(FINE, getDisplayName() + " failed to build", e);

            if (e instanceof IOException)
                Util.displayIOException((IOException) e, listener);

            Functions.printStackTrace(e, listener.fatalError(e.getMessage()));
        } else {
            LOGGER.log(SEVERE, getDisplayName() + " failed to build and we don't even have a listener", e);
        }
    }

    /**
     * Called when a job started building.
     */
    protected void onStartBuilding() {
        LOGGER.log(FINER, "moving to BUILDING on {0}", this);
        state = State.BUILDING;
        startTime = System.currentTimeMillis();
        if (runner != null)
            RunnerStack.INSTANCE.push(runner);
        RunListener.fireInitialize(this);
    }

    /**
     * Called when a job finished building normally or abnormally.
     */
    protected void onEndBuilding() {
        // signal that we've finished building.
        state = State.COMPLETED;
        LOGGER.log(FINER, "moving to COMPLETED on {0}", this);
        if (runner != null) {
            // MavenBuilds may be created without their corresponding runners.
            runner.checkpoints.allDone();
            runner = null;
            RunnerStack.INSTANCE.pop();
        }
        if (result == null) {
            result = Result.FAILURE;
            LOGGER.log(WARNING, "{0}: No build result is set, so marking as failure. This should not happen.", this);
        }

        RunListener.fireFinalized(this);
    }

    /**
     * Save the settings to a file.
     */
    @Override
    public synchronized void save() throws IOException {
        if (BulkChange.contains(this))   return;
        getDataFile().write(this);
        SaveableListener.fireOnChange(this, getDataFile());
    }

    private @NonNull XmlFile getDataFile() {
        return new XmlFile(XSTREAM, new File(getRootDir(), "build.xml"));
    }

    protected Object writeReplace() {
        return XmlFile.replaceIfNotAtTopLevel(this, () -> new Replacer(this));
    }

    private static class Replacer {
        private final String id;

        Replacer(Run<?, ?> r) {
            id = r.getExternalizableId();
        }

        private Object readResolve() {
            return fromExternalizableId(id);
        }
    }

    /**
     * Gets the log of the build as a string.
     * @return Returns the log or an empty string if it has not been found
     * @deprecated since 2007-11-11.
     *     Use {@link #getLog(int)} instead as it avoids loading
     *     the whole log into memory unnecessarily.
     */
    @Deprecated
    public @NonNull String getLog() throws IOException {
        return Util.loadFile(getLogFile(), getCharset());
    }

    /**
     * Gets the log of the build as a list of strings (one per log line).
     * The number of lines returned is constrained by the maxLines parameter.
     *
     * @param maxLines The maximum number of log lines to return.  If the log
     * is bigger than this, only the most recent lines are returned.
     * @return A list of log lines.  Will have no more than maxLines elements.
     * @throws IOException If there is a problem reading the log file.
     */
    public @NonNull List<String> getLog(int maxLines) throws IOException {
        if (maxLines == 0) {
            return Collections.emptyList();
        }

        int lines = 0;
        long filePointer;
        final List<String> lastLines = new ArrayList<>(Math.min(maxLines, 128));
        final List<Byte> bytes = new ArrayList<>();

        try (RandomAccessFile fileHandler = new RandomAccessFile(getLogFile(), "r")) {
            long fileLength = fileHandler.length() - 1;

            for (filePointer = fileLength; filePointer != -1 && maxLines != lines; filePointer--) {
                fileHandler.seek(filePointer);
                byte readByte = fileHandler.readByte();

                if (readByte == 0x0A) {
                    if (filePointer < fileLength) {
                        lines = lines + 1;
                        lastLines.add(convertBytesToString(bytes));
                        bytes.clear();
                    }
                } else if (readByte != 0xD) {
                    bytes.add(readByte);
                }
            }
        }

        if (lines != maxLines) {
            lastLines.add(convertBytesToString(bytes));
        }

        Collections.reverse(lastLines);

        // If the log has been truncated, include that information.
        // Use set (replaces the first element) rather than add so that
        // the list doesn't grow beyond the specified maximum number of lines.
        if (lines == maxLines) {
            lastLines.set(0, "[...truncated " + Functions.humanReadableByteSize(filePointer) + "...]");
        }

        return ConsoleNote.removeNotes(lastLines);
    }

    private String convertBytesToString(List<Byte> bytes) {
        Collections.reverse(bytes);
        byte[] byteArray = new byte[bytes.size()];
        for (int i = 0; i < byteArray.length; i++) {
            byteArray[i] = bytes.get(i);
        }
        return new String(byteArray, getCharset());
    }

    public void doBuildStatus(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        rsp.sendRedirect2(req.getContextPath() + "/images/48x48/" + getBuildStatusUrl());
    }

    public static class Summary {
        /**
         * Is this build worse or better, compared to the previous build?
         */
        public boolean isWorse;
        public String message;

        public Summary(boolean worse, String message) {
            this.isWorse = worse;
            this.message = message;
        }
    }

    /**
     * Used to implement {@link #getBuildStatusSummary}.
     * @since 1.575
     */
    public abstract static class StatusSummarizer implements ExtensionPoint {
        /**
         * Possibly summarizes the reasons for a build’s status.
         * @param run a completed build
         * @param trend the result of {@link ResultTrend#getResultTrend(hudson.model.Run)} on {@code run} (precomputed for efficiency)
         * @return a summary, or null to fall back to other summarizers or built-in behavior
         */
        public abstract @CheckForNull Summary summarize(@NonNull Run<?, ?> run, @NonNull ResultTrend trend);
    }

    /**
     * Gets an object which represents the single line summary of the status of this build
     * (especially in comparison with the previous build.)
     * @see StatusSummarizer
     */
    public @NonNull Summary getBuildStatusSummary() {
        if (isBuilding()) {
            return new Summary(false, Messages.Run_Summary_Unknown());
        }

        ResultTrend trend = ResultTrend.getResultTrend(this);

        for (StatusSummarizer summarizer : ExtensionList.lookup(StatusSummarizer.class)) {
            Summary summary = summarizer.summarize(this, trend);
            if (summary != null) {
                return summary;
            }
        }

        switch (trend) {
            case ABORTED: return new Summary(false, Messages.Run_Summary_Aborted());

            case NOT_BUILT: return new Summary(false, Messages.Run_Summary_NotBuilt());

            case FAILURE: return new Summary(true, Messages.Run_Summary_BrokenSinceThisBuild());

            case STILL_FAILING:
                RunT since = getPreviousNotFailedBuild();
                if (since == null)
                    return new Summary(false, Messages.Run_Summary_BrokenForALongTime());
                RunT failedBuild = since.getNextBuild();
                return new Summary(false, Messages.Run_Summary_BrokenSince(failedBuild.getDisplayName()));

            case NOW_UNSTABLE:
            case STILL_UNSTABLE:
                return new Summary(false, Messages.Run_Summary_Unstable());
            case UNSTABLE:
                return new Summary(true, Messages.Run_Summary_Unstable());

            case SUCCESS:
                return new Summary(false, Messages.Run_Summary_Stable());

            case FIXED:
                return new Summary(false, Messages.Run_Summary_BackToNormal());

            default:
                return new Summary(false, Messages.Run_Summary_Unknown());
        }
    }

    /**
     * Serves the artifacts.
     * @throws AccessDeniedException Access denied
     */
    public @NonNull DirectoryBrowserSupport doArtifact() {
        if (Functions.isArtifactsPermissionEnabled()) {
          checkPermission(ARTIFACTS);
        }
        return new DirectoryBrowserSupport(this, getArtifactManager().root(), Messages.Run_ArtifactsBrowserTitle(project.getDisplayName(), getDisplayName()), "package.png", true);
    }

    /**
     * Returns the build number in the body.
     */
    public void doBuildNumber(StaplerResponse2 rsp) throws IOException {
        rsp.setContentType("text/plain");
        rsp.setCharacterEncoding("US-ASCII");
        rsp.setStatus(HttpServletResponse.SC_OK);
        rsp.getWriter().print(number);
    }

    /**
     * Returns the build time stamp in the body.
     */
    public void doBuildTimestamp(StaplerRequest2 req, StaplerResponse2 rsp, @QueryParameter String format) throws IOException {
        rsp.setContentType("text/plain");
        rsp.setCharacterEncoding("US-ASCII");
        rsp.setStatus(HttpServletResponse.SC_OK);
        DateFormat df = format == null ?
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.ENGLISH) :
                new SimpleDateFormat(format, req.getLocale());
        rsp.getWriter().print(df.format(getTime()));
    }

    /**
     * Sends out the raw console output.
     *
     * @since 2.475
     */
    public void doConsoleText(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        if (Util.isOverridden(Run.class, getClass(), "doConsoleText", StaplerRequest.class, StaplerResponse.class)) {
            doConsoleText(StaplerRequest.fromStaplerRequest2(req), StaplerResponse.fromStaplerResponse2(rsp));
        } else {
            doConsoleTextImpl(req, rsp);
        }
    }

    /**
     * @deprecated use {@link #doConsoleText(StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    @StaplerNotDispatchable
    public void doConsoleText(StaplerRequest req, StaplerResponse rsp) throws IOException {
        doConsoleTextImpl(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
    }

    private void doConsoleTextImpl(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        rsp.setContentType("text/plain;charset=UTF-8");
        try (InputStream input = getLogInputStream();
             OutputStream os = rsp.getOutputStream();
             PlainTextConsoleOutputStream out = new PlainTextConsoleOutputStream(os)) {
            IOUtils.copy(input, out);
        }
    }

    /**
     * Handles incremental log output.
     * @deprecated as of 1.352
     *      Use {@code getLogText().doProgressiveText(req,rsp)}
     */
    @Deprecated
    public void doProgressiveLog(StaplerRequest req, StaplerResponse rsp) throws IOException {
        getLogText().doProgressText(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
    }

    /**
     * Checks whether keep status can be toggled.
     * Normally it can, but if there is a complex reason (from subclasses) why this build must be kept, the toggle is meaningless.
     * @return true if {@link #doToggleLogKeep} and {@link #keepLog(boolean)} and {@link #keepLog()} are options
     * @since 1.510
     */
    public boolean canToggleLogKeep() {
        if (!keepLog && isKeepLog()) {
            // Definitely prevented.
            return false;
        }
        // TODO may be that keepLog is on (perhaps toggler earlier) yet isKeepLog() would be true anyway.
        // In such a case this will incorrectly return true and logKeep.jelly will allow the toggle.
        // However at least then (after redirecting to the same page) the toggle button will correctly disappear.
        return true;
    }

    @RequirePOST
    public void doToggleLogKeep(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        keepLog(!keepLog);
        rsp.forwardToPreviousPage(req);
    }

    /**
     * Marks this build to be kept.
     */
    @CLIMethod(name = "keep-build")
    public final void keepLog() throws IOException {
        keepLog(true);
    }

    public void keepLog(boolean newValue) throws IOException {
        checkPermission(newValue ? UPDATE : DELETE);
        keepLog = newValue;
        save();
    }

    /**
     * Deletes the build when the button is pressed.
     *
     * @since 2.475
     */
    @RequirePOST
    public void doDoDelete(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        if (Util.isOverridden(Run.class, getClass(), "doDoDelete", StaplerRequest.class, StaplerResponse.class)) {
            try {
                doDoDelete(StaplerRequest.fromStaplerRequest2(req), StaplerResponse.fromStaplerResponse2(rsp));
            } catch (javax.servlet.ServletException e) {
                throw ServletExceptionWrapper.toJakartaServletException(e);
            }
            return;
        } else {
            doDoDeleteImpl(req, rsp);
        }
    }

    /**
     * @deprecated use {@link #doDoDelete(StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    @StaplerNotDispatchable
    public void doDoDelete(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        try {
            doDoDeleteImpl(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    private void doDoDeleteImpl(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        checkPermission(DELETE);

        // We should not simply delete the build if it has been explicitly
        // marked to be preserved, or if the build should not be deleted
        // due to dependencies!
        String why = getWhyKeepLog();
        if (why != null) {
            sendError(Messages.Run_UnableToDelete(getFullDisplayName(), why), req, rsp);
            return;
        }

        try {
            delete();
        }
        catch (IOException ex) {
            req.setAttribute("stackTraces", Functions.printThrowable(ex));
            req.getView(this, "delete-retry.jelly").forward(req, rsp);
            return;
        }
        rsp.sendRedirect2(req.getContextPath() + '/' + getParent().getUrl());
    }

    public void setDescription(String description) throws IOException {
        checkPermission(UPDATE);
        this.description = description;
        save();
    }

    /**
     * Accepts the new description.
     */
    @RequirePOST
    public synchronized void doSubmitDescription(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        setDescription(req.getParameter("description"));
        rsp.sendRedirect(".");  // go to the top page
    }

    /**
     * @deprecated as of 1.292
     *      Use {@link #getEnvironment(TaskListener)} instead.
     */
    @Deprecated
    public Map<String, String> getEnvVars() {
        LOGGER.log(WARNING, "deprecated call to Run.getEnvVars\n\tat {0}", new Throwable().getStackTrace()[1]);
        try {
            return getEnvironment(new LogTaskListener(LOGGER, Level.INFO));
        } catch (IOException | InterruptedException e) {
            return new EnvVars();
        }
    }

    /**
     * @deprecated as of 1.305 use {@link #getEnvironment(TaskListener)}
     */
    @Deprecated
    public EnvVars getEnvironment() throws IOException, InterruptedException {
        LOGGER.log(WARNING, "deprecated call to Run.getEnvironment\n\tat {0}", new Throwable().getStackTrace()[1]);
        return getEnvironment(new LogTaskListener(LOGGER, Level.INFO));
    }

    /**
     * Returns the map that contains environmental variables to be used for launching
     * processes for this build.
     *
     * <p>
     * {@link hudson.tasks.BuildStep}s that invoke external processes should use this.
     * This allows {@link BuildWrapper}s and other project configurations (such as JDK selection)
     * to take effect.
     *
     * <p>
     * Unlike earlier {@link #getEnvVars()}, this map contains the whole environment,
     * not just the overrides, so one can introspect values to change its behavior.
     *
     * @return the map with the environmental variables.
     * @since 1.305
     */
    public @NonNull EnvVars getEnvironment(@NonNull TaskListener listener) throws IOException, InterruptedException {
        Computer c = Computer.currentComputer();
        Node n = c == null ? null : c.getNode();

        EnvVars env = getParent().getEnvironment(n, listener);
        env.putAll(getCharacteristicEnvVars());

        // apply them in a reverse order so that higher ordinal ones can modify values added by lower ordinal ones
        for (EnvironmentContributor ec : EnvironmentContributor.all().reverseView())
            ec.buildEnvironmentFor(this, env, listener);

        if (!(this instanceof AbstractBuild)) {
            for (EnvironmentContributingAction a : getActions(EnvironmentContributingAction.class)) {
                a.buildEnvironment(this, env);
            }
        } // else for compatibility reasons, handled in override after buildEnvironments

        return env;
    }

    /**
     * Builds up the environment variable map that's sufficient to identify a process
     * as ours. This is used to kill run-away processes via {@link ProcessTree#killAll(Map)}.
     */
    public @NonNull final EnvVars getCharacteristicEnvVars() {
        EnvVars env = getParent().getCharacteristicEnvVars();
        env.put("BUILD_NUMBER", String.valueOf(number));
        env.put("BUILD_ID", getId());
        env.put("BUILD_TAG", "jenkins-" + getParent().getFullName().replace('/', '-') + "-" + number);
        return env;
    }

    /**
     * Produces an identifier for this run unique in the system.
     * @return the {@link Job#getFullName}, then {@code #}, then {@link #getNumber}
     * @see #fromExternalizableId
     */
    public @NonNull String getExternalizableId() {
        return project.getFullName() + "#" + getNumber();
    }

    /**
     * Tries to find a run from an persisted identifier.
     * @param id as produced by {@link #getExternalizableId}
     * @return the same run, or null if the job or run was not found
     * @throws IllegalArgumentException if the ID is malformed
     * @throws AccessDeniedException as per {@link ItemGroup#getItem}
     */
    public @CheckForNull static Run<?, ?> fromExternalizableId(String id) throws IllegalArgumentException, AccessDeniedException {
        int hash = id.lastIndexOf('#');
        if (hash <= 0) {
            throw new IllegalArgumentException("Invalid id");
        }
        String jobName = id.substring(0, hash);
        int number;
        try {
            number = Integer.parseInt(id.substring(hash + 1));
        } catch (NumberFormatException x) {
            throw new IllegalArgumentException(x);
        }
        Jenkins j = Jenkins.getInstanceOrNull();
        if (j == null) {
            LOGGER.fine(() -> "Jenkins not running");
            return null;
        }
        Job<?, ?> job = j.getItemByFullName(jobName, Job.class);
        if (job == null) {
            LOGGER.fine(() -> "no such job " + jobName + " when running as " + Jenkins.getAuthentication2().getName());
            return null;
        }
        return job.getBuildByNumber(number);
    }

    /**
     * Returns the estimated duration for this run if it is currently running.
     * Default to {@link Job#getEstimatedDuration()}, may be overridden in subclasses
     * if duration may depend on run specific parameters (like incremental Maven builds).
     *
     * @return the estimated duration in milliseconds
     * @since 1.383
     */
    @Exported
    public long getEstimatedDuration() {
        return project.getEstimatedDuration();
    }

    @POST
    public @NonNull HttpResponse doConfigSubmit(StaplerRequest2 req) throws IOException, ServletException, FormException {
        checkPermission(UPDATE);
        try (BulkChange bc = new BulkChange(this)) {
            JSONObject json = req.getSubmittedForm();
            submit(json);
            bc.commit();
        }
        return FormApply.success(".");
    }

    protected void submit(JSONObject json) throws IOException {
        setDisplayName(Util.fixEmptyAndTrim(json.getString("displayName")));
        setDescription(json.getString("description"));
    }

    public static final XStream XSTREAM = new XStream2();

    /**
     * Alias to {@link #XSTREAM} so that one can access additional methods on {@link XStream2} more easily.
     */
    public static final XStream2 XSTREAM2 = (XStream2) XSTREAM;

    static {
        XSTREAM.alias("build", FreeStyleBuild.class);
        XSTREAM.registerConverter(Result.conv);
    }

    private static final Logger LOGGER = Logger.getLogger(Run.class.getName());

    /**
     * Sort by date. Newer ones first.
     */
    public static final Comparator<Run> ORDER_BY_DATE = new Comparator<>() {
        @Override
        public int compare(@NonNull Run lhs, @NonNull Run rhs) {
            long lt = lhs.getTimeInMillis();
            long rt = rhs.getTimeInMillis();
            return Long.compare(rt, lt);
        }
    };

    /**
     * {@link FeedAdapter} to produce feed from the summary of this build.
     */
    public static final FeedAdapter<Run> FEED_ADAPTER = new DefaultFeedAdapter();

    /**
     * {@link FeedAdapter} to produce feeds to show one build per project.
     */
    public static final FeedAdapter<Run> FEED_ADAPTER_LATEST = new DefaultFeedAdapter() {
        /**
         * The entry unique ID needs to be tied to a project, so that
         * new builds will replace the old result.
         */
        @Override
        public String getEntryID(Run e) {
            // can't use a meaningful year field unless we remember when the job was created.
            return "tag:hudson.dev.java.net,2008:" + e.getParent().getAbsoluteUrl();
        }
    };

    /**
     * {@link BuildBadgeAction} that shows the build is being kept.
     */
    public final class KeepLogBuildBadge implements BuildBadgeAction {
        @Override
        public @CheckForNull String getIconFileName() { return null; }

        @Override
        public @CheckForNull String getDisplayName() { return null; }

        @Override
        public @CheckForNull String getUrlName() { return null; }

        public @CheckForNull String getWhyKeepLog() { return Run.this.getWhyKeepLog(); }
    }

    public static final PermissionGroup PERMISSIONS = new PermissionGroup(Run.class, Messages._Run_Permissions_Title());
    public static final Permission DELETE = new Permission(PERMISSIONS, "Delete", Messages._Run_DeletePermission_Description(), Permission.DELETE, PermissionScope.RUN);
    public static final Permission UPDATE = new Permission(PERMISSIONS, "Update", Messages._Run_UpdatePermission_Description(), Permission.UPDATE, PermissionScope.RUN);
    /** See {@link hudson.Functions#isArtifactsPermissionEnabled} */
    public static final Permission ARTIFACTS = new Permission(PERMISSIONS, "Artifacts", Messages._Run_ArtifactsPermission_Description(), null,
                                                              Functions.isArtifactsPermissionEnabled(), new PermissionScope[]{PermissionScope.RUN});

    private static class DefaultFeedAdapter implements FeedAdapter<Run> {
        @Override
        public String getEntryTitle(Run entry) {
            return entry.getFullDisplayName() + " (" + entry.getBuildStatusSummary().message + ")";
        }

        @Override
        public String getEntryUrl(Run entry) {
            return entry.getUrl();
        }

        @Override
        public String getEntryID(Run entry) {
            return "tag:" + "hudson.dev.java.net,"
                + entry.getTimestamp().get(Calendar.YEAR) + ":"
                + entry.getParent().getFullName() + ':' + entry.getId();
        }

        @Override
        public String getEntryDescription(Run entry) {
            return entry.getDescription();
        }

        @Override
        public Calendar getEntryTimestamp(Run entry) {
            return entry.getTimestamp();
        }

        @Override
        public String getEntryAuthor(Run entry) {
            return JenkinsLocationConfiguration.get().getAdminAddress();
        }
    }

    @Override
    public Object getDynamic(String token, StaplerRequest2 req, StaplerResponse2 rsp) {
        if (Util.isOverridden(Run.class, getClass(), "getDynamic", String.class, StaplerRequest.class, StaplerResponse.class)) {
            return getDynamic(token, StaplerRequest.fromStaplerRequest2(req), StaplerResponse.fromStaplerResponse2(rsp));
        } else {
            Object returnedResult = super.getDynamic(token, req, rsp);
            return getDynamicImpl(token, returnedResult);
        }
    }

    /**
     * @deprecated use {@link #getDynamic(String, StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    @Override
    public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        Object returnedResult = super.getDynamic(token, req, rsp);
        return getDynamicImpl(token, returnedResult);
    }

    private Object getDynamicImpl(String token, Object returnedResult) {
        if (returnedResult == null) {
            //check transient actions too
            for (Action action : getTransientActions()) {
                String urlName = action.getUrlName();
                if (urlName == null) {
                    continue;
                }
                if (urlName.equals(token)) {
                    return action;
                }
            }
            // Next/Previous Build links on an action page (like /job/Abc/123/testReport)
            // will also point to same action (/job/Abc/124/testReport), but other builds
            // may not have the action.. tell browsers to redirect up to the build page.
            returnedResult = new RedirectUp();
        }
        return returnedResult;
    }

    @Override
    @Restricted(NoExternalUse.class)
    public Object getTarget() {
        if (!SKIP_PERMISSION_CHECK) {
            // This is a bit weird, but while the Run's PermissionScope does not have READ, delegate to the parent
            if (!getParent().hasPermission(Item.DISCOVER)) {
                return null;
            }
            getParent().checkPermission(Item.READ);
        }
        return this;
    }

    /**
     * Escape hatch for StaplerProxy-based access control
     */
    @Restricted(NoExternalUse.class)
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static /* Script Console modifiable */ boolean SKIP_PERMISSION_CHECK = SystemProperties.getBoolean(Run.class.getName() + ".skipPermissionCheck");


    public static class RedirectUp {
        public void doDynamic(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
            // Compromise to handle both browsers (auto-redirect) and programmatic access
            // (want accurate 404 response).. send 404 with javascript to redirect browsers.
            rsp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            rsp.setContentType("text/html;charset=UTF-8");
            PrintWriter out = rsp.getWriter();
            Util.printRedirect(req.getContextPath(), "..", "Not found", out);
            out.flush();
        }
    }

    @Extension
    public static final class BasicRunDetailFactory extends DetailFactory<Run> {

        @Override
        public Class<Run> type() {
            return Run.class;
        }

        @NonNull @Override public Collection<? extends Detail> createFor(@NonNull Run target) {
            return List.of(new TimestampDetail(target), new DurationDetail(target));
        }
    }
}
