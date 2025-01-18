/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Yahoo! Inc., CloudBees, Inc.
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

import static java.util.logging.Level.WARNING;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.Util;
import hudson.console.ModelHyperlinkNote;
import hudson.model.Fingerprint.BuildPtr;
import hudson.model.Fingerprint.RangeSet;
import hudson.model.labels.LabelAtom;
import hudson.model.listeners.RunListener;
import hudson.model.listeners.SCMListener;
import hudson.remoting.ChannelClosedException;
import hudson.remoting.RequestAbortedException;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.NullChangeLogParser;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import hudson.slaves.NodeProperty;
import hudson.slaves.OfflineCause;
import hudson.slaves.WorkspaceList;
import hudson.slaves.WorkspaceList.Lease;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.BuildTrigger;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Builder;
import hudson.tasks.Fingerprinter.FingerprintAction;
import hudson.tasks.Publisher;
import hudson.util.AdaptedIterator;
import hudson.util.HttpResponses;
import hudson.util.Iterators;
import hudson.util.VariableResolver;
import io.jenkins.servlet.ServletExceptionWrapper;
import jakarta.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.ref.WeakReference;
import java.nio.channels.ClosedByInterruptException;
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
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.model.lazy.BuildReference;
import jenkins.model.lazy.LazyBuildMixIn;
import jenkins.scm.RunWithSCM;
import jenkins.util.SystemProperties;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.xml.sax.SAXException;

/**
 * Base implementation of {@link Run}s that build software.
 *
 * For now this is primarily the common part of {@link Build} and MavenBuild.
 *
 * @author Kohsuke Kawaguchi
 * @see AbstractProject
 */
public abstract class AbstractBuild<P extends AbstractProject<P, R>, R extends AbstractBuild<P, R>> extends Run<P, R> implements Queue.Executable, LazyBuildMixIn.LazyLoadingRun<P, R>, RunWithSCM<P, R> {

    /**
     * Set if we want the blame information to flow from upstream to downstream build.
     */
    private static final boolean upstreamCulprits = SystemProperties.getBoolean("hudson.upstreamCulprits");

    /**
     * Name of the agent this project was built on.
     * Null or "" if built by the built-in node. (null happens when we read old record that didn't have this information.)
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
     */
    private ChangeLogParser scm;

    /**
     * Changes in this build.
     */
    private transient volatile WeakReference<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSet;

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
     * During the build this field remembers {@link hudson.tasks.BuildWrapper.Environment}s created by
     * {@link BuildWrapper}. This design is bit ugly but forced due to compatibility.
     */
    protected transient List<Environment> buildEnvironments;

    private final transient LazyBuildMixIn.RunMixIn<P, R> runMixIn = new LazyBuildMixIn.RunMixIn<>() {
        @Override protected R asRun() {
            return _this();
        }
    };

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

    @Override public final LazyBuildMixIn.RunMixIn<P, R> getRunMixIn() {
        return runMixIn;
    }

    @NonNull
    @Override protected final BuildReference<R> createReference() {
        return getRunMixIn().createReference();
    }

    @Override protected final void dropLinks() {
        getRunMixIn().dropLinks();
    }

    @Override
    public R getPreviousBuild() {
        return getRunMixIn().getPreviousBuild();
    }

    @Override
    public R getNextBuild() {
        return getRunMixIn().getNextBuild();
    }

    /**
     * Returns a {@link Slave} on which this build was done.
     *
     * @return
     *      null, for example if the agent that this build run no longer exists.
     */
    public @CheckForNull Node getBuiltOn() {
        if (builtOn == null || builtOn.isEmpty())
            return Jenkins.get();
        else
            return Jenkins.get().getNode(builtOn);
    }

    /**
     * Returns the name of the agent it was built on; null or "" if built by the built-in node.
     * (null happens when we read old record that didn't have this information.)
     */
    @Exported(name = "builtOn")
    public String getBuiltOnStr() {
        return builtOn;
    }

    /**
     * Allows subtypes to set the value of {@link #builtOn}.
     * This is used for those implementations where an {@link AbstractBuild} is made 'built' without
     * actually running its {@link #run()} method.
     *
     * @since 1.429
     */
    protected void setBuiltOnStr(String builtOn) {
        this.builtOn = builtOn;
    }

    /**
     * Gets the nearest ancestor {@link AbstractBuild} that belongs to
     * {@linkplain AbstractProject#getRootProject() the root project of getProject()} that
     * dominates/governs/encompasses this build.
     *
     * <p>
     * Some projects (such as matrix projects, Maven projects, or promotion processes) form a tree of jobs,
     * and still in some of them, builds of child projects are related/tied to that of the parent project.
     * In such a case, this method returns the governing build.
     *
     * @return never null. In the worst case the build dominates itself.
     * @since 1.421
     * @see AbstractProject#getRootProject()
     */
    public AbstractBuild<?, ?> getRootBuild() {
        return this;
    }

    @Override
    public Queue.Executable getParentExecutable() {
        AbstractBuild<?, ?> rootBuild = getRootBuild();
        return rootBuild != this ? rootBuild : null;
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
     * @deprecated navigation through a hierarchy should be done through breadcrumbs, do not add a link using this method
     */
    @Deprecated(since = "2.364")
    public String getUpUrl() {
        return Functions.getNearestAncestorUrl(Stapler.getCurrentRequest2(), getParent()) + '/';
    }

    /**
     * Gets the directory where this build is being built.
     *
     * <p>
     * Note to implementors: to control where the workspace is created, override
     * {@link AbstractBuildExecution#decideWorkspace(Node,WorkspaceList)}.
     *
     * @return
     *      null if the workspace is on an agent that's not connected. Note that once the build is completed,
     *      the workspace may be used to build something else, so the value returned from this method may
     *      no longer show a workspace as it was used for this build.
     * @since 1.319
     */
    public final @CheckForNull FilePath getWorkspace() {
        if (workspace == null) return null;
        Node n = getBuiltOn();
        if (n == null) return null;
        return n.createPath(workspace);
    }

    /**
     * Normally, a workspace is assigned by {@link hudson.model.Run.RunExecution}, but this lets you set the workspace in case
     * {@link AbstractBuild} is created without a build.
     */
    protected void setWorkspace(@NonNull FilePath ws) {
        this.workspace = ws.getRemote();
    }

    /**
     * Returns the root directory of the checked-out module.
     * <p>
     * This is usually where {@code pom.xml}, {@code build.xml}
     * and so on exists.
     */
    public final FilePath getModuleRoot() {
        FilePath ws = getWorkspace();
        if (ws == null)    return null;
        return getParent().getScm().getModuleRoot(ws, this);
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
        if (ws == null)    return null;
        return getParent().getScm().getModuleRoots(ws, this);
    }

    @Override
    @CheckForNull public Set<String> getCulpritIds() {
        return culprits;
    }

    @Override
    @Exported
    @NonNull public Set<User> getCulprits() {
        return RunWithSCM.super.getCulprits();
    }

    @Override
    public boolean shouldCalculateCulprits() {
        return getCulpritIds() == null;
    }

    @Override
    @NonNull
    public Set<User> calculateCulprits() {
        Set<User> c = RunWithSCM.super.calculateCulprits();

        AbstractBuild<P, R> p = getPreviousCompletedBuild();
        if (upstreamCulprits) {
            // If we have dependencies since the last successful build, add their authors to our list
            if (p != null && p.getPreviousNotFailedBuild() != null) {
                Map<AbstractProject, AbstractBuild.DependencyChange> depmap =
                        p.getDependencyChanges(p.getPreviousSuccessfulBuild());
                for (AbstractBuild.DependencyChange dep : depmap.values()) {
                    for (AbstractBuild<?, ?> b : dep.getBuilds()) {
                        for (ChangeLogSet.Entry entry : b.getChangeSet()) {
                            c.add(entry.getAuthor());
                        }
                    }
                }
            }
        }

        return c;
    }

    /**
     * Gets the version of Hudson that was used to build this job.
     *
     * @since 1.246
     */
    public String getHudsonVersion() {
        return hudsonVersion;
    }

    /**
     * @deprecated as of 1.467
     *      Please use {@link hudson.model.Run.RunExecution}
     */
    @Deprecated
    public abstract class AbstractRunner extends AbstractBuildExecution {

    }

    public abstract class AbstractBuildExecution extends Runner {
        /*
            Some plugins might depend on this instance castable to Runner, so we need to use
            deprecated class here.
         */

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
         * Lease of the workspace.
         */
        private Lease lease;

        /**
         * Returns the current {@link Node} on which we are building.
         * @return Returns the current {@link Node}
         * @throws IllegalStateException if that cannot be determined
         */
        protected final @NonNull Node getCurrentNode() throws IllegalStateException {
            Executor exec = Executor.currentExecutor();
            if (exec == null) {
                throw new IllegalStateException("not being called from an executor thread");
            }
            Computer c = exec.getOwner();
            Node node = c.getNode();
            if (node == null) {
                throw new IllegalStateException("no longer a configured node for " + c.getName());
            }
            return node;
        }

        public Launcher getLauncher() {
            return launcher;
        }

        public BuildListener getListener() {
            return listener;
        }

        /**
         * Allocates the workspace from {@link WorkspaceList}.
         *
         * @param n
         *      Passed in for the convenience. The node where the build is running.
         * @param wsl
         *      Passed in for the convenience. The returned path must be registered to this object.
         */
        protected Lease decideWorkspace(@NonNull Node n, WorkspaceList wsl) throws InterruptedException, IOException {
            String customWorkspace = getProject().getCustomWorkspace();
            if (customWorkspace != null) {
                FilePath rootPath = n.getRootPath();
                if (rootPath == null) {
                    throw new AbortException(n.getDisplayName() + " seems to be offline");
                }
                // we allow custom workspaces to be concurrently used between jobs.
                return Lease.createDummyLease(rootPath.child(getEnvironment(listener).expand(customWorkspace)));
            }
            // TODO: this cast is indicative of abstraction problem
            FilePath ws = n.getWorkspaceFor((TopLevelItem) getProject());
            if (ws == null) {
                throw new AbortException(n.getDisplayName() + " seems to be offline");
            }
            return wsl.allocate(ws, getBuild());
        }

        @NonNull
        @Override
        public Result run(@NonNull BuildListener listener) throws Exception {
            final Node node = getCurrentNode();

            assert builtOn == null;
            builtOn = node.getNodeName();
            hudsonVersion = Jenkins.VERSION;
            this.listener = listener;

            Result result = null;
            buildEnvironments = new ArrayList<>();
            // JENKINS-43889: try/finally to make sure Environments are eventually torn down. This used to be done in
            // the doRun() implementation, but was not happening in case of early error (for instance in SCM checkout).
            // Because some plugin (Maven) implement their own doRun() logic which still includes tearing down in some
            // cases, we use a dummy Environment as a marker, to avoid doing it here if redundant.
            TearDownCheckEnvironment tearDownMarker = new TearDownCheckEnvironment();
            buildEnvironments.add(tearDownMarker);
            try {
                launcher = createLauncher(listener);
                if (!Jenkins.get().getNodes().isEmpty()) {
                    if (node instanceof Jenkins) {
                        listener.getLogger().print(Messages.AbstractBuild_BuildingOnMaster());
                    } else {
                        listener.getLogger().print(Messages.AbstractBuild_BuildingRemotely(ModelHyperlinkNote.encodeTo("/computer/" + builtOn, node.getDisplayName())));
                        Set<LabelAtom> assignedLabels = new HashSet<>(node.getAssignedLabels());
                        assignedLabels.remove(node.getSelfLabel());
                        if (!assignedLabels.isEmpty()) {
                            boolean first = true;
                            for (LabelAtom label : assignedLabels) {
                                if (first) {
                                    listener.getLogger().print(" (");
                                    first = false;
                                } else {
                                    listener.getLogger().print(' ');
                                }
                                listener.getLogger().print(label.getName());
                            }
                            listener.getLogger().print(')');
                        }
                    }
                } else {
                    listener.getLogger().print(Messages.AbstractBuild_Building());
                }

                lease = decideWorkspace(node, Computer.currentComputer().getWorkspaceList());

                workspace = lease.path.getRemote();
                listener.getLogger().println(Messages.AbstractBuild_BuildingInWorkspace(workspace));

                for (WorkspaceListener wl : WorkspaceListener.all()) {
                    wl.beforeUse(AbstractBuild.this, lease.path, listener);
                }

                getProject().getScmCheckoutStrategy().preCheckout(AbstractBuild.this, launcher, this.listener);
                getProject().getScmCheckoutStrategy().checkout(this);

                if (!preBuild(listener, project.getProperties()))
                    return Result.FAILURE;

                result = doRun(listener);
            } finally {
                if (!tearDownMarker.tornDown) {
                    // looks like environments are not torn down yet, do it now (might affect the build result)
                    result = Result.combine(result, tearDownBuildEnvironments(listener));
                }
            }

            if (node.getChannel() != null) {
                // kill run-away processes that are left
                // use multiple environment variables so that people can escape this massacre by overriding an environment
                // variable for some processes
                launcher.kill(getCharacteristicEnvVars());
            }

            // this is ugly, but for historical reason, if non-null value is returned
            // it should become the final result.
            if (result == null)    result = getResult();
            if (result == null)    result = Result.SUCCESS;

            return result;
        }

        /**
         * Tear down all build environments (in reverse order).
         * <p>
         * Returns a failure {@link Result} in case of failure of at least one {@code tearDown()} method (returning
         * false, or throwing some exception), and {@code null} if everything went fine.
         *
         * @return a build result in case of failure/exception
         * @throws InterruptedException
         *      if thrown while tearing down an environment (would be the first caught one in case caught several)
         */
        private Result tearDownBuildEnvironments(@NonNull BuildListener listener) throws InterruptedException {
            Result result = null;
            InterruptedException firstInterruptedException = null;
            // iterate in reverse order on the environments list
            for (int i = buildEnvironments.size() - 1; i >= 0; i--) {
                final Environment environment = buildEnvironments.get(i);
                try {
                    if (!environment.tearDown(AbstractBuild.this, listener)) {
                        // by returning false, tearDown() can actually fail the build
                        result = Result.combine(result, Result.FAILURE);
                    }
                } catch (InterruptedException e) {
                    // We got interrupted while tearing down an environment.  We'll still try to tear down the
                    // remaining ones, but then we'll re-throw the (first) caught InterruptedException, to let
                    // the caller (ie., Run#execute(RunExecution)) deal with it properly.
                    if (firstInterruptedException == null) {
                        firstInterruptedException = e;
                    } else {
                        // log only InterruptedException we won't re-throw
                        Functions.printStackTrace(e, listener.error("Interrupted during tear down: " + e.getMessage()));
                    }
                } catch (IOException | RuntimeException e) {
                    // exceptions are only logged, to give a chance to all environments to tear down
                    if (e instanceof IOException) {
                        // similar to Run#handleFatalBuildProblem(BuildListener, Throwable)
                        Util.displayIOException((IOException) e, listener);
                    }
                    Functions.printStackTrace(e, listener.error("Unable to tear down: " + e.getMessage()));
                    // would UNSTABLE be more sensible? (see discussion in PR #4517)
                    result = Result.combine(result, Result.FAILURE);
                }
            }
            if (firstInterruptedException != null) {
                // don't forget we've been interrupted
                throw firstInterruptedException;
            }
            return result;
        }

        /**
         * Creates a {@link Launcher} that this build will use. This can be overridden by derived types
         * to decorate the resulting {@link Launcher}.
         *
         * @param listener
         *      Always non-null. Connected to the main build output.
         */
        @NonNull
        protected Launcher createLauncher(@NonNull BuildListener listener) throws IOException, InterruptedException {
            final Node currentNode = getCurrentNode();
            Launcher l = currentNode.createLauncher(listener);

            if (project instanceof BuildableItemWithBuildWrappers biwbw) {
                for (BuildWrapper bw : biwbw.getBuildWrappersList())
                    l = bw.decorateLauncher(AbstractBuild.this, l, listener);
            }

            for (RunListener rl : RunListener.all()) {
                Environment environment = rl.setUpEnvironment(AbstractBuild.this, l, listener);
                if (environment != null) {
                    buildEnvironments.add(environment);
                }
            }

            for (NodeProperty nodeProperty : Jenkins.get().getGlobalNodeProperties()) {
                Environment environment = nodeProperty.setUp(AbstractBuild.this, l, listener);
                if (environment != null) {
                    buildEnvironments.add(environment);
                }
            }

            for (NodeProperty nodeProperty : currentNode.getNodeProperties()) {
                Environment environment = nodeProperty.setUp(AbstractBuild.this, l, listener);
                if (environment != null) {
                    buildEnvironments.add(environment);
                }
            }

            return l;
        }

        public void defaultCheckout() throws IOException, InterruptedException {
            AbstractBuild<?, ?> build = AbstractBuild.this;
            AbstractProject<?, ?> project = build.getProject();

            for (int retryCount = project.getScmCheckoutRetryCount(); ; retryCount--) {
                build.scm = NullChangeLogParser.INSTANCE;

                try {
                    File changeLogFile = new File(build.getRootDir(), "changelog.xml");
                    if (project.checkout(build, launcher, listener, changeLogFile)) {
                        // check out succeeded
                        SCM scm = project.getScm();
                        for (SCMListener l : SCMListener.all()) {
                            try {
                                l.onCheckout(build, scm, build.getWorkspace(), listener, changeLogFile, build.getAction(SCMRevisionState.class));
                            } catch (Exception e) {
                                throw new IOException(e);
                            }
                        }

                        build.scm = scm.createChangeLogParser();
                        build.changeSet = new WeakReference<>(build.calcChangeSet());

                        for (SCMListener l : SCMListener.all())
                            try {
                                l.onChangeLogParsed(build, listener, build.getChangeSet());
                            } catch (Exception e) {
                                throw new IOException("Failed to parse changelog", e);
                            }

                        // Get a chance to do something after checkout and changelog is done
                        scm.postCheckout(build, launcher, build.getWorkspace(), listener);

                        return;
                    }
                } catch (AbortException e) {
                    listener.error(e.getMessage());
                } catch (ClosedByInterruptException | InterruptedIOException e) {
                    throw (InterruptedException) new InterruptedException().initCause(e);
                } catch (IOException e) {
                    // checkout error not yet reported
                    Functions.printStackTrace(e, listener.getLogger());
                }

                if (retryCount == 0)   // all attempts failed
                    throw new RunnerAbortedException();

                listener.getLogger().println("Retrying after 10 seconds");
                Thread.sleep(10000);
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
        protected abstract Result doRun(BuildListener listener) throws Exception;

        /**
         * @see #post(BuildListener)
         */
        protected abstract void post2(BuildListener listener) throws Exception;

        @Override
        public final void post(@NonNull BuildListener listener) throws Exception {
            try {
                post2(listener);
            } finally {
                // update the culprit list
                SortedSet<String> r = new TreeSet<>();
                for (User u : getCulprits())
                    r.add(u.getId());
                culprits = Collections.unmodifiableSet(r);
                CheckPoint.CULPRITS_DETERMINED.report();
            }
        }

        @Override
        public void cleanUp(@NonNull BuildListener listener) throws Exception {
            if (lease != null) {
                lease.release();
                lease = null;
            }
            BuildTrigger.execute(AbstractBuild.this, listener);
            buildEnvironments = null;
        }

        /**
         * @deprecated as of 1.356
         *      Use {@link #performAllBuildSteps(BuildListener, Map, boolean)}
         */
       @Deprecated
        protected final void performAllBuildStep(BuildListener listener, Map<?, ? extends BuildStep> buildSteps, boolean phase) throws InterruptedException, IOException {
            performAllBuildSteps(listener, buildSteps.values(), phase);
        }

        protected final boolean performAllBuildSteps(BuildListener listener, Map<?, ? extends BuildStep> buildSteps, boolean phase) throws InterruptedException, IOException {
            return performAllBuildSteps(listener, buildSteps.values(), phase);
        }

        /**
         * @deprecated as of 1.356
         *      Use {@link #performAllBuildSteps(BuildListener, Iterable, boolean)}
         */
        @Deprecated
        protected final void performAllBuildStep(BuildListener listener, Iterable<? extends BuildStep> buildSteps, boolean phase) throws InterruptedException, IOException {
            performAllBuildSteps(listener, buildSteps, phase);
        }

        /**
         * Runs all the given build steps, even if one of them fail.
         *
         * @param phase
         *      true for the post build processing, and false for the final "run after finished" execution.
         *
         * @return false if any build step failed
         */
        protected final boolean performAllBuildSteps(BuildListener listener, Iterable<? extends BuildStep> buildSteps, boolean phase) throws InterruptedException, IOException {
            boolean r = true;
            for (BuildStep bs : buildSteps) {
                if ((bs instanceof Publisher && ((Publisher) bs).needsToRunAfterFinalized()) ^ phase)
                    try {
                        if (!perform(bs, listener)) {
                            LOGGER.log(Level.FINE, "{0} : {1} failed", new Object[] {AbstractBuild.this, bs});
                            r = false;
                            if (phase) {
                                setResult(Result.FAILURE);
                            }
                        }
                    } catch (Exception | LinkageError e) {
                        reportError(bs, e, listener, phase);
                        r = false;
                    }
            }
            return r;
        }

        private void reportError(BuildStep bs, Throwable e, BuildListener listener, boolean phase) {
            final String buildStep;

            if (bs instanceof Describable) {
                buildStep = ((Describable) bs).getDescriptor().getDisplayName();
            } else {
                buildStep = bs.getClass().getName();
            }

            if (e instanceof AbortException) {
                LOGGER.log(Level.FINE, "{0} : {1} failed", new Object[] {AbstractBuild.this, buildStep});
                listener.error("Step ‘" + buildStep + "’ failed: " + e.getMessage());
            } else {
                String msg = "Step ‘" + buildStep + "’ aborted due to exception: ";
                Functions.printStackTrace(e, listener.error(msg));
                LOGGER.log(WARNING, msg, e);
            }

            if (phase) {
                setResult(Result.FAILURE);
            }
        }

        /**
         * Calls a build step.
         */
        protected final boolean perform(BuildStep bs, BuildListener listener) throws InterruptedException, IOException {
            BuildStepMonitor mon = bs.getRequiredMonitorService();
            Result oldResult = AbstractBuild.this.getResult();
            for (BuildStepListener bsl : BuildStepListener.all()) {
                bsl.started(AbstractBuild.this, bs, listener);
            }

            boolean canContinue = false;
            try {

                canContinue = mon.perform(bs, AbstractBuild.this, launcher, listener);
            } catch (RequestAbortedException | ChannelClosedException ex) {
                // Channel is closed, do not continue
                reportBrokenChannel(listener);
            } catch (RuntimeException ex) {
                Functions.printStackTrace(ex, listener.error("Build step failed with exception"));
            }

            for (BuildStepListener bsl : BuildStepListener.all()) {
                bsl.finished(AbstractBuild.this, bs, listener, canContinue);
            }
            Result newResult = AbstractBuild.this.getResult();
            if (newResult != oldResult) {
                String buildStepName = getBuildStepName(bs);
                listener.getLogger().format("Build step '%s' changed build result to %s%n", buildStepName, newResult);
            }
            if (!canContinue) {
                String buildStepName = getBuildStepName(bs);
                listener.getLogger().format("Build step '%s' marked build as failure%n", buildStepName);
            }
            return canContinue;
        }

        private void reportBrokenChannel(BuildListener listener) throws IOException {
            final Node node = getCurrentNode();
            listener.hyperlink("/" + node.toComputer().getUrl() + "log", "Agent went offline during the build");
            listener.getLogger().println();
            final OfflineCause offlineCause = node.toComputer().getOfflineCause();
            if (offlineCause != null) {
                listener.error(offlineCause.toString());
            }
        }

        private String getBuildStepName(BuildStep bs) {
            if (bs instanceof Describable<?>) {
                return ((Describable<?>) bs).getDescriptor().getDisplayName();
            } else {
                return bs.getClass().getSimpleName();
            }
        }

        protected final boolean preBuild(BuildListener listener, Map<?, ? extends BuildStep> steps) {
            return preBuild(listener, steps.values());
        }

        protected final boolean preBuild(BuildListener listener, Collection<? extends BuildStep> steps) {
            return preBuild(listener, (Iterable<? extends BuildStep>) steps);
        }

        protected final boolean preBuild(BuildListener listener, Iterable<? extends BuildStep> steps) {
            for (BuildStep bs : steps)
                if (!bs.prebuild(AbstractBuild.this, listener)) {
                    LOGGER.log(Level.FINE, "{0} : {1} failed", new Object[] {AbstractBuild.this, bs});
                    return false;
                }
            return true;
        }
    }

    /**
     * An {@link Environment} which does nothing, but change state when it gets torn down. Used in
     * {@link AbstractBuildExecution#run(BuildListener)} to detect whether environments have yet to be torn down,
     * or if it has been done already (in the {@link AbstractBuildExecution#doRun(BuildListener)} implementation).
     */
    private static class TearDownCheckEnvironment extends Environment {
        private boolean tornDown = false;

        @Override
        public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
            this.tornDown = true;
            return true;
        }
    }

    /*
     * No need to lock the entire AbstractBuild on change set calculation
     */
    private transient Object changeSetLock = new Object();

    /**
     * Gets the changes incorporated into this build.
     *
     * @return never null.
     */
    @Exported
    @NonNull public ChangeLogSet<? extends ChangeLogSet.Entry> getChangeSet() {
        synchronized (changeSetLock) {
            if (scm == null) {
                scm = NullChangeLogParser.INSTANCE;
            }
        }

        ChangeLogSet<? extends ChangeLogSet.Entry> cs = null;
        if (changeSet != null)
            cs = changeSet.get();

        if (cs == null)
            cs = calcChangeSet();

        // defensive check. if the calculation fails (such as through an exception),
        // set a dummy value so that it'll work the next time. the exception will
        // be still reported, giving the plugin developer an opportunity to fix it.
        if (cs == null)
            cs = ChangeLogSet.createEmpty(this);

        changeSet = new WeakReference<>(cs);
        return cs;
    }

    @Override
    @NonNull public List<ChangeLogSet<? extends ChangeLogSet.Entry>> getChangeSets() {
        ChangeLogSet<? extends ChangeLogSet.Entry> cs = getChangeSet();
        return cs.isEmptySet() ? Collections.emptyList() : List.of(cs);
    }

    /**
     * Returns true if the changelog is already computed.
     */
    public boolean hasChangeSetComputed() {
        File changelogFile = new File(getRootDir(), "changelog.xml");
        return changelogFile.exists();
    }

    private ChangeLogSet<? extends ChangeLogSet.Entry> calcChangeSet() {
        File changelogFile = new File(getRootDir(), "changelog.xml");
        if (!changelogFile.exists())
            return ChangeLogSet.createEmpty(this);

        try {
            return scm.parse(this, changelogFile);
        } catch (IOException | SAXException e) {
            LOGGER.log(WARNING, "Failed to parse " + changelogFile, e);
        }
        return ChangeLogSet.createEmpty(this);
    }

    @NonNull
    @Override
    public EnvVars getEnvironment(@NonNull TaskListener log) throws IOException, InterruptedException {
        EnvVars env = super.getEnvironment(log);
        FilePath ws = getWorkspace();
        if (ws != null) { // if this is done very early on in the build, workspace may not be decided yet. see JENKINS-3997
            env.put("WORKSPACE", ws.getRemote());
            FilePath tempDir = WorkspaceList.tempDir(ws);
            if (tempDir != null) {
                env.put("WORKSPACE_TMP", tempDir.getRemote()); // JENKINS-60634
            }
        }

        project.getScm().buildEnvVars(this, env);

        if (buildEnvironments != null)
            for (Environment e : buildEnvironments)
                e.buildEnvVars(env);

        for (EnvironmentContributingAction a : getActions(EnvironmentContributingAction.class))
            a.buildEnvVars(this, env);

        EnvVars.resolve(env);

        return env;
    }

    /**
     * During the build, expose the environments contributed by {@link BuildWrapper}s and others.
     *
     * <p>
     * Since 1.444, executor thread that's doing the build can access mutable underlying list,
     * which allows the caller to add/remove environments. The recommended way of adding
     * environment is through {@link BuildWrapper}, but this might be handy for build steps
     * who wants to expose additional environment variables to the rest of the build.
     *
     * @return can be empty list, but never null. Immutable.
     * @since 1.437
     */
    public EnvironmentList getEnvironments() {
        Executor e = Executor.currentExecutor();
        if (e != null && e.getCurrentExecutable() == this) {
            if (buildEnvironments == null)    buildEnvironments = new ArrayList<>();
            return new EnvironmentList(buildEnvironments);
        }

        return new EnvironmentList(buildEnvironments == null ? Collections.emptyList() : List.copyOf(buildEnvironments));
    }

    public Calendar due() {
        return getTimestamp();
    }

    /**
     * {@inheritDoc}
     * The action may have a {@code summary.jelly} view containing a {@code <t:summary>} or other {@code <tr>}.
     */
    @Override public void addAction(@NonNull Action a) {
        super.addAction(a);
    }

    @SuppressWarnings("deprecation")
    public List<Action> getPersistentActions() {
        return super.getActions();
    }

    /**
     * Builds up a set of variable names that contain sensitive values that
     * should not be exposed. The expectation is that this set is populated with
     * keys returned by {@link #getBuildVariables()} that should have their
     * values masked for display purposes.
     *
     * @since 1.378
     */
    public Set<String> getSensitiveBuildVariables() {
        Set<String> s = new HashSet<>();

        ParametersAction parameters = getAction(ParametersAction.class);
        if (parameters != null) {
            for (ParameterValue p : parameters) {
                if (p.isSensitive()) {
                    s.add(p.getName());
                }
            }
        }

        // Allow BuildWrappers to determine if any of their data is sensitive
        if (project instanceof BuildableItemWithBuildWrappers) {
            for (BuildWrapper bw : ((BuildableItemWithBuildWrappers) project).getBuildWrappersList()) {
                bw.makeSensitiveBuildVariables(this, s);
            }
        }

        return s;
    }

    /**
     * Provides additional variables and their values to {@link Builder}s.
     *
     * <p>
     * This mechanism is used by {@code MatrixConfiguration} to pass
     * the configuration values to the current build. It is up to
     * {@link Builder}s to decide whether they want to recognize the values
     * or how to use them.
     *
     * <p>
     * This also includes build parameters if a build is parameterized.
     *
     * @return
     *      The returned map is mutable so that subtypes can put more values.
     */
    public Map<String, String> getBuildVariables() {
        Map<String, String> r = new HashMap<>();

        ParametersAction parameters = getAction(ParametersAction.class);
        if (parameters != null) {
            // this is a rather round about way of doing this...
            for (ParameterValue p : parameters) {
                String v = p.createVariableResolver(this).resolve(p.getName());
                if (v != null) r.put(p.getName(), v);
            }
        }

        // allow the BuildWrappers to contribute additional build variables
        if (project instanceof BuildableItemWithBuildWrappers) {
            for (BuildWrapper bw : ((BuildableItemWithBuildWrappers) project).getBuildWrappersList())
                bw.makeBuildVariables(this, r);
        }

        for (BuildVariableContributor bvc : BuildVariableContributor.all())
            bvc.buildVariablesFor(this, r);

        return r;
    }

    /**
     * Creates {@link VariableResolver} backed by {@link #getBuildVariables()}.
     */
    public final VariableResolver<String> getBuildVariableResolver() {
        return new VariableResolver.ByMap<>(getBuildVariables());
    }

    /**
     * @deprecated Use {@link #getAction(Class)} on {@code AbstractTestResultAction}.
     */
    @Deprecated
    public Action getTestResultAction() {
        try {
            return getAction(Jenkins.get().getPluginManager().uberClassLoader.loadClass("hudson.tasks.test.AbstractTestResultAction").asSubclass(Action.class));
        } catch (ClassNotFoundException x) {
            return null;
        }
    }

    /**
     * @deprecated Use {@link #getAction(Class)} on {@code AggregatedTestResultAction}.
     */
    @Deprecated
    public Action getAggregatedTestResultAction() {
        try {
            return getAction(Jenkins.get().getPluginManager().uberClassLoader.loadClass("hudson.tasks.test.AggregatedTestResultAction").asSubclass(Action.class));
        } catch (ClassNotFoundException x) {
            return null;
        }
    }

    /**
     * Invoked by {@link Executor} to performs a build.
     */
    @Override
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
        for (AbstractProject<?, ?> p : getParent().getDownstreamProjects()) {
            if (!p.isKeepDependencies()) continue;

            AbstractBuild<?, ?> fb = p.getFirstBuild();
            if (fb == null)        continue; // no active record

            // is there any active build that depends on us?
            for (int i : getDownstreamRelationship(p).listNumbersReverse()) {
                // TODO: this is essentially a "find intersection between two sparse sequences"
                // and we should be able to do much better.

                if (i < fb.getNumber())
                    continue OUTER; // all the other records are younger than the first record, so pointless to search.

                AbstractBuild<?, ?> b = p.getBuildByNumber(i);
                if (b != null)
                    return Messages.AbstractBuild_KeptBecause(p.hasPermission(Item.READ) ? b.toString() : "?");
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
     *      The range will be empty if no build of that project matches this (or there is no {@link FingerprintAction}), but it'll never be null.
     */
    public RangeSet getDownstreamRelationship(AbstractProject that) {
        RangeSet rs = new RangeSet();

        FingerprintAction f = getAction(FingerprintAction.class);
        if (f == null)     return rs;

        // look for fingerprints that point to this build as the source, and merge them all
        for (Fingerprint e : f.getFingerprints().values()) {

            if (upstreamCulprits) {
                // With upstreamCulprits, we allow downstream relationships
                // from intermediate jobs
                rs.add(e.getRangeSet(that));
            } else {
                BuildPtr o = e.getOriginal();
                if (o != null && o.is(this))
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
    public Iterable<AbstractBuild<?, ?>> getDownstreamBuilds(final AbstractProject<?, ?> that) {
        final Iterable<Integer> nums = getDownstreamRelationship(that).listNumbers();

        return new Iterable<>() {
            @Override
            public Iterator<AbstractBuild<?, ?>> iterator() {
                return Iterators.removeNull(
                    new AdaptedIterator<>(nums) {
                        @Override
                        protected AbstractBuild<?, ?> adapt(Integer item) {
                            return that.getBuildByNumber(item);
                        }
                    });
            }
        };
    }

    /**
     * Gets the dependency relationship from this build (as the sink)
     * and that project (as the source.)
     *
     * @return
     *      Build number of the upstream build that feed into this build,
     *      or -1 if no record is available (for example if there is no {@link FingerprintAction}, even if there is an {@link Cause.UpstreamCause}).
     */
    public int getUpstreamRelationship(AbstractProject that) {
        FingerprintAction f = getAction(FingerprintAction.class);
        if (f == null)     return -1;

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
                if (o != null && o.belongsTo(that))
                    n = Math.max(n, o.getNumber());
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
    public AbstractBuild<?, ?> getUpstreamRelationshipBuild(AbstractProject<?, ?> that) {
        int n = getUpstreamRelationship(that);
        if (n == -1)   return null;
        return that.getBuildByNumber(n);
    }

    /**
     * Gets the downstream builds of this build, which are the builds of the
     * downstream projects that use artifacts of this build.
     *
     * @return
     *      For each project with fingerprinting enabled, returns the range
     *      of builds (which can be empty if no build uses the artifact from this build or downstream is not {@link AbstractProject#isFingerprintConfigured}.)
     */
    public Map<AbstractProject, RangeSet> getDownstreamBuilds() {
        Map<AbstractProject, RangeSet> r = new HashMap<>();
        for (AbstractProject p : getParent().getDownstreamProjects()) {
            if (p.isFingerprintConfigured())
                r.put(p, getDownstreamRelationship(p));
        }
        return r;
    }

    /**
     * Gets the upstream builds of this build, which are the builds of the
     * upstream projects whose artifacts feed into this build.
     * @return empty if there is no {@link FingerprintAction} (even if there is an {@link Cause.UpstreamCause})
     * @see #getTransitiveUpstreamBuilds()
     */
    public Map<AbstractProject, Integer> getUpstreamBuilds() {
        return _getUpstreamBuilds(getParent().getUpstreamProjects());
    }

    /**
     * Works like {@link #getUpstreamBuilds()}  but also includes all the transitive
     * dependencies as well.
     */
    public Map<AbstractProject, Integer> getTransitiveUpstreamBuilds() {
        return _getUpstreamBuilds(getParent().getTransitiveUpstreamProjects());
    }

    private Map<AbstractProject, Integer> _getUpstreamBuilds(Collection<AbstractProject> projects) {
        Map<AbstractProject, Integer> r = new HashMap<>();
        for (AbstractProject p : projects) {
            int n = getUpstreamRelationship(p);
            if (n >= 0)
                r.put(p, n);
        }
        return r;
    }

    /**
     * Gets the changes in the dependency between the given build and this build.
     * @return empty if there is no {@link FingerprintAction}
     */
    public Map<AbstractProject, DependencyChange> getDependencyChanges(AbstractBuild from) {
        if (from == null)             return Collections.emptyMap(); // make it easy to call this from views
        FingerprintAction n = this.getAction(FingerprintAction.class);
        FingerprintAction o = from.getAction(FingerprintAction.class);
        if (n == null || o == null)     return Collections.emptyMap();

        Map<AbstractProject, Integer> ndep = n.getDependencies(true);
        Map<AbstractProject, Integer> odep = o.getDependencies(true);

        Map<AbstractProject, DependencyChange> r = new HashMap<>();

        for (Map.Entry<AbstractProject, Integer> entry : odep.entrySet()) {
            AbstractProject p = entry.getKey();
            Integer oldNumber = entry.getValue();
            Integer newNumber = ndep.get(p);
            if (newNumber != null && oldNumber.compareTo(newNumber) < 0) {
                r.put(p, new DependencyChange(p, oldNumber, newNumber));
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

        public DependencyChange(AbstractProject<?, ?> project, int fromId, int toId) {
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
            List<AbstractBuild> r = new ArrayList<>();

            AbstractBuild<?, ?> b = project.getNearestBuild(fromId);
            if (b != null && b.getNumber() == fromId)
                b = b.getNextBuild(); // fromId exclusive

            while (b != null && b.getNumber() <= toId) {
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
     * @deprecated as of 1.489
     *      Use {@link #doStop()}
     */
    @Deprecated
    @RequirePOST // #doStop() should be preferred, but better to be safe
    public void doStop(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        try {
            doStop().generateResponse(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp), this);
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    /**
     * Stops this build if it's still going.
     *
     * If we use this/executor/stop URL, it causes 404 if the build is already killed,
     * as {@link #getExecutor()} returns null.
     *
     * @since 1.489
     */
    @RequirePOST
    public synchronized HttpResponse doStop() throws IOException, ServletException {
        Executor e = getExecutor();
        if (e == null)
            e = getOneOffExecutor();
        if (e != null)
            return e.doStop();
        else
            // nothing is building
            return HttpResponses.forwardToPreviousPage();
    }

    private static final Logger LOGGER = Logger.getLogger(AbstractBuild.class.getName());
}
