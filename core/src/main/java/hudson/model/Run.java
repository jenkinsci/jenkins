/*
 * The MIT License
 * 
 * Copyright (c) 2004-2012, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Daniel Dyer, Red Hat, Inc., Tom Huybrechts, Romain Seguy, Yahoo! Inc.,
 * Darek Ostolski, CloudBees, Inc.
 *
 * Copyright (c) 2012, Martin Schroeder, Intel Mobile Communications GmbH
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

import com.jcraft.jzlib.GZIPInputStream;
import com.thoughtworks.xstream.XStream;
import hudson.AbortException;
import hudson.BulkChange;
import hudson.EnvVars;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.FeedAdapter;
import hudson.Functions;
import jenkins.util.SystemProperties;
import hudson.Util;
import hudson.XmlFile;
import hudson.cli.declarative.CLIMethod;
import hudson.console.*;
import hudson.model.Descriptor.FormException;
import hudson.model.Run.RunExecution;
import hudson.model.listeners.RunListener;
import hudson.model.listeners.SaveableListener;
import hudson.model.queue.Executables;
import hudson.model.queue.SubTask;
import hudson.search.SearchIndexBuilder;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import hudson.tasks.BuildWrapper;
import hudson.util.FormApply;
import hudson.util.LogTaskListener;
import hudson.util.ProcessTree;
import hudson.util.XStream2;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import static java.util.logging.Level.*;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import jenkins.model.ArtifactManager;
import jenkins.model.ArtifactManagerConfiguration;
import jenkins.model.ArtifactManagerFactory;
import jenkins.model.BuildDiscarder;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import jenkins.model.PeepholePermalink;
import jenkins.model.RunAction2;
import jenkins.model.StandardArtifactManager;
import jenkins.model.lazy.BuildReference;
import jenkins.model.lazy.LazyBuildMixIn;
import jenkins.util.VirtualFile;
import jenkins.util.io.OnMaster;
import net.sf.json.JSONObject;
import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.Authentication;
import org.apache.commons.io.IOUtils;
import org.apache.commons.jelly.XMLOutput;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.*;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;

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
public abstract class Run <JobT extends Job<JobT,RunT>,RunT extends Run<JobT,RunT>>
        extends Actionable implements ExtensionPoint, Comparable<RunT>, AccessControlled, PersistenceRoot, DescriptorByNameOwner, OnMaster {

    /**
     * The original {@link Queue.Item#getId()} has not yet been mapped onto the {@link Run} instance.
     * @since 1.601
     */
    public static final long QUEUE_ID_UNKNOWN = -1;

    protected transient final @Nonnull JobT project;

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
    protected volatile transient RunT previousBuild;

    /**
     * Next build. Can be null.
     *
     * External code should use {@link #getNextBuild()}
     */
    @Restricted(NoExternalUse.class)
    protected volatile transient RunT nextBuild;

    /**
     * Pointer to the next younger build in progress. This data structure is lazily updated,
     * so it may point to the build that's already completed. This pointer is set to 'this'
     * if the computation determines that everything earlier than this build is already completed.
     */
    /* does not compile on JDK 7: private*/ volatile transient RunT previousBuildInProgress;

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
     * Human-readable description. Can be null.
     */
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
    private volatile transient State state;

    private static enum State {
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
     * Keeps this log entries.
     */
    private boolean keepLog;

    /**
     * If the build is in progress, remember {@link RunExecution} that's running it.
     * This field is not persisted.
     */
    private volatile transient RunExecution runner;

    /**
     * Artifact manager associated with this build, if any.
     * @since 1.532
     */
    private @CheckForNull ArtifactManager artifactManager;

    /**
     * Creates a new {@link Run}.
     * @param job Owner job
     */
    protected Run(@Nonnull JobT job) throws IOException {
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
    protected Run(@Nonnull JobT job, @Nonnull Calendar timestamp) {
        this(job,timestamp.getTimeInMillis());
    }

    /** @see #Run(Job, Calendar) */
    protected Run(@Nonnull JobT job, long timestamp) {
        this.project = job;
        this.timestamp = timestamp;
        this.state = State.NOT_STARTED;
    }

    /**
     * Loads a run from a log file.
     */
    protected Run(@Nonnull JobT project, @Nonnull File buildDir) throws IOException {
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
        // TODO ABORTED would perhaps make more sense than FAILURE:
        this.result = Result.FAILURE;  // defensive measure. value should be overwritten by unmarshal, but just in case the saved data is inconsistent
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
                    getActions().remove(a); // if possible; might be in an inconsistent state
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
        List<Action> actions = new ArrayList<Action>();
        for (TransientBuildActionFactory factory: TransientBuildActionFactory.all()) {
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
    public void addAction(@Nonnull Action a) {
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
    @SuppressWarnings({"unchecked"})
    protected @Nonnull RunT _this() {
        return (RunT)this;
    }

    /**
     * Ordering based on build numbers.
     */
    public int compareTo(@Nonnull RunT that) {
        return this.number - that.number;
    }

    /**
     * Get the {@link Queue.Item#getId()} of the original queue item from where this Run instance
     * originated.
     * @return The queue item ID.
     * @since 1.601
     */
    @Exported
    public long getQueueId() {
        return queueId;
    }

    /**
     * Set the queue item ID.
     * <p/>
     * Mapped from the {@link Queue.Item#getId()}.
     * @param queueId The queue item ID.
     */
    @Restricted(NoExternalUse.class)
    public void setQueueId(long queueId) {
        this.queueId = queueId;
    }

    /**
     * Returns the build result.
     *
     * <p>
     * When a build is {@link #isBuilding() in progress}, this method
     * returns an intermediate result.
     * @return The status of the build, if it has completed or some build step has set a status; may be null if the build is ongoing.
     */
    @Exported
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
    public void setResult(@Nonnull Result r) {
        if (state != State.BUILDING) {
            throw new IllegalStateException("cannot change build result while in " + state);
        }

        // result can only get worse
        if (result==null || r.isWorseThan(result)) {
            result = r;
            LOGGER.log(FINE, this + " in " + getRootDir() + ": result is set to " + r, LOGGER.isLoggable(Level.FINER) ? new Exception() : null);
        }
    }

    /**
     * Gets the subset of {@link #getActions()} that consists of {@link BuildBadgeAction}s.
     */
    public @Nonnull List<BuildBadgeAction> getBadgeActions() {
        List<BuildBadgeAction> r = getActions(BuildBadgeAction.class);
        if(isKeepLog()) {
            r.add(new KeepLogBuildBadge());
        }
        return r;
    }

    /**
     * Returns true if the build is not completed yet.
     * This includes "not started yet" state.
     */
    @Exported
    public boolean isBuilding() {
        return state.compareTo(State.POST_PRODUCTION) < 0;
    }

    /**
     * Determine whether the run is being build right now.
     * @return true if after started and before completed.
     * @since 1.538
     */
    protected boolean isInProgress() {
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
     * @see Executables#getExecutor
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
        for( Computer c : Jenkins.getInstance().getComputers() ) {
            for (Executor e : c.getOneOffExecutors()) {
                if(e.getCurrentExecutable()==this)
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
    public final @Nonnull Charset getCharset() {
        if(charset==null)   return Charset.defaultCharset();
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
    public @Nonnull List<Cause> getCauses() {
        CauseAction a = getAction(CauseAction.class);
        if (a==null)    return Collections.emptyList();
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
     * Returns true if this log file should be kept and not deleted.
     *
     * This is used as a signal to the {@link BuildDiscarder}.
     */
    @Exported
    public final boolean isKeepLog() {
        return getWhyKeepLog()!=null;
    }

    /**
     * If {@link #isKeepLog()} returns true, returns a short, human-readable
     * sentence that explains why it's being kept.
     */ 
    public @CheckForNull String getWhyKeepLog() {
        if(keepLog)
            return Messages.Run_MarkedExplicitly();
        return null;    // not marked at all
    }

    /**
     * The project this build is for.
     */ 
    public @Nonnull JobT getParent() {
        return project;
    }

    /**
     * When the build is scheduled.
     *
     * @see #getStartTimeInMillis()
     */   
    @Exported
    public @Nonnull Calendar getTimestamp() {
        GregorianCalendar c = new GregorianCalendar();
        c.setTimeInMillis(timestamp);
        return c;
    }

    /**
     * Same as {@link #getTimestamp()} but in a different type.
     */   
    public final @Nonnull Date getTime() {
        return new Date(timestamp);
    }

    /**
     * Same as {@link #getTimestamp()} but in a different type, that is since the time of the epoc.
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
        if (startTime==0)   return timestamp;   // fallback: approximate by the queuing time
        return startTime;
    }

    @Exported
    public String getDescription() {
        return description;
    }


    /**
     * Returns the length-limited description.
     * @return The length-limited description.
     */   
    public @Nonnull String getTruncatedDescription() {
        final int maxDescrLength = 100;
        if (description == null || description.length() < maxDescrLength) {
            return description;
        }

        final String ending = "...";
        final int sz = description.length(), maxTruncLength = maxDescrLength - ending.length();

        boolean inTag = false;
        int displayChars = 0;
        int lastTruncatablePoint = -1;

        for (int i=0; i<sz; i++) {
            char ch = description.charAt(i);
            if(ch == '<') {
                inTag = true;
            } else if (ch == '>') {
                inTag = false;
                if (displayChars <= maxTruncLength) {
                    lastTruncatablePoint = i + 1;
                }
            }
            if (!inTag) {
                displayChars++;
                if (displayChars <= maxTruncLength && ch == ' ') {
                    lastTruncatablePoint = i;
                }
            }
        }

        String truncDesc = description;

        // Could not find a preferred truncable index, force a trunc at maxTruncLength
        if (lastTruncatablePoint == -1)
            lastTruncatablePoint = maxTruncLength;

        if (displayChars >= maxDescrLength) {
            truncDesc = truncDesc.substring(0, lastTruncatablePoint) + ending;
        }
        
        return truncDesc;
        
    }

    /**
     * Gets the string that says how long since this build has started.
     *
     * @return
     *      string like "3 minutes" "1 day" etc.
     */
    public @Nonnull String getTimestampString() {
        long duration = new GregorianCalendar().getTimeInMillis()-timestamp;
        return Util.getPastTimeString(duration);
    }

    /**
     * Returns the timestamp formatted in xs:dateTime.
     */
    public @Nonnull String getTimestampString2() {
        return Util.XS_DATETIME_FORMATTER.format(new Date(timestamp));
    }

    /**
     * Gets the string that says how long the build took to run.
     */
    public @Nonnull String getDurationString() {
        if (hasntStartedYet()) {
            return Messages.Run_NotStartedYet();
        } else if (isBuilding()) {
            return Messages.Run_InProgressDuration(
                    Util.getTimeSpanString(System.currentTimeMillis()-startTime));
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

    /**
     * Gets the icon color for display.
     */
    public @Nonnull BallColor getIconColor() {
        if(!isBuilding()) {
            // already built
            return getResult().color;
        }

        // a new build is in progress
        BallColor baseColor;
        RunT pb = getPreviousBuild();
        if(pb==null)
            baseColor = BallColor.NOTBUILT;
        else
            baseColor = pb.getIconColor();

        return baseColor.anime();
    }

    /**
     * Returns true if the build is still queued and hasn't started yet.
     */
    public boolean hasntStartedYet() {
        return state ==State.NOT_STARTED;
    }

    @Override
    public String toString() {
        return project.getFullName() + " #" + number;
    }

    @Exported
    public String getFullDisplayName() {
        return project.getFullDisplayName()+' '+getDisplayName();
    }

    @Exported
    public String getDisplayName() {
        return displayName!=null ? displayName : "#"+number;
    }

    public boolean hasCustomDisplayName() {
        return displayName!=null;
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

    @Exported(visibility=2)
    public int getNumber() {
        return number;
    }

    /**
     * Called by {@link RunMap} to obtain a reference to this run.
     * @return Reference to the build. Never null
     * @see jenkins.model.lazy.LazyBuildMixIn.RunMixIn#createReference
     * @since 1.556
     */   
    protected @Nonnull BuildReference<RunT> createReference() {
        return new BuildReference<RunT>(getId(), _this());
    }

    /**
     * Called by {@link RunMap} to drop bi-directional links in preparation for
     * deleting a build.
     * @see jenkins.model.lazy.LazyBuildMixIn.RunMixIn#dropLinks
     * @since 1.556
     */
    protected void dropLinks() {
        if(nextBuild!=null)
            nextBuild.previousBuild = previousBuild;
        if(previousBuild!=null)
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
        RunT r=getPreviousBuild();
        while (r!=null && r.isBuilding())
            r=r.getPreviousBuild();
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
        if(previousBuildInProgress==this)   return null;    // the most common case

        List<RunT> fixUp = new ArrayList<RunT>();
        RunT r = _this(); // 'r' is the source of the pointer (so that we can add it to fix up if we find that the target of the pointer is inefficient.)
        RunT answer;
        while (true) {
            RunT n = r.previousBuildInProgress;
            if (n==null) {// no field computed yet.
                n=r.getPreviousBuild();
                fixUp.add(r);
            }
            if (r==n || n==null) {
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
            f.previousBuildInProgress = answer==null ? f : answer;
        return answer;
    }

    /**
     * Returns the last build that was actually built - i.e., skipping any with Result.NOT_BUILT
     */ 
    public @CheckForNull RunT getPreviousBuiltBuild() {
        RunT r=getPreviousBuild();
        // in certain situations (aborted m2 builds) r.getResult() can still be null, although it should theoretically never happen
        while( r!=null && (r.getResult() == null || r.getResult()==Result.NOT_BUILT) )
            r=r.getPreviousBuild();
        return r;
    }

    /**
     * Returns the last build that didn't fail before this build.
     */ 
    public @CheckForNull RunT getPreviousNotFailedBuild() {
        RunT r=getPreviousBuild();
        while( r!=null && r.getResult()==Result.FAILURE )
            r=r.getPreviousBuild();
        return r;
    }

    /**
     * Returns the last failed build before this build.
     */  
    public @CheckForNull RunT getPreviousFailedBuild() {
        RunT r=getPreviousBuild();
        while( r!=null && r.getResult()!=Result.FAILURE )
            r=r.getPreviousBuild();
        return r;
    }

    /**
     * Returns the last successful build before this build.
     * @since 1.383
     */
    public @CheckForNull RunT getPreviousSuccessfulBuild() {
        RunT r=getPreviousBuild();
        while( r!=null && r.getResult()!=Result.SUCCESS )
            r=r.getPreviousBuild();
        return r;
    }

    /**
     * Returns the last 'numberOfBuilds' builds with a build result >= 'threshold'.
     * 
     * @param numberOfBuilds the desired number of builds
     * @param threshold the build result threshold
     * @return a list with the builds (youngest build first).
     *   May be smaller than 'numberOfBuilds' or even empty
     *   if not enough builds satisfying the threshold have been found. Never null.
     * @since 1.383
     */  
    public @Nonnull List<RunT> getPreviousBuildsOverThreshold(int numberOfBuilds, @Nonnull Result threshold) {
        List<RunT> builds = new ArrayList<RunT>(numberOfBuilds);
        
        RunT r = getPreviousBuild();
        while (r != null && builds.size() < numberOfBuilds) {
            if (!r.isBuilding() && 
                 (r.getResult() != null && r.getResult().isBetterOrEqualTo(threshold))) {
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

    /**
     * Returns the URL of this {@link Run}, relative to the context root of Hudson.
     *
     * @return
     *      String like "job/foo/32/" with trailing slash but no leading slash. 
     */
    // I really messed this up. I'm hoping to fix this some time
    // it shouldn't have trailing '/', and instead it should have leading '/'
    public @Nonnull String getUrl() {

        // RUN may be accessed using permalinks, as "/lastSuccessful" or other, so try to retrieve this base URL
        // looking for "this" in the current request ancestors
        // @see also {@link AbstractItem#getUrl}
        StaplerRequest req = Stapler.getCurrentRequest();
        if (req != null) {
            String seed = Functions.getNearestAncestorUrl(req,this);
            if(seed!=null) {
                // trim off the context path portion and leading '/', but add trailing '/'
                return seed.substring(req.getContextPath().length()+1)+'/';
            }
        }

        return project.getUrl()+getNumber()+'/';
    }

    /**
     * Obtains the absolute URL to this build.
     *
     * @deprecated
     *      This method shall <b>NEVER</b> be used during HTML page rendering, as it's too easy for
     *      misconfiguration to break this value, with network set up like Apache reverse proxy.
     *      This method is only intended for the remote API clients who cannot resolve relative references.
     */
    @Exported(visibility=2,name="url")
    @Deprecated
    public final @Nonnull String getAbsoluteUrl() {
        return project.getAbsoluteUrl()+getNumber()+'/';
    }

    public final @Nonnull String getSearchUrl() {
        return getNumber()+"/";
    }

    /**
     * Unique ID of this build.
     * Usually the decimal form of {@link #number}, but may be a formatted timestamp for historical builds.
     */
    @Exported
    public @Nonnull String getId() {
        return id != null ? id : Integer.toString(number);
    }
    
    @Override
    public @CheckForNull Descriptor getDescriptorByName(String className) {
        return Jenkins.getInstance().getDescriptorByName(className);
    }

    /**
     * Get the root directory of this {@link Run} on the master.
     * Files related to this {@link Run} should be stored below this directory.
     * @return Root directory of this {@link Run} on the master. Never null
     */
    @Override
    public @Nonnull File getRootDir() {
        return new File(project.getBuildDir(), Integer.toString(number));
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
    public final @Nonnull ArtifactManager getArtifactManager() {
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
    public final synchronized @Nonnull ArtifactManager pickArtifactManager() throws IOException {
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
        return new File(getRootDir(),"archive");
    }

    /**
     * Gets the artifacts (relative to {@link #getArtifactsDir()}.
     * @return The list can be empty but never null
     */    
    @Exported  
    public @Nonnull List<Artifact> getArtifacts() {
        return getArtifactsUpTo(Integer.MAX_VALUE);
    }

    /**
     * Gets the first N artifacts.
     * @return The list can be empty but never null
     */ 
    public @Nonnull List<Artifact> getArtifactsUpTo(int artifactsNumber) {
        ArtifactList r = new ArtifactList();
        try {
            addArtifacts(getArtifactManager().root(), "", "", r, null, artifactsNumber);
        } catch (IOException x) {
            LOGGER.log(Level.WARNING, null, x);
        }
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

    private int addArtifacts(@Nonnull VirtualFile dir, 
            @Nonnull String path, @Nonnull String pathHref, 
            @Nonnull ArtifactList r, @Nonnull Artifact parent, int upTo) throws IOException {
        VirtualFile[] kids = dir.list();
        Arrays.sort(kids);

        int n = 0;
        for (VirtualFile sub : kids) {
            String child = sub.getName();
            String childPath = path + child;
            String childHref = pathHref + Util.rawEncode(child);
            String length = sub.isFile() ? String.valueOf(sub.length()) : "";
            boolean collapsed = (kids.length==1 && parent!=null);
            Artifact a;
            if (collapsed) {
                // Collapse single items into parent node where possible:
                a = new Artifact(parent.getFileName() + '/' + child, childPath,
                                 sub.isDirectory() ? null : childHref, length,
                                 parent.getTreeNodeId());
                r.tree.put(a, r.tree.remove(parent));
            } else {
                // Use null href for a directory:
                a = new Artifact(child, childPath,
                                 sub.isDirectory() ? null : childHref, length,
                                 "n" + ++r.idSeq);
                r.tree.put(a, parent!=null ? parent.getTreeNodeId() : null);
            }
            if (sub.isDirectory()) {
                n += addArtifacts(sub, childPath + '/', childHref + '/', r, a, upTo-n);
                if (n>=upTo) break;
            } else {
                // Don't store collapsed path in ArrayList (for correct data in external API)
                r.add(collapsed ? new Artifact(child, a.relativePath, a.href, length, a.treeNodeId) : a);
                if (++n>=upTo) break;
            }
        }
        return n;
    }

    /**
     * Maximum number of artifacts to list before using switching to the tree view.
     */
    public static final int LIST_CUTOFF = Integer.parseInt(SystemProperties.getString("hudson.model.Run.ArtifactList.listCutoff", "16"));

    /**
     * Maximum number of artifacts to show in tree view before just showing a link.
     */
    public static final int TREE_CUTOFF = Integer.parseInt(SystemProperties.getString("hudson.model.Run.ArtifactList.treeCutoff", "40"));

    // ..and then "too many"

    public final class ArtifactList extends ArrayList<Artifact> {
        private static final long serialVersionUID = 1L;
        /**
         * Map of Artifact to treeNodeId of parent node in tree view.
         * Contains Artifact objects for directories and files (the ArrayList contains only files).
         */
        private LinkedHashMap<Artifact,String> tree = new LinkedHashMap<Artifact,String>();
        private int idSeq = 0;

        public Map<Artifact,String> getTree() {
            return tree;
        }

        public void computeDisplayName() {
            if(size()>LIST_CUTOFF)   return; // we are not going to display file names, so no point in computing this

            int maxDepth = 0;
            int[] len = new int[size()];
            String[][] tokens = new String[size()][];
            for( int i=0; i<tokens.length; i++ ) {
                tokens[i] = get(i).relativePath.split("[\\\\/]+");
                maxDepth = Math.max(maxDepth,tokens[i].length);
                len[i] = 1;
            }

            boolean collision;
            int depth=0;
            do {
                collision = false;
                Map<String,Integer/*index*/> names = new HashMap<String,Integer>();
                for (int i = 0; i < tokens.length; i++) {
                    String[] token = tokens[i];
                    String displayName = combineLast(token,len[i]);
                    Integer j = names.put(displayName, i);
                    if(j!=null) {
                        collision = true;
                        if(j>=0)
                            len[j]++;
                        len[i]++;
                        names.put(displayName,-1);  // occupy this name but don't let len[i] incremented with additional collisions
                    }
                }
            } while(collision && depth++<maxDepth);

            for (int i = 0; i < tokens.length; i++)
                get(i).displayPath = combineLast(tokens[i],len[i]);

//            OUTER:
//            for( int n=1; n<maxLen; n++ ) {
//                // if we just display the last n token, would it be suffice for disambiguation?
//                Set<String> names = new HashSet<String>();
//                for (String[] token : tokens) {
//                    if(!names.add(combineLast(token,n)))
//                        continue OUTER; // collision. Increase n and try again
//                }
//
//                // this n successfully diambiguates
//                for (int i = 0; i < tokens.length; i++) {
//                    String[] token = tokens[i];
//                    get(i).displayPath = combineLast(token,n);
//                }
//                return;
//            }

//            // it's impossible to get here, as that means
//            // we have the same artifacts archived twice, but be defensive
//            for (Artifact a : this)
//                a.displayPath = a.relativePath;
        }

        /**
         * Combines last N token into the "a/b/c" form.
         */
        private String combineLast(String[] token, int n) {
            StringBuilder buf = new StringBuilder();
            for( int i=Math.max(0,token.length-n); i<token.length; i++ ) {
                if(buf.length()>0)  buf.append('/');
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
    	@Exported(visibility=3)
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
         *length of this artifact for files.
         */
        private String length;

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
        public @Nonnull File getFile() {
            return new File(getArtifactsDir(),relativePath);
        }

        /**
         * Returns just the file name portion, without the path.
         */
    	@Exported(visibility=3)
        public String getFileName() {
            return name;
        }

    	@Exported(visibility=3)
        public String getDisplayPath() {
            return displayPath;
        }

        public String getHref() {
            return href;
        }

        public String getLength() {
            return length;
        }
        
        public long getFileSize(){
            return Long.decode(length);
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
     * Returns the log file.
     * @return The file may reference both uncompressed or compressed logs
     */  
    public @Nonnull File getLogFile() {
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
     * @throws IOException 
     * @return An input stream from the log file. 
     *   If the log file does not exist, the error message will be returned to the output.
     * @since 1.349
     */
    public @Nonnull InputStream getLogInputStream() throws IOException {
    	File logFile = getLogFile();
    	
    	if (logFile.exists() ) {
    	    // Checking if a ".gz" file was return
    	    FileInputStream fis = new FileInputStream(logFile);
    	    if (logFile.getName().endsWith(".gz")) {
    	        return new GZIPInputStream(fis);
    	    } else {
    	        return fis;
    	    }
    	}
    	
        String message = "No such file: " + logFile;
    	return new ByteArrayInputStream(charset != null ? message.getBytes(charset) : message.getBytes());
    }
   
    public @Nonnull Reader getLogReader() throws IOException {
        if (charset==null)  return new InputStreamReader(getLogInputStream());
        else                return new InputStreamReader(getLogInputStream(),charset);
    }

    /**
     * Used from <tt>console.jelly</tt> to write annotated log to the given output.
     *
     * @since 1.349
     */
    public void writeLogTo(long offset, @Nonnull XMLOutput out) throws IOException {
        try {
			getLogText().writeHtmlTo(offset,out.asWriter());
		} catch (IOException e) {
			// try to fall back to the old getLogInputStream()
			// mainly to support .gz compressed files
			// In this case, console annotation handling will be turned off.
			InputStream input = getLogInputStream();
			try {
				IOUtils.copy(input, out.asWriter());
			} finally {
				IOUtils.closeQuietly(input);
			}
		}
    }

    /**
     * Writes the complete log from the start to finish to the {@link OutputStream}.
     *
     * If someone is still writing to the log, this method will not return until the whole log
     * file gets written out.
     * <p/>
     * The method does not close the {@link OutputStream}.
     */
    public void writeWholeLogTo(@Nonnull OutputStream out) throws IOException, InterruptedException {
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
    public @Nonnull AnnotatedLargeText getLogText() {
        return new AnnotatedLargeText(getLogFile(),getCharset(),!isLogUpdated(),this);
    }

    @Override
    protected @Nonnull SearchIndexBuilder makeSearchIndex() {
        SearchIndexBuilder builder = super.makeSearchIndex()
                .add("console")
                .add("changes");
        for (Action a : getAllActions()) {
            if(a.getIconFileName()!=null)
                builder.add(a.getUrlName());
        }
        return builder;
    }

    public @Nonnull Api getApi() {
        return new Api(this);
    }

    @Override
    public void checkPermission(@Nonnull Permission p) {
        getACL().checkPermission(p);
    }

    @Override
    public boolean hasPermission(@Nonnull Permission p) {
        return getACL().hasPermission(p);
    }

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
        File rootDir = getRootDir();
        if (!rootDir.isDirectory()) {
            throw new IOException(this + ": " + rootDir + " looks to have already been deleted; siblings: " + Arrays.toString(project.getBuildDir().list()));
        }
        
        RunListener.fireDeleted(this);

        synchronized (this) { // avoid holding a lock while calling plugin impls of onDeleted
        File tmp = new File(rootDir.getParentFile(),'.'+rootDir.getName());
        
        if (tmp.exists()) {
            Util.deleteRecursive(tmp);
        }
        // TODO on Java 7 prefer: Files.move(rootDir.toPath(), tmp.toPath(), StandardCopyOption.ATOMIC_MOVE)
        boolean renamingSucceeded = rootDir.renameTo(tmp);
        Util.deleteRecursive(tmp);
        // some user reported that they see some left-over .xyz files in the workspace,
        // so just to make sure we've really deleted it, schedule the deletion on VM exit, too.
        if(tmp.exists())
            tmp.deleteOnExit();

        if(!renamingSucceeded)
            throw new IOException(rootDir+" is in use");
        LOGGER.log(FINE, "{0}: {1} successfully deleted", new Object[] {this, rootDir});

        removeRunFromParent();
        }
    }

    @SuppressWarnings("unchecked") // seems this is too clever for Java's type system?
    private void removeRunFromParent() {
        getParent().removeRun((RunT)this);
    }


    /**
     * @see CheckPoint#report()
     */
    /*package*/ static void reportCheckpoint(@Nonnull CheckPoint id) {
        Run<?,?>.RunExecution exec = RunnerStack.INSTANCE.peek();
        if (exec == null) {
            return;
        }
        exec.checkpoints.report(id);
    }

    /**
     * @see CheckPoint#block()
     */
    /*package*/ static void waitForCheckpoint(@Nonnull CheckPoint id, @CheckForNull BuildListener listener, @CheckForNull String waiter) throws InterruptedException {
        while(true) {
            Run<?,?>.RunExecution exec = RunnerStack.INSTANCE.peek();
            if (exec == null) {
                return;
            }
            Run b = exec.getBuild().getPreviousBuildInProgress();
            if(b==null)     return; // no pending earlier build
            Run.RunExecution runner = b.runner;
            if(runner==null) {
                // polled at the wrong moment. try again.
                Thread.sleep(0);
                continue;
            }
            if(runner.checkpoints.waitForCheckPoint(id, listener, waiter))
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
            private final Set<CheckPoint> checkpoints = new HashSet<CheckPoint>();

            private boolean allDone;

            protected synchronized void report(@Nonnull CheckPoint identifier) {
                checkpoints.add(identifier);
                notifyAll();
            }

            protected synchronized boolean waitForCheckPoint(@Nonnull CheckPoint identifier, @CheckForNull BuildListener listener, @CheckForNull String waiter) throws InterruptedException {
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

        private final Map<Object,Object> attributes = new HashMap<Object, Object>();

        /**
         * Performs the main build and returns the status code.
         *
         * @throws Exception
         *      exception will be recorded and the build will be considered a failure.
         */
        public abstract @Nonnull Result run(@Nonnull BuildListener listener ) throws Exception, RunnerAbortedException;

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
        public abstract void post(@Nonnull BuildListener listener ) throws Exception;

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
        public abstract void cleanUp(@Nonnull BuildListener listener) throws Exception;

        public @Nonnull RunT getBuild() {
            return _this();
        }

        public @Nonnull JobT getProject() {
            return _this().getParent();
        }

        /**
         * Bag of stuff to allow plugins to store state for the duration of a build
         * without persisting it.
         *
         * @since 1.473
         */
        public @Nonnull Map<Object,Object> getAttributes() {
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
     *      Use {@link #execute(RunExecution)}
     */
    @Deprecated
    protected final void run(@Nonnull Runner job) {
        execute(job);
    }

    protected final void execute(@Nonnull RunExecution job) {
        if(result!=null)
            return;     // already built.

        StreamBuildListener listener=null;

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
                    listener = createBuildListener(job, listener, charset);
                    listener.started(getCauses());

                    Authentication auth = Jenkins.getAuthentication();
                    if (!auth.equals(ACL.SYSTEM)) {
                        String name = auth.getName();
                        if (!auth.equals(Jenkins.ANONYMOUS)) {
                            name = ModelHyperlinkNote.encodeTo(User.get(name));
                        }
                        listener.getLogger().println(Messages.Run_running_as_(name));
                    }

                    RunListener.fireStarted(this,listener);

                    updateSymlinks(listener);

                    setResult(job.run(listener));

                    LOGGER.log(INFO, "{0} main build action completed: {1}", new Object[] {this, result});
                    CheckPoint.MAIN_COMPLETED.report();
                } catch (ThreadDeath t) {
                    throw t;
                } catch( AbortException e ) {// orderly abortion.
                    result = Result.FAILURE;
                    listener.error(e.getMessage());
                    LOGGER.log(FINE, "Build "+this+" aborted",e);
                } catch( RunnerAbortedException e ) {// orderly abortion.
                    result = Result.FAILURE;
                    LOGGER.log(FINE, "Build "+this+" aborted",e);
                } catch( InterruptedException e) {
                    // aborted
                    result = Executor.currentExecutor().abortResult();
                    listener.getLogger().println(Messages.Run_BuildAborted());
                    Executor.currentExecutor().recordCauseOfInterruption(Run.this,listener);
                    LOGGER.log(Level.INFO, this + " aborted", e);
                } catch( Throwable e ) {
                    handleFatalBuildProblem(listener,e);
                    result = Result.FAILURE;
                }

                // even if the main build fails fatally, try to run post build processing
                job.post(listener);

            } catch (ThreadDeath t) {
                throw t;
            } catch( Throwable e ) {
                handleFatalBuildProblem(listener,e);
                result = Result.FAILURE;
            } finally {
                long end = System.currentTimeMillis();
                duration = Math.max(end - start, 0);  // @see HUDSON-5844

                // advance the state.
                // the significance of doing this is that Jenkins
                // will now see this build as completed.
                // things like triggering other builds requires this as pre-condition.
                // see issue #980.
                LOGGER.log(FINER, "moving into POST_PRODUCTION on {0}", this);
                state = State.POST_PRODUCTION;

                if (listener != null) {
                    RunListener.fireCompleted(this,listener);
                    try {
                        job.cleanUp(listener);
                    } catch (Exception e) {
                        handleFatalBuildProblem(listener,e);
                        // too late to update the result now
                    }
                    listener.finished(result);
                    listener.closeQuietly();
                }

                try {
                    save();
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed to save build record",e);
                }
            }

            try {
                getParent().logRotate();
            } catch (Exception e) {
		LOGGER.log(Level.SEVERE, "Failed to rotate log",e);
	    }
        } finally {
            onEndBuilding();
        }
    }

    private StreamBuildListener createBuildListener(@Nonnull RunExecution job, StreamBuildListener listener, Charset charset) throws IOException, InterruptedException {
        // don't do buffering so that what's written to the listener
        // gets reflected to the file immediately, which can then be
        // served to the browser immediately
        OutputStream logger = new FileOutputStream(getLogFile(), true);
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

        listener = new StreamBuildListener(logger,charset);
        return listener;
    }

    /**
     * Makes sure that {@code lastSuccessful} and {@code lastStable} legacy links in the project’s root directory exist.
     * Normally you do not need to call this explicitly, since {@link #execute} does so,
     * but this may be needed if you are creating synthetic {@link Run}s as part of a container project (such as Maven builds in a module set).
     * You should also ensure that {@link RunListener#fireStarted} and {@link RunListener#fireCompleted} are called.
     * @param listener probably unused
     * @throws InterruptedException probably not thrown
     * @since 1.530
     */
    public final void updateSymlinks(@Nonnull TaskListener listener) throws InterruptedException {
        createSymlink(listener, "lastSuccessful", PermalinkProjectAction.Permalink.LAST_SUCCESSFUL_BUILD);
        createSymlink(listener, "lastStable", PermalinkProjectAction.Permalink.LAST_STABLE_BUILD);
    }
    /**
     * Backward compatibility.
     *
     * We used to have $JENKINS_HOME/jobs/JOBNAME/lastStable and lastSuccessful symlinked to the appropriate
     * builds, but now those are done in {@link PeepholePermalink}. So here, we simply create symlinks that
     * resolves to the symlink created by {@link PeepholePermalink}.
     */
    private void createSymlink(@Nonnull TaskListener listener, @Nonnull String name, @Nonnull PermalinkProjectAction.Permalink target) throws InterruptedException {
        File buildDir = getParent().getBuildDir();
        File rootDir = getParent().getRootDir();
        String targetDir;
        if (buildDir.equals(new File(rootDir, "builds"))) {
            targetDir = "builds" + File.separator + target.getId();
        } else {
            targetDir = buildDir + File.separator + target.getId();
        }
        Util.createSymlink(rootDir, targetDir, name, listener);
    }

    /**
     * Handles a fatal build problem (exception) that occurred during the build.
     */
    private void handleFatalBuildProblem(@Nonnull BuildListener listener, @Nonnull Throwable e) {
        if(listener!=null) {
            LOGGER.log(FINE, getDisplayName()+" failed to build",e);

            if(e instanceof IOException)
                Util.displayIOException((IOException)e,listener);

            e.printStackTrace(listener.fatalError(e.getMessage()));
        } else {
            LOGGER.log(SEVERE, getDisplayName()+" failed to build and we don't even have a listener",e);
        }
    }

    /**
     * Called when a job started building.
     */
    protected void onStartBuilding() {
        LOGGER.log(FINER, "moving to BUILDING on {0}", this);
        state = State.BUILDING;
        startTime = System.currentTimeMillis();
        if (runner!=null)
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
        if (runner!=null) {
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
    public synchronized void save() throws IOException {
        if(BulkChange.contains(this))   return;
        getDataFile().write(this);
        SaveableListener.fireOnChange(this, getDataFile());
    }

    private @Nonnull XmlFile getDataFile() {
        return new XmlFile(XSTREAM,new File(getRootDir(),"build.xml"));
    }

    /**
     * Gets the log of the build as a string.
     * @return Returns the log or an empty string if it has not been found
     * @deprecated since 2007-11-11.
     *     Use {@link #getLog(int)} instead as it avoids loading
     *     the whole log into memory unnecessarily.
     */
    @Deprecated
    public @Nonnull String getLog() throws IOException {
        return Util.loadFile(getLogFile(),getCharset());
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
    public @Nonnull List<String> getLog(int maxLines) throws IOException {
        int lineCount = 0;
        List<String> logLines = new LinkedList<String>();
        if (maxLines == 0) {
            return logLines;
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(getLogFile()),getCharset()));
        try {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                logLines.add(line);
                ++lineCount;
                // If we have too many lines, remove the oldest line.  This way we
                // never have to hold the full contents of a huge log file in memory.
                // Adding to and removing from the ends of a linked list are cheap
                // operations.
                if (lineCount > maxLines)
                    logLines.remove(0);
            }
        } finally {
            reader.close();
        }

        // If the log has been truncated, include that information.
        // Use set (replaces the first element) rather than add so that
        // the list doesn't grow beyond the specified maximum number of lines.
        if (lineCount > maxLines)
            logLines.set(0, "[...truncated " + (lineCount - (maxLines - 1)) + " lines...]");

        return ConsoleNote.removeNotes(logLines);
    }

    public void doBuildStatus( StaplerRequest req, StaplerResponse rsp ) throws IOException {
        rsp.sendRedirect2(req.getContextPath()+"/images/48x48/"+getBuildStatusUrl());
    }

    public @Nonnull String getBuildStatusUrl() {
        return getIconColor().getImage();
    }

    public String getBuildStatusIconClassName() {
        return getIconColor().getIconClassName();
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
    public static abstract class StatusSummarizer implements ExtensionPoint {
        /**
         * Possibly summarizes the reasons for a build’s status.
         * @param run a completed build
         * @param trend the result of {@link ResultTrend#getResultTrend(hudson.model.Run)} on {@code run} (precomputed for efficiency)
         * @return a summary, or null to fall back to other summarizers or built-in behavior
         */
        public abstract @CheckForNull Summary summarize(@Nonnull Run<?,?> run, @Nonnull ResultTrend trend);
    }

    /**
     * Gets an object which represents the single line summary of the status of this build
     * (especially in comparison with the previous build.)
     * @see StatusSummarizer
     */
    public @Nonnull Summary getBuildStatusSummary() {
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
            case ABORTED : return new Summary(false, Messages.Run_Summary_Aborted());
            
            case NOT_BUILT : return new Summary(false, Messages.Run_Summary_NotBuilt());
            
            case FAILURE : return new Summary(true, Messages.Run_Summary_BrokenSinceThisBuild());
            
            case STILL_FAILING : 
                RunT since = getPreviousNotFailedBuild();
                if(since==null)
                    return new Summary(false, Messages.Run_Summary_BrokenForALongTime());
                RunT failedBuild = since.getNextBuild();
                return new Summary(false, Messages.Run_Summary_BrokenSince(failedBuild.getDisplayName()));
           
            case NOW_UNSTABLE:
            case STILL_UNSTABLE :
                return new Summary(false, Messages.Run_Summary_Unstable());
            case UNSTABLE :
                return new Summary(true, Messages.Run_Summary_Unstable());
                
            case SUCCESS :
                return new Summary(false, Messages.Run_Summary_Stable());
            
            case FIXED :
                return new Summary(false, Messages.Run_Summary_BackToNormal());
                
        }
        
        return new Summary(false, Messages.Run_Summary_Unknown());
    }

    /**
     * Serves the artifacts.
     * @throws AccessDeniedException Access denied
     */
    public @Nonnull DirectoryBrowserSupport doArtifact() {
        if(Functions.isArtifactsPermissionEnabled()) {
          checkPermission(ARTIFACTS);
        }
        return new DirectoryBrowserSupport(this, getArtifactManager().root(), Messages.Run_ArtifactsBrowserTitle(project.getDisplayName(), getDisplayName()), "package.png", true);
    }

    /**
     * Returns the build number in the body.
     */
    public void doBuildNumber(StaplerResponse rsp) throws IOException {
        rsp.setContentType("text/plain");
        rsp.setCharacterEncoding("US-ASCII");
        rsp.setStatus(HttpServletResponse.SC_OK);
        rsp.getWriter().print(number);
    }

    /**
     * Returns the build time stamp in the body.
     */
    public void doBuildTimestamp( StaplerRequest req, StaplerResponse rsp, @QueryParameter String format) throws IOException {
        rsp.setContentType("text/plain");
        rsp.setCharacterEncoding("US-ASCII");
        rsp.setStatus(HttpServletResponse.SC_OK);
        DateFormat df = format==null ?
                DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT, Locale.ENGLISH) :
                new SimpleDateFormat(format,req.getLocale());
        rsp.getWriter().print(df.format(getTime()));
    }

    /**
     * Sends out the raw console output.
     */
    public void doConsoleText(StaplerRequest req, StaplerResponse rsp) throws IOException {
        rsp.setContentType("text/plain;charset=UTF-8");
        PlainTextConsoleOutputStream out = new PlainTextConsoleOutputStream(rsp.getCompressedOutputStream(req));
        InputStream input = getLogInputStream();
        try {
            IOUtils.copy(input, out);
            out.flush();
        } finally {
            IOUtils.closeQuietly(input);
            IOUtils.closeQuietly(out);
        }
    }

    /**
     * Handles incremental log output.
     * @deprecated as of 1.352
     *      Use {@code getLogText().doProgressiveText(req,rsp)}
     */
    @Deprecated
    public void doProgressiveLog( StaplerRequest req, StaplerResponse rsp) throws IOException {
        getLogText().doProgressText(req,rsp);
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

    public void doToggleLogKeep( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        keepLog(!keepLog);
        rsp.forwardToPreviousPage(req);
    }

    /**
     * Marks this build to keep the log.
     */
    @CLIMethod(name="keep-build")
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
     */
    @RequirePOST
    public void doDoDelete( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        checkPermission(DELETE);

        // We should not simply delete the build if it has been explicitly
        // marked to be preserved, or if the build should not be deleted
        // due to dependencies!
        String why = getWhyKeepLog();
        if (why!=null) {
            sendError(Messages.Run_UnableToDelete(getFullDisplayName(), why), req, rsp);
            return;
        }

        try{
            delete();
        }
        catch(IOException ex){
            StringWriter writer = new StringWriter();
            ex.printStackTrace(new PrintWriter(writer));
            req.setAttribute("stackTraces", writer);
            req.getView(this, "delete-retry.jelly").forward(req, rsp);  
            return;
        }
        rsp.sendRedirect2(req.getContextPath()+'/' + getParent().getUrl());
    }

    public void setDescription(String description) throws IOException {
        checkPermission(UPDATE);
        this.description = description;
        save();
    }
    
    /**
     * Accepts the new description.
     */
    public synchronized void doSubmitDescription( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        setDescription(req.getParameter("description"));
        rsp.sendRedirect(".");  // go to the top page
    }

    /**
     * @deprecated as of 1.292
     *      Use {@link #getEnvironment(TaskListener)} instead.
     */
    @Deprecated
    public Map<String,String> getEnvVars() {
        LOGGER.log(WARNING, "deprecated call to Run.getEnvVars\n\tat {0}", new Throwable().getStackTrace()[1]);
        try {
            return getEnvironment(new LogTaskListener(LOGGER, Level.INFO));
        } catch (IOException e) {
            return new EnvVars();
        } catch (InterruptedException e) {
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
    public @Nonnull EnvVars getEnvironment(@Nonnull TaskListener listener) throws IOException, InterruptedException {
        Computer c = Computer.currentComputer();
        Node n = c==null ? null : c.getNode();

        EnvVars env = getParent().getEnvironment(n,listener);
        env.putAll(getCharacteristicEnvVars());

        // apply them in a reverse order so that higher ordinal ones can modify values added by lower ordinal ones
        for (EnvironmentContributor ec : EnvironmentContributor.all().reverseView())
            ec.buildEnvironmentFor(this,env,listener);

        return env;
    }

    /**
     * Builds up the environment variable map that's sufficient to identify a process
     * as ours. This is used to kill run-away processes via {@link ProcessTree#killAll(Map)}.
     */
    public @Nonnull final EnvVars getCharacteristicEnvVars() {
        EnvVars env = getParent().getCharacteristicEnvVars();
        env.put("BUILD_NUMBER",String.valueOf(number));
        env.put("BUILD_ID",getId());
        env.put("BUILD_TAG","jenkins-"+getParent().getFullName().replace('/', '-')+"-"+number);
        return env;
    }

    /**
     * Produces an identifier for this run unique in the system.
     * @return the {@link Job#getFullName}, then {@code #}, then {@link #getNumber}
     * @see #fromExternalizableId
     */
    public @Nonnull String getExternalizableId() {
        return project.getFullName() + "#" + getNumber();
    }

    /**
     * Tries to find a run from an persisted identifier.
     * @param id as produced by {@link #getExternalizableId}
     * @return the same run, or null if the job or run was not found
     * @throws IllegalArgumentException if the ID is malformed
     */
    public @CheckForNull static Run<?,?> fromExternalizableId(String id) throws IllegalArgumentException {
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
        Jenkins j = Jenkins.getInstance();
        Job<?,?> job = j.getItemByFullName(jobName, Job.class);
        if (job == null) {
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

    @RequirePOST
    public @Nonnull HttpResponse doConfigSubmit( StaplerRequest req ) throws IOException, ServletException, FormException {
        checkPermission(UPDATE);
        BulkChange bc = new BulkChange(this);
        try {
            JSONObject json = req.getSubmittedForm();
            submit(json);
            bc.commit();
        } finally {
            bc.abort();
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
    public static final XStream2 XSTREAM2 = (XStream2)XSTREAM;

    static {
        XSTREAM.alias("build",FreeStyleBuild.class);
        XSTREAM.registerConverter(Result.conv);
    }

    private static final Logger LOGGER = Logger.getLogger(Run.class.getName());

    /**
     * Sort by date. Newer ones first. 
     */
    public static final Comparator<Run> ORDER_BY_DATE = new Comparator<Run>() {
        public int compare(@Nonnull Run lhs, @Nonnull Run rhs) {
            long lt = lhs.getTimeInMillis();
            long rt = rhs.getTimeInMillis();
            if(lt>rt)   return -1;
            if(lt<rt)   return 1;
            return 0;
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
            return "tag:hudson.dev.java.net,2008:"+e.getParent().getAbsoluteUrl();
        }
    };

    /**
     * {@link BuildBadgeAction} that shows the logs are being kept.
     */
    public final class KeepLogBuildBadge implements BuildBadgeAction {
        public @CheckForNull String getIconFileName() { return null; }
        public @CheckForNull String getDisplayName() { return null; }
        public @CheckForNull String getUrlName() { return null; }
        public @CheckForNull String getWhyKeepLog() { return Run.this.getWhyKeepLog(); }
    }

    public static final PermissionGroup PERMISSIONS = new PermissionGroup(Run.class,Messages._Run_Permissions_Title());
    public static final Permission DELETE = new Permission(PERMISSIONS,"Delete",Messages._Run_DeletePermission_Description(),Permission.DELETE, PermissionScope.RUN);
    public static final Permission UPDATE = new Permission(PERMISSIONS,"Update",Messages._Run_UpdatePermission_Description(),Permission.UPDATE, PermissionScope.RUN);
    /** See {@link hudson.Functions#isArtifactsPermissionEnabled} */
    public static final Permission ARTIFACTS = new Permission(PERMISSIONS,"Artifacts",Messages._Run_ArtifactsPermission_Description(), null,
                                                              Functions.isArtifactsPermissionEnabled(), new PermissionScope[]{PermissionScope.RUN});

    private static class DefaultFeedAdapter implements FeedAdapter<Run> {
        public String getEntryTitle(Run entry) {
            return entry+" ("+entry.getBuildStatusSummary().message+")";
        }

        public String getEntryUrl(Run entry) {
            return entry.getUrl();
        }

        public String getEntryID(Run entry) {
            return "tag:" + "hudson.dev.java.net,"
                + entry.getTimestamp().get(Calendar.YEAR) + ":"
                + entry.getParent().getFullName()+':'+entry.getId();
        }

        public String getEntryDescription(Run entry) {
            return entry.getDescription();
        }

        public Calendar getEntryTimestamp(Run entry) {
            return entry.getTimestamp();
        }

        public String getEntryAuthor(Run entry) {
            return JenkinsLocationConfiguration.get().getAdminAddress();
        }
    }

    @Override
    public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        Object returnedResult = super.getDynamic(token, req, rsp);
        if (returnedResult == null){
            //check transient actions too
            for(Action action: getTransientActions()){
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

    public static class RedirectUp {
        public void doDynamic(StaplerResponse rsp) throws IOException {
            // Compromise to handle both browsers (auto-redirect) and programmatic access
            // (want accurate 404 response).. send 404 with javscript to redirect browsers.
            rsp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            rsp.setContentType("text/html;charset=UTF-8");
            PrintWriter out = rsp.getWriter();
            out.println("<html><head>" +
                "<meta http-equiv='refresh' content='1;url=..'/>" +
                "<script>window.location.replace('..');</script>" +
                "</head>" +
                "<body style='background-color:white; color:white;'>" +
                "Not found</body></html>");
            out.flush();
        }
    }
}
