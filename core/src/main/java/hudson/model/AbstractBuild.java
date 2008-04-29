package hudson.model;

import hudson.Functions;
import hudson.Launcher;
import hudson.Util;
import hudson.matrix.MatrixConfiguration;
import hudson.maven.MavenBuild;
import hudson.model.Fingerprint.BuildPtr;
import hudson.model.Fingerprint.RangeSet;
import hudson.model.listeners.SCMListener;
import hudson.scm.CVSChangeLogParser;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.scm.SCM;
import hudson.tasks.BuildStep;
import hudson.tasks.Builder;
import hudson.tasks.Fingerprinter.FingerprintAction;
import hudson.tasks.Publisher;
import hudson.tasks.BuildWrapper;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.util.AdaptedIterator;
import hudson.util.Iterators;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.xml.sax.SAXException;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base implementation of {@link Run}s that build software.
 *
 * For now this is primarily the common part of {@link Build} and {@link MavenBuild}.
 *
 * @author Kohsuke Kawaguchi
 * @see AbstractProject
 */
public abstract class AbstractBuild<P extends AbstractProject<P,R>,R extends AbstractBuild<P,R>> extends Run<P,R> implements Queue.Executable {

    /**
     * Name of the slave this project was built on.
     * Null or "" if built by the master. (null happens when we read old record that didn't have this information.)
     */
    private String builtOn;

    /**
     * Version of Hudson that built this.
     */
    private String hudsonVersion;

    /**
     * SCM used for this build.
     * Maybe null, for historical reason, in which case CVS is assumed.
     */
    private ChangeLogParser scm;

    /**
     * Changes in this build.
     */
    private volatile transient ChangeLogSet<? extends Entry> changeSet;

    /**
     * Cumulative list of people who contributed to the build problem.
     *
     * <p>
     * This is a list of {@link User#getId() user ids} who made a change
     * since the last non-broken build. Can be null (which should be
     * treated like empty set), because of the compatibility.
     *
     * <p>
     * This field is semi-final --- once set the value will never be modified.
     *
     * @since 1.137
     */
    private volatile Set<String> culprits;
    
    /**
     * During the build this field remembers {@link BuildWrapper.Environment}s created by
     * {@link BuildWrapper}. This design is bit ugly but forced due to compatibility.
     */
    protected transient List<BuildWrapper.Environment> buildEnvironments;

    protected AbstractBuild(P job) throws IOException {
        super(job);
    }

    protected AbstractBuild(P job, Calendar timestamp) {
        super(job, timestamp);
    }

    protected AbstractBuild(P project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    public final P getProject() {
        return getParent();
    }

    /**
     * Returns a {@link Slave} on which this build was done.
     */
    public Node getBuiltOn() {
        if(builtOn==null || builtOn.equals(""))
            return Hudson.getInstance();
        else
            return Hudson.getInstance().getSlave(builtOn);
    }

    /**
     * Returns the name of the slave it was built on, or null if it was the master.
     */
    @Exported(name="builtOn")
    public String getBuiltOnStr() {
        return builtOn;
    }

    /**
     * Used to render the side panel "Back to project" link.
     *
     * <p>
     * In a rare situation where a build can be reached from multiple paths,
     * returning different URLs from this method based on situations might
     * be desirable.
     *
     * <p>
     * If you override this method, you'll most likely also want to override
     * {@link #getDisplayName()}.
     */
    public String getUpUrl() {
        return Functions.getNearestAncestorUrl(Stapler.getCurrentRequest(),getParent())+'/';
    }

    /**
     * List of users who committed a change since the last non-broken build till now.
     *
     * <p>
     * This list at least always include people who made changes in this build, but
     * if the previous build was a failure it also includes the culprit list from there.
     *
     * @return
     *      can be empty but never null.
     */
    @Exported
    public Set<User> getCulprits() {
        if(culprits==null) {
            Set<User> r = new HashSet<User>();
            if(getPreviousBuild()!=null && isBuilding() && getPreviousBuild().getResult().isWorseThan(Result.UNSTABLE)) {
                // we are still building, so this is just the current latest information,
                // but we seems to be failing so far, so inherit culprits from the previous build.
                // isBuilding() check is to avoid recursion when loading data from old Hudson, which doesn't record
                // this information
                r.addAll(getPreviousBuild().getCulprits());
            }
            for( Entry e : getChangeSet() )
                r.add(e.getAuthor());
            return r;
        }

        return new AbstractSet<User>() {
            public Iterator<User> iterator() {
                return new AdaptedIterator<String,User>(culprits.iterator()) {
                    protected User adapt(String id) {
                        return User.get(id);
                    }
                };
            }

            public int size() {
                return culprits.size();
            }
        };
    }

    /**
     * Returns true if this user has made a commit to this build.
     *
     * @since 1.191
     */
    public boolean hasParticipant(User user) {
        for (ChangeLogSet.Entry e : getChangeSet())
            if(e.getAuthor()==user)
                return true;
        return false;
    }

    protected abstract class AbstractRunner implements Runner {
        /**
         * Since configuration can be changed while a build is in progress,
         * stick to one launcher and use it.
         */
        protected Launcher launcher;

        /**
         * Returns the current {@link Node} on which we are buildling.
         */
        protected final Node getCurrentNode() {
            return Executor.currentExecutor().getOwner().getNode();
        }

        public Result run(BuildListener listener) throws Exception {
            Node node = getCurrentNode();
            assert builtOn==null;
            builtOn = node.getNodeName();
            hudsonVersion = Hudson.VERSION;

            launcher = node.createLauncher(listener);
            if(node instanceof Slave)
                listener.getLogger().println(Messages.AbstractBuild_BuildingRemotely(node.getNodeName()));

            if(checkout(listener))
                return Result.FAILURE;

            if(!preBuild(listener,project.getProperties()))
                return Result.FAILURE;

            Result result = doRun(listener);

            // the two if statements here are a real mess.
            // the "return null to mean success" convention is not very consistent
            // so we should probably get rid of that. And in the mean time
            // this check will allows us to create symlinks
            if(result!=null && result!=Result.SUCCESS)
                return result;  // abort here

            if(getResult()==null || getResult()==Result.SUCCESS)
                createLastSuccessfulLink(listener);

            return Result.SUCCESS;
        }

        private void createLastSuccessfulLink(BuildListener listener) throws InterruptedException {
            Util.createSymlink(getProject().getBuildDir(),"builds/"+getId(),"../lastSuccessful",listener);
        }

        private boolean checkout(BuildListener listener) throws Exception {
            if(!project.checkout(AbstractBuild.this,launcher,listener,new File(getRootDir(),"changelog.xml")))
                return true;

            SCM scm = project.getScm();

            AbstractBuild.this.scm = scm.createChangeLogParser();
            AbstractBuild.this.changeSet = AbstractBuild.this.calcChangeSet();

            for (SCMListener l : Hudson.getInstance().getSCMListeners())
                l.onChangeLogParsed(AbstractBuild.this,listener,changeSet);
            return false;
        }

        /**
         * The portion of a build that is specific to a subclass of {@link AbstractBuild}
         * goes here.
         *
         * @return
         *      null to continue the build normally (that means the doRun method
         *      itself run successfully)
         *      Return a non-null value to abort the build right there with the specified result code.
         */
        protected abstract Result doRun(BuildListener listener) throws Exception, RunnerAbortedException;

        /**
         * @see #post(BuildListener)
         */
        protected abstract void post2(BuildListener listener) throws Exception;

        public final void post(BuildListener listener) throws Exception {
            try {
                post2(listener);
            } finally {
                // update the culprit list
                HashSet<String> r = new HashSet<String>();
                for (User u : getCulprits())
                    r.add(u.getId());
                culprits = r;
            }
        }

        public void cleanUp(BuildListener listener) throws Exception {
            // default is no-op
        }

        protected final void performAllBuildStep(BuildListener listener, Map<?,? extends BuildStep> buildSteps, boolean phase) throws InterruptedException, IOException {
            performAllBuildStep(listener,buildSteps.values(),phase);
        }

        /**
         * Runs all the given build steps, even if one of them fail.
         *
         * @param phase
         *      true for the post build processing, and false for the final "run after finished" execution.
         */
        protected final void performAllBuildStep(BuildListener listener, Iterable<? extends BuildStep> buildSteps, boolean phase) throws InterruptedException, IOException {
            for( BuildStep bs : buildSteps ) {
                if( (bs instanceof Publisher && ((Publisher)bs).needsToRunAfterFinalized()) ^ phase)
                    bs.perform(AbstractBuild.this, launcher, listener);
            }
        }

        protected final boolean preBuild(BuildListener listener,Map<?,? extends BuildStep> steps) {
            return preBuild(listener,steps.values());
        }

        protected final boolean preBuild(BuildListener listener,Collection<? extends BuildStep> steps) {
            for( BuildStep bs : steps )
                if(!bs.prebuild(AbstractBuild.this,listener))
                    return false;
            return true;
        }
    }

    /**
     * Gets the changes incorporated into this build.
     *
     * @return never null.
     */
    @Exported
    public ChangeLogSet<? extends Entry> getChangeSet() {
        if(scm==null)
            scm = new CVSChangeLogParser();

        if(changeSet==null) // cached value
            changeSet = calcChangeSet();
        return changeSet;
    }

    /**
     * Returns true if the changelog is already computed.
     */
    public boolean hasChangeSetComputed() {
        File changelogFile = new File(getRootDir(), "changelog.xml");
        return changelogFile.exists();
    }

    private ChangeLogSet<? extends Entry> calcChangeSet() {
        File changelogFile = new File(getRootDir(), "changelog.xml");
        if(!changelogFile.exists())
            return ChangeLogSet.createEmpty(this);

        try {
            return scm.parse(this,changelogFile);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        }
        return ChangeLogSet.createEmpty(this);
    }

    @Override
    public Map<String,String> getEnvVars() {
        Map<String,String> env = super.getEnvVars();
        env.put("WORKSPACE", getProject().getWorkspace().getRemote());
        // servlet container may have set CLASSPATH in its launch script,
        // so don't let that inherit to the new child process.
        // see http://www.nabble.com/Run-Job-with-JDK-1.4.2-tf4468601.html
        env.put("CLASSPATH","");

        JDK jdk = project.getJDK();
        if(jdk !=null)
            jdk.buildEnvVars(env);
        project.getScm().buildEnvVars(this,env);

        if(buildEnvironments!=null) {
            for (BuildWrapper.Environment e : buildEnvironments)
                e.buildEnvVars(env);
        }
        return env;
    }

    public Calendar due() {
        return (Calendar)timestamp.clone();
    }

    /**
     * Provides additional variables and their values to {@link Builder}s.
     *
     * <p>
     * This mechanism is used by {@link MatrixConfiguration} to pass
     * the configuration values to the current build. It is up to
     * {@link Builder}s to decide whether it wants to recognize the values
     * or how to use them.
     *
     * ugly ugly hack.
     */
    public Map<String,String> getBuildVariables() {
        return Collections.emptyMap();
    }
    
    /**
     * Gets {@link AbstractTestResultAction} associated with this build if any.
     */
    public AbstractTestResultAction getTestResultAction() {
        return getAction(AbstractTestResultAction.class);
    }

    /**
     * Invoked by {@link Executor} to performs a build.
     */
    public abstract void run();

//
//
// fingerprint related stuff
//
//

    @Override
    public String getWhyKeepLog() {
        // if any of the downstream project is configured with 'keep dependency component',
        // we need to keep this log
        OUTER:
        for (AbstractProject<?,?> p : getParent().getDownstreamProjects()) {
            if(!p.isKeepDependencies()) continue;

            AbstractBuild<?,?> fb = p.getFirstBuild();
            if(fb==null)        continue; // no active record

            // is there any active build that depends on us?
            for(int i : getDownstreamRelationship(p).listNumbersReverse()) {
                // TODO: this is essentially a "find intersection between two sparse sequences"
                // and we should be able to do much better.

                if(i<fb.getNumber())
                    continue OUTER; // all the other records are younger than the first record, so pointless to search.

                AbstractBuild<?,?> b = p.getBuildByNumber(i);
                if(b!=null)
                    return Messages.AbstractBuild_KeptBecause(b);
            }
        }

        return super.getWhyKeepLog();
    }


    /**
     * Gets the dependency relationship from this build (as the source)
     * and that project (as the sink.)
     *
     * @return
     *      range of build numbers that represent which downstream builds are using this build.
     *      The range will be empty if no build of that project matches this, but it'll never be null.
     */
    public RangeSet getDownstreamRelationship(AbstractProject that) {
        RangeSet rs = new RangeSet();

        FingerprintAction f = getAction(FingerprintAction.class);
        if(f==null)     return rs;

        // look for fingerprints that point to this build as the source, and merge them all
        for (Fingerprint e : f.getFingerprints().values()) {
            BuildPtr o = e.getOriginal();
            if(o!=null && o.is(this))
                rs.add(e.getRangeSet(that));
        }

        return rs;
    }

    /**
     * Works like {@link #getDownstreamRelationship(AbstractProject)} but returns
     * the actual build objects, in ascending order.
     * @since 1.150
     */
    public Iterable<AbstractBuild<?,?>> getDownstreamBuilds(final AbstractProject<?,?> that) {
        final Iterable<Integer> nums = getDownstreamRelationship(that).listNumbers();

        return new Iterable<AbstractBuild<?, ?>>() {
            public Iterator<AbstractBuild<?, ?>> iterator() {
                return new Iterators.FilterIterator<AbstractBuild<?,?>>(
                        new AdaptedIterator<Integer,AbstractBuild<?,?>>(nums) {
                            protected AbstractBuild<?, ?> adapt(Integer item) {
                                return that.getBuildByNumber(item);
                            }
                        }) {
                    protected boolean filter(AbstractBuild<?,?> build) {
                        return build!=null;
                    }
                };
            }
        };
    }

    /**
     * Gets the dependency relationship from this build (as the sink)
     * and that project (as the source.)
     *
     * @return
     *      Build number of the upstream build that feed into this build,
     *      or -1 if no record is available.
     */
    public int getUpstreamRelationship(AbstractProject that) {
        FingerprintAction f = getAction(FingerprintAction.class);
        if(f==null)     return -1;

        int n = -1;

        // look for fingerprints that point to the given project as the source, and merge them all
        for (Fingerprint e : f.getFingerprints().values()) {
            BuildPtr o = e.getOriginal();
            if(o!=null && o.belongsTo(that))
                n = Math.max(n,o.getNumber());
        }

        return n;
    }

    /**
     * Works like {@link #getUpstreamRelationship(AbstractProject)} but returns the
     * actual build object.
     *
     * @return
     *      null if no such upstream build was found, or it was found but the
     *      build record is already lost.
     */
    public AbstractBuild<?,?> getUpstreamRelationshipBuild(AbstractProject<?,?> that) {
        int n = getUpstreamRelationship(that);
        if(n==-1)   return null;
        return that.getBuildByNumber(n);
    }

    /**
     * Gets the downstream builds of this build, which are the builds of the
     * downstream projects that use artifacts of this build.
     *
     * @return
     *      For each project with fingerprinting enabled, returns the range
     *      of builds (which can be empty if no build uses the artifact from this build.)
     */
    public Map<AbstractProject,RangeSet> getDownstreamBuilds() {
        Map<AbstractProject,RangeSet> r = new HashMap<AbstractProject,RangeSet>();
        for (AbstractProject p : getParent().getDownstreamProjects()) {
            if(p.isFingerprintConfigured())
                r.put(p,getDownstreamRelationship(p));
        }
        return r;
    }
    
    /**
     * Gets the upstream builds of this build, which are the builds of the
     * upstream projects whose artifacts feed into this build.
     *
     * @see #getTransitiveUpstreamBuilds()
     */
    public Map<AbstractProject,Integer> getUpstreamBuilds() {
        return _getUpstreamBuilds(getParent().getUpstreamProjects());
    }

    /**
     * Works like {@link #getUpstreamBuilds()}  but also includes all the transitive
     * dependencies as well.
     */
    public Map<AbstractProject,Integer> getTransitiveUpstreamBuilds() {
        return _getUpstreamBuilds(getParent().getTransitiveUpstreamProjects());
    }

    private Map<AbstractProject, Integer> _getUpstreamBuilds(Collection<AbstractProject> projects) {
        Map<AbstractProject,Integer> r = new HashMap<AbstractProject,Integer>();
        for (AbstractProject p : projects) {
            int n = getUpstreamRelationship(p);
            if(n>=0)
                r.put(p,n);
        }
        return r;
    }

    /**
     * Gets the changes in the dependency between the given build and this build.
     */
    public Map<AbstractProject,DependencyChange> getDependencyChanges(AbstractBuild from) {
        if(from==null)             return Collections.emptyMap(); // make it easy to call this from views
        FingerprintAction n = this.getAction(FingerprintAction.class);
        FingerprintAction o = from.getAction(FingerprintAction.class);
        if(n==null || o==null)     return Collections.emptyMap();

        Map<AbstractProject,Integer> ndep = n.getDependencies();
        Map<AbstractProject,Integer> odep = o.getDependencies();

        Map<AbstractProject,DependencyChange> r = new HashMap<AbstractProject,DependencyChange>();

        for (Map.Entry<AbstractProject,Integer> entry : odep.entrySet()) {
            AbstractProject p = entry.getKey();
            Integer oldNumber = entry.getValue();
            Integer newNumber = ndep.get(p);
            if(newNumber!=null && oldNumber.compareTo(newNumber)<0) {
                r.put(p,new DependencyChange(p,oldNumber,newNumber));
            }
        }

        return r;
    }

    /**
     * Represents a change in the dependency.
     */
    public static final class DependencyChange {
        /**
         * The dependency project.
         */
        public final AbstractProject project;
        /**
         * Version of the dependency project used in the previous build.
         */
        public final int fromId;
        /**
         * {@link Build} object for {@link #fromId}. Can be null if the log is gone.
         */
        public final AbstractBuild from;
        /**
         * Version of the dependency project used in this build.
         */
        public final int toId;

        public final AbstractBuild to;

        public DependencyChange(AbstractProject<?,?> project, int fromId, int toId) {
            this.project = project;
            this.fromId = fromId;
            this.toId = toId;
            this.from = project.getBuildByNumber(fromId);
            this.to = project.getBuildByNumber(toId);
        }

        /**
         * Gets the {@link AbstractBuild} objects (fromId,toId].
         * <p>
         * This method returns all such available builds in the ascending order
         * of IDs, but due to log rotations, some builds may be already unavailable. 
         */
        public List<AbstractBuild> getBuilds() {
            List<AbstractBuild> r = new ArrayList<AbstractBuild>();

            AbstractBuild<?,?> b = (AbstractBuild)project.getNearestBuild(fromId);
            if(b!=null && b.getNumber()==fromId)
                b = b.getNextBuild(); // fromId exclusive

            while(b!=null && b.getNumber()<=toId) {
                r.add(b);
                b = b.getNextBuild();
            }

            return r;
        }
    }

//
//
// web methods
//
//
    /**
     * Stops this build if it's still going.
     *
     * If we use this/executor/stop URL, it causes 404 if the build is already killed,
     * as {@link #getExecutor()} returns null.
     */
    public synchronized void doStop( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        Executor e = getExecutor();
        if(e!=null)
            e.doStop(req,rsp);
        else
            // nothing is building
            rsp.forwardToPreviousPage(req);
    }
}
