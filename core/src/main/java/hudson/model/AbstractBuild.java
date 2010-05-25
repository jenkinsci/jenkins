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
package hudson.model;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Functions;
import hudson.Launcher;
import hudson.Util;
import hudson.FilePath;
import hudson.slaves.WorkspaceList;
import hudson.slaves.NodeProperty;
import hudson.slaves.WorkspaceList.Lease;
import hudson.matrix.MatrixConfiguration;
import hudson.model.Fingerprint.BuildPtr;
import hudson.model.Fingerprint.RangeSet;
import hudson.model.listeners.SCMListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.scm.SCM;
import hudson.scm.NullChangeLogParser;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Builder;
import hudson.tasks.Fingerprinter.FingerprintAction;
import hudson.tasks.Publisher;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.util.AdaptedIterator;
import hudson.util.Iterators;
import hudson.util.LogTaskListener;
import hudson.util.VariableResolver;
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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base implementation of {@link Run}s that build software.
 *
 * For now this is primarily the common part of {@link Build} and MavenBuild.
 *
 * @author Kohsuke Kawaguchi
 * @see AbstractProject
 */
public abstract class AbstractBuild<P extends AbstractProject<P,R>,R extends AbstractBuild<P,R>> extends Run<P,R> implements Queue.Executable {

    /**
     * Set if we want the blame information to flow from upstream to downstream build.
     */
    private static final boolean upstreamCulprits = Boolean.getBoolean("hudson.upstreamCulprits");

    /**
     * Name of the slave this project was built on.
     * Null or "" if built by the master. (null happens when we read old record that didn't have this information.)
     */
    private String builtOn;

    /**
     * The file path on the node that performed a build. Kept as a string since {@link FilePath} is not serializable into XML.
     * @since 1.319
     */
    private String workspace;

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
    protected transient List<Environment> buildEnvironments;

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
     *
     * @return
     *      null, for example if the slave that this build run no longer exists.
     */
    public Node getBuiltOn() {
        if (builtOn==null || builtOn.equals(""))
            return Hudson.getInstance();
        else
            return Hudson.getInstance().getNode(builtOn);
    }

    /**
     * Returns the name of the slave it was built on; null or "" if built by the master.
     * (null happens when we read old record that didn't have this information.)
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
     * Gets the directory where this build is being built.
     *
     * <p>
     * Note to implementors: to control where the workspace is created, override
     * {@link AbstractRunner#decideWorkspace(Node,WorkspaceList)}.
     *
     * @return
     *      null if the workspace is on a slave that's not connected. Note that once the build is completed,
     *      the workspace may be used to build something else, so the value returned from this method may
     *      no longer show a workspace as it was used for this build.
     * @since 1.319
     */
    public final FilePath getWorkspace() {
        if (workspace==null) return null;
        Node n = getBuiltOn();
        if (n==null) return null;
        return n.createPath(workspace);
    }

    /**
     * Normally, a workspace is assigned by {@link Runner}, but this lets you set the workspace in case
     * {@link AbstractBuild} is created without a build.
     */
    protected void setWorkspace(FilePath ws) {
        this.workspace = ws.getRemote();
    }

    /**
     * Returns the root directory of the checked-out module.
     * <p>
     * This is usually where <tt>pom.xml</tt>, <tt>build.xml</tt>
     * and so on exists.
     */
    public final FilePath getModuleRoot() {
        FilePath ws = getWorkspace();
        if (ws==null)    return null;
        return getParent().getScm().getModuleRoot(ws);
    }

    /**
     * Returns the root directories of all checked-out modules.
     * <p>
     * Some SCMs support checking out multiple modules into the same workspace.
     * In these cases, the returned array will have a length greater than one.
     * @return The roots of all modules checked out from the SCM.
     */
    public FilePath[] getModuleRoots() {
        FilePath ws = getWorkspace();
        if (ws==null)    return null;
        return getParent().getScm().getModuleRoots(ws);
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
        if (culprits==null) {
            Set<User> r = new HashSet<User>();
            R p = getPreviousCompletedBuild();
            if (p !=null && isBuilding()) {
                Result pr = p.getResult();
                if (pr!=null && pr.isWorseThan(Result.UNSTABLE)) {
                    // we are still building, so this is just the current latest information,
                    // but we seems to be failing so far, so inherit culprits from the previous build.
                    // isBuilding() check is to avoid recursion when loading data from old Hudson, which doesn't record
                    // this information
                    r.addAll(p.getCulprits());
                }
            }
            for (Entry e : getChangeSet())
                r.add(e.getAuthor());

            if (upstreamCulprits) {
                // If we have dependencies since the last successful build, add their authors to our list
                if (getPreviousNotFailedBuild() != null) {
                    Map <AbstractProject,AbstractBuild.DependencyChange> depmap = getDependencyChanges(getPreviousNotFailedBuild());
                    for (AbstractBuild.DependencyChange dep : depmap.values()) {
                        for (AbstractBuild<?,?> b : dep.getBuilds()) {
                            for (Entry entry : b.getChangeSet()) {
                                r.add(entry.getAuthor());
                            }
                        }
                    }
                }
            }

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
            if (e.getAuthor()==user)
                return true;
        return false;
    }

    /**
     * Gets the version of Hudson that was used to build this job.
     *
     * @since 1.246
     */
    public String getHudsonVersion() {
        return hudsonVersion;
    }

    @Override
    public synchronized void delete() throws IOException {
        // Need to check if deleting this build affects lastSuccessful/lastStable symlinks
        R lastSuccessful = getProject().getLastSuccessfulBuild(),
          lastStable = getProject().getLastStableBuild();

        super.delete();

        try {
            if (lastSuccessful == this)
                updateSymlink("lastSuccessful", getProject().getLastSuccessfulBuild());
            if (lastStable == this)
                updateSymlink("lastStable", getProject().getLastStableBuild());
        } catch (InterruptedException ex) {
            LOGGER.warning("Interrupted update of lastSuccessful/lastStable symlinks for "
                           + getProject().getDisplayName());
            // handle it later
            Thread.currentThread().interrupt();
        }
    }

    private void updateSymlink(String name, AbstractBuild<?,?> newTarget) throws InterruptedException {
        if (newTarget != null)
            newTarget.createSymlink(new LogTaskListener(LOGGER, Level.WARNING), name);
        else
            new File(getProject().getBuildDir(), "../"+name).delete();
    }

    private void createSymlink(TaskListener listener, String name) throws InterruptedException {
        Util.createSymlink(getProject().getBuildDir(),"builds/"+getId(),"../"+name,listener);
    }

    protected abstract class AbstractRunner extends Runner {
        /**
         * Since configuration can be changed while a build is in progress,
         * create a launcher once and stick to it for the entire build duration.
         */
        protected Launcher launcher;

        /**
         * Output/progress of this build goes here.
         */
        protected BuildListener listener;

        /**
         * Returns the current {@link Node} on which we are buildling.
         */
        protected final Node getCurrentNode() {
            return Executor.currentExecutor().getOwner().getNode();
        }

        /**
         * Allocates the workspace from {@link WorkspaceList}.
         *
         * @param n
         *      Passed in for the convenience. The node where the build is running.
         * @param wsl
         *      Passed in for the convenience. The returned path must be registered to this object.
         */
        protected Lease decideWorkspace(Node n, WorkspaceList wsl) throws InterruptedException, IOException {
            // TODO: this cast is indicative of abstraction problem
            return wsl.allocate(n.getWorkspaceFor((TopLevelItem)getProject()));
        }

        public Result run(BuildListener listener) throws Exception {
            Node node = getCurrentNode();
            assert builtOn==null;
            builtOn = node.getNodeName();
            hudsonVersion = Hudson.VERSION;
            this.listener = listener;

            launcher = createLauncher(listener);
            if (!Hudson.getInstance().getNodes().isEmpty())
                listener.getLogger().println(node instanceof Hudson ? Messages.AbstractBuild_BuildingOnMaster() : Messages.AbstractBuild_BuildingRemotely(builtOn));

            final Lease lease = decideWorkspace(node,Computer.currentComputer().getWorkspaceList());

            try {
                workspace = lease.path.getRemote();
                node.getFileSystemProvisioner().prepareWorkspace(AbstractBuild.this,lease.path,listener);

                checkout(listener);

                if (!preBuild(listener,project.getProperties()))
                    return Result.FAILURE;

                Result result = doRun(listener);

                // kill run-away processes that are left
                // use multiple environment variables so that people can escape this massacre by overriding an environment
                // variable for some processes
                launcher.kill(getCharacteristicEnvVars());

                // this is ugly, but for historical reason, if non-null value is returned
                // it should become the final result.
                if (result==null)    result = getResult();
                if (result==null)    result = Result.SUCCESS;

                return result;
            } finally {
                lease.release();
                this.listener = null;
            }
        }

        /**
         * Creates a {@link Launcher} that this build will use. This can be overridden by derived types
         * to decorate the resulting {@link Launcher}.
         *
         * @param listener
         *      Always non-null. Connected to the main build output.
         */
        protected Launcher createLauncher(BuildListener listener) throws IOException, InterruptedException {
            Launcher l = getCurrentNode().createLauncher(listener);

            if (project instanceof BuildableItemWithBuildWrappers) {
                BuildableItemWithBuildWrappers biwbw = (BuildableItemWithBuildWrappers) project;
                for (BuildWrapper bw : biwbw.getBuildWrappersList())
                    l = bw.decorateLauncher(AbstractBuild.this,l,listener);
            }

            buildEnvironments = new ArrayList<Environment>();

            for (NodeProperty nodeProperty: Hudson.getInstance().getGlobalNodeProperties()) {
                Environment environment = nodeProperty.setUp(AbstractBuild.this, l, listener);
                if (environment != null) {
                    buildEnvironments.add(environment);
                }
            }

            for (NodeProperty nodeProperty: Computer.currentComputer().getNode().getNodeProperties()) {
                Environment environment = nodeProperty.setUp(AbstractBuild.this, l, listener);
                if (environment != null) {
                    buildEnvironments.add(environment);
                }
            }

            return l;
        }

        private void checkout(BuildListener listener) throws Exception {
            try {
                for (int retryCount=project.getScmCheckoutRetryCount(); ; retryCount--) {
                    // for historical reasons, null in the scm field means CVS, so we need to explicitly set this to something
                    // in case check out fails and leaves a broken changelog.xml behind.
                    // see http://www.nabble.com/CVSChangeLogSet.parse-yields-SAXParseExceptions-when-parsing-bad-*AccuRev*-changelog.xml-files-td22213663.html
                    AbstractBuild.this.scm = new NullChangeLogParser();

                    try {
                        if (project.checkout(AbstractBuild.this,launcher,listener,new File(getRootDir(),"changelog.xml"))) {
                            // check out succeeded
                            SCM scm = project.getScm();

                            AbstractBuild.this.scm = scm.createChangeLogParser();
                            AbstractBuild.this.changeSet = AbstractBuild.this.calcChangeSet();

                            for (SCMListener l : Hudson.getInstance().getSCMListeners())
                                l.onChangeLogParsed(AbstractBuild.this,listener,changeSet);
                            return;
                        }
                    } catch (AbortException e) {
                        // checkout error already reported
                    } catch (IOException e) {
                        // checkout error not yet reported
                        listener.getLogger().println(e.getMessage());
                    }

                    if (retryCount == 0)   // all attempts failed
                        throw new RunnerAbortedException();

                    listener.getLogger().println("Retrying after 10 seconds");
                    Thread.sleep(10000);
                }
            } catch (InterruptedException e) {
                listener.getLogger().println(Messages.AbstractProject_ScmAborted());
                LOGGER.log(Level.INFO, AbstractBuild.this + " aborted", e);
                throw new RunnerAbortedException();
            }
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

                if (result.isBetterOrEqualTo(Result.UNSTABLE))
                    createSymlink(listener, "lastSuccessful");

                if (result.isBetterOrEqualTo(Result.SUCCESS))
                    createSymlink(listener, "lastStable");
            } finally {
                // update the culprit list
                HashSet<String> r = new HashSet<String>();
                for (User u : getCulprits())
                    r.add(u.getId());
                culprits = r;
                CheckPoint.CULPRITS_DETERMINED.report();
            }
        }

        public void cleanUp(BuildListener listener) throws Exception {
            // default is no-op
        }

        /**
         * @deprecated as of 1.356
         *      Use {@link #performAllBuildSteps(BuildListener, Map, boolean)}
         */
        protected final void performAllBuildStep(BuildListener listener, Map<?,? extends BuildStep> buildSteps, boolean phase) throws InterruptedException, IOException {
            performAllBuildSteps(listener,buildSteps.values(),phase);
        }

        protected final boolean performAllBuildSteps(BuildListener listener, Map<?,? extends BuildStep> buildSteps, boolean phase) throws InterruptedException, IOException {
            return performAllBuildSteps(listener,buildSteps.values(),phase);
        }

        /**
         * @deprecated as of 1.356
         *      Use {@link #performAllBuildSteps(BuildListener, Iterable, boolean)}
         */
        protected final void performAllBuildStep(BuildListener listener, Iterable<? extends BuildStep> buildSteps, boolean phase) throws InterruptedException, IOException {
            performAllBuildSteps(listener,buildSteps,phase);
        }

        /**
         * Runs all the given build steps, even if one of them fail.
         *
         * @param phase
         *      true for the post build processing, and false for the final "run after finished" execution.
         */
        protected final boolean performAllBuildSteps(BuildListener listener, Iterable<? extends BuildStep> buildSteps, boolean phase) throws InterruptedException, IOException {
            boolean r = true;
            for (BuildStep bs : buildSteps) {
                if ((bs instanceof Publisher && ((Publisher)bs).needsToRunAfterFinalized()) ^ phase)
                    try {
                        r &= perform(bs,listener);
                    } catch (Exception e) {
                        String msg = "Publisher " + bs.getClass().getName() + " aborted due to exception";
                        e.printStackTrace(listener.error(msg));
                        LOGGER.log(Level.WARNING, msg, e);
                        setResult(Result.FAILURE);
                    }
            }
            return r;
        }

        /**
         * Calls a build step.
         */
        protected final boolean perform(BuildStep bs, BuildListener listener) throws InterruptedException, IOException {
            BuildStepMonitor mon;
            try {
                mon = bs.getRequiredMonitorService();
            } catch (AbstractMethodError e) {
                mon = BuildStepMonitor.BUILD;
            }
            return mon.perform(bs, AbstractBuild.this, launcher, listener);
        }

        protected final boolean preBuild(BuildListener listener,Map<?,? extends BuildStep> steps) {
            return preBuild(listener,steps.values());
        }

        protected final boolean preBuild(BuildListener listener,Collection<? extends BuildStep> steps) {
            return preBuild(listener,(Iterable<? extends BuildStep>)steps);
        }

        protected final boolean preBuild(BuildListener listener,Iterable<? extends BuildStep> steps) {
            for (BuildStep bs : steps)
                if (!bs.prebuild(AbstractBuild.this,listener))
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
        if (scm==null) {
            // for historical reason, null means CVS.
            try {
                Class<?> c = Hudson.getInstance().getPluginManager().uberClassLoader.loadClass("hudson.scm.CVSChangeLogParser");
                scm = (ChangeLogParser)c.newInstance();
            } catch (ClassNotFoundException e) {
                // if CVS isn't available, fall back to something non-null.
                scm = new NullChangeLogParser();
            } catch (InstantiationException e) {
                scm = new NullChangeLogParser();
                throw (Error)new InstantiationError().initCause(e);
            } catch (IllegalAccessException e) {
                scm = new NullChangeLogParser();
                throw (Error)new IllegalAccessError().initCause(e);
            }
        }

        if (changeSet==null) // cached value
            try {
                changeSet = calcChangeSet();
            } finally {
                // defensive check. if the calculation fails (such as through an exception),
                // set a dummy value so that it'll work the next time. the exception will
                // be still reported, giving the plugin developer an opportunity to fix it.
                if (changeSet==null)
                    changeSet=ChangeLogSet.createEmpty(this);
            }
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
        if (!changelogFile.exists())
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
    public EnvVars getEnvironment(TaskListener log) throws IOException, InterruptedException {
        EnvVars env = super.getEnvironment(log);
        FilePath ws = getWorkspace();
        if (ws!=null)   // if this is done very early on in the build, workspace may not be decided yet. see HUDSON-3997
            env.put("WORKSPACE", ws.getRemote());
        // servlet container may have set CLASSPATH in its launch script,
        // so don't let that inherit to the new child process.
        // see http://www.nabble.com/Run-Job-with-JDK-1.4.2-tf4468601.html
        env.put("CLASSPATH","");

        JDK jdk = project.getJDK();
        if (jdk != null) {
            Computer computer = Computer.currentComputer();
            if (computer != null) { // just in case were not in a build
                jdk = jdk.forNode(computer.getNode(), log);
            }
            jdk.buildEnvVars(env);
        }
        project.getScm().buildEnvVars(this,env);

        if (buildEnvironments!=null)
            for (Environment e : buildEnvironments)
                e.buildEnvVars(env);

        for (EnvironmentContributingAction a : Util.filter(getActions(),EnvironmentContributingAction.class))
            a.buildEnvVars(this,env);

        EnvVars.resolve(env);

        return env;
    }

    public Calendar due() {
        return getTimestamp();
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
     * <p>
     * This also includes build parameters if a build is parameterized.
     *
     * @return
     *      The returned map is mutable so that subtypes can put more values.
     */
    public Map<String,String> getBuildVariables() {
        Map<String,String> r = new HashMap<String, String>();

        ParametersAction parameters = getAction(ParametersAction.class);
        if (parameters!=null) {
            // this is a rather round about way of doing this...
            for (ParameterValue p : parameters) {
                String v = p.createVariableResolver(this).resolve(p.getName());
                if (v!=null) r.put(p.getName(),v);
            }
        }
        return r;
    }

    /**
     * Creates {@link VariableResolver} backed by {@link #getBuildVariables()}.
     */
    public final VariableResolver<String> getBuildVariableResolver() {
        return new VariableResolver.ByMap<String>(getBuildVariables());
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
            if (!p.isKeepDependencies()) continue;

            AbstractBuild<?,?> fb = p.getFirstBuild();
            if (fb==null)        continue; // no active record

            // is there any active build that depends on us?
            for (int i : getDownstreamRelationship(p).listNumbersReverse()) {
                // TODO: this is essentially a "find intersection between two sparse sequences"
                // and we should be able to do much better.

                if (i<fb.getNumber())
                    continue OUTER; // all the other records are younger than the first record, so pointless to search.

                AbstractBuild<?,?> b = p.getBuildByNumber(i);
                if (b!=null)
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
        if (f==null)     return rs;

        // look for fingerprints that point to this build as the source, and merge them all
        for (Fingerprint e : f.getFingerprints().values()) {

            if (upstreamCulprits) {
                // With upstreamCulprits, we allow downstream relationships
                // from intermediate jobs
                rs.add(e.getRangeSet(that));
            } else {
                BuildPtr o = e.getOriginal();
                if (o!=null && o.is(this))
                    rs.add(e.getRangeSet(that));
            }
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
        if (f==null)     return -1;

        int n = -1;

        // look for fingerprints that point to the given project as the source, and merge them all
        for (Fingerprint e : f.getFingerprints().values()) {
            if (upstreamCulprits) {
                // With upstreamCulprits, we allow upstream relationships
                // from intermediate jobs
                Fingerprint.RangeSet rangeset = e.getRangeSet(that);
                if (!rangeset.isEmpty()) {
                    n = Math.max(n, rangeset.listNumbersReverse().iterator().next());
                }
            } else {
                BuildPtr o = e.getOriginal();
                if (o!=null && o.belongsTo(that))
                    n = Math.max(n,o.getNumber());
            }
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
        if (n==-1)   return null;
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
            if (p.isFingerprintConfigured())
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
            if (n>=0)
                r.put(p,n);
        }
        return r;
    }

    /**
     * Gets the changes in the dependency between the given build and this build.
     */
    public Map<AbstractProject,DependencyChange> getDependencyChanges(AbstractBuild from) {
        if (from==null)             return Collections.emptyMap(); // make it easy to call this from views
        FingerprintAction n = this.getAction(FingerprintAction.class);
        FingerprintAction o = from.getAction(FingerprintAction.class);
        if (n==null || o==null)     return Collections.emptyMap();

        Map<AbstractProject,Integer> ndep = n.getDependencies();
        Map<AbstractProject,Integer> odep = o.getDependencies();

        Map<AbstractProject,DependencyChange> r = new HashMap<AbstractProject,DependencyChange>();

        for (Map.Entry<AbstractProject,Integer> entry : odep.entrySet()) {
            AbstractProject p = entry.getKey();
            Integer oldNumber = entry.getValue();
            Integer newNumber = ndep.get(p);
            if (newNumber!=null && oldNumber.compareTo(newNumber)<0) {
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
            if (b!=null && b.getNumber()==fromId)
                b = b.getNextBuild(); // fromId exclusive

            while (b!=null && b.getNumber()<=toId) {
                r.add(b);
                b = b.getNextBuild();
            }

            return r;
        }
    }

    //
    // web methods
    //

    /**
     * Stops this build if it's still going.
     *
     * If we use this/executor/stop URL, it causes 404 if the build is already killed,
     * as {@link #getExecutor()} returns null.
     */
    public synchronized void doStop(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        Executor e = getExecutor();
        if (e!=null)
            e.doStop(req,rsp);
        else
            // nothing is building
            rsp.forwardToPreviousPage(req);
    }

    private static final Logger LOGGER = Logger.getLogger(AbstractBuild.class.getName());
}
