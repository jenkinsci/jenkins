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

import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import hudson.EnvVars;
import hudson.Functions;
import antlr.ANTLRException;
import hudson.AbortException;
import hudson.CopyOnWrite;
import hudson.FeedAdapter;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.cli.declarative.CLIMethod;
import hudson.cli.declarative.CLIResolver;
import hudson.model.Cause.LegacyCodeCause;
import hudson.model.Cause.RemoteCause;
import hudson.model.Cause.UserIdCause;
import hudson.model.Descriptor.FormException;
import hudson.model.Fingerprint.RangeSet;
import hudson.model.PermalinkProjectAction.Permalink;
import hudson.model.Queue.Executable;
import hudson.model.Queue.Task;
import hudson.model.queue.QueueTaskFuture;
import hudson.model.queue.ScheduleResult;
import hudson.model.queue.SubTask;
import hudson.model.RunMap.Constructor;
import hudson.model.labels.LabelAtom;
import hudson.model.labels.LabelExpression;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.SCMPollListener;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.SubTaskContributor;
import hudson.node_monitors.DiskSpaceMonitor;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.scm.NullSCM;
import hudson.scm.PollingResult;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import hudson.scm.SCMS;
import hudson.search.SearchIndexBuilder;
import hudson.security.ACL;
import hudson.security.Permission;
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
import hudson.util.EditDistance;
import hudson.util.FormValidation;
import hudson.widgets.BuildHistoryWidget;
import hudson.widgets.HistoryWidget;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import jenkins.model.ModelObjectWithChildren;
import jenkins.model.lazy.AbstractLazyLoadRunMap.Direction;
import jenkins.scm.DefaultSCMCheckoutStrategyImpl;
import jenkins.scm.SCMCheckoutStrategy;
import jenkins.scm.SCMCheckoutStrategyDescriptor;
import jenkins.util.TimeDuration;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.jenkinsci.bytecode.AdaptField;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.ForwardToView;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.logging.Level;
import java.util.logging.Logger;

import static hudson.scm.PollingResult.*;
import javax.annotation.CheckForNull;
import static javax.servlet.http.HttpServletResponse.*;

/**
 * Base implementation of {@link Job}s that build software.
 *
 * For now this is primarily the common part of {@link Project} and MavenModule.
 *
 * @author Kohsuke Kawaguchi
 * @see AbstractBuild
 */
@SuppressWarnings("rawtypes")
public abstract class AbstractProject<P extends AbstractProject<P,R>,R extends AbstractBuild<P,R>> extends Job<P,R> implements BuildableItem, ModelObjectWithChildren {

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
    private volatile transient SCMRevisionState pollingBaseline = null;

    /**
     * All the builds keyed by their build number.
     *
     * External code should use {@link #getBuildByNumber(int)} or {@link #getLastBuild()} and traverse via
     * {@link Run#getPreviousBuild()}
     */
    @Restricted(NoExternalUse.class)
    @SuppressWarnings("deprecation") // [JENKINS-15156] builds accessed before onLoad or onCreatedFromScratch called
    protected transient RunMap<R> builds = new RunMap<R>();

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
    @AdaptField(was=List.class)
    protected volatile DescribableList<Trigger<?>,TriggerDescriptor> triggers = new DescribableList<Trigger<?>,TriggerDescriptor>(this);
    private static final AtomicReferenceFieldUpdater<AbstractProject,DescribableList> triggersUpdater
            = AtomicReferenceFieldUpdater.newUpdater(AbstractProject.class,DescribableList.class,"triggers");

    /**
     * {@link Action}s contributed from subsidiary objects associated with
     * {@link AbstractProject}, such as from triggers, builders, publishers, etc.
     *
     * We don't want to persist them separately, and these actions
     * come and go as configuration change, so it's kept separate.
     */
    @CopyOnWrite
    protected transient volatile List<Action> transientActions = new Vector<Action>();

    private boolean concurrentBuild;

    /**
     * See {@link #setCustomWorkspace(String)}.
     *
     * @since 1.410
     */
    private String customWorkspace;
    
    protected AbstractProject(ItemGroup parent, String name) {
        super(parent,name);

        if(Jenkins.getInstance()!=null && !Jenkins.getInstance().getNodes().isEmpty()) {
            // if a new job is configured with Hudson that already has slave nodes
            // make it roamable by default
            canRoam = true;
        }
    }

    @Override
    public synchronized void save() throws IOException {
        super.save();
        updateTransientActions();
    }

    @Override
    public void onCreatedFromScratch() {
        super.onCreatedFromScratch();
        builds = createBuildRunMap();
        // solicit initial contributions, especially from TransientProjectActionFactory
        updateTransientActions();
    }

    @Override
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);

        RunMap<R> builds = createBuildRunMap();

        RunMap<R> currentBuilds = this.builds;

        if (currentBuilds==null && parent!=null) {
            // are we overwriting what currently exist?
            // this is primarily when Jenkins is getting reloaded
            Item current;
            try {
                current = parent.getItem(name);
            } catch (RuntimeException x) {
                LOGGER.log(Level.WARNING, "failed to look up " + name + " in " + parent, x);
                current = null;
            }
            if (current!=null && current.getClass()==getClass()) {
                currentBuilds = ((AbstractProject)current).builds;
            }
        }
        if (currentBuilds !=null) {
            // if we are reloading, keep all those that are still building intact
            for (R r : currentBuilds.getLoadedBuilds().values()) {
                if (r.isBuilding())
                    builds.put(r);
            }
        }
        this.builds = builds;
        triggers().setOwner(this);
        for (Trigger t : triggers())
            t.start(this, Items.updatingByXml.get());
        if(scm==null)
            scm = new NullSCM(); // perhaps it was pointing to a plugin that no longer exists.

        if(transientActions==null)
            transientActions = new Vector<Action>();    // happens when loaded from disk
        updateTransientActions();
    }

    private RunMap<R> createBuildRunMap() {
        return new RunMap<R>(getBuildDir(), new Constructor<R>() {
            public R create(File dir) throws IOException {
                return loadBuild(dir);
            }
        });
    }

    @WithBridgeMethods(List.class)
    protected DescribableList<Trigger<?>,TriggerDescriptor> triggers() {
        if (triggers == null) {
            triggersUpdater.compareAndSet(this,null,new DescribableList<Trigger<?>,TriggerDescriptor>(this));
        }
        return triggers;
    }

    @Override
    public EnvVars getEnvironment(Node node, TaskListener listener) throws IOException, InterruptedException {
        EnvVars env =  super.getEnvironment(node, listener);

        JDK jdk = getJDK();
        if (jdk != null) {
            if (node != null) { // just in case were not in a build
                jdk = jdk.forNode(node, listener);
            }
            jdk.buildEnvVars(env);
        }

        return env;
    }

    @Override
    protected void performDelete() throws IOException, InterruptedException {
        // prevent a new build while a delete operation is in progress
        makeDisabled(true);
        FilePath ws = getWorkspace();
        if(ws!=null) {
            Node on = getLastBuiltOn();
            getScm().processWorkspaceBeforeDeletion(this, ws, on);
            if(on!=null)
                on.getFileSystemProvisioner().discardWorkspace(this,ws);
        }
        super.performDelete();
    }

    /**
     * Does this project perform concurrent builds?
     * @since 1.319
     */
    @Exported
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
    public Label getAssignedLabel() {
        if(canRoam)
            return null;

        if(assignedNode==null)
            return Jenkins.getInstance().getSelfLabel();
        return Jenkins.getInstance().getLabel(assignedNode);
    }

    /**
     * Set of labels relevant to this job.
     *
     * This method is used to determine what slaves are relevant to jobs, for example by {@link View}s.
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
    public String getAssignedLabelString() {
        if (canRoam || assignedNode==null)    return null;
        try {
            LabelExpression.parseExpression(assignedNode);
            return assignedNode;
        } catch (ANTLRException e) {
            // must be old label or host name that includes whitespace or other unsafe chars
            return LabelAtom.escape(assignedNode);
        }
    }

    /**
     * Sets the assigned label.
     */
    public void setAssignedLabel(Label l) throws IOException {
        if(l==null) {
            canRoam = true;
            assignedNode = null;
        } else {
            canRoam = false;
            if(l== Jenkins.getInstance().getSelfLabel())  assignedNode = null;
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
        return AlternativeUiTextProvider.get(PRONOUN, this,Messages.AbstractProject_Pronoun());
    }

    /**
     * Gets the human readable display name to be rendered in the "Build Now" link.
     *
     * @since 1.401
     */
    public String getBuildNowText() {
        return AlternativeUiTextProvider.get(BUILD_NOW_TEXT, this, isParameterized() ? Messages.AbstractProject_build_with_parameters() : Messages.AbstractProject_BuildNow());
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
    public AbstractProject<?,?> getRootProject() {
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
     *      null if the workspace is on a slave that's not connected.
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
    private AbstractBuild getBuildForDeprecatedMethods() {
        Executor e = Executor.currentExecutor();
        if(e!=null) {
            Executable exe = e.getCurrentExecutable();
            if (exe instanceof AbstractBuild) {
                AbstractBuild b = (AbstractBuild) exe;
                if(b.getProject()==this)
                    return b;
            }
        }
        R lb = getLastBuild();
        if(lb!=null)    return lb;
        return null;
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
    public final FilePath getSomeWorkspace() {
        R b = getSomeBuildWithWorkspace();
        if (b!=null) return b.getWorkspace();
        for (WorkspaceBrowser browser : Jenkins.getInstance().getExtensionList(WorkspaceBrowser.class)) {
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
        int cnt=0;
        for (R b = getLastBuild(); cnt<5 && b!=null; b=b.getPreviousBuild()) {
            FilePath ws = b.getWorkspace();
            if (ws!=null)   return b;
        }
        return null;
    }
    
    private R getSomeBuildWithExistingWorkspace() throws IOException, InterruptedException {
        int cnt=0;
        for (R b = getLastBuild(); cnt<5 && b!=null; b=b.getPreviousBuild()) {
            FilePath ws = b.getWorkspace();
            if (ws!=null && ws.exists())   return b;
        }
        return null;
    }

    /**
     * Returns the root directory of the checked-out module.
     * <p>
     * This is usually where <tt>pom.xml</tt>, <tt>build.xml</tt>
     * and so on exists.
     *
     * @deprecated as of 1.319
     *      See {@link #getWorkspace()} for a migration strategy.
     */
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
    public FilePath[] getModuleRoots() {
        AbstractBuild b = getBuildForDeprecatedMethods();
        return b != null ? b.getModuleRoots() : null;
    }

    public int getQuietPeriod() {
        return quietPeriod!=null ? quietPeriod : Jenkins.getInstance().getQuietPeriod();
    }

    public SCMCheckoutStrategy getScmCheckoutStrategy() {
        return scmCheckoutStrategy == null ? new DefaultSCMCheckoutStrategyImpl() : scmCheckoutStrategy;
    }

    public void setScmCheckoutStrategy(SCMCheckoutStrategy scmCheckoutStrategy) throws IOException {
        this.scmCheckoutStrategy = scmCheckoutStrategy;
        save();
    }


    public int getScmCheckoutRetryCount() {
        return scmCheckoutRetryCount !=null ? scmCheckoutRetryCount : Jenkins.getInstance().getScmCheckoutRetryCount();
    }

    // ugly name because of EL
    public boolean getHasCustomQuietPeriod() {
        return quietPeriod!=null;
    }

    /**
     * Sets the custom quiet period of this project, or revert to the global default if null is given. 
     */
    public void setQuietPeriod(Integer seconds) throws IOException {
        this.quietPeriod = seconds;
        save();
    }
    
    public boolean hasCustomScmCheckoutRetryCount(){
        return scmCheckoutRetryCount != null;
    }

    @Override
    public boolean isBuildable() {
        return !isDisabled() && !isHoldOffBuildUntilSave();
    }

    /**
     * Used in <tt>sidepanel.jelly</tt> to decide whether to display
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

    public boolean isDisabled() {
        return disabled;
    }
    
    /**
     * Validates the retry count Regex
     */
    public FormValidation doCheckRetryCount(@QueryParameter String value)throws IOException,ServletException{
        // retry count is optional so this is ok
        if(value == null || value.trim().equals(""))
            return FormValidation.ok();
        if (!value.matches("[0-9]*")) {
            return FormValidation.error("Invalid retry count");
        } 
        return FormValidation.ok();
    }

    /**
     * Marks the build as disabled.
     */
    public void makeDisabled(boolean b) throws IOException {
        if(disabled==b)     return; // noop
        this.disabled = b;
        if(b)
            Jenkins.getInstance().getQueue().cancel(this);
        
        save();
        ItemListener.fireOnUpdated(this);
    }

    /**
     * Specifies whether this project may be disabled by the user.
     * By default, it can be only if this is a {@link TopLevelItem};
     * would be false for matrix configurations, etc.
     * @return true if the GUI should allow {@link #doDisable} and the like
     * @since 1.475
     */
    public boolean supportsMakeDisabled() {
        return this instanceof TopLevelItem;
    }

    public void disable() throws IOException {
        makeDisabled(true);
    }

    public void enable() throws IOException {
        makeDisabled(false);
    }

    @Override
    public BallColor getIconColor() {
        if(isDisabled())
            return BallColor.DISABLED;
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
        Vector<Action> ta = new Vector<Action>();

        for (JobProperty<? super P> p : Util.fixNull(properties))
            ta.addAll(p.getJobActions((P)this));

        for (TransientProjectActionFactory tpaf : TransientProjectActionFactory.all())
            ta.addAll(Util.fixNull(tpaf.createFor(this))); // be defensive against null
        return ta;
    }

    /**
     * Returns the live list of all {@link Publisher}s configured for this project.
     *
     * <p>
     * This method couldn't be called <tt>getPublishers()</tt> because existing methods
     * in sub-classes return different inconsistent types.
     */
    public abstract DescribableList<Publisher,Descriptor<Publisher>> getPublishersList();

    @Override
    public void addProperty(JobProperty<? super P> jobProp) throws IOException {
        super.addProperty(jobProp);
        updateTransientActions();
    }

    public List<ProminentProjectAction> getProminentActions() {
        List<Action> a = getActions();
        List<ProminentProjectAction> pa = new Vector<ProminentProjectAction>();
        for (Action action : a) {
            if(action instanceof ProminentProjectAction)
                pa.add((ProminentProjectAction) action);
        }
        return pa;
    }

    @Override
    public void doConfigSubmit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, FormException {
        super.doConfigSubmit(req,rsp);

        updateTransientActions();

        Set<AbstractProject> upstream = Collections.emptySet();
        if(req.getParameter("pseudoUpstreamTrigger")!=null) {
            upstream = new HashSet<AbstractProject>(Items.fromNameList(getParent(),req.getParameter("upstreamProjects"),AbstractProject.class));
        }

        convertUpstreamBuildTrigger(upstream);

        // notify the queue as the project might be now tied to different node
        Jenkins.getInstance().getQueue().scheduleMaintenance();

        // this is to reflect the upstream build adjustments done above
        Jenkins.getInstance().rebuildDependencyGraphAsync();
    }

    /**
     * Reflect the submission of the pseudo 'upstream build trigger'.
     */
    /* package */ void convertUpstreamBuildTrigger(Set<AbstractProject> upstream) throws IOException {

        SecurityContext saveCtx = ACL.impersonate(ACL.SYSTEM);
        try {
            for (AbstractProject<?,?> p : Jenkins.getInstance().getAllItems(AbstractProject.class)) {
                // Don't consider child projects such as MatrixConfiguration:
                if (!p.isConfigurable()) continue;
                boolean isUpstream = upstream.contains(p);
                synchronized(p) {
                    // does 'p' include us in its BuildTrigger?
                    DescribableList<Publisher,Descriptor<Publisher>> pl = p.getPublishersList();
                    BuildTrigger trigger = pl.get(BuildTrigger.class);
                    List<AbstractProject> newChildProjects = trigger == null ? new ArrayList<AbstractProject>():trigger.getChildProjects(p);
                    if(isUpstream) {
                        if(!newChildProjects.contains(this))
                            newChildProjects.add(this);
                    } else {
                        newChildProjects.remove(this);
                    }

                    if(newChildProjects.isEmpty()) {
                        pl.remove(BuildTrigger.class);
                    } else {
                        // here, we just need to replace the old one with the new one,
                        // but there was a regression (we don't know when it started) that put multiple BuildTriggers
                        // into the list. For us not to lose the data, we need to merge them all.
                        List<BuildTrigger> existingList = pl.getAll(BuildTrigger.class);
                        BuildTrigger existing;
                        switch (existingList.size()) {
                        case 0:
                            existing = null;
                            break;
                        case 1:
                            existing = existingList.get(0);
                            break;
                        default:
                            pl.removeAll(BuildTrigger.class);
                            Set<AbstractProject> combinedChildren = new HashSet<AbstractProject>();
                            for (BuildTrigger bt : existingList)
                                combinedChildren.addAll(bt.getChildProjects(p));
                            existing = new BuildTrigger(new ArrayList<AbstractProject>(combinedChildren),existingList.get(0).getThreshold());
                            pl.add(existing);
                            break;
                        }

                        if(existing!=null && existing.hasSame(p,newChildProjects))
                            continue;   // no need to touch
                        pl.replace(new BuildTrigger(newChildProjects,
                            existing==null? Result.SUCCESS:existing.getThreshold()));
                    }
                }
            }
        } finally {
            SecurityContextHolder.setContext(saveCtx);
        }
    }

    /**
	 * @deprecated
	 *    Use {@link #scheduleBuild(Cause)}.  Since 1.283
	 */
    public boolean scheduleBuild() {
    	return scheduleBuild(new LegacyCodeCause());
    }
    
	/**
	 * @deprecated
	 *    Use {@link #scheduleBuild(int, Cause)}.  Since 1.283
	 */
    public boolean scheduleBuild(int quietPeriod) {
    	return scheduleBuild(quietPeriod, new LegacyCodeCause());
    }
    
    /**
     * Schedules a build of this project.
     *
     * @return
     *      true if the project is added to the queue.
     *      false if the task was rejected from the queue (such as when the system is being shut down.)
     */
    public boolean scheduleBuild(Cause c) {
        return scheduleBuild(getQuietPeriod(), c);
    }

    public boolean scheduleBuild(int quietPeriod, Cause c) {
        return scheduleBuild(quietPeriod, c, new Action[0]);
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
        return scheduleBuild2(quietPeriod,c,actions)!=null;
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
        return scheduleBuild2(quietPeriod,c,Arrays.asList(actions));
    }

    /**
     * Schedules a build of this project, and returns a {@link Future} object
     * to wait for the completion of the build.
     *
     * @param actions
     *      For the convenience of the caller, this collection can contain null, and those will be silently ignored.
     * @since 1.383
     */
    @SuppressWarnings("unchecked")
    @WithBridgeMethods(Future.class)
    public QueueTaskFuture<R> scheduleBuild2(int quietPeriod, Cause c, Collection<? extends Action> actions) {
        if (!isBuildable())
            return null;

        List<Action> queueActions = new ArrayList<Action>(actions);
        if (isParameterized() && Util.filter(queueActions, ParametersAction.class).isEmpty()) {
            queueActions.add(new ParametersAction(getDefaultParametersValues()));
        }

        if (c != null) {
            queueActions.add(new CauseAction(c));
        }

        ScheduleResult i = Jenkins.getInstance().getQueue().schedule2(this, quietPeriod, queueActions);
        if(i.isAccepted())
            return (QueueTaskFuture)i.getItem().getFuture();
        return null;
    }

    private List<ParameterValue> getDefaultParametersValues() {
        ParametersDefinitionProperty paramDefProp = getProperty(ParametersDefinitionProperty.class);
        ArrayList<ParameterValue> defValues = new ArrayList<ParameterValue>();
        
        /*
         * This check is made ONLY if someone will call this method even if isParametrized() is false.
         */
        if(paramDefProp == null)
            return defValues;
        
        /* Scan for all parameter with an associated default values */
        for(ParameterDefinition paramDefinition : paramDefProp.getParameterDefinitions())
        {
           ParameterValue defaultValue  = paramDefinition.getDefaultParameterValue();
            
            if(defaultValue != null)
                defValues.add(defaultValue);           
        }
        
        return defValues;
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
        return scheduleBuild2(quietPeriod, new LegacyCodeCause());
    }
    
    /**
     * Schedules a build of this project, and returns a {@link Future} object
     * to wait for the completion of the build.
     */
    @WithBridgeMethods(Future.class)
    public QueueTaskFuture<R> scheduleBuild2(int quietPeriod, Cause c) {
        return scheduleBuild2(quietPeriod, c, new Action[0]);
    }

    /**
     * Schedules a polling of this project.
     */
    public boolean schedulePolling() {
        if(isDisabled())    return false;
        SCMTrigger scmt = getTrigger(SCMTrigger.class);
        if(scmt==null)      return false;
        scmt.run();
        return true;
    }

    /**
     * Returns true if the build is in the queue.
     */
    @Override
    public boolean isInQueue() {
        return Jenkins.getInstance().getQueue().contains(this);
    }

    @Override
    public Queue.Item getQueueItem() {
        return Jenkins.getInstance().getQueue().getItem(this);
    }

    /**
     * Gets the JDK that this project is configured with, or null.
     */
    public JDK getJDK() {
        return Jenkins.getInstance().getJDK(jdk);
    }

    /**
     * Overwrites the JDK setting.
     */
    public void setJDK(JDK jdk) throws IOException {
        this.jdk = jdk.getName();
        save();
    }

    public BuildAuthorizationToken getAuthToken() {
        return authToken;
    }

    @Override
    public RunMap<R> _getRuns() {
        if (builds == null) {
            throw new IllegalStateException("no run map created yet for " + this);
        }
        assert builds.baseDirInitialized() : "neither onCreatedFromScratch nor onLoad called on " + this + " yet";
        return builds;
    }

    @Override
    public void removeRun(R run) {
        if (!this.builds.remove(run)) {
            LOGGER.log(Level.WARNING, "{0} did not contain {1} to begin with", new Object[] {this, run});
        }
    }

    /**
     * {@inheritDoc}
     *
     * More efficient implementation.
     */
    @Override
    public R getBuild(String id) {
        return builds.getById(id);
    }

    /**
     * {@inheritDoc}
     *
     * More efficient implementation.
     */
    @Override
    public R getBuildByNumber(int n) {
        return builds.getByNumber(n);
    }

    /**
     * {@inheritDoc}
     *
     * More efficient implementation.
     */
    @Override
    public R getFirstBuild() {
        return builds.oldestBuild();
    }

    @Override
    public @CheckForNull R getLastBuild() {
        return builds.newestBuild();
    }

    @Override
    public R getNearestBuild(int n) {
        return builds.search(n, Direction.ASC);
    }

    @Override
    public R getNearestOldBuild(int n) {
        return builds.search(n, Direction.DESC);
    }

    /**
     * Determines Class&lt;R>.
     */
    protected abstract Class<R> getBuildClass();

    // keep track of the previous time we started a build
    private transient long lastBuildStartTime;
    
    /**
     * Creates a new build of this project for immediate execution.
     */
    protected synchronized R newBuild() throws IOException {
    	// make sure we don't start two builds in the same second
    	// so the build directories will be different too
    	long timeSinceLast = System.currentTimeMillis() - lastBuildStartTime;
    	if (timeSinceLast < 1000) {
    		try {
				Thread.sleep(1000 - timeSinceLast);
			} catch (InterruptedException e) {
			}
    	}
    	lastBuildStartTime = System.currentTimeMillis();
        try {
            R lastBuild = getBuildClass().getConstructor(getClass()).newInstance(this);
            builds.put(lastBuild);
            return lastBuild;
        } catch (InstantiationException e) {
            throw new Error(e);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        } catch (InvocationTargetException e) {
            throw handleInvocationTargetException(e);
        } catch (NoSuchMethodException e) {
            throw new Error(e);
        }
    }

    private IOException handleInvocationTargetException(InvocationTargetException e) {
        Throwable t = e.getTargetException();
        if(t instanceof Error)  throw (Error)t;
        if(t instanceof RuntimeException)   throw (RuntimeException)t;
        if(t instanceof IOException)    return (IOException)t;
        throw new Error(t);
    }

    /**
     * Loads an existing build record from disk.
     */
    protected R loadBuild(File dir) throws IOException {
        try {
            return getBuildClass().getConstructor(getClass(),File.class).newInstance(this,dir);
        } catch (InstantiationException e) {
            throw new Error(e);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        } catch (InvocationTargetException e) {
            throw handleInvocationTargetException(e);
        } catch (NoSuchMethodException e) {
            throw new Error(e);
        }
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
    @Override
    public List<Action> getActions() {
        // add all the transient actions, too
        List<Action> actions = new Vector<Action>(super.getActions());
        actions.addAll(transientActions);
        // return the read only list to cause a failure on plugins who try to add an action here
        return Collections.unmodifiableList(actions);
    }

    /**
     * Gets the {@link Node} where this project was last built on.
     *
     * @return
     *      null if no information is available (for example,
     *      if no build was done yet.)
     */
    public Node getLastBuiltOn() {
        // where was it built on?
        AbstractBuild b = getLastBuild();
        if(b==null)
            return null;
        else
            return b.getBuiltOn();
    }

    public Object getSameNodeConstraint() {
        return this; // in this way, any member that wants to run with the main guy can nominate the project itself 
    }

    public final Task getOwnerTask() {
        return this;
    }

    @Nonnull
    public Authentication getDefaultAuthentication() {
        // backward compatible behaviour.
        return ACL.SYSTEM;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * A project must be blocked if its own previous build is in progress,
     * or if the blockBuildWhenUpstreamBuilding option is true and an upstream
     * project is building, but derived classes can also check other conditions.
     */
    public boolean isBuildBlocked() {
        return getCauseOfBlockage()!=null;
    }

    public String getWhyBlocked() {
        CauseOfBlockage cb = getCauseOfBlockage();
        return cb!=null ? cb.getShortDescription() : null;
    }

    /**
     * Blocked because the previous build is already in progress.
     */
    public static class BecauseOfBuildInProgress extends CauseOfBlockage {
        private final AbstractBuild<?,?> build;

        public BecauseOfBuildInProgress(AbstractBuild<?, ?> build) {
            this.build = build;
        }

        @Override
        public String getShortDescription() {
            Executor e = build.getExecutor();
            String eta = "";
            if (e != null)
                eta = Messages.AbstractProject_ETA(e.getEstimatedRemainingTime());
            int lbn = build.getNumber();
            return Messages.AbstractProject_BuildInProgress(lbn, eta);
        }
    }
    
    /**
     * Because the downstream build is in progress, and we are configured to wait for that.
     */
    public static class BecauseOfDownstreamBuildInProgress extends CauseOfBlockage {
        public final AbstractProject<?,?> up;

        public BecauseOfDownstreamBuildInProgress(AbstractProject<?,?> up) {
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
        public final AbstractProject<?,?> up;

        public BecauseOfUpstreamBuildInProgress(AbstractProject<?,?> up) {
            this.up = up;
        }

        @Override
        public String getShortDescription() {
            return Messages.AbstractProject_UpstreamBuildInProgress(up.getName());
        }
    }

    public CauseOfBlockage getCauseOfBlockage() {
        // Block builds until they are done with post-production
        if (isLogUpdated() && !isConcurrentBuild())
            return new BecauseOfBuildInProgress(getLastBuild());
        if (blockBuildWhenDownstreamBuilding()) {
            AbstractProject<?,?> bup = getBuildingDownstream();
            if (bup!=null)
                return new BecauseOfDownstreamBuildInProgress(bup);
        }
        if (blockBuildWhenUpstreamBuilding()) {
            AbstractProject<?,?> bup = getBuildingUpstream();
            if (bup!=null)
                return new BecauseOfUpstreamBuildInProgress(bup);
        }
        return null;
    }

    /**
     * Returns the project if any of the downstream project is either
     * building, waiting, pending or buildable.
     * <p>
     * This means eventually there will be an automatic triggering of
     * the given project (provided that all builds went smoothly.)
     */
    public AbstractProject getBuildingDownstream() {
        Set<Task> unblockedTasks = Jenkins.getInstance().getQueue().getUnblockedTasks();

        for (AbstractProject tup : getTransitiveDownstreamProjects()) {
			if (tup!=this && (tup.isBuilding() || unblockedTasks.contains(tup)))
                return tup;
        }
        return null;
    }

    /**
     * Returns the project if any of the upstream project is either
     * building or is in the queue.
     * <p>
     * This means eventually there will be an automatic triggering of
     * the given project (provided that all builds went smoothly.)
     */
    public AbstractProject getBuildingUpstream() {
        Set<Task> unblockedTasks = Jenkins.getInstance().getQueue().getUnblockedTasks();

        for (AbstractProject tup : getTransitiveUpstreamProjects()) {
			if (tup!=this && (tup.isBuilding() || unblockedTasks.contains(tup)))
                return tup;
        }
        return null;
    }

    public List<SubTask> getSubTasks() {
        List<SubTask> r = new ArrayList<SubTask>();
        r.add(this);
        for (SubTaskContributor euc : SubTaskContributor.all())
            r.addAll(euc.forProject(this));
        for (JobProperty<? super P> p : properties)
            r.addAll(p.getSubTasks());
        return r;
    }

    public R createExecutable() throws IOException {
        if(isDisabled())    return null;
        return newBuild();
    }

    public void checkAbortPermission() {
        checkPermission(AbstractProject.ABORT);
    }

    public boolean hasAbortPermission() {
        return hasPermission(AbstractProject.ABORT);
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
    public Resource getWorkspaceResource() {
        return new Resource(getFullDisplayName()+" workspace");
    }

    /**
     * List of necessary resources to perform the build of this project.
     */
    public ResourceList getResourceList() {
        final Set<ResourceActivity> resourceActivities = getResourceActivities();
        final List<ResourceList> resourceLists = new ArrayList<ResourceList>(1 + resourceActivities.size());
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
        if(scm==null)
            return true;    // no SCM

        FilePath workspace = build.getWorkspace();
        try {
            workspace.mkdirs();
        } catch (IOException e) {
            // Can't create workspace dir - Is slave disk full ?
            new DiskSpaceMonitor().markNodeOfflineIfDiskspaceIsTooLow(build.getBuiltOn().toComputer());
            throw e;
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
        if (baseline==null) {
            try {
                baseline = getScm()._calcRevisionsFromBuild(build, launcher, listener);
            } catch (AbstractMethodError e) {
                baseline = SCMRevisionState.NONE; // pre-1.345 SCM implementations, which doesn't use the baseline in polling
            }
            if (baseline!=null)
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
    public boolean pollSCMChanges( TaskListener listener ) {
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
    public PollingResult poll( TaskListener listener ) {
        SCM scm = getScm();
        if (scm==null) {
            listener.getLogger().println(Messages.AbstractProject_NoSCM());
            return NO_CHANGES;
        }
        if (!isBuildable()) {
            listener.getLogger().println(Messages.AbstractProject_Disabled());
            return NO_CHANGES;
        }

        R lb = getLastBuild();
        if (lb==null) {
            listener.getLogger().println(Messages.AbstractProject_NoBuilds());
            return isInQueue() ? NO_CHANGES : BUILD_NOW;
        }

        if (pollingBaseline==null) {
            R success = getLastSuccessfulBuild(); // if we have a persisted baseline, we'll find it by this
            for (R r=lb; r!=null; r=r.getPreviousBuild()) {
                SCMRevisionState s = r.getAction(SCMRevisionState.class);
                if (s!=null) {
                    pollingBaseline = s;
                    break;
                }
                if (r==success) break;  // searched far enough
            }
            // NOTE-NO-BASELINE:
            // if we don't have baseline yet, it means the data is built by old Hudson that doesn't set the baseline
            // as action, so we need to compute it. This happens later.
        }

        try {
            SCMPollListener.fireBeforePolling(this, listener);
            PollingResult r = _poll(listener, scm);
            SCMPollListener.firePollingSuccess(this,listener, r);
            return r;
        } catch (AbortException e) {
            listener.getLogger().println(e.getMessage());
            listener.fatalError(Messages.AbstractProject_Aborted());
            LOGGER.log(Level.FINE, "Polling "+this+" aborted",e);
            SCMPollListener.firePollingFailed(this, listener,e);
            return NO_CHANGES;
        } catch (IOException e) {
            e.printStackTrace(listener.fatalError(e.getMessage()));
            SCMPollListener.firePollingFailed(this, listener,e);
            return NO_CHANGES;
        } catch (InterruptedException e) {
            e.printStackTrace(listener.fatalError(Messages.AbstractProject_PollingABorted()));
            SCMPollListener.firePollingFailed(this, listener,e);
            return NO_CHANGES;
        } catch (RuntimeException e) {
            SCMPollListener.firePollingFailed(this, listener,e);
            throw e;
        } catch (Error e) {
            SCMPollListener.firePollingFailed(this, listener,e);
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
            FilePath ws=b.getWorkspace();

            WorkspaceOfflineReason workspaceOfflineReason = workspaceOffline( b );
            if ( workspaceOfflineReason != null ) {
                // workspace offline
                for (WorkspaceBrowser browser : Jenkins.getInstance().getExtensionList(WorkspaceBrowser.class)) {
                    ws = browser.getWorkspace(this);
                    if (ws != null) {
                        return pollWithWorkspace(listener, scm, b, ws, browser.getWorkspaceList());
                    }
                }

                // build now, or nothing will ever be built
                Label label = getAssignedLabel();
                if (label != null && label.isSelfLabel()) {
                    // if the build is fixed on a node, then attempting a build will do us
                    // no good. We should just wait for the slave to come back.
                    listener.getLogger().print(Messages.AbstractProject_NoWorkspace());
                    listener.getLogger().println( " (" + workspaceOfflineReason.name() + ")");
                    return NO_CHANGES;
                }
                listener.getLogger().println( ws==null
                    ? Messages.AbstractProject_WorkspaceOffline()
                    : Messages.AbstractProject_NoWorkspace());
                if (isInQueue()) {
                    listener.getLogger().println(Messages.AbstractProject_AwaitingBuildForWorkspace());
                    return NO_CHANGES;
                } else {
                    listener.getLogger().print(Messages.AbstractProject_NewBuildForWorkspace());
                    listener.getLogger().println( " (" + workspaceOfflineReason.name() + ")");
                    return BUILD_NOW;
                }
            } else {
                WorkspaceList l = b.getBuiltOn().toComputer().getWorkspaceList();
                return pollWithWorkspace(listener, scm, b, ws, l);

            }
        } else {
            // polling without workspace
            LOGGER.fine("Polling SCM changes of " + getName());

            if (pollingBaseline==null) // see NOTE-NO-BASELINE above
                calcPollingBaseline(getLastBuild(),null,listener);
            PollingResult r = scm.poll(this, null, null, listener, pollingBaseline);
            pollingBaseline = r.remote;
            return r;
        }
    }

    private PollingResult pollWithWorkspace(TaskListener listener, SCM scm, R lb, FilePath ws, WorkspaceList l) throws InterruptedException, IOException {
        // if doing non-concurrent build, acquire a workspace in a way that causes builds to block for this workspace.
        // this prevents multiple workspaces of the same job --- the behavior of Hudson < 1.319.
        //
        // OTOH, if a concurrent build is chosen, the user is willing to create a multiple workspace,
        // so better throughput is achieved over time (modulo the initial cost of creating that many workspaces)
        // by having multiple workspaces
        WorkspaceList.Lease lease = l.acquire(ws, !concurrentBuild);
        Node node = lb.getBuiltOn();
        Launcher launcher = ws.createLauncher(listener).decorateByEnv(getEnvironment(node,listener));
        try {
            listener.getLogger().println("Polling SCM changes on " + node.getSelfLabel().getName());
            LOGGER.fine("Polling SCM changes of " + getName());
            if (pollingBaseline==null) // see NOTE-NO-BASELINE above
                calcPollingBaseline(lb,launcher,listener);
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
        builton_node_no_executors
    }

    private WorkspaceOfflineReason workspaceOffline(R build) throws IOException, InterruptedException {
        FilePath ws = build.getWorkspace();
        if (ws==null || !ws.exists()) {
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
        for( R build = getLastBuild(); build!=null; build=build.getPreviousBuild())
            if(build.hasParticipant(user))
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
        addToList(trigger,triggers());
    }

    public void removeTrigger(TriggerDescriptor trigger) throws IOException {
        removeFromList(trigger,triggers());
    }

    protected final synchronized <T extends Describable<T>>
    void addToList( T item, List<T> collection ) throws IOException {
        //No support to replace item in position, remove then add
        removeFromList(item.getDescriptor(), collection);
        collection.add(item);
        save();
        updateTransientActions();
    }

    protected final synchronized <T extends Describable<T>>
    void removeFromList(Descriptor<T> item, List<T> collection) throws IOException {
        final Iterator<T> iCollection = collection.iterator();
        while(iCollection.hasNext()) {
            final T next = iCollection.next();
            if(next.getDescriptor()==item) {
                // found it
                iCollection.remove();
                save();
                updateTransientActions();
                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    public Map<TriggerDescriptor,Trigger<?>> getTriggers() {
        return triggers().toMap();
    }

    /**
     * Gets the specific trigger, or null if the propert is not configured for this job.
     */
    public <T extends Trigger> T getTrigger(Class<T> clazz) {
        for (Trigger p : triggers()) {
            if(clazz.isInstance(p))
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
    @Exported
    public final List<AbstractProject> getDownstreamProjects() {
        return Jenkins.getInstance().getDependencyGraph().getDownstream(this);
    }

    @Exported
    public final List<AbstractProject> getUpstreamProjects() {
        return Jenkins.getInstance().getDependencyGraph().getUpstream(this);
    }

    /**
     * Returns only those upstream projects that defines {@link BuildTrigger} to this project.
     * This is a subset of {@link #getUpstreamProjects()}
     *
     * @return A List of upstream projects that has a {@link BuildTrigger} to this project.
     */
    public final List<AbstractProject> getBuildTriggerUpstreamProjects() {
        ArrayList<AbstractProject> result = new ArrayList<AbstractProject>();
        for (AbstractProject<?,?> ap : getUpstreamProjects()) {
            BuildTrigger buildTrigger = ap.getPublishersList().get(BuildTrigger.class);
            if (buildTrigger != null)
                if (buildTrigger.getChildProjects(ap).contains(this))
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
        return Jenkins.getInstance().getDependencyGraph().getTransitiveUpstream(this);
    }

    /**
     * Gets all the downstream projects including transitive downstream projects.
     *
     * @since 1.138
     */
    public final Set<AbstractProject> getTransitiveDownstreamProjects() {
        return Jenkins.getInstance().getDependencyGraph().getTransitiveDownstream(this);
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
        TreeMap<Integer,RangeSet> r = new TreeMap<Integer,RangeSet>(REVERSE_INTEGER_COMPARATOR);

        checkAndRecord(that, r, this.getBuilds());
        // checkAndRecord(that, r, that.getBuilds());

        return r;
    }

    /**
     * Helper method for getDownstreamRelationship.
     *
     * For each given build, find the build number range of the given project and put that into the map.
     */
    private void checkAndRecord(AbstractProject that, TreeMap<Integer, RangeSet> r, Collection<R> builds) {
        for (R build : builds) {
            RangeSet rs = build.getDownstreamRelationship(that);
            if(rs==null || rs.isEmpty())
                continue;

            int n = build.getNumber();

            RangeSet value = r.get(n);
            if(value==null)
                r.put(n,rs);
            else
                value.add(rs);
        }
    }

    /**
     * Builds the dependency graph.
     * @see DependencyGraph
     */
    protected abstract void buildDependencyGraph(DependencyGraph graph);

    @Override
    protected SearchIndexBuilder makeSearchIndex() {
        SearchIndexBuilder sib = super.makeSearchIndex();
        if(isBuildable() && hasPermission(Jenkins.ADMINISTER))
            sib.add("build","build");
        return sib;
    }

    @Override
    protected HistoryWidget createHistoryWidget() {
        return new BuildHistoryWidget<R>(this,builds,HISTORY_ADAPTER);
    }
    
    public boolean isParameterized() {
        return getProperty(ParametersDefinitionProperty.class) != null;
    }

//
//
// actions
//
//
    /**
     * Schedules a new build command.
     */
    public void doBuild( StaplerRequest req, StaplerResponse rsp, @QueryParameter TimeDuration delay ) throws IOException, ServletException {
        if (delay==null)    delay=new TimeDuration(getQuietPeriod());

        // if a build is parameterized, let that take over
        ParametersDefinitionProperty pp = getProperty(ParametersDefinitionProperty.class);
        if (pp != null && !req.getMethod().equals("POST")) {
            // show the parameter entry form.
            req.getView(pp, "index.jelly").forward(req, rsp);
            return;
        }

        BuildAuthorizationToken.checkPermission(this, authToken, req, rsp);

        if (pp != null) {
            pp._doBuild(req,rsp,delay);
            return;
        }

        if (!isBuildable())
            throw HttpResponses.error(SC_INTERNAL_SERVER_ERROR,new IOException(getFullName()+" is not buildable"));

        ScheduleResult r = Jenkins.getInstance().getQueue().schedule2(this, delay.getTime(), getBuildCause(req));
        if (r.isAccepted()) {
            rsp.sendRedirect(SC_CREATED,req.getContextPath()+'/'+r.getItem().getUrl());
        } else
            rsp.sendRedirect(".");
    }

    /** @deprecated use {@link #doBuild(StaplerRequest, StaplerResponse, TimeDuration)} */
    @Deprecated
    public void doBuild(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        doBuild(req, rsp, TimeDuration.fromString(req.getParameter("delay")));
    }

    /**
     * Computes the build cause, using RemoteCause or UserCause as appropriate.
     */
    /*package*/ CauseAction getBuildCause(StaplerRequest req) {
        Cause cause;
        if (authToken != null && authToken.getToken() != null && req.getParameter("token") != null) {
            // Optional additional cause text when starting via token
            String causeText = req.getParameter("cause");
            cause = new RemoteCause(req.getRemoteAddr(), causeText);
        } else {
            cause = new UserIdCause();
        }
        return new CauseAction(cause);
    }

    /**
     * Computes the delay by taking the default value and the override in the request parameter into the account.
     *
     * @deprecated as of 1.489
     *      Inject {@link TimeDuration}.
     */
    public int getDelay(StaplerRequest req) throws ServletException {
        String delay = req.getParameter("delay");
        if (delay==null)    return getQuietPeriod();

        try {
            // TODO: more unit handling
            if(delay.endsWith("sec"))   delay=delay.substring(0,delay.length()-3);
            if(delay.endsWith("secs"))  delay=delay.substring(0,delay.length()-4);
            return Integer.parseInt(delay);
        } catch (NumberFormatException e) {
            throw new ServletException("Invalid delay parameter value: "+delay);
        }
    }

    /**
     * Supports build trigger with parameters via an HTTP GET or POST.
     * Currently only String parameters are supported.
     */
    public void doBuildWithParameters(StaplerRequest req, StaplerResponse rsp, @QueryParameter TimeDuration delay) throws IOException, ServletException {
        BuildAuthorizationToken.checkPermission(this, authToken, req, rsp);

        ParametersDefinitionProperty pp = getProperty(ParametersDefinitionProperty.class);
        if (pp != null) {
            pp.buildWithParameters(req,rsp,delay);
        } else {
        	throw new IllegalStateException("This build is not parameterized!");
        }
    	
    }

    /** @deprecated use {@link #doBuildWithParameters(StaplerRequest, StaplerResponse, TimeDuration)} */
    @Deprecated
    public void doBuildWithParameters(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        doBuildWithParameters(req, rsp, TimeDuration.fromString(req.getParameter("delay")));
    }

    /**
     * Schedules a new SCM polling command.
     */
    public void doPolling( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        BuildAuthorizationToken.checkPermission(this, authToken, req, rsp);
        schedulePolling();
        rsp.sendRedirect(".");
    }

    /**
     * Cancels a scheduled build.
     */
    @RequirePOST
    public void doCancelQueue( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        checkPermission(ABORT);

        Jenkins.getInstance().getQueue().cancel(this);
        rsp.forwardToPreviousPage(req);
    }

    /**
     * Deletes this project.
     */
    @Override
    @RequirePOST
    public void doDoDelete(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException {
        delete();
        if (req == null || rsp == null)
            return;
        List<Ancestor> ancestors = req.getAncestors();
        ListIterator<Ancestor> it = ancestors.listIterator(ancestors.size());
        String url = getParent().getUrl(); // fallback but we ought to get to Jenkins.instance at the root
        while (it.hasPrevious()) {
            Object a = it.previous().getObject();
            if (a instanceof View) {
                url = ((View) a).getUrl();
                break;
            } else if (a instanceof ViewGroup) {
                url = ((ViewGroup) a).getUrl();
                break;
            }
        }
        rsp.sendRedirect2(req.getContextPath() + '/' + url);
    }
    
    @Override
    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, FormException {
        super.submit(req,rsp);
        JSONObject json = req.getSubmittedForm();

        makeDisabled(req.getParameter("disable")!=null);

        jdk = req.getParameter("jdk");
        if(req.getParameter("hasCustomQuietPeriod")!=null) {
            quietPeriod = Integer.parseInt(req.getParameter("quiet_period"));
        } else {
            quietPeriod = null;
        }
        if(req.getParameter("hasCustomScmCheckoutRetryCount")!=null) {
            scmCheckoutRetryCount = Integer.parseInt(req.getParameter("scmCheckoutRetryCount"));
        } else {
            scmCheckoutRetryCount = null;
        }
        blockBuildWhenDownstreamBuilding = req.getParameter("blockBuildWhenDownstreamBuilding")!=null;
        blockBuildWhenUpstreamBuilding = req.getParameter("blockBuildWhenUpstreamBuilding")!=null;

        if(req.hasParameter("customWorkspace")) {
            customWorkspace = Util.fixEmptyAndTrim(req.getParameter("customWorkspace.directory"));
        } else {
            customWorkspace = null;
        }

        if (json.has("scmCheckoutStrategy"))
            scmCheckoutStrategy = req.bindJSON(SCMCheckoutStrategy.class,
                json.getJSONObject("scmCheckoutStrategy"));
        else
            scmCheckoutStrategy = null;

        
        if(req.getParameter("hasSlaveAffinity")!=null) {
            assignedNode = Util.fixEmptyAndTrim(req.getParameter("_.assignedLabelString"));
        } else {
            assignedNode = null;
        }
        canRoam = assignedNode==null;

        concurrentBuild = req.getSubmittedForm().has("concurrentBuild");

        authToken = BuildAuthorizationToken.create(req);

        setScm(SCMS.parseSCM(req,this));

        for (Trigger t : triggers())
            t.stop();
        triggers.replaceBy(buildDescribable(req, Trigger.for_(this)));
        for (Trigger t : triggers())
            t.start(this,true);

        for (Publisher _t : Descriptor.newInstancesFromHeteroList(req, json, "publisher", Jenkins.getInstance().getExtensionList(BuildTrigger.DescriptorImpl.class))) {
            BuildTrigger t = (BuildTrigger) _t;
            for (AbstractProject downstream : t.getChildProjects(this)) {
                downstream.checkPermission(BUILD);
            }
        }
    }

    /**
     * @deprecated
     *      As of 1.261. Use {@link #buildDescribable(StaplerRequest, List)} instead.
     */
    protected final <T extends Describable<T>> List<T> buildDescribable(StaplerRequest req, List<? extends Descriptor<T>> descriptors, String prefix) throws FormException, ServletException {
        return buildDescribable(req,descriptors);
    }

    protected final <T extends Describable<T>> List<T> buildDescribable(StaplerRequest req, List<? extends Descriptor<T>> descriptors)
        throws FormException, ServletException {

        JSONObject data = req.getSubmittedForm();
        List<T> r = new Vector<T>();
        for (Descriptor<T> d : descriptors) {
            String safeName = d.getJsonSafeClassName();
            if (req.getParameter(safeName) != null) {
                T instance = d.newInstance(req, data.getJSONObject(safeName));
                r.add(instance);
            }
        }
        return r;
    }

    public ContextMenu doChildrenContextMenu(StaplerRequest request, StaplerResponse response) throws Exception {
        // not sure what would be really useful here. This needs more thoughts.
        // for the time being, I'm starting with permalinks
        ContextMenu menu = new ContextMenu();
        for (Permalink p : getPermalinks()) {
            if (p.resolve(this)!=null)
                menu.add(p.getId(),p.getDisplayName());
        }
        return menu;
    }

    /**
     * Serves the workspace files.
     */
    public DirectoryBrowserSupport doWs( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, InterruptedException {
        checkPermission(AbstractProject.WORKSPACE);
        FilePath ws = getSomeWorkspace();
        if ((ws == null) || (!ws.exists())) {
            // if there's no workspace, report a nice error message
            // Would be good if when asked for *plain*, do something else!
            // (E.g. return 404, or send empty doc.)
            // Not critical; client can just check if content type is not text/plain,
            // which also serves to detect old versions of Hudson.
            req.getView(this,"noWorkspace.jelly").forward(req,rsp);
            return null;
        } else {
            return new DirectoryBrowserSupport(this, ws, getDisplayName()+" workspace", "folder.png", true);
        }
    }

    /**
     * Wipes out the workspace.
     */
    public HttpResponse doDoWipeOutWorkspace() throws IOException, ServletException, InterruptedException {
        checkPermission(Functions.isWipeOutPermissionEnabled() ? WIPEOUT : BUILD);
        R b = getSomeBuildWithWorkspace();
        FilePath ws = b!=null ? b.getWorkspace() : null;
        if (ws!=null && getScm().processWorkspaceBeforeDeletion(this, ws, b.getBuiltOn())) {
            ws.deleteRecursive();
            for (WorkspaceListener wl : WorkspaceListener.all()) {
                wl.afterDelete(this);
            }
            return new HttpRedirect(".");
        } else {
            // If we get here, that means the SCM blocked the workspace deletion.
            return new ForwardToView(this,"wipeOutWorkspaceBlocked.jelly");
        }
    }

    @CLIMethod(name="disable-job")
    @RequirePOST
    public HttpResponse doDisable() throws IOException, ServletException {
        checkPermission(CONFIGURE);
        makeDisabled(true);
        return new HttpRedirect(".");
    }

    @CLIMethod(name="enable-job")
    @RequirePOST
    public HttpResponse doEnable() throws IOException, ServletException {
        checkPermission(CONFIGURE);
        makeDisabled(false);
        return new HttpRedirect(".");
    }

    /**
     * RSS feed for changes in this project.
     */
    public void doRssChangelog(  StaplerRequest req, StaplerResponse rsp  ) throws IOException, ServletException {
        class FeedItem {
            ChangeLogSet.Entry e;
            int idx;

            public FeedItem(Entry e, int idx) {
                this.e = e;
                this.idx = idx;
            }

            AbstractBuild<?,?> getBuild() {
                return e.getParent().build;
            }
        }

        List<FeedItem> entries = new ArrayList<FeedItem>();

        for(R r=getLastBuild(); r!=null; r=r.getPreviousBuild()) {
            int idx=0;
            for( ChangeLogSet.Entry e : r.getChangeSet())
                entries.add(new FeedItem(e,idx++));
        }

        RSS.forwardToRss(
            getDisplayName()+' '+getScm().getDescriptor().getDisplayName()+" changes",
            getUrl()+"changes",
            entries, new FeedAdapter<FeedItem>() {
                public String getEntryTitle(FeedItem item) {
                    return "#"+item.getBuild().number+' '+item.e.getMsg()+" ("+item.e.getAuthor()+")";
                }

                public String getEntryUrl(FeedItem item) {
                    return item.getBuild().getUrl()+"changes#detail"+item.idx;
                }

                public String getEntryID(FeedItem item) {
                    return getEntryUrl(item);
                }

                public String getEntryDescription(FeedItem item) {
                    StringBuilder buf = new StringBuilder();
                    for(String path : item.e.getAffectedPaths())
                        buf.append(path).append('\n');
                    return buf.toString();
                }

                public Calendar getEntryTimestamp(FeedItem item) {
                    return item.getBuild().getTimestamp();
                }

                public String getEntryAuthor(FeedItem entry) {
                    return JenkinsLocationConfiguration.get().getAdminAddress();
                }
            },
            req, rsp );
    }

    /**
     * {@link AbstractProject} subtypes should implement this base class as a descriptor.
     *
     * @since 1.294
     */
    public static abstract class AbstractProjectDescriptor extends TopLevelItemDescriptor {
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

        public FormValidation doCheckAssignedLabelString(@QueryParameter String value) {
            if (Util.fixEmpty(value)==null)
                return FormValidation.ok(); // nothing typed yet
            try {
                Label.parseExpression(value);
            } catch (ANTLRException e) {
                return FormValidation.error(e,
                        Messages.AbstractProject_AssignedLabelString_InvalidBooleanExpression(e.getMessage()));
            }
            Label l = Jenkins.getInstance().getLabel(value);
            if (l.isEmpty()) {
                for (LabelAtom a : l.listAtoms()) {
                    if (a.isEmpty()) {
                        LabelAtom nearest = LabelAtom.findNearest(a.getName());
                        return FormValidation.warning(Messages.AbstractProject_AssignedLabelString_NoMatch_DidYouMean(a.getName(),nearest.getDisplayName()));
                    }
                }
                return FormValidation.warning(Messages.AbstractProject_AssignedLabelString_NoMatch());
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckCustomWorkspace(@QueryParameter(value="customWorkspace.directory") String customWorkspace){
        	if(Util.fixEmptyAndTrim(customWorkspace)==null)
        		return FormValidation.error(Messages.AbstractProject_CustomWorkspaceEmpty());
        	else
        		return FormValidation.ok();
        }
        
        public AutoCompletionCandidates doAutoCompleteUpstreamProjects(@QueryParameter String value) {
            AutoCompletionCandidates candidates = new AutoCompletionCandidates();
            List<Job> jobs = Jenkins.getInstance().getItems(Job.class);
            for (Job job: jobs) {
                if (job.getFullName().startsWith(value)) {
                    if (job.hasPermission(Item.READ)) {
                        candidates.add(job.getFullName());
                    }
                }
            }
            return candidates;
        }

        public AutoCompletionCandidates doAutoCompleteAssignedLabelString(@QueryParameter String value) {
            AutoCompletionCandidates c = new AutoCompletionCandidates();
            Set<Label> labels = Jenkins.getInstance().getLabels();
            List<String> queries = new AutoCompleteSeeder(value).getSeeds();

            for (String term : queries) {
                for (Label l : labels) {
                    if (l.getName().startsWith(term)) {
                        c.add(l.getName());
                    }
                }
            }
            return c;
        }

        public List<SCMCheckoutStrategyDescriptor> getApplicableSCMCheckoutStrategyDescriptors(AbstractProject p) {
            return SCMCheckoutStrategyDescriptor._for(p);
        }

        /**
        * Utility class for taking the current input value and computing a list
        * of potential terms to match against the list of defined labels.
         */
        static class AutoCompleteSeeder {
            private String source;

            AutoCompleteSeeder(String source) {
                this.source = source;
            }

            List<String> getSeeds() {
                ArrayList<String> terms = new ArrayList<String>();
                boolean trailingQuote = source.endsWith("\"");
                boolean leadingQuote = source.startsWith("\"");
                boolean trailingSpace = source.endsWith(" ");

                if (trailingQuote || (trailingSpace && !leadingQuote)) {
                    terms.add("");
                } else {
                    if (leadingQuote) {
                        int quote = source.lastIndexOf('"');
                        if (quote == 0) {
                            terms.add(source.substring(1));
                        } else {
                            terms.add("");
                        }
                    } else {
                        int space = source.lastIndexOf(' ');
                        if (space > -1) {
                            terms.add(source.substring(space+1));
                        } else {
                            terms.add(source);
                        }
                    }
                }

                return terms;
            }
        }
    }

    /**
     * Finds a {@link AbstractProject} that has the name closest to the given name.
     */
    public static AbstractProject findNearest(String name) {
        return findNearest(name,Hudson.getInstance());
    }

    /**
     * Finds a {@link AbstractProject} whose name (when referenced from the specified context) is closest to the given name.
     *
     * @since 1.419
     */
    public static AbstractProject findNearest(String name, ItemGroup context) {
        List<AbstractProject> projects = Hudson.getInstance().getAllItems(AbstractProject.class);
        String[] names = new String[projects.size()];
        for( int i=0; i<projects.size(); i++ )
            names[i] = projects.get(i).getRelativeNameFrom(context);

        String nearest = EditDistance.findNearest(name, names);
        return (AbstractProject)Jenkins.getInstance().getItem(nearest,context);
    }

    private static final Comparator<Integer> REVERSE_INTEGER_COMPARATOR = new Comparator<Integer>() {
        public int compare(Integer o1, Integer o2) {
            return o2-o1;
        }
    };

    private static final Logger LOGGER = Logger.getLogger(AbstractProject.class.getName());

    /**
     * Permission to abort a build
     */
    public static final Permission ABORT = CANCEL;

    /**
     * Replaceable "Build Now" text.
     */
    public static final Message<AbstractProject> BUILD_NOW_TEXT = new Message<AbstractProject>();

    /**
     * Used for CLI binding.
     */
    @CLIResolver
    public static AbstractProject resolveForCLI(
            @Argument(required=true,metaVar="NAME",usage="Job name") String name) throws CmdLineException {
        AbstractProject item = Jenkins.getInstance().getItemByFullName(name, AbstractProject.class);
        if (item==null)
            throw new CmdLineException(null,Messages.AbstractItem_NoSuchJobExists(name,AbstractProject.findNearest(name).getFullName()));
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
        this.customWorkspace= Util.fixEmptyAndTrim(customWorkspace);
        save();
    }
    
}
