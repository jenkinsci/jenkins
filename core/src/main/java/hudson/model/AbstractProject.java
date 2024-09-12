/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Brian Westrich, Erik Ramfelt, Ertan Deniz, Jean-Baptiste Quenot,
 * Luca Domenico Milanesio, R. Tyler Ballance, Stephen Connolly, Tom Huybrechts,
 * id:cactusman, Yahoo! Inc., Andrew Bayer, Manufacture Francaise des Pneumatiques
 * Michelin, Romain Seguy
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

import static hudson.scm.PollingResult.BUILD_NOW;
import static hudson.scm.PollingResult.NO_CHANGES;

import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.CopyOnWrite;
import hudson.EnvVars;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.Util;
import hudson.cli.declarative.CLIResolver;
import hudson.model.Descriptor.FormException;
import hudson.model.Fingerprint.RangeSet;
import hudson.model.Node.Mode;
import hudson.model.Queue.Executable;
import hudson.model.Queue.Task;
import hudson.model.labels.LabelAtom;
import hudson.model.labels.LabelExpression;
import hudson.model.listeners.SCMPollListener;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskFuture;
import hudson.model.queue.SubTask;
import hudson.model.queue.SubTaskContributor;
import hudson.scm.NullSCM;
import hudson.scm.PollingResult;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import hudson.scm.SCMS;
import hudson.search.SearchIndexBuilder;
import hudson.security.Permission;
import hudson.slaves.Cloud;
import hudson.slaves.WorkspaceList;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildTrigger;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tasks.Publisher;
import hudson.triggers.SCMTrigger;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.AlternativeUiTextProvider;
import hudson.util.AlternativeUiTextProvider.Message;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import io.jenkins.servlet.ServletExceptionWrapper;
import jakarta.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.BlockedBecauseOfBuildInProgress;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.model.Uptime;
import jenkins.model.lazy.LazyBuildMixIn;
import jenkins.scm.DefaultSCMCheckoutStrategyImpl;
import jenkins.scm.SCMCheckoutStrategy;
import jenkins.scm.SCMCheckoutStrategyDescriptor;
import jenkins.scm.SCMDecisionHandler;
import jenkins.triggers.SCMTriggerItem;
import jenkins.util.TimeDuration;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.ForwardToView;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.verb.POST;

/**
 * Base implementation of {@link Job}s that build software.
 *
 * For now this is primarily the common part of {@link Project} and MavenModule.
 *
 * @author Kohsuke Kawaguchi
 * @see AbstractBuild
 */
@SuppressWarnings("rawtypes")
public abstract class AbstractProject<P extends AbstractProject<P, R>, R extends AbstractBuild<P, R>> extends Job<P, R> implements BuildableItem, LazyBuildMixIn.LazyLoadingJob<P, R>, ParameterizedJobMixIn.ParameterizedJob<P, R> {

    /**
     * {@link SCM} associated with the project.
     * To allow derived classes to link {@link SCM} config to elsewhere,
     * access to this variable should always go through {@link #getScm()}.
     */
    private volatile SCM scm = new NullSCM();

    /**
     * Controls how the checkout is done.
     */
    private volatile SCMCheckoutStrategy scmCheckoutStrategy;

    /**
     * State returned from {@link SCM#poll(AbstractProject, Launcher, FilePath, TaskListener, SCMRevisionState)}.
     */
    private transient volatile SCMRevisionState pollingBaseline = null;

    private transient LazyBuildMixIn<P, R> buildMixIn;

    /**
     * All the builds keyed by their build number.
     * Kept here for binary compatibility only; otherwise use {@link #buildMixIn}.
     * External code should use {@link #getBuildByNumber(int)} or {@link #getLastBuild()} and traverse via
     * {@link Run#getPreviousBuild()}
     */
    @Restricted(NoExternalUse.class)
    protected transient RunMap<R> builds;

    /**
     * The quiet period. Null to delegate to the system default.
     */
    private volatile Integer quietPeriod = null;

    /**
     * The retry count. Null to delegate to the system default.
     */
    private volatile Integer scmCheckoutRetryCount = null;

    /**
     * If this project is configured to be only built on a certain label,
     * this value will be set to that label.
     *
     * For historical reasons, this is called 'assignedNode'. Also for
     * a historical reason, null to indicate the affinity
     * with the master node.
     *
     * @see #canRoam
     */
    private String assignedNode;

    /**
     * True if this project can be built on any node.
     *
     * <p>
     * This somewhat ugly flag combination is so that we can migrate
     * existing Hudson installations nicely.
     */
    private volatile boolean canRoam;

    /**
     * True to suspend new builds.
     */
    protected volatile boolean disabled;

    /**
     * True to keep builds of this project in queue when downstream projects are
     * building. False by default to keep from breaking existing behavior.
     */
    protected volatile boolean blockBuildWhenDownstreamBuilding = false;

    /**
     * True to keep builds of this project in queue when upstream projects are
     * building. False by default to keep from breaking existing behavior.
     */
    protected volatile boolean blockBuildWhenUpstreamBuilding = false;

    /**
     * Identifies {@link JDK} to be used.
     * Null if no explicit configuration is required.
     *
     * <p>
     * Can't store {@link JDK} directly because {@link Jenkins} and {@link Project}
     * are saved independently.
     *
     * @see Jenkins#getJDK(String)
     */
    private volatile String jdk;

    private volatile BuildAuthorizationToken authToken = null;

    /**
     * List of all {@link Trigger}s for this project.
     */
    protected volatile DescribableList<Trigger<?>, TriggerDescriptor> triggers = new DescribableList<>(this);
    private static final AtomicReferenceFieldUpdater<AbstractProject, DescribableList> triggersUpdater
            = AtomicReferenceFieldUpdater.newUpdater(AbstractProject.class, DescribableList.class, "triggers");

    /**
     * {@link Action}s contributed from subsidiary objects associated with
     * {@link AbstractProject}, such as from triggers, builders, publishers, etc.
     *
     * We don't want to persist them separately, and these actions
     * come and go as configuration change, so it's kept separate.
     */
    @CopyOnWrite
    protected transient volatile List<Action> transientActions = new Vector<>();

    private boolean concurrentBuild;

    /**
     * See {@link #setCustomWorkspace(String)}.
     *
     * @since 1.410
     */
    private String customWorkspace;

    protected AbstractProject(ItemGroup parent, String name) {
        super(parent, name);
        buildMixIn = createBuildMixIn();
        builds = buildMixIn.getRunMap();

        Jenkins j = Jenkins.getInstanceOrNull();
        if (j != null && !j.getNodes().isEmpty()) {
            // if a new job is configured with Hudson that already has agent nodes
            // make it roamable by default
            canRoam = true;
        }
    }

    private LazyBuildMixIn<P, R> createBuildMixIn() {
        return new LazyBuildMixIn<>() {
            @SuppressWarnings("unchecked") // untypable
            @Override protected P asJob() {
                return (P) AbstractProject.this;
            }

            @Override protected Class<R> getBuildClass() {
                return AbstractProject.this.getBuildClass();
            }
        };
    }

    @Override public LazyBuildMixIn<P, R> getLazyBuildMixIn() {
        return buildMixIn;
    }

    @Override
    public synchronized void save() throws IOException {
        super.save();
        updateTransientActions();
    }

    @Override
    public void onCreatedFromScratch() {
        super.onCreatedFromScratch();
        buildMixIn.onCreatedFromScratch();
        builds = buildMixIn.getRunMap();
        // solicit initial contributions, especially from TransientProjectActionFactory
        updateTransientActions();
    }

    @Override
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);
        if (buildMixIn == null) {
            buildMixIn = createBuildMixIn();
        }
        buildMixIn.onLoad(parent, name);
        builds = buildMixIn.getRunMap();
        triggers().setOwner(this);
        for (Trigger t : triggers()) {
            try {
                t.start(this, Items.currentlyUpdatingByXml());
            } catch (Throwable e) {
                LOGGER.log(Level.WARNING, "could not start trigger while loading project '" + getFullName() + "'", e);
            }
        }
        if (scm == null)
            scm = new NullSCM(); // perhaps it was pointing to a plugin that no longer exists.

        if (transientActions == null)
            transientActions = new Vector<>();    // happens when loaded from disk
        updateTransientActions();
    }

    @WithBridgeMethods(List.class)
    protected DescribableList<Trigger<?>, TriggerDescriptor> triggers() {
        if (triggers == null) {
            triggersUpdater.compareAndSet(this, null, new DescribableList<Trigger<?>, TriggerDescriptor>(this));
        }
        return triggers;
    }

    @NonNull
    @Override
    public EnvVars getEnvironment(@CheckForNull Node node, @NonNull TaskListener listener) throws IOException, InterruptedException {
        EnvVars env =  super.getEnvironment(node, listener);

        JDK jdkTool = getJDK();
        if (jdkTool != null) {
            if (node != null) { // just in case were not in a build
                jdkTool = jdkTool.forNode(node, listener);
            }
            jdkTool.buildEnvVars(env);
        } else if (!JDK.isDefaultName(jdk)) {
            listener.getLogger().println("No JDK named ‘" + jdk + "’ found");
        }

        return env;
    }

    @Override
    protected void performDelete() throws IOException, InterruptedException {
        // prevent a new build while a delete operation is in progress
        if (supportsMakeDisabled()) {
            setDisabled(true);
            Jenkins.get().getQueue().cancel(this);
        }
        FilePath ws = getWorkspace();
        if (ws != null) {
            Node on = getLastBuiltOn();
            getScm().processWorkspaceBeforeDeletion(this, ws, on);
        }
        super.performDelete();
    }

    /**
     * Does this project perform concurrent builds?
     * @since 1.319
     */
    @Exported
    @Override
    public boolean isConcurrentBuild() {
        return concurrentBuild;
    }

    public void setConcurrentBuild(boolean b) throws IOException {
        concurrentBuild = b;
        save();
    }

    /**
     * If this project is configured to be always built on this node,
     * return that {@link Node}. Otherwise null.
     */
    @Override
    public @CheckForNull Label getAssignedLabel() {
        if (canRoam)
            return null;

        if (assignedNode == null)
            return Jenkins.get().getSelfLabel();
        return Jenkins.get().getLabel(assignedNode);
    }

    /**
     * Set of labels relevant to this job.
     *
     * This method is used to determine what agents are relevant to jobs, for example by {@link View}s.
     * It does not affect the scheduling. This information is informational and the best-effort basis.
     *
     * @since 1.456
     * @return
     *      Minimally it should contain {@link #getAssignedLabel()}. The set can contain null element
     *      to correspond to the null return value from {@link #getAssignedLabel()}.
     */
    public Set<Label> getRelevantLabels() {
        return Collections.singleton(getAssignedLabel());
    }

    /**
     * Gets the textual representation of the assigned label as it was entered by the user.
     */
    @Exported(name = "labelExpression")
    public String getAssignedLabelString() {
        if (canRoam || assignedNode == null)    return null;
        try {
            Label.parseExpression(assignedNode);
            return assignedNode;
        } catch (IllegalArgumentException e) {
            // must be old label or host name that includes whitespace or other unsafe chars
            return LabelAtom.escape(assignedNode);
        }
    }

    /**
     * Sets the assigned label.
     */
    public void setAssignedLabel(Label l) throws IOException {
        if (l == null) {
            canRoam = true;
            assignedNode = null;
        } else {
            canRoam = false;
            if (l == Jenkins.get().getSelfLabel())  assignedNode = null;
            else                                        assignedNode = l.getExpression();
        }
        save();
    }

    /**
     * Assigns this job to the given node. A convenience method over {@link #setAssignedLabel(Label)}.
     */
    public void setAssignedNode(Node l) throws IOException {
        setAssignedLabel(l.getSelfLabel());
    }

    /**
     * Get the term used in the UI to represent this kind of {@link AbstractProject}.
     * Must start with a capital letter.
     */
    @Override
    public String getPronoun() {
        return AlternativeUiTextProvider.get(PRONOUN, this, Messages.AbstractProject_Pronoun());
    }

    /**
     * Gets the human readable display name to be rendered in the "Build Now" link.
     *
     * @since 1.401
     */
    @Override
    public String getBuildNowText() {
        // For compatibility, still use the deprecated replacer if specified.
        return AlternativeUiTextProvider.get(BUILD_NOW_TEXT, this, ParameterizedJobMixIn.ParameterizedJob.super.getBuildNowText());
    }

    /**
     * Gets the nearest ancestor {@link TopLevelItem} that's also an {@link AbstractProject}.
     *
     * <p>
     * Some projects (such as matrix projects, Maven projects, or promotion processes) form a tree of jobs
     * that acts as a single unit. This method can be used to find the top most dominating job that
     * covers such a tree.
     *
     * @return never null.
     * @see AbstractBuild#getRootBuild()
     */
    public AbstractProject<?, ?> getRootProject() {
        if (this instanceof TopLevelItem) {
            return this;
        } else {
            ItemGroup p = this.getParent();
            if (p instanceof AbstractProject)
                return ((AbstractProject) p).getRootProject();
            return this;
        }
    }

    /**
     * Gets the directory where the module is checked out.
     *
     * @return
     *      null if the workspace is on an agent that's not connected.
     * @deprecated as of 1.319
     *      To support concurrent builds of the same project, this method is moved to {@link AbstractBuild}.
     *      For backward compatibility, this method returns the right {@link AbstractBuild#getWorkspace()} if called
     *      from {@link Executor}, and otherwise the workspace of the last build.
     *
     *      <p>
     *      If you are calling this method during a build from an executor, switch it to {@link AbstractBuild#getWorkspace()}.
     *      If you are calling this method to serve a file from the workspace, doing a form validation, etc., then
     *      use {@link #getSomeWorkspace()}
     */
    @Deprecated
    public final FilePath getWorkspace() {
        AbstractBuild b = getBuildForDeprecatedMethods();
        return b != null ? b.getWorkspace() : null;

    }

    /**
     * Various deprecated methods in this class all need the 'current' build.  This method returns
     * the build suitable for that purpose.
     *
     * @return An AbstractBuild for deprecated methods to use.
     */
    @CheckForNull
    private AbstractBuild getBuildForDeprecatedMethods() {
        Executor e = Executor.currentExecutor();
        if (e != null) {
            Executable exe = e.getCurrentExecutable();
            if (exe instanceof AbstractBuild b) {
                if (b.getProject() == this)
                    return b;
            }
        }
        return getLastBuild();
    }

    /**
     * Gets a workspace for some build of this project.
     *
     * <p>
     * This is useful for obtaining a workspace for the purpose of form field validation, where exactly
     * which build the workspace belonged is less important. The implementation makes a cursory effort
     * to find some workspace.
     *
     * @return
     *      null if there's no available workspace.
     * @since 1.319
     */
    public final @CheckForNull FilePath getSomeWorkspace() {
        R b = getSomeBuildWithWorkspace();
        if (b != null) return b.getWorkspace();
        for (WorkspaceBrowser browser : ExtensionList.lookup(WorkspaceBrowser.class)) {
            FilePath f = browser.getWorkspace(this);
            if (f != null) return f;
        }
        return null;
    }

    /**
     * Gets some build that has a live workspace.
     *
     * @return null if no such build exists.
     */
    public final R getSomeBuildWithWorkspace() {
        for (R b = getLastBuild(); b != null; b = b.getPreviousBuild()) {
            FilePath ws = b.getWorkspace();
            if (ws != null)   return b;
        }
        return null;
    }

    private R getSomeBuildWithExistingWorkspace() throws IOException, InterruptedException {
        for (R b = getLastBuild(); b != null; b = b.getPreviousBuild()) {
            FilePath ws = b.getWorkspace();
            if (ws != null && ws.exists())   return b;
        }
        return null;
    }

    /**
     * Returns the root directory of the checked-out module.
     * <p>
     * This is usually where {@code pom.xml}, {@code build.xml}
     * and so on exists.
     *
     * @deprecated as of 1.319
     *      See {@link #getWorkspace()} for a migration strategy.
     */
    @Deprecated
    public FilePath getModuleRoot() {
        AbstractBuild b = getBuildForDeprecatedMethods();
        return b != null ? b.getModuleRoot() : null;
    }

    /**
     * Returns the root directories of all checked-out modules.
     * <p>
     * Some SCMs support checking out multiple modules into the same workspace.
     * In these cases, the returned array will have a length greater than one.
     * @return The roots of all modules checked out from the SCM.
     *
     * @deprecated as of 1.319
     *      See {@link #getWorkspace()} for a migration strategy.
     */
    @Deprecated
    public FilePath[] getModuleRoots() {
        AbstractBuild b = getBuildForDeprecatedMethods();
        return b != null ? b.getModuleRoots() : null;
    }

    @Override
    public int getQuietPeriod() {
        return quietPeriod != null ? quietPeriod : Jenkins.get().getQuietPeriod();
    }

    public SCMCheckoutStrategy getScmCheckoutStrategy() {
        return scmCheckoutStrategy == null ? new DefaultSCMCheckoutStrategyImpl() : scmCheckoutStrategy;
    }

    public void setScmCheckoutStrategy(SCMCheckoutStrategy scmCheckoutStrategy) throws IOException {
        this.scmCheckoutStrategy = scmCheckoutStrategy;
        save();
    }


    public int getScmCheckoutRetryCount() {
        return scmCheckoutRetryCount != null ? scmCheckoutRetryCount : Jenkins.get().getScmCheckoutRetryCount();
    }

    // ugly name because of EL
    public boolean getHasCustomQuietPeriod() {
        return quietPeriod != null;
    }

    /**
     * Sets the custom quiet period of this project, or revert to the global default if null is given.
     */
    public void setQuietPeriod(Integer seconds) throws IOException {
        this.quietPeriod = seconds;
        save();
    }

    public boolean hasCustomScmCheckoutRetryCount() {
        return scmCheckoutRetryCount != null;
    }

    @Override
    public boolean isBuildable() {
        return ParameterizedJobMixIn.ParameterizedJob.super.isBuildable();
    }

    /**
     * Used in {@code sidepanel.jelly} to decide whether to display
     * the config/delete/build links.
     */
    public boolean isConfigurable() {
        return true;
    }

    public boolean blockBuildWhenDownstreamBuilding() {
        return blockBuildWhenDownstreamBuilding;
    }

    public void setBlockBuildWhenDownstreamBuilding(boolean b) throws IOException {
        blockBuildWhenDownstreamBuilding = b;
        save();
    }

    public boolean blockBuildWhenUpstreamBuilding() {
        return blockBuildWhenUpstreamBuilding;
    }

    public void setBlockBuildWhenUpstreamBuilding(boolean b) throws IOException {
        blockBuildWhenUpstreamBuilding = b;
        save();
    }

    @Override
    @Exported
    public boolean isDisabled() {
        return disabled;
    }

    @Restricted(DoNotUse.class)
    @Override
    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    /**
     * Validates the retry count Regex
     */
    public FormValidation doCheckRetryCount(@QueryParameter String value)throws IOException, ServletException {
        // retry count is optional so this is ok
        if (value == null || value.trim().isEmpty())
            return FormValidation.ok();
        if (!value.matches("[0-9]*")) {
            return FormValidation.error("Invalid retry count");
        }
        return FormValidation.ok();
    }

    /**
     * {@inheritDoc}
     * By default, it can be only if this is a {@link TopLevelItem};
     * would be false for matrix configurations, etc.
     * @since 1.475
     */
    @Override
    public boolean supportsMakeDisabled() {
        return this instanceof TopLevelItem;
    }

    // Seems to be used only by tests; do not bother pulling up.
    public void disable() throws IOException {
        makeDisabled(true);
    }

    // Ditto.
    public void enable() throws IOException {
        makeDisabled(false);
    }

    @Override
    public BallColor getIconColor() {
        if (isDisabled())
            return isBuilding() ? BallColor.DISABLED_ANIME : BallColor.DISABLED;
        else
            return super.getIconColor();
    }

    /**
     * effectively deprecated. Since using updateTransientActions correctly
     * under concurrent environment requires a lock that can too easily cause deadlocks.
     *
     * <p>
     * Override {@link #createTransientActions()} instead.
     */
    protected void updateTransientActions() {
        transientActions = createTransientActions();
    }

    protected List<Action> createTransientActions() {
        Vector<Action> ta = new Vector<>();

        for (JobProperty<? super P> p : Util.fixNull(properties))
            ta.addAll(p.getJobActions((P) this));

        for (TransientProjectActionFactory tpaf : TransientProjectActionFactory.all()) {
            try {
                ta.addAll(Util.fixNull(tpaf.createFor(this))); // be defensive against null
            } catch (RuntimeException e) {
                LOGGER.log(Level.SEVERE, "Could not load actions from " + tpaf + " for " + this, e);
            }
        }
        return ta;
    }

    /**
     * Returns the live list of all {@link Publisher}s configured for this project.
     *
     * <p>
     * This method couldn't be called {@code getPublishers()} because existing methods
     * in sub-classes return different inconsistent types.
     */
    public abstract DescribableList<Publisher, Descriptor<Publisher>> getPublishersList();

    @Override
    public void addProperty(JobProperty<? super P> jobProp) throws IOException {
        super.addProperty(jobProp);
        updateTransientActions();
    }

    public List<ProminentProjectAction> getProminentActions() {
        return getActions(ProminentProjectAction.class);
    }

    @Override
    @POST
    public void doConfigSubmit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, FormException {
        super.doConfigSubmit(req, rsp);

        updateTransientActions();

        // notify the queue as the project might be now tied to different node
        Jenkins.get().getQueue().scheduleMaintenance();

        // this is to reflect the upstream build adjustments done above
        Jenkins.get().rebuildDependencyGraphAsync();
    }

    /**
     * Schedules a build.
     *
     * Important: the actions should be persistable without outside references (e.g. don't store
     * references to this project). To provide parameters for a parameterized project, add a ParametersAction. If
     * no ParametersAction is provided for such a project, one will be created with the default parameter values.
     *
     * @param quietPeriod the quiet period to observer
     * @param c the cause for this build which should be recorded
     * @param actions a list of Actions that will be added to the build
     * @return whether the build was actually scheduled
     */
    public boolean scheduleBuild(int quietPeriod, Cause c, Action... actions) {
        return scheduleBuild2(quietPeriod, c, actions) != null;
    }

    /**
     * Schedules a build of this project, and returns a {@link Future} object
     * to wait for the completion of the build.
     *
     * @param actions
     *      For the convenience of the caller, this array can contain null, and those will be silently ignored.
     */
    @WithBridgeMethods(Future.class)
    public QueueTaskFuture<R> scheduleBuild2(int quietPeriod, Cause c, Action... actions) {
        return scheduleBuild2(quietPeriod, c, Arrays.asList(actions));
    }

    /**
     * Schedules a build of this project, and returns a {@link Future} object
     * to wait for the completion of the build.
     *
     * @param actions
     *      For the convenience of the caller, this collection can contain null, and those will be silently ignored.
     * @since 1.383
     */
    @WithBridgeMethods(Future.class)
    public QueueTaskFuture<R> scheduleBuild2(int quietPeriod, Cause c, Collection<? extends Action> actions) {
        List<Action> queueActions = new ArrayList<>(actions);
        if (c != null) {
            queueActions.add(new CauseAction(c));
        }
        return scheduleBuild2(quietPeriod, queueActions.toArray(new Action[0]));
    }

    /**
     * Schedules a build, and returns a {@link Future} object
     * to wait for the completion of the build.
     *
     * <p>
     * Production code shouldn't be using this, but for tests this is very convenient, so this isn't marked
     * as deprecated.
     */
    @SuppressWarnings("deprecation")
    @WithBridgeMethods(Future.class)
    public QueueTaskFuture<R> scheduleBuild2(int quietPeriod) {
        return scheduleBuild2(quietPeriod, new Cause.LegacyCodeCause());
    }

    /**
     * Schedules a build of this project, and returns a {@link Future} object
     * to wait for the completion of the build.
     */
    @WithBridgeMethods(Future.class)
    public QueueTaskFuture<R> scheduleBuild2(int quietPeriod, Cause c) {
        return scheduleBuild2(quietPeriod, c, new Action[0]);
    }

    @Override
    public QueueTaskFuture<R> scheduleBuild2(int quietPeriod, Action... actions) {
        return ParameterizedJobMixIn.ParameterizedJob.super.scheduleBuild2(quietPeriod, actions);
    }

    /**
     * Schedules a polling of this project.
     * @see SCMTriggerItem#schedulePolling
     */
    public boolean schedulePolling() {
        if (isDisabled())    return false;
        SCMTrigger scmt = getTrigger(SCMTrigger.class);
        if (scmt == null)      return false;
        scmt.run();
        return true;
    }

    /**
     * Returns true if the build is in the queue.
     */
    @Override
    public boolean isInQueue() {
        return Jenkins.get().getQueue().contains(this);
    }

    @Override
    public Queue.Item getQueueItem() {
        return Jenkins.get().getQueue().getItem(this);
    }

    /**
     * Gets the JDK that this project is configured with, or null.
     */
    public JDK getJDK() {
        return Jenkins.get().getJDK(jdk);
    }

    /**
     * Overwrites the JDK setting.
     */
    public void setJDK(JDK jdk) throws IOException {
        this.jdk = jdk.getName();
        save();
    }

    @Override
    public BuildAuthorizationToken getAuthToken() {
        return authToken;
    }

    @Override
    public RunMap<R> _getRuns() {
        return buildMixIn._getRuns();
    }

    @Override
    public void removeRun(R run) {
        buildMixIn.removeRun(run);
    }

    /**
     * {@inheritDoc}
     *
     * More efficient implementation.
     */
    @Override
    public R getBuild(String id) {
        return buildMixIn.getBuild(id);
    }

    /**
     * {@inheritDoc}
     *
     * More efficient implementation.
     */
    @Override
    public R getBuildByNumber(int n) {
        return buildMixIn.getBuildByNumber(n);
    }

    /**
     * {@inheritDoc}
     *
     * More efficient implementation.
     */
    @Override
    public R getFirstBuild() {
        return buildMixIn.getFirstBuild();
    }

    @Override
    public @CheckForNull R getLastBuild() {
        return buildMixIn.getLastBuild();
    }

    @Override
    public R getNearestBuild(int n) {
        return buildMixIn.getNearestBuild(n);
    }

    @Override
    public R getNearestOldBuild(int n) {
        return buildMixIn.getNearestOldBuild(n);
    }

    @Override
    protected List<R> getEstimatedDurationCandidates() {
        return buildMixIn.getEstimatedDurationCandidates();
    }

    /**
     * Type token for the corresponding build type.
     * The build class must have two constructors:
     * one taking this project type;
     * and one taking this project type, then {@link File}.
     */
    protected abstract Class<R> getBuildClass();

    /**
     * Creates a new build of this project for immediate execution.
     */
    protected synchronized R newBuild() throws IOException {
        return buildMixIn.newBuild();
    }

    /**
     * Loads an existing build record from disk.
     */
    protected R loadBuild(File dir) throws IOException {
        return buildMixIn.loadBuild(dir);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Note that this method returns a read-only view of {@link Action}s.
     * {@link BuildStep}s and others who want to add a project action
     * should do so by implementing {@link BuildStep#getProjectActions(AbstractProject)}.
     *
     * @see TransientProjectActionFactory
     */
    @SuppressWarnings("deprecation")
    @NonNull
    @Override
    public List<Action> getActions() {
        // add all the transient actions, too
        List<Action> actions = new Vector<>(super.getActions());
        actions.addAll(transientActions);
        // return the read only list to cause a failure on plugins who try to add an action here
        return Collections.unmodifiableList(actions);
    }

    // TODO implement addAction, addOrReplaceAction, removeAction, removeActions, replaceActions

    /**
     * Gets the {@link Node} where this project was last built on.
     *
     * @return
     *      null if no information is available (for example,
     *      if no build was done yet.)
     */
    @SuppressWarnings("deprecation")
    @Override
    public Node getLastBuiltOn() {
        // where was it built on?
        AbstractBuild b = getLastBuild();
        if (b == null)
            return null;
        else
            return b.getBuiltOn();
    }

    @Override
    public Object getSameNodeConstraint() {
        return this; // in this way, any member that wants to run with the main guy can nominate the project itself
    }

    /**
     * @deprecated use {@link BlockedBecauseOfBuildInProgress} instead.
     */
    @Deprecated
    public static class BecauseOfBuildInProgress extends BlockedBecauseOfBuildInProgress {
        public BecauseOfBuildInProgress(@NonNull AbstractBuild<?, ?> build) {
            super(build);
        }
    }

    /**
     * Because the downstream build is in progress, and we are configured to wait for that.
     */
    public static class BecauseOfDownstreamBuildInProgress extends CauseOfBlockage {
        public final AbstractProject<?, ?> up;

        public BecauseOfDownstreamBuildInProgress(AbstractProject<?, ?> up) {
            this.up = up;
        }

        @Override
        public String getShortDescription() {
            return Messages.AbstractProject_DownstreamBuildInProgress(up.getName());
        }
    }

    /**
     * Because the upstream build is in progress, and we are configured to wait for that.
     */
    public static class BecauseOfUpstreamBuildInProgress extends CauseOfBlockage {
        public final AbstractProject<?, ?> up;

        public BecauseOfUpstreamBuildInProgress(AbstractProject<?, ?> up) {
            this.up = up;
        }

        @Override
        public String getShortDescription() {
            return Messages.AbstractProject_UpstreamBuildInProgress(up.getName());
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * A project must be blocked if its own previous build is in progress,
     * or if the blockBuildWhenUpstreamBuilding option is true and an upstream
     * project is building, but derived classes can also check other conditions.
     */
    @Override
    public CauseOfBlockage getCauseOfBlockage() {
        // Block builds until they are done with post-production
        if (!isConcurrentBuild() && isLogUpdated()) {
            final R lastBuild = getLastBuild();
            if (lastBuild != null) {
                return new BlockedBecauseOfBuildInProgress(lastBuild);
            } else {
                // The build has been likely deleted after the isLogUpdated() call.
                // Another cause may be an API implementation glitch in the implementation for AbstractProject.
                // Anyway, we should let the code go then.
                LOGGER.log(Level.FINE, "The last build has been deleted during the non-concurrent cause creation. The build is not blocked anymore");
            }
        }
        if (blockBuildWhenDownstreamBuilding()) {
            AbstractProject<?, ?> bup = getBuildingDownstream();
            if (bup != null)
                return new BecauseOfDownstreamBuildInProgress(bup);
        }
        if (blockBuildWhenUpstreamBuilding()) {
            AbstractProject<?, ?> bup = getBuildingUpstream();
            if (bup != null)
                return new BecauseOfUpstreamBuildInProgress(bup);
        }
        return null;
    }

    /**
     * Returns the project if any of the downstream project is either
     * building, or queued and not blocked by an upstream/downstream project build.
     * <p>
     * This means eventually there will be an automatic triggering of
     * the given project (provided that all builds went smoothly.)
     */
    public AbstractProject getBuildingDownstream() {
        // Unblocked downstream tasks must block this project.
        Set<Task> tasks = Jenkins.get().getQueue().getUnblockedTasks();
        // Blocked downstream tasks must block this project.
        // Projects blocked by upstream or downstream builds
        // are ignored to break deadlocks.
        for (Queue.BlockedItem item : Jenkins.get().getQueue().getBlockedItems()) {
            if (item.isCauseOfBlockageNull() ||
                    item.getCauseOfBlockage() instanceof AbstractProject.BecauseOfUpstreamBuildInProgress ||
                    item.getCauseOfBlockage() instanceof AbstractProject.BecauseOfDownstreamBuildInProgress) {
                continue;
            }
            tasks.add(item.task);
        }

        for (AbstractProject tup : getTransitiveDownstreamProjects()) {
            if (tup != this && (tup.isBuilding() || tasks.contains(tup)))
                return tup;
        }
        return null;
    }

    /**
     * Returns the project if any of the upstream project is either
     * building, or queued and not blocked by an upstream/downstream project build.
     * <p>
     * This means eventually there will be an automatic triggering of
     * the given project (provided that all builds went smoothly.)
     */
    public AbstractProject getBuildingUpstream() {
        // Unblocked upstream tasks must block this project.
        Set<Task> tasks = Jenkins.get().getQueue().getUnblockedTasks();
        // Blocked upstream tasks must block this project.
        // Projects blocked by upstream or downstream builds
        // are ignored to break deadlocks.
        for (Queue.BlockedItem item : Jenkins.get().getQueue().getBlockedItems()) {
            if (item.isCauseOfBlockageNull() ||
                    item.getCauseOfBlockage() instanceof AbstractProject.BecauseOfUpstreamBuildInProgress ||
                    item.getCauseOfBlockage() instanceof AbstractProject.BecauseOfDownstreamBuildInProgress) {
                continue;
            }
            tasks.add(item.task);
        }

        for (AbstractProject tup : getTransitiveUpstreamProjects()) {
            if (tup != this && (tup.isBuilding() || tasks.contains(tup)))
                return tup;
        }
        return null;
    }

    @Override
    public List<SubTask> getSubTasks() {
        List<SubTask> r = new ArrayList<>();
        r.add(this);
        for (SubTaskContributor euc : SubTaskContributor.all())
            r.addAll(euc.forProject(this));
        for (JobProperty<? super P> p : properties)
            r.addAll(p.getSubTasks());
        return r;
    }

    @Override // same as ParameterizedJob version except calls possibly overridden newBuild
    public @CheckForNull R createExecutable() throws IOException {
        if (isDisabled())    return null;
        return newBuild();
    }

    @Override
    public void checkAbortPermission() {
        checkPermission(CANCEL);
    }

    @Override
    public boolean hasAbortPermission() {
        return hasPermission(CANCEL);
    }

    /**
     * Gets the {@link Resource} that represents the workspace of this project.
     * Useful for locking and mutual exclusion control.
     *
     * @deprecated as of 1.319
     *      Projects no longer have a fixed workspace, ands builds will find an available workspace via
     *      {@link WorkspaceList} for each build (furthermore, that happens after a build is started.)
     *      So a {@link Resource} representation for a workspace at the project level no longer makes sense.
     *
     *      <p>
     *      If you need to lock a workspace while you do some computation, see the source code of
     *      {@link #pollSCMChanges(TaskListener)} for how to obtain a lock of a workspace through {@link WorkspaceList}.
     */
    @Deprecated
    public Resource getWorkspaceResource() {
        return new Resource(getFullDisplayName() + " workspace");
    }

    /**
     * List of necessary resources to perform the build of this project.
     */
    @Override
    public ResourceList getResourceList() {
        final Set<ResourceActivity> resourceActivities = getResourceActivities();
        final List<ResourceList> resourceLists = new ArrayList<>(1 + resourceActivities.size());
        for (ResourceActivity activity : resourceActivities) {
            if (activity != this && activity != null) {
                // defensive infinite recursion and null check
                resourceLists.add(activity.getResourceList());
            }
        }
        return ResourceList.union(resourceLists);
    }

    /**
     * Set of child resource activities of the build of this project (override in child projects).
     * @return The set of child resource activities of the build of this project.
     */
    protected Set<ResourceActivity> getResourceActivities() {
        return Collections.emptySet();
    }

    public boolean checkout(AbstractBuild build, Launcher launcher, BuildListener listener, File changelogFile) throws IOException, InterruptedException {
        SCM scm = getScm();
        if (scm == null)
            return true;    // no SCM

        FilePath workspace = build.getWorkspace();
        if (workspace != null) {
            workspace.mkdirs();
        } else {
            throw new AbortException("Cannot checkout SCM, workspace is not defined");
        }


        boolean r = scm.checkout(build, launcher, workspace, listener, changelogFile);
        if (r) {
            // Only calcRevisionsFromBuild if checkout was successful. Note that modern SCM implementations
            // won't reach this line anyway, as they throw AbortExceptions on checkout failure.
            calcPollingBaseline(build, launcher, listener);
        }
        return r;
    }

    /**
     * Pushes the baseline up to the newly checked out revision.
     */
    private void calcPollingBaseline(AbstractBuild build, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        SCMRevisionState baseline = build.getAction(SCMRevisionState.class);
        if (baseline == null) {
            try {
                baseline = getScm().calcRevisionsFromBuild(build, launcher, listener);
            } catch (AbstractMethodError e) {
                baseline = SCMRevisionState.NONE; // pre-1.345 SCM implementations, which doesn't use the baseline in polling
            }
            if (baseline != null)
                build.addAction(baseline);
        }
        pollingBaseline = baseline;
    }

    /**
     * Checks if there's any update in SCM, and returns true if any is found.
     *
     * @deprecated as of 1.346
     *      Use {@link #poll(TaskListener)} instead.
     */
    @Deprecated
    public boolean pollSCMChanges(TaskListener listener) {
        return poll(listener).hasChanges();
    }

    /**
     * Checks if there's any update in SCM, and returns true if any is found.
     *
     * <p>
     * The implementation is responsible for ensuring mutual exclusion between polling and builds
     * if necessary.
     *
     * @since 1.345
     */
    public PollingResult poll(TaskListener listener) {
        SCM scm = getScm();
        if (scm == null) {
            listener.getLogger().println(Messages.AbstractProject_NoSCM());
            return NO_CHANGES;
        }
        if (!isBuildable()) {
            listener.getLogger().println(Messages.AbstractProject_Disabled());
            return NO_CHANGES;
        }
        SCMDecisionHandler veto = SCMDecisionHandler.firstShouldPollVeto(this);
        if (veto != null) {
            listener.getLogger().println(Messages.AbstractProject_PollingVetoed(veto));
            return NO_CHANGES;
        }

        R lb = getLastBuild();
        if (lb == null) {
            listener.getLogger().println(Messages.AbstractProject_NoBuilds());
            return isInQueue() ? NO_CHANGES : BUILD_NOW;
        }

        if (pollingBaseline == null) {
            R success = getLastSuccessfulBuild(); // if we have a persisted baseline, we'll find it by this
            for (R r = lb; r != null; r = r.getPreviousBuild()) {
                SCMRevisionState s = r.getAction(SCMRevisionState.class);
                if (s != null) {
                    pollingBaseline = s;
                    break;
                }
                if (r == success) break;  // searched far enough
            }
            // NOTE-NO-BASELINE:
            // if we don't have baseline yet, it means the data is built by old Hudson that doesn't set the baseline
            // as action, so we need to compute it. This happens later.
        }

        try {
            SCMPollListener.fireBeforePolling(this, listener);
            PollingResult r = _poll(listener, scm);
            SCMPollListener.firePollingSuccess(this, listener, r);
            return r;
        } catch (AbortException e) {
            listener.getLogger().println(e.getMessage());
            listener.fatalError(Messages.AbstractProject_Aborted());
            LOGGER.log(Level.FINE, "Polling " + this + " aborted", e);
            SCMPollListener.firePollingFailed(this, listener, e);
            return NO_CHANGES;
        } catch (IOException e) {
            Functions.printStackTrace(e, listener.fatalError(e.getMessage()));
            SCMPollListener.firePollingFailed(this, listener, e);
            return NO_CHANGES;
        } catch (InterruptedException e) {
            Functions.printStackTrace(e, listener.fatalError(Messages.AbstractProject_PollingABorted()));
            SCMPollListener.firePollingFailed(this, listener, e);
            return NO_CHANGES;
        } catch (RuntimeException | Error e) {
            SCMPollListener.firePollingFailed(this, listener, e);
            throw e;
        }
    }

    /**
     * {@link #poll(TaskListener)} method without the try/catch block that does listener notification and .
     */
    private PollingResult _poll(TaskListener listener, SCM scm) throws IOException, InterruptedException {
        if (scm.requiresWorkspaceForPolling()) {
            R b = getSomeBuildWithExistingWorkspace();
            if (b == null) b = getLastBuild();
            // lock the workspace for the given build
            FilePath ws = b.getWorkspace();

            WorkspaceOfflineReason workspaceOfflineReason = workspaceOffline(b);
            if (workspaceOfflineReason != null) {
                // workspace offline
                for (WorkspaceBrowser browser : ExtensionList.lookup(WorkspaceBrowser.class)) {
                    ws = browser.getWorkspace(this);
                    if (ws != null) {
                        return pollWithWorkspace(listener, scm, b, ws, browser.getWorkspaceList());
                    }
                }

                // At this point we start thinking about triggering a build just to get a workspace,
                // because otherwise there's no way we can detect changes.
                // However, first there are some conditions in which we do not want to do so.
                // give time for agents to come online if we are right after reconnection (JENKINS-8408)
                long running = Jenkins.get().getInjector().getInstance(Uptime.class).getUptime();
                long remaining = TimeUnit.MINUTES.toMillis(10) - running;
                if (remaining > 0 && /* this logic breaks tests of polling */!Functions.getIsUnitTest()) {
                    listener.getLogger().print(Messages.AbstractProject_AwaitingWorkspaceToComeOnline(remaining / 1000));
                    listener.getLogger().println(" (" + workspaceOfflineReason.name() + ")");
                    return NO_CHANGES;
                }

                // Do not trigger build, if no suitable agent is online
                if (workspaceOfflineReason.equals(WorkspaceOfflineReason.all_suitable_nodes_are_offline)) {
                    // No suitable executor is online
                    listener.getLogger().print(Messages.AbstractProject_AwaitingWorkspaceToComeOnline(running / 1000));
                    listener.getLogger().println(" (" + workspaceOfflineReason.name() + ")");
                    return NO_CHANGES;
                }

                Label label = getAssignedLabel();
                if (label != null && label.isSelfLabel()) {
                    // if the build is fixed on a node, then attempting a build will do us
                    // no good. We should just wait for the agent to come back.
                    listener.getLogger().print(Messages.AbstractProject_NoWorkspace());
                    listener.getLogger().println(" (" + workspaceOfflineReason.name() + ")");
                    return NO_CHANGES;
                }

                listener.getLogger().println(ws == null
                    ? Messages.AbstractProject_WorkspaceOffline()
                    : Messages.AbstractProject_NoWorkspace());
                if (isInQueue()) {
                    listener.getLogger().println(Messages.AbstractProject_AwaitingBuildForWorkspace());
                    return NO_CHANGES;
                }

                // build now, or nothing will ever be built
                listener.getLogger().print(Messages.AbstractProject_NewBuildForWorkspace());
                listener.getLogger().println(" (" + workspaceOfflineReason.name() + ")");
                return BUILD_NOW;
            } else {
                WorkspaceList l = b.getBuiltOn().toComputer().getWorkspaceList();
                return pollWithWorkspace(listener, scm, b, ws, l);
            }

        } else {
            // polling without workspace
            LOGGER.fine("Polling SCM changes of " + getName());
            if (pollingBaseline == null) // see NOTE-NO-BASELINE above
                calcPollingBaseline(getLastBuild(), null, listener);
            PollingResult r = scm.poll(this, null, null, listener, pollingBaseline);
            pollingBaseline = r.remote;
            return r;
        }
    }

    private PollingResult pollWithWorkspace(TaskListener listener, SCM scm, R lb, @NonNull FilePath ws, WorkspaceList l) throws InterruptedException, IOException {
        // if doing non-concurrent build, acquire a workspace in a way that causes builds to block for this workspace.
        // this prevents multiple workspaces of the same job --- the behavior of Hudson < 1.319.
        //
        // OTOH, if a concurrent build is chosen, the user is willing to create a multiple workspace,
        // so better throughput is achieved over time (modulo the initial cost of creating that many workspaces)
        // by having multiple workspaces
        Node node = lb.getBuiltOn();
        Launcher launcher = ws.createLauncher(listener).decorateByEnv(getEnvironment(node, listener));
        WorkspaceList.Lease lease = l.acquire(ws, !concurrentBuild);
        try {
            String nodeName = node != null ? node.getSelfLabel().getName() : "[node_unavailable]";
            listener.getLogger().println("Polling SCM changes on " + nodeName);
            LOGGER.fine("Polling SCM changes of " + getName());
            if (pollingBaseline == null) // see NOTE-NO-BASELINE above
                calcPollingBaseline(lb, launcher, listener);
            PollingResult r = scm.poll(this, launcher, ws, listener, pollingBaseline);
            pollingBaseline = r.remote;
            return r;
        } finally {
            lease.release();
        }
    }

    enum WorkspaceOfflineReason {
        nonexisting_workspace,
        builton_node_gone,
        builton_node_no_executors,
        all_suitable_nodes_are_offline,
        use_ondemand_slave
    }

    /**
     * Returns true if all suitable nodes for the job are offline.
     *
     */
    private boolean isAllSuitableNodesOffline(R build) {
        Label label = getAssignedLabel();

        if (label != null) {
            //Invalid label. Put in queue to make administrator fix
            if (label.getNodes().isEmpty()) {
                return false;
            }
            //Returns true, if all suitable nodes are offline
            return label.isOffline();
        } else {
            if (canRoam) {
                for (Node n : Jenkins.get().getNodes()) {
                    Computer c = n.toComputer();
                    if (c != null && c.isOnline() && c.isAcceptingTasks() && n.getMode() == Mode.NORMAL) {
                        // Some executor is online that  is ready and this job can run anywhere
                        return false;
                    }
                }
                //We can roam, check that the master is set to be used as much as possible, and not tied jobs only.
                return Jenkins.get().getMode() == Mode.EXCLUSIVE;
            }
        }
        return true;
    }

    private WorkspaceOfflineReason workspaceOffline(R build) throws IOException, InterruptedException {
        FilePath ws = build.getWorkspace();
        Label label = getAssignedLabel();

        if (isAllSuitableNodesOffline(build)) {
            Collection<Cloud> applicableClouds = label == null ? Jenkins.get().clouds : label.getClouds();
            return applicableClouds.isEmpty() ? WorkspaceOfflineReason.all_suitable_nodes_are_offline : WorkspaceOfflineReason.use_ondemand_slave;
        }

        if (ws == null || !ws.exists()) {
            return WorkspaceOfflineReason.nonexisting_workspace;
        }

        Node builtOn = build.getBuiltOn();
        if (builtOn == null) { // node built-on doesn't exist anymore
            return WorkspaceOfflineReason.builton_node_gone;
        }

        if (builtOn.toComputer() == null) { // node still exists, but has 0 executors - o.s.l.t.
            return WorkspaceOfflineReason.builton_node_no_executors;
        }

        return null;
    }

    /**
     * Returns true if this user has made a commit to this project.
     *
     * @since 1.191
     */
    public boolean hasParticipant(User user) {
        for (R build = getLastBuild(); build != null; build = build.getPreviousBuild())
            if (build.hasParticipant(user))
                return true;
        return false;
    }

    @Exported
    public SCM getScm() {
        return scm;
    }

    public void setScm(SCM scm) throws IOException {
        this.scm = scm;
        save();
    }

    /**
     * Adds a new {@link Trigger} to this {@link Project} if not active yet.
     */
    public void addTrigger(Trigger<?> trigger) throws IOException {
        addToList(trigger, triggers());
    }

    public void removeTrigger(TriggerDescriptor trigger) throws IOException {
        removeFromList(trigger, triggers());
    }

    protected final synchronized <T extends Describable<T>>
    void addToList(T item, List<T> collection) throws IOException {
        //No support to replace item in position, remove then add
        removeFromList(item.getDescriptor(), collection);
        collection.add(item);
        save();
        updateTransientActions();
    }

    protected final synchronized <T extends Describable<T>>
    void removeFromList(Descriptor<T> item, List<T> collection) throws IOException {
        final Iterator<T> iCollection = collection.iterator();
        while (iCollection.hasNext()) {
            final T next = iCollection.next();
            if (next.getDescriptor() == item) {
                // found it
                iCollection.remove();
                save();
                updateTransientActions();
                return;
            }
        }
    }

    @Override public Map<TriggerDescriptor, Trigger<?>> getTriggers() {
        return triggers().toMap();
    }

    /**
     * Gets the specific trigger, or null if the property is not configured for this job.
     */
    public <T extends Trigger> T getTrigger(Class<T> clazz) {
        for (Trigger p : triggers()) {
            if (clazz.isInstance(p))
                return clazz.cast(p);
        }
        return null;
    }

//
//
// fingerprint related
//
//
    /**
     * True if the builds of this project produces {@link Fingerprint} records.
     */
    public abstract boolean isFingerprintConfigured();

    /**
     * Gets the other {@link AbstractProject}s that should be built
     * when a build of this project is completed.
     */
    public final List<AbstractProject> getDownstreamProjects() {
        return Jenkins.get().getDependencyGraph().getDownstream(this);
    }

    @Exported(name = "downstreamProjects")
    @Restricted(DoNotUse.class) // only for exporting
    public List<AbstractProject> getDownstreamProjectsForApi() {
        List<AbstractProject> r = new ArrayList<>();
        for (AbstractProject p : getDownstreamProjects()) {
            if (p.hasPermission(Item.READ)) {
                r.add(p);
            }
        }
        return r;
    }

    public final List<AbstractProject> getUpstreamProjects() {
        return Jenkins.get().getDependencyGraph().getUpstream(this);
    }

    @Exported(name = "upstreamProjects")
    @Restricted(DoNotUse.class) // only for exporting
    public List<AbstractProject> getUpstreamProjectsForApi() {
        List<AbstractProject> r = new ArrayList<>();
        for (AbstractProject p : getUpstreamProjects()) {
            if (p.hasPermission(Item.READ)) {
                r.add(p);
            }
        }
        return r;
    }

    /**
     * Returns only those upstream projects that defines {@link BuildTrigger} to this project.
     * This is a subset of {@link #getUpstreamProjects()}
     * <p>No longer used in the UI.
     * @return A List of upstream projects that has a {@link BuildTrigger} to this project.
     */
    public final List<AbstractProject> getBuildTriggerUpstreamProjects() {
        ArrayList<AbstractProject> result = new ArrayList<>();
        for (AbstractProject<?, ?> ap : getUpstreamProjects()) {
            BuildTrigger buildTrigger = ap.getPublishersList().get(BuildTrigger.class);
            if (buildTrigger != null)
                if (buildTrigger.getChildJobs(ap).contains(this))
                    result.add(ap);
        }
        return result;
    }

    /**
     * Gets all the upstream projects including transitive upstream projects.
     *
     * @since 1.138
     */
    public final Set<AbstractProject> getTransitiveUpstreamProjects() {
        return Jenkins.get().getDependencyGraph().getTransitiveUpstream(this);
    }

    /**
     * Gets all the downstream projects including transitive downstream projects.
     *
     * @since 1.138
     */
    public final Set<AbstractProject> getTransitiveDownstreamProjects() {
        return Jenkins.get().getDependencyGraph().getTransitiveDownstream(this);
    }

    /**
     * Gets the dependency relationship map between this project (as the source)
     * and that project (as the sink.)
     *
     * @return
     *      can be empty but not null. build number of this project to the build
     *      numbers of that project.
     */
    public SortedMap<Integer, RangeSet> getRelationship(AbstractProject that) {
        TreeMap<Integer, RangeSet> r = new TreeMap<>(REVERSE_INTEGER_COMPARATOR);

        checkAndRecord(that, r, this.getBuilds());
        // checkAndRecord(that, r, that.getBuilds());

        return r;
    }

    /**
     * Helper method for getDownstreamRelationship.
     *
     * For each given build, find the build number range of the given project and put that into the map.
     */
    private void checkAndRecord(AbstractProject that, TreeMap<Integer, RangeSet> r, Iterable<R> builds) {
        for (R build : builds) {
            RangeSet rs = build.getDownstreamRelationship(that);
            if (rs == null || rs.isEmpty())
                continue;

            int n = build.getNumber();

            RangeSet value = r.get(n);
            if (value == null)
                r.put(n, rs);
            else
                value.add(rs);
        }
    }

    /**
     * Builds the dependency graph.
     * Since 1.558, not abstract and by default includes dependencies contributed by {@link #triggers()}.
     */
    protected void buildDependencyGraph(DependencyGraph graph) {
        triggers().buildDependencyGraph(this, graph);
    }

    @Override
    protected SearchIndexBuilder makeSearchIndex() {
        return getParameterizedJobMixIn().extendSearchIndex(super.makeSearchIndex());
    }

//
//
// actions
//
//

    /** @deprecated use {@link #doBuild(StaplerRequest2, StaplerResponse2, TimeDuration)} */
    @Deprecated
    public void doBuild(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        try {
            doBuild(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp), TimeDuration.fromString(req.getParameter("delay")));
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    /**
     * Computes the delay by taking the default value and the override in the request parameter into the account.
     *
     * @deprecated as of 1.489
     *      Inject {@link TimeDuration}.
     */
    @Deprecated
    public int getDelay(StaplerRequest req) throws javax.servlet.ServletException {
        String delay = req.getParameter("delay");
        if (delay == null)    return getQuietPeriod();

        try {
            // TODO: more unit handling
            if (delay.endsWith("sec"))   delay = delay.substring(0, delay.length() - 3);
            if (delay.endsWith("secs"))  delay = delay.substring(0, delay.length() - 4);
            return Integer.parseInt(delay);
        } catch (NumberFormatException e) {
            throw new javax.servlet.ServletException("Invalid delay parameter value: " + delay, e);
        }
    }

    /** @deprecated use {@link #doBuildWithParameters(StaplerRequest2, StaplerResponse2, TimeDuration)} */
    @Deprecated
    public void doBuildWithParameters(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        try {
            doBuildWithParameters(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp), TimeDuration.fromString(req.getParameter("delay")));
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    @Override // in case schedulePolling was overridden
    public void doPolling(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        BuildAuthorizationToken.checkPermission((Job) this, authToken, req, rsp);
        schedulePolling();
        rsp.sendRedirect(".");
    }

    /**
     * @since TODO
     */
    @Override
    protected void submit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, FormException {
        if (Util.isOverridden(AbstractProject.class, getClass(), "submit", StaplerRequest.class, StaplerResponse.class)) {
            try {
                submit(StaplerRequest.fromStaplerRequest2(req), StaplerResponse.fromStaplerResponse2(rsp));
            } catch (javax.servlet.ServletException e) {
                throw ServletExceptionWrapper.toJakartaServletException(e);
            }
        } else {
            super.submit(req, rsp);
            submitImpl(req, rsp);
        }
    }

    /**
     * @deprecated use {@link #submit(StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    @Override
    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException, FormException {
        super.submit(req, rsp);
        try {
            submitImpl(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    private void submitImpl(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, FormException {
        JSONObject json = req.getSubmittedForm();

        makeDisabled(!json.optBoolean("enable"));

        jdk = json.optString("jdk", null);

        if (json.optBoolean("hasCustomQuietPeriod", json.has("quiet_period"))) {
            quietPeriod = json.optInt("quiet_period");
        } else {
            quietPeriod = null;
        }

        if (json.optBoolean("hasCustomScmCheckoutRetryCount", json.has("scmCheckoutRetryCount"))) {
            scmCheckoutRetryCount = json.optInt("scmCheckoutRetryCount");
        } else {
            scmCheckoutRetryCount = null;
        }

        blockBuildWhenDownstreamBuilding = json.optBoolean("blockBuildWhenDownstreamBuilding");
        blockBuildWhenUpstreamBuilding = json.optBoolean("blockBuildWhenUpstreamBuilding");

        if (req.hasParameter("customWorkspace.directory")) {
            // Workaround for JENKINS-25221 while plugins are being updated.
            LOGGER.log(Level.WARNING, "label assignment is using legacy 'customWorkspace.directory'");
            customWorkspace = Util.fixEmptyAndTrim(req.getParameter("customWorkspace.directory"));
        } else if (json.optBoolean("hasCustomWorkspace", json.has("customWorkspace"))) {
            customWorkspace = Util.fixEmptyAndTrim(json.optString("customWorkspace"));
        } else {
            customWorkspace = null;
        }

        if (json.has("scmCheckoutStrategy"))
            scmCheckoutStrategy = req.bindJSON(SCMCheckoutStrategy.class,
                json.getJSONObject("scmCheckoutStrategy"));
        else
            scmCheckoutStrategy = null;

        if (json.optBoolean("hasSlaveAffinity", json.has("label"))) {
            assignedNode = Util.fixEmptyAndTrim(json.optString("label"));
        } else if (req.hasParameter("_.assignedLabelString")) {
            // Workaround for JENKINS-25372 while plugin is being updated.
            // Keep this condition second for JENKINS-25533
            LOGGER.log(Level.WARNING, "label assignment is using legacy '_.assignedLabelString'");
            assignedNode = Util.fixEmptyAndTrim(req.getParameter("_.assignedLabelString"));
        } else  {
            assignedNode = null;
        }
        canRoam = assignedNode == null;

        keepDependencies = json.has("keepDependencies");

        concurrentBuild = json.optBoolean("concurrentBuild");

        authToken = BuildAuthorizationToken.create(req);

        setScm(SCMS.parseSCM(req, this));

        for (Trigger t : triggers())
            t.stop();
        triggers.rebuild(req, json.getJSONObject("triggers"), Trigger.for_(this));
        for (Trigger t : triggers())
            t.start(this, true);
    }

    /**
     * @deprecated
     *      As of 1.261. Use {@link #buildDescribable(StaplerRequest2, List)} instead.
     */
    @Deprecated
    protected final <T extends Describable<T>> List<T> buildDescribable(StaplerRequest req, List<? extends Descriptor<T>> descriptors, String prefix) throws FormException, javax.servlet.ServletException {
        try {
            return buildDescribable(StaplerRequest.toStaplerRequest2(req), descriptors);
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    protected final <T extends Describable<T>> List<T> buildDescribable(StaplerRequest2 req, List<? extends Descriptor<T>> descriptors)
        throws FormException, ServletException {

        JSONObject data = req.getSubmittedForm();
        List<T> r = new Vector<>();
        for (Descriptor<T> d : descriptors) {
            String safeName = d.getJsonSafeClassName();
            if (req.getParameter(safeName) != null) {
                T instance = d.newInstance(req, data.getJSONObject(safeName));
                r.add(instance);
            }
        }
        return r;
    }

    /**
     * Serves the workspace files.
     */
    public DirectoryBrowserSupport doWs(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, InterruptedException {
        checkPermission(Item.WORKSPACE);
        FilePath ws = getSomeWorkspace();
        if (ws == null || !ws.exists()) {
            // if there's no workspace, report a nice error message
            // Would be good if when asked for *plain*, do something else!
            // (E.g. return 404, or send empty doc.)
            // Not critical; client can just check if content type is not text/plain,
            // which also serves to detect old versions of Hudson.
            req.getView(this, "noWorkspace.jelly").forward(req, rsp);
            return null;
        } else {
            Computer c = ws.toComputer();
            String title;
            if (c == null) {
                title = Messages.AbstractProject_WorkspaceTitle(getDisplayName());
            } else {
                title = Messages.AbstractProject_WorkspaceTitleOnComputer(getDisplayName(), c.getDisplayName());
            }
            return new DirectoryBrowserSupport(this, ws, title, "folder.png", true);
        }
    }

    /**
     * Wipes out the workspace.
     */
    @RequirePOST
    public HttpResponse doDoWipeOutWorkspace() throws IOException, InterruptedException {
        checkPermission(Functions.isWipeOutPermissionEnabled() ? WIPEOUT : BUILD);
        R b = getSomeBuildWithWorkspace();
        FilePath ws = b != null ? b.getWorkspace() : null;
        if (ws != null && getScm().processWorkspaceBeforeDeletion(this, ws, b.getBuiltOn())) {
            ws.deleteRecursive();
            for (WorkspaceListener wl : WorkspaceListener.all()) {
                wl.afterDelete(this);
            }
            return new HttpRedirect(".");
        } else {
            // If we get here, that means the SCM blocked the workspace deletion.
            return new ForwardToView(this, "wipeOutWorkspaceBlocked.jelly");
        }
    }

    /**
     * {@link AbstractProject} subtypes should implement this base class as a descriptor.
     *
     * @since 1.294
     */
    public abstract static class AbstractProjectDescriptor extends TopLevelItemDescriptor {
        /**
         * {@link AbstractProject} subtypes can override this method to veto some {@link Descriptor}s
         * from showing up on their configuration screen. This is often useful when you are building
         * a workflow/company specific project type, where you want to limit the number of choices
         * given to the users.
         *
         * <p>
         * Some {@link Descriptor}s define their own schemes for controlling applicability
         * (such as {@link BuildStepDescriptor#isApplicable(Class)}),
         * This method works like AND in conjunction with them;
         * Both this method and that method need to return true in order for a given {@link Descriptor}
         * to show up for the given {@link Project}.
         *
         * <p>
         * The default implementation returns true for everything.
         *
         * @see BuildStepDescriptor#isApplicable(Class)
         * @see BuildWrapperDescriptor#isApplicable(AbstractProject)
         * @see TriggerDescriptor#isApplicable(Item)
         */
        @Override
        public boolean isApplicable(Descriptor descriptor) {
            return true;
        }

        @Restricted(DoNotUse.class)
        public FormValidation doCheckAssignedLabelString(@AncestorInPath AbstractProject<?, ?> project,
                                                         @QueryParameter String value) {
          // Provide a legacy interface in case plugins are not going through p:config-assignedLabel
          // see: JENKINS-25372
          LOGGER.log(Level.WARNING, "checking label via legacy '_.assignedLabelString'");
          return doCheckLabel(project, value);
        }

        public FormValidation doCheckLabel(@AncestorInPath AbstractProject<?, ?> project,
                                           @QueryParameter String value) {
            return LabelExpression.validate(value, project);
        }

        /**
         * Validate label expression string.
         *
         * @param project May be specified to perform project specific validation.
         * @since 1.590
         * @deprecated Use {@link LabelExpression#validate(String, Item)} instead.
         */
        @Deprecated
        public static @NonNull FormValidation validateLabelExpression(String value, @CheckForNull AbstractProject<?, ?> project) {
            return LabelExpression.validate(value, project);
        }

        public FormValidation doCheckCustomWorkspace(@QueryParameter String customWorkspace) {
            if (Util.fixEmptyAndTrim(customWorkspace) == null)
                return FormValidation.error(Messages.AbstractProject_CustomWorkspaceEmpty());
            else
                return FormValidation.ok();
        }

        public AutoCompletionCandidates doAutoCompleteUpstreamProjects(@QueryParameter String value) {
            AutoCompletionCandidates candidates = new AutoCompletionCandidates();
            List<TopLevelItem> jobs = Jenkins.get().getItems(j -> j instanceof Job && j.getFullName().startsWith(value));
            for (TopLevelItem job : jobs) {
                candidates.add(job.getFullName());
            }
            return candidates;
        }

        @Restricted(DoNotUse.class)
        public AutoCompletionCandidates doAutoCompleteAssignedLabelString(@QueryParameter String value) {
          // Provide a legacy interface in case plugins are not going through p:config-assignedLabel
          // see: JENKINS-25372
          LOGGER.log(Level.WARNING, "autocompleting label via legacy '_.assignedLabelString'");
          return doAutoCompleteLabel(value);
        }

        public AutoCompletionCandidates doAutoCompleteLabel(@QueryParameter String value) {
            return LabelExpression.autoComplete(value);
        }

        public List<SCMCheckoutStrategyDescriptor> getApplicableSCMCheckoutStrategyDescriptors(AbstractProject p) {
            return SCMCheckoutStrategyDescriptor._for(p);
        }
    }

    /**
     * Finds a {@link AbstractProject} that has the name closest to the given name.
     * @see Items#findNearest
     */
    public static @CheckForNull AbstractProject findNearest(String name) {
        return findNearest(name, Jenkins.get());
    }

    /**
     * Finds a {@link AbstractProject} whose name (when referenced from the specified context) is closest to the given name.
     *
     * @since 1.419
     * @see Items#findNearest
     */
    public static @CheckForNull AbstractProject findNearest(String name, ItemGroup context) {
        return Items.findNearest(AbstractProject.class, name, context);
    }

    private static final Comparator<Integer> REVERSE_INTEGER_COMPARATOR = Comparator.reverseOrder();

    private static final Logger LOGGER = Logger.getLogger(AbstractProject.class.getName());

    /**
     * @deprecated Just use {@link #CANCEL}.
     */
    @Deprecated
    public static final Permission ABORT = CANCEL;

    /**
     * @deprecated Use {@link ParameterizedJobMixIn#BUILD_NOW_TEXT}.
     */
    @Deprecated
    public static final Message<AbstractProject> BUILD_NOW_TEXT = new Message<>();

    /**
     * Used for CLI binding.
     */
    @CLIResolver
    public static AbstractProject resolveForCLI(
            @Argument(required = true, metaVar = "NAME", usage = "Job name") String name) throws CmdLineException {
        AbstractProject item = Jenkins.get().getItemByFullName(name, AbstractProject.class);
        if (item == null) {
            AbstractProject project = AbstractProject.findNearest(name);
            throw new CmdLineException(null, project == null ? Messages.AbstractItem_NoSuchJobExistsWithoutSuggestion(name)
                    : Messages.AbstractItem_NoSuchJobExists(name, project.getFullName()));
        }
        return item;
    }

    public String getCustomWorkspace() {
        return customWorkspace;
    }

    /**
     * User-specified workspace directory, or null if it's up to Jenkins.
     *
     * <p>
     * Normally a project uses the workspace location assigned by its parent container,
     * but sometimes people have builds that have hard-coded paths.
     *
     * <p>
     * This is not {@link File} because it may have to hold a path representation on another OS.
     *
     * <p>
     * If this path is relative, it's resolved against {@link Node#getRootPath()} on the node where this workspace
     * is prepared.
     *
     * @since 1.410
     */
    public void setCustomWorkspace(String customWorkspace) throws IOException {
        this.customWorkspace = Util.fixEmptyAndTrim(customWorkspace);
        save();
    }

    /**
     * Plugins may want to contribute additional restrictions on the use of specific labels for specific projects.
     * This extension point allows such restrictions.
     *
     * @since 1.540
     * @deprecated Use {@link jenkins.model.labels.LabelValidator} instead.
     */
    @Deprecated
    public abstract static class LabelValidator implements ExtensionPoint {

        /**
         * Check the use of the label within the specified context.
         * <p>
         * Note that "OK" responses (and any text/markup that may be set on them) will be ignored. Only warnings and
         * errors are taken into account, and aggregated across all validators.
         *
         * @param project the project that wants to restrict itself to the specified label.
         * @param label   the label that the project wants to restrict itself to.
         * @return the {@link FormValidation} result.
         */
        @NonNull
        public abstract FormValidation check(@NonNull AbstractProject<?, ?> project, @NonNull Label label);

        /**
         * Validates the use of a label within a particular context.
         * <p>
         * Note that "OK" responses (and any text/markup that may be set on them) will be ignored. Only warnings and
         * errors are taken into account, and aggregated across all validators.
         * <p>
         * This method exists to allow plugins to implement an override for it, enabling checking in non-AbstractProject
         * contexts without needing to update their Jenkins dependency (and using the new
         * {@link jenkins.model.labels.LabelValidator} instead).
         *
         * @param item  The context item to be restricted by the label.
         * @param label The label that the job wants to restrict itself to.
         * @return The validation result.
         *
         * @since 2.243
         */
        @NonNull
        public FormValidation checkItem(@NonNull Item item, @NonNull Label label) {
            if (item instanceof AbstractProject<?, ?>) {
                return this.check((AbstractProject<?, ?>) item, label);
            }
            return FormValidation.ok();
        }

    }

}
