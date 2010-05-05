/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Daniel Dyer, Red Hat, Inc., Tom Huybrechts
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

import hudson.AbortException;
import hudson.BulkChange;
import hudson.EnvVars;
import hudson.ExtensionPoint;
import hudson.FeedAdapter;
import hudson.FilePath;
import hudson.Util;
import hudson.XmlFile;
import hudson.cli.declarative.CLIMethod;
import hudson.console.AnnotatedLargeText;
import hudson.console.ConsoleNote;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import hudson.model.listeners.RunListener;
import hudson.model.listeners.SaveableListener;
import hudson.search.SearchIndexBuilder;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.tasks.LogRotator;
import hudson.tasks.Mailer;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildStep;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.util.FlushProofOutputStream;
import hudson.util.IOException2;
import hudson.util.LogTaskListener;
import hudson.util.XStream2;
import hudson.util.ProcessTree;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.jelly.XMLOutput;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import com.thoughtworks.xstream.XStream;

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
        extends Actionable implements ExtensionPoint, Comparable<RunT>, AccessControlled, PersistenceRoot, DescriptorByNameOwner {

    protected transient final JobT project;

    /**
     * Build number.
     *
     * <p>
     * In earlier versions &lt; 1.24, this number is not unique nor continuous,
     * but going forward, it will, and this really replaces the build id.
     */
    public /*final*/ int number;

    /**
     * Previous build. Can be null.
     * These two fields are maintained and updated by {@link RunMap}.
     */
    protected volatile transient RunT previousBuild;

    /**
     * Next build. Can be null.
     */
    protected volatile transient RunT nextBuild;

    /**
     * Pointer to the next younger build in progress. This data structure is lazily updated,
     * so it may point to the build that's already completed. This pointer is set to 'this'
     * if the computation determines that everything earlier than this build is already completed.
     */
    /* does not compile on JDK 7: private*/ volatile transient RunT previousBuildInProgress;

    /**
     * When the build is scheduled.
     */
    protected transient final long timestamp;

    /**
     * The build result.
     * This value may change while the state is in {@link State#BUILDING}.
     */
    protected volatile Result result;

    /**
     * Human-readable description. Can be null.
     */
    protected volatile String description;

    /**
     * The current build state.
     */
    protected volatile transient State state;

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
     * If the build is in progress, remember {@link Runner} that's running it.
     * This field is not persisted.
     */
    private volatile transient Runner runner;

    protected static final ThreadLocal<SimpleDateFormat> ID_FORMATTER =
            new ThreadLocal<SimpleDateFormat>() {
                @Override
                protected SimpleDateFormat initialValue() {
                    return new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
                }
            };

    /**
     * Creates a new {@link Run}.
     */
    protected Run(JobT job) throws IOException {
        this(job, new GregorianCalendar());
        this.number = project.assignBuildNumber();
    }

    /**
     * Constructor for creating a {@link Run} object in
     * an arbitrary state.
     */
    protected Run(JobT job, Calendar timestamp) {
        this(job,timestamp.getTimeInMillis());
    }

    protected Run(JobT job, long timestamp) {
        this.project = job;
        this.timestamp = timestamp;
        this.state = State.NOT_STARTED;
    }

    /**
     * Loads a run from a log file.
     */
    protected Run(JobT project, File buildDir) throws IOException {
        this(project, parseTimestampFromBuildDir(buildDir));
        this.previousBuildInProgress = _this(); // loaded builds are always completed
        this.state = State.COMPLETED;
        this.result = Result.FAILURE;  // defensive measure. value should be overwritten by unmarshal, but just in case the saved data is inconsistent
        getDataFile().unmarshal(this); // load the rest of the data
    }

    /*package*/ static long parseTimestampFromBuildDir(File buildDir) throws IOException {
        try {
            return ID_FORMATTER.get().parse(buildDir.getName()).getTime();
        } catch (ParseException e) {
            throw new IOException2("Invalid directory name "+buildDir,e);
        } catch (NumberFormatException e) {
            throw new IOException2("Invalid directory name "+buildDir,e);
        }
    }

    /**
     * Obtains 'this' in a more type safe signature.
     */
    @SuppressWarnings({"unchecked"})
    private RunT _this() {
        return (RunT)this;
    }

    /**
     * Ordering based on build numbers.
     */
    public int compareTo(RunT that) {
        return this.number - that.number;
    }

    /**
     * Returns the build result.
     *
     * <p>
     * When a build is {@link #isBuilding() in progress}, this method
     * returns an intermediate result.
     */
    @Exported
    public Result getResult() {
        return result;
    }

    public void setResult(Result r) {
        // state can change only when we are building
        assert state==State.BUILDING;

        StackTraceElement caller = findCaller(Thread.currentThread().getStackTrace(),"setResult");


        // result can only get worse
        if(result==null) {
            result = r;
            LOGGER.fine(toString()+" : result is set to "+r+" by "+caller);
        } else {
            if(r.isWorseThan(result)) {
                LOGGER.fine(toString()+" : result is set to "+r+" by "+caller);
                result = r;
            }
        }
    }

    /**
     * Gets the subset of {@link #getActions()} that consists of {@link BuildBadgeAction}s.
     */
    public List<BuildBadgeAction> getBadgeActions() {
        List<BuildBadgeAction> r = null;
        for (Action a : getActions()) {
            if(a instanceof BuildBadgeAction) {
                if(r==null)
                    r = new ArrayList<BuildBadgeAction>();
                r.add((BuildBadgeAction)a);
            }
        }
        if(isKeepLog()) {
            if(r==null)
                r = new ArrayList<BuildBadgeAction>();
            r.add(new KeepLogBuildBadge());
        }
        if(r==null)     return Collections.emptyList();
        else            return r;
    }

    private StackTraceElement findCaller(StackTraceElement[] stackTrace, String callee) {
        for(int i=0; i<stackTrace.length-1; i++) {
            StackTraceElement e = stackTrace[i];
            if(e.getMethodName().equals(callee))
                return stackTrace[i+1];
        }
        return null; // not found
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
     * Returns true if the log file is still being updated.
     */
    public boolean isLogUpdated() {
        return state.compareTo(State.COMPLETED) < 0;
    }

    /**
     * Gets the {@link Executor} building this job, if it's being built.
     * Otherwise null.
     */
    public Executor getExecutor() {
        for( Computer c : Hudson.getInstance().getComputers() ) {
            for (Executor e : c.getExecutors()) {
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
    public final Charset getCharset() {
        if(charset==null)   return Charset.defaultCharset();
        return Charset.forName(charset);
    }

    /**
     * Returns the {@link Cause}s that tirggered a build.
     *
     * <p>
     * If a build sits in the queue for a long time, multiple build requests made during this period
     * are all rolled up into one build, hence this method may return a list.
     *
     * @return
     *      can be empty but never null. read-only.
     * @since 1.321
     */
    public List<Cause> getCauses() {
        CauseAction a = getAction(CauseAction.class);
        if (a==null)    return Collections.emptyList();
        return Collections.unmodifiableList(a.getCauses());
    }

    /**
     * Returns true if this log file should be kept and not deleted.
     *
     * This is used as a signal to the {@link LogRotator}.
     */
    @Exported
    public final boolean isKeepLog() {
        return getWhyKeepLog()!=null;
    }

    /**
     * If {@link #isKeepLog()} returns true, returns a human readable
     * one-line string that explains why it's being kept.
     */
    public String getWhyKeepLog() {
        if(keepLog)
            return Messages.Run_MarkedExplicitly();
        return null;    // not marked at all
    }

    /**
     * The project this build is for.
     */
    public JobT getParent() {
        return project;
    }

    /**
     * When the build is scheduled.
     */
    @Exported
    public Calendar getTimestamp() {
        GregorianCalendar c = new GregorianCalendar();
        c.setTimeInMillis(timestamp);
        return c;
    }

    /**
     * Same as {@link #getTimestamp()} but in a different type.
     */
    public final Date getTime() {
        return new Date(timestamp);
    }

    /**
     * Same as {@link #getTimestamp()} but in a different type, that is since the time of the epoc.
     */
    public final long getTimeInMillis() {
        return timestamp;
    }

    @Exported
    public String getDescription() {
        return description;
    }


    /**
     * Returns the length-limited description.
     * @return The length-limited description.
     */
    public String getTruncatedDescription() {
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
    public String getTimestampString() {
        long duration = new GregorianCalendar().getTimeInMillis()-timestamp;
        return Util.getPastTimeString(duration);
    }

    /**
     * Returns the timestamp formatted in xs:dateTime.
     */
    public String getTimestampString2() {
        return Util.XS_DATETIME_FORMATTER.format(new Date(timestamp));
    }

    /**
     * Gets the string that says how long the build took to run.
     */
    public String getDurationString() {
        if(isBuilding())
            return Messages.Run_InProgressDuration(
                    Util.getTimeSpanString(System.currentTimeMillis()-timestamp));
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
    public BallColor getIconColor() {
        if(!isBuilding()) {
            // already built
            return getResult().color;
        }

        // a new build is in progress
        BallColor baseColor;
        if(previousBuild==null)
            baseColor = BallColor.GREY;
        else
            baseColor = previousBuild.getIconColor();

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
        return getFullDisplayName();
    }

    @Exported
    public String getFullDisplayName() {
        return project.getFullDisplayName()+" #"+number;
    }

    public String getDisplayName() {
        return "#"+number;
    }

    @Exported(visibility=2)
    public int getNumber() {
        return number;
    }

    public RunT getPreviousBuild() {
        return previousBuild;
    }

    /**
     * Gets the most recent {@linkplain #isBuilding() completed} build excluding 'this' Run itself.
     */
    public final RunT getPreviousCompletedBuild() {
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
    public final RunT getPreviousBuildInProgress() {
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
    public RunT getPreviousBuiltBuild() {
        RunT r=previousBuild;
        while( r!=null && r.getResult()==Result.NOT_BUILT )
            r=r.previousBuild;
        return r;
    }

    /**
     * Returns the last build that didn't fail before this build.
     */
    public RunT getPreviousNotFailedBuild() {
        RunT r=previousBuild;
        while( r!=null && r.getResult()==Result.FAILURE )
            r=r.previousBuild;
        return r;
    }

    /**
     * Returns the last failed build before this build.
     */
    public RunT getPreviousFailedBuild() {
        RunT r=previousBuild;
        while( r!=null && r.getResult()!=Result.FAILURE )
            r=r.previousBuild;
        return r;
    }

    public RunT getNextBuild() {
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
    public String getUrl() {
        return project.getUrl()+getNumber()+'/';
    }

    /**
     * Obtains the absolute URL to this build.
     *
     * @deprecated
     *      This method shall <b>NEVER</b> be used during HTML page rendering, as it won't work with
     *      network set up like Apache reverse proxy.
     *      This method is only intended for the remote API clients who cannot resolve relative references
     *      (even this won't work for the same reason, which should be fixed.)
     */
    @Exported(visibility=2,name="url")
    public final String getAbsoluteUrl() {
        return project.getAbsoluteUrl()+getNumber()+'/';
    }

    public final String getSearchUrl() {
        return getNumber()+"/";
    }

    /**
     * Unique ID of this build.
     */
    @Exported
    public String getId() {
        return ID_FORMATTER.get().format(new Date(timestamp));
    }
    
    /**
     * Get the date formatter used to convert the directory name in to a timestamp
     * This is nasty exposure of private data, but needed all the time the directory
     * containing the build is used as it's timestamp.
     */
    public static DateFormat getIDFormatter() {
    	return ID_FORMATTER.get();
    }

    public Descriptor getDescriptorByName(String className) {
        return Hudson.getInstance().getDescriptorByName(className);
    }

    /**
     * Root directory of this {@link Run} on the master.
     *
     * Files related to this {@link Run} should be stored below this directory.
     */
    public File getRootDir() {
        File f = new File(project.getBuildDir(),getId());
        f.mkdirs();
        return f;
    }

    /**
     * Gets the directory where the artifacts are archived.
     */
    public File getArtifactsDir() {
        return new File(getRootDir(),"archive");
    }

    /**
     * Gets the artifacts (relative to {@link #getArtifactsDir()}.
     */
    @Exported
    public List<Artifact> getArtifacts() {
        return getArtifactsUpTo(Integer.MAX_VALUE);
    }

    /**
     * Gets the first N artifacts.
     */
    public List<Artifact> getArtifactsUpTo(int n) {
        ArtifactList r = new ArtifactList();
        addArtifacts(getArtifactsDir(),"","",r,null,n);
        r.computeDisplayName();
        return r;
    }

    /**
     * Returns true if this run has any artifacts.
     *
     * <p>
     * The strange method name is so that we can access it from EL.
     */
    public boolean getHasArtifacts() {
        return !getArtifactsUpTo(1).isEmpty();
    }

    private int addArtifacts( File dir, String path, String pathHref, ArtifactList r, Artifact parent, int upTo ) {
        String[] children = dir.list();
        if(children==null)  return 0;
        Arrays.sort(children, String.CASE_INSENSITIVE_ORDER);

        int n = 0;
        for (String child : children) {
            String childPath = path + child;
            String childHref = pathHref + Util.rawEncode(child);
            File sub = new File(dir, child);
            boolean collapsed = (children.length==1 && parent!=null);
            Artifact a;
            if (collapsed) {
                // Collapse single items into parent node where possible:
                a = new Artifact(parent.getFileName() + '/' + child, childPath,
                                 sub.isDirectory() ? null : childHref, parent.getTreeNodeId());
                r.tree.put(a, r.tree.remove(parent));
            } else {
                // Use null href for a directory:
                a = new Artifact(child, childPath,
                                 sub.isDirectory() ? null : childHref, "n" + ++r.idSeq);
                r.tree.put(a, parent!=null ? parent.getTreeNodeId() : null);
            }
            if (sub.isDirectory()) {
                n += addArtifacts(sub, childPath + '/', childHref + '/', r, a, upTo-n);
                if (n>=upTo) break;
            } else {
                // Don't store collapsed path in ArrayList (for correct data in external API)
                r.add(collapsed ? new Artifact(child, a.relativePath, a.href, a.treeNodeId) : a);
                if (++n>=upTo) break;;
            }
        }
        return n;
    }

    /**
     * Maximum number of artifacts to list before using switching to the tree view.
     */
    public static final int LIST_CUTOFF = Integer.parseInt(System.getProperty("hudson.model.Run.ArtifactList.listCutoff", "16"));

    /**
     * Maximum number of artifacts to show in tree view before just showing a link.
     */
    public static final int TREE_CUTOFF = Integer.parseInt(System.getProperty("hudson.model.Run.ArtifactList.treeCutoff", "40"));

    // ..and then "too many"

    public final class ArtifactList extends ArrayList<Artifact> {
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
         * Relative path name from {@link Run#getArtifactsDir()}
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

        /*package for test*/ Artifact(String name, String relativePath, String href, String treeNodeId) {
            this.name = name;
            this.relativePath = relativePath;
            this.href = href;
            this.treeNodeId = treeNodeId;
        }

        /**
         * Gets the artifact file.
         */
        public File getFile() {
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
     */
    public File getLogFile() {
        return new File(getRootDir(),"log");
    }

    /**
     * Returns an input stream that reads from the log file.
     * It will use a gzip-compressed log file (log.gz) if that exists.
     *
     * @throws IOException 
     * @return an input stream from the log file, or null if none exists
     * @since 1.349
     */
    public InputStream getLogInputStream() throws IOException {
    	File logFile = getLogFile();
    	if (logFile.exists() ) {
            return new FileInputStream(logFile);
    	}

    	File compressedLogFile = new File(logFile.getParentFile(), logFile.getName()+ ".gz");
    	if (compressedLogFile.exists()) {
            return new GZIPInputStream(new FileInputStream(compressedLogFile));
    	}
    	
    	return new NullInputStream(0);
    }

    public Reader getLogReader() throws IOException {
        if (charset==null)  return new InputStreamReader(getLogInputStream());
        else                return new InputStreamReader(getLogInputStream(),charset);
    }

    /**
     * Used from <tt>console.jelly</tt> to write annotated log to the given output.
     *
     * @since 1.349
     */
    public void writeLogTo(long offset, XMLOutput out) throws IOException {
        // TODO: resurrect compressed log file support
        getLogText().writeHtmlTo(offset,out.asWriter());
    }

    /**
     * Used to URL-bind {@link AnnotatedLargeText}.
     */
    public AnnotatedLargeText getLogText() {
        return new AnnotatedLargeText(getLogFile(),getCharset(),!isLogUpdated(),this);
    }

    @Override
    protected SearchIndexBuilder makeSearchIndex() {
        SearchIndexBuilder builder = super.makeSearchIndex()
                .add("console")
                .add("changes");
        for (Action a : getActions()) {
            if(a.getIconFileName()!=null)
                builder.add(a.getUrlName());
        }
        return builder;
    }

    public Api getApi() {
        return new Api(this);
    }

    public void checkPermission(Permission p) {
        getACL().checkPermission(p);
    }

    public boolean hasPermission(Permission p) {
        return getACL().hasPermission(p);
    }

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
        File artifactsDir = getArtifactsDir();

        Util.deleteContentsRecursive(artifactsDir);
    }

    /**
     * Deletes this build and its entire log
     *
     * @throws IOException
     *      if we fail to delete.
     */
    public synchronized void delete() throws IOException {
        RunListener.fireDeleted(this);

        // if we have a symlink, delete it, too
        File link = new File(project.getBuildDir(), String.valueOf(getNumber()));
        link.delete();

        File rootDir = getRootDir();
        File tmp = new File(rootDir.getParentFile(),'.'+rootDir.getName());
        
        boolean renamingSucceeded = rootDir.renameTo(tmp);
        Util.deleteRecursive(tmp);
        // some user reported that they see some left-over .xyz files in the workspace,
        // so just to make sure we've really deleted it, schedule the deletion on VM exit, too.
        if(tmp.exists())
            tmp.deleteOnExit();

        if(!renamingSucceeded)
            throw new IOException(rootDir+" is in use");

        removeRunFromParent();
    }

    @SuppressWarnings("unchecked") // seems this is too clever for Java's type system?
    private void removeRunFromParent() {
        getParent().removeRun((RunT)this);
    }


    /**
     * @see CheckPoint#report()
     */
    /*package*/ static void reportCheckpoint(CheckPoint id) {
        RunnerStack.INSTANCE.peek().checkpoints.report(id);
    }

    /**
     * @see CheckPoint#block()
     */
    /*package*/ static void waitForCheckpoint(CheckPoint id) throws InterruptedException {
        while(true) {
            Run b = RunnerStack.INSTANCE.peek().getBuild().getPreviousBuildInProgress();
            if(b==null)     return; // no pending earlier build
            Run.Runner runner = b.runner;
            if(runner==null) {
                // polled at the wrong moment. try again.
                Thread.sleep(0);
                continue;
            }
            if(runner.checkpoints.waitForCheckPoint(id))
                return; // confirmed that the previous build reached the check point

            // the previous build finished without ever reaching the check point. try again.
        }
    }

    protected abstract class Runner {
        /**
         * Keeps track of the check points attained by a build, and abstracts away the synchronization needed to 
         * maintain this data structure.
         */
        private final class CheckpointSet {
            /**
             * Stages of the builds that this runner has completed. This is used for concurrent {@link Runner}s to
             * coordinate and serialize their executions where necessary.
             */
            private final Set<CheckPoint> checkpoints = new HashSet<CheckPoint>();

            private boolean allDone;

            protected synchronized void report(CheckPoint identifier) {
                checkpoints.add(identifier);
                notifyAll();
            }

            protected synchronized boolean waitForCheckPoint(CheckPoint identifier) throws InterruptedException {
                final Thread t = Thread.currentThread();
                final String oldName = t.getName();
                t.setName(oldName+" : waiting for "+identifier+" on "+getFullDisplayName());
                try {
                    while(!allDone && !checkpoints.contains(identifier))
                        wait();
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

        /**
         * Performs the main build and returns the status code.
         *
         * @throws Exception
         *      exception will be recorded and the build will be considered a failure.
         */
        public abstract Result run( BuildListener listener ) throws Exception, RunnerAbortedException;

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
        public abstract void post( BuildListener listener ) throws Exception;

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
        public abstract void cleanUp(BuildListener listener) throws Exception;

        protected final RunT getBuild() {
            return _this();
        }
    }

    /**
     * Used in {@link Runner#run} to indicates that a fatal error in a build
     * is reported to {@link BuildListener} and the build should be simply aborted
     * without further recording a stack trace.
     */
    public static final class RunnerAbortedException extends RuntimeException {}

    protected final void run(Runner job) {
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
                    Charset charset = Computer.currentComputer().getDefaultCharset();
                    this.charset = charset.name();
                    listener = new StreamBuildListener(getLogFile(),charset);

                    listener.started(getCauses());

                    RunListener.fireStarted(this,listener);

                    // create a symlink from build number to ID.
                    Util.createSymlink(getParent().getBuildDir(),getId(),String.valueOf(getNumber()),listener);

                    setResult(job.run(listener));

                    LOGGER.info(toString()+" main build action completed: "+result);
                    CheckPoint.MAIN_COMPLETED.report();
                } catch (ThreadDeath t) {
                    throw t;
                } catch( AbortException e ) {// orderly abortion.
                    result = Result.FAILURE;
                    LOGGER.log(Level.FINE, "Build "+this+" aborted",e);
                } catch( RunnerAbortedException e ) {// orderly abortion.
                    result = Result.FAILURE;
                    LOGGER.log(Level.FINE, "Build "+this+" aborted",e);
                } catch( InterruptedException e) {
                    // aborted
                    result = Result.ABORTED;
                    listener.getLogger().println(Messages.Run_BuildAborted());
                    LOGGER.log(Level.INFO,toString()+" aborted",e);
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
                // the significance of doing this is that Hudson
                // will now see this build as completed.
                // things like triggering other builds requires this as pre-condition.
                // see issue #980.
                state = State.POST_PRODUCTION;

                try {
                    job.cleanUp(listener);
                } catch (Exception e) {
                    handleFatalBuildProblem(listener,e);
                    // too late to update the result now
                }

                RunListener.fireCompleted(this,listener);

                if(listener!=null)
                    listener.finished(result);
                if(listener!=null)
                    listener.closeQuietly();

                try {
                    save();
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed to save build record",e);
                }
            }

            try {
                getParent().logRotate();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to rotate log",e);
            } catch (InterruptedException e) {
                LOGGER.log(Level.SEVERE, "Failed to rotate log",e);
            }
        } finally {
            onEndBuilding();
        }
    }

    /**
     * Handles a fatal build problem (exception) that occurred during the build.
     */
    private void handleFatalBuildProblem(BuildListener listener, Throwable e) {
        if(listener!=null) {
            if(e instanceof IOException)
                Util.displayIOException((IOException)e,listener);

            Writer w = listener.fatalError(e.getMessage());
            if(w!=null) {
                try {
                    e.printStackTrace(new PrintWriter(w));
                    w.close();
                } catch (IOException e1) {
                    // ignore
                }
            }
        }
    }

    /**
     * Called when a job started building.
     */
    protected void onStartBuilding() {
        state = State.BUILDING;
        if (runner!=null)
            RunnerStack.INSTANCE.push(runner);
    }

    /**
     * Called when a job finished building normally or abnormally.
     */
    protected void onEndBuilding() {
        // signal that we've finished building.
        if (runner!=null) {
            // MavenBuilds may be created without their corresponding runners.
            state = State.COMPLETED;
            runner.checkpoints.allDone();
            runner = null;
            RunnerStack.INSTANCE.pop();
        } else {
            state = State.COMPLETED;
        }
        if (result == null) {
            result = Result.FAILURE;
            LOGGER.warning(toString() + ": No build result is set, so marking as failure. This shouldn't happen.");
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

    private XmlFile getDataFile() {
        return new XmlFile(XSTREAM,new File(getRootDir(),"build.xml"));
    }

    /**
     * Gets the log of the build as a string.
     *
     * @deprecated since 2007-11-11.
     *     Use {@link #getLog(int)} instead as it avoids loading
     *     the whole log into memory unnecessarily.
     */
    @Deprecated
    public String getLog() throws IOException {
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
    public List<String> getLog(int maxLines) throws IOException {
        int lineCount = 0;
        List<String> logLines = new LinkedList<String>();
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

    public String getBuildStatusUrl() {
        return getIconColor().getImage();
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
     * Gets an object that computes the single line summary of this build.
     */
    public Summary getBuildStatusSummary() {
        Run prev = getPreviousBuild();

        if(getResult()==Result.SUCCESS) {
            if(prev==null || prev.getResult()== Result.SUCCESS)
                return new Summary(false, Messages.Run_Summary_Stable());
            else
                return new Summary(false, Messages.Run_Summary_BackToNormal());
        }

        if(getResult()==Result.FAILURE) {
            RunT since = getPreviousNotFailedBuild();
            if(since==null)
                return new Summary(false, Messages.Run_Summary_BrokenForALongTime());
            if(since==prev)
                return new Summary(true, Messages.Run_Summary_BrokenSinceThisBuild());
            return new Summary(false, Messages.Run_Summary_BrokenSince(since.getDisplayName()));
        }

        if(getResult()==Result.ABORTED)
            return new Summary(false, Messages.Run_Summary_Aborted());

        if(getResult()==Result.UNSTABLE) {
            if(((Run)this) instanceof AbstractBuild) {
                AbstractTestResultAction trN = ((AbstractBuild)(Run)this).getTestResultAction();
                AbstractTestResultAction trP = prev==null ? null : ((AbstractBuild) prev).getTestResultAction();
                if(trP==null) {
                    if(trN!=null && trN.getFailCount()>0)
                        return new Summary(false, Messages.Run_Summary_TestFailures(trN.getFailCount()));
                    else // ???
                        return new Summary(false, Messages.Run_Summary_Unstable());
                }
                if(trP.getFailCount()==0)
                    return new Summary(true, Messages.Run_Summary_TestsStartedToFail(trN.getFailCount()));
                if(trP.getFailCount() < trN.getFailCount())
                    return new Summary(true, Messages.Run_Summary_MoreTestsFailing(trN.getFailCount()-trP.getFailCount(), trN.getFailCount()));
                if(trP.getFailCount() > trN.getFailCount())
                    return new Summary(false, Messages.Run_Summary_LessTestsFailing(trP.getFailCount()-trN.getFailCount(), trN.getFailCount()));

                return new Summary(false, Messages.Run_Summary_TestsStillFailing(trN.getFailCount()));
            }
        }

        return new Summary(false, Messages.Run_Summary_Unknown());
    }

    /**
     * Serves the artifacts.
     */
    public DirectoryBrowserSupport doArtifact() {
        return new DirectoryBrowserSupport(this,new FilePath(getArtifactsDir()), project.getDisplayName()+' '+getDisplayName(), "package.gif", true);
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
        // Prevent jelly from flushing stream so Content-Length header can be added afterwards
        FlushProofOutputStream out = new FlushProofOutputStream(rsp.getCompressedOutputStream(req));
        getLogText().writeLogTo(0,out);
        out.close();
    }

    /**
     * Handles incremental log output.
     * @deprecated as of 1.352
     *      Use {@code getLogText().doProgressiveText(req,rsp)}
     */
    public void doProgressiveLog( StaplerRequest req, StaplerResponse rsp) throws IOException {
        getLogText().doProgressText(req,rsp);
    }

    public void doToggleLogKeep( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        checkPermission(UPDATE);

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
        keepLog = newValue;
        save();
    }

    /**
     * Deletes the build when the button is pressed.
     */
    public void doDoDelete( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        requirePOST();
        checkPermission(DELETE);

        // We should not simply delete the build if it has been explicitly
        // marked to be preserved, or if the build should not be deleted
        // due to dependencies!
        String why = getWhyKeepLog();
        if (why!=null) {
            sendError(Messages.Run_UnableToDelete(toString(),why),req,rsp);
            return;
        }

        delete();
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
        req.setCharacterEncoding("UTF-8");
        setDescription(req.getParameter("description"));
        rsp.sendRedirect(".");  // go to the top page
    }

    /**
     * @deprecated as of 1.292
     *      Use {@link #getEnvironment()} instead.
     */
    public Map<String,String> getEnvVars() {
        try {
            return getEnvironment();
        } catch (IOException e) {
            return new EnvVars();
        } catch (InterruptedException e) {
            return new EnvVars();
        }
    }

    /**
     * @deprecated as of 1.305 use {@link #getEnvironment(TaskListener)}
     */
    public EnvVars getEnvironment() throws IOException, InterruptedException {
        return getEnvironment(new LogTaskListener(LOGGER, Level.INFO));
    }

    /**
     * Returns the map that contains environmental variables to be used for launching
     * processes for this build.
     *
     * <p>
     * {@link BuildStep}s that invoke external processes should use this.
     * This allows {@link BuildWrapper}s and other project configurations (such as JDK selection)
     * to take effect.
     *
     * <p>
     * Unlike earlier {@link #getEnvVars()}, this map contains the whole environment,
     * not just the overrides, so one can introspect values to change its behavior.
     * @since 1.305
     */
    public EnvVars getEnvironment(TaskListener log) throws IOException, InterruptedException {
        EnvVars env = Computer.currentComputer().getEnvironment().overrideAll(getCharacteristicEnvVars());
        String rootUrl = Hudson.getInstance().getRootUrl();
        if(rootUrl!=null) {
            env.put("HUDSON_URL", rootUrl);
            env.put("BUILD_URL", rootUrl+getUrl());
            env.put("JOB_URL", rootUrl+getParent().getUrl());
        }
        
        if(!env.containsKey("HUDSON_HOME"))
            env.put("HUDSON_HOME", Hudson.getInstance().getRootDir().getPath() );

        Thread t = Thread.currentThread();
        if (t instanceof Executor) {
            Executor e = (Executor) t;
            env.put("EXECUTOR_NUMBER",String.valueOf(e.getNumber()));
            env.put("NODE_NAME",e.getOwner().getName());
        }

        return env;
    }

    /**
     * Builds up the environment variable map that's sufficient to identify a process
     * as ours. This is used to kill run-away processes via {@link ProcessTree#killAll(Map)}.
     */
    public final EnvVars getCharacteristicEnvVars() {
        EnvVars env = new EnvVars();
        env.put("BUILD_NUMBER",String.valueOf(number));
        env.put("BUILD_ID",getId());
        env.put("BUILD_TAG","hudson-"+getParent().getName()+"-"+number);
        env.put("JOB_NAME",getParent().getFullName());
        return env;
    }

    public String getExternalizableId() {
        return project.getName() + "#" + getNumber();
    }

    public static Run<?,?> fromExternalizableId(String id) {
        int hash = id.lastIndexOf('#');
        if (hash <= 0) {
            throw new IllegalArgumentException("Invalid id");
        }
        String jobName = id.substring(0, hash);
        int number = Integer.parseInt(id.substring(hash + 1));

        Job<?,?> job = (Job<?,?>) Hudson.getInstance().getItem(jobName);
        return job.getBuildByNumber(number);
    }


    public static final XStream XSTREAM = new XStream2();
    static {
        XSTREAM.alias("build",FreeStyleBuild.class);
        XSTREAM.alias("matrix-build",MatrixBuild.class);
        XSTREAM.alias("matrix-run",MatrixRun.class);
        XSTREAM.registerConverter(Result.conv);
    }

    private static final Logger LOGGER = Logger.getLogger(Run.class.getName());

    /**
     * Sort by date. Newer ones first. 
     */
    public static final Comparator<Run> ORDER_BY_DATE = new Comparator<Run>() {
        public int compare(Run lhs, Run rhs) {
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
        public String getIconFileName() { return null; }
        public String getDisplayName() { return null; }
        public String getUrlName() { return null; }
        public String getWhyKeepLog() { return Run.this.getWhyKeepLog(); }
    }

    public static final PermissionGroup PERMISSIONS = new PermissionGroup(Run.class,Messages._Run_Permissions_Title());
    public static final Permission DELETE = new Permission(PERMISSIONS,"Delete",Messages._Run_DeletePermission_Description(),Permission.DELETE);
    public static final Permission UPDATE = new Permission(PERMISSIONS,"Update",Messages._Run_UpdatePermission_Description(),Permission.UPDATE);

    private static class DefaultFeedAdapter implements FeedAdapter<Run> {
        public String getEntryTitle(Run entry) {
            return entry+" ("+entry.getResult()+")";
        }

        public String getEntryUrl(Run entry) {
            return entry.getUrl();
        }

        public String getEntryID(Run entry) {
            return "tag:" + "hudson.dev.java.net,"
                + entry.getTimestamp().get(Calendar.YEAR) + ":"
                + entry.getParent().getName()+':'+entry.getId();
        }

        public String getEntryDescription(Run entry) {
            // TODO: this could provide some useful details
            return null;
        }

        public Calendar getEntryTimestamp(Run entry) {
            return entry.getTimestamp();
        }

        public String getEntryAuthor(Run entry) {
            return Mailer.descriptor().getAdminAddress();
        }
    }

    @Override
    public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        Object result = super.getDynamic(token, req, rsp);
        if (result == null)
            // Next/Previous Build links on an action page (like /job/Abc/123/testReport)
            // will also point to same action (/job/Abc/124/testReport), but other builds
            // may not have the action.. tell browsers to redirect up to the build page.
            result = new RedirectUp();
        return result;
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