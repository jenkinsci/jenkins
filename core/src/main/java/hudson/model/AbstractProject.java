/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Brian Westrich, Erik Ramfelt, Ertan Deniz, Jean-Baptiste Quenot, Luca Domenico Milanesio, R. Tyler Ballance, Stephen Connolly, Tom Huybrechts, id:cactusman
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
import hudson.CopyOnWrite;
import hudson.FeedAdapter;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.cli.declarative.CLIMethod;
import hudson.cli.declarative.CLIResolver;
import hudson.diagnosis.OldDataMonitor;
import hudson.slaves.WorkspaceList;
import hudson.model.Cause.LegacyCodeCause;
import hudson.model.Cause.UserCause;
import hudson.model.Cause.RemoteCause;
import hudson.model.Descriptor.FormException;
import hudson.model.Fingerprint.RangeSet;
import hudson.model.RunMap.Constructor;
import hudson.model.Queue.WaitingItem;
import hudson.model.Queue.Executable;
import hudson.model.queue.CauseOfBlockage;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import hudson.scm.SCMS;
import hudson.scm.PollingResult;
import hudson.scm.SCMRevisionState;
import static hudson.scm.PollingResult.NO_CHANGES;
import static hudson.scm.PollingResult.BUILD_NOW;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

import hudson.search.SearchIndexBuilder;
import hudson.security.Permission;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildTrigger;
import hudson.tasks.Mailer;
import hudson.tasks.Publisher;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.triggers.SCMTrigger;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.DescribableList;
import hudson.util.EditDistance;
import hudson.util.FormValidation;
import hudson.widgets.BuildHistoryWidget;
import hudson.widgets.HistoryWidget;
import net.sf.json.JSONObject;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.ForwardToView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base implementation of {@link Job}s that build software.
 *
 * For now this is primarily the common part of {@link Project} and MavenModule.
 *
 * @author Kohsuke Kawaguchi
 * @see AbstractBuild
 */
public abstract class AbstractProject<P extends AbstractProject<P,R>,R extends AbstractBuild<P,R>> extends Job<P,R> implements BuildableItem {

    /**
     * {@link SCM} associated with the project.
     * To allow derived classes to link {@link SCM} config to elsewhere,
     * access to this variable should always go through {@link #getScm()}.
     */
    private volatile SCM scm = new NullSCM();

    /**
     * State returned from {@link SCM#poll(AbstractProject, Launcher, FilePath, TaskListener, SCMRevisionState)}.
     */
    private volatile transient SCMRevisionState pollingBaseline = null;

    /**
     * All the builds keyed by their build number.
     */
    protected transient /*almost final*/ RunMap<R> builds = new RunMap<R>();

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
     * True to keep builds of this project in queue when upstream projects are
     * building. False by default to keep from breaking existing behavior.
     */
    protected volatile boolean blockBuildWhenUpstreamBuilding = false;

    /**
     * Identifies {@link JDK} to be used.
     * Null if no explicit configuration is required.
     *
     * <p>
     * Can't store {@link JDK} directly because {@link Hudson} and {@link Project}
     * are saved independently.
     *
     * @see Hudson#getJDK(String)
     */
    private volatile String jdk;

    /**
     * @deprecated since 2007-01-29.
     */
    private transient boolean enableRemoteTrigger;

    private volatile BuildAuthorizationToken authToken = null;

    /**
     * List of all {@link Trigger}s for this project.
     */
    protected List<Trigger<?>> triggers = new Vector<Trigger<?>>();

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

    protected AbstractProject(ItemGroup parent, String name) {
        super(parent,name);

        if(!Hudson.getInstance().getNodes().isEmpty()) {
            // if a new job is configured with Hudson that already has slave nodes
            // make it roamable by default
            canRoam = true;
        }
    }

    @Override
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);

        this.builds = new RunMap<R>();
        this.builds.load(this,new Constructor<R>() {
            public R create(File dir) throws IOException {
                return loadBuild(dir);
            }
        });

        // boolean! Can't tell if xml file contained false..
        if (enableRemoteTrigger) OldDataMonitor.report(this, "1.77");
        if(triggers==null) {
            // it didn't exist in < 1.28
            triggers = new Vector<Trigger<?>>();
            OldDataMonitor.report(this, "1.28");
        }
        for (Trigger t : triggers)
            t.start(this,false);
        if(scm==null)
            scm = new NullSCM(); // perhaps it was pointing to a plugin that no longer exists.

        if(transientActions==null)
            transientActions = new Vector<Action>();    // happens when loaded from disk
        updateTransientActions();
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
        return Hudson.CONCURRENT_BUILD && concurrentBuild;
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
            return Hudson.getInstance().getSelfLabel();
        return Hudson.getInstance().getLabel(assignedNode);
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
            if(l==Hudson.getInstance().getSelfLabel())  assignedNode = null;
            else                                        assignedNode = l.getName();
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
        return Messages.AbstractProject_Pronoun();
    }

    /**
     * Returns the root project value.
     *
     * @return the root project value.
     */
    public AbstractProject getRootProject() {
        if (this.getParent() instanceof Hudson) {
            return this;
        } else {
            return ((AbstractProject) this.getParent()).getRootProject();
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
        Executor e = Executor.currentExecutor();
        if(e!=null) {
            Executable exe = e.getCurrentExecutable();
            if (exe instanceof AbstractBuild) {
                AbstractBuild b = (AbstractBuild) exe;
                if(b.getProject()==this)
                    return b.getWorkspace();
            }
        }
        R lb = getLastBuild();
        if(lb!=null)    return lb.getWorkspace();
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
        return b!=null ? b.getWorkspace() : null;
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
        FilePath ws = getWorkspace();
        if(ws==null)    return null;
        return getScm().getModuleRoot(ws);
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
        return getScm().getModuleRoots(getWorkspace());
    }

    public int getQuietPeriod() {
        return quietPeriod!=null ? quietPeriod : Hudson.getInstance().getQuietPeriod();
    }
    
    public int getScmCheckoutRetryCount() {
        return scmCheckoutRetryCount !=null ? scmCheckoutRetryCount : Hudson.getInstance().getScmCheckoutRetryCount();
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
            Hudson.getInstance().getQueue().cancel(this);
        save();
    }

    @CLIMethod(name="disable-job")
    public void disable() throws IOException {
        makeDisabled(true);
    }

    @CLIMethod(name="enable-job")
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

    protected void updateTransientActions() {
        Vector<Action> ta = new Vector<Action>();

        for (JobProperty<? super P> p : properties)
            ta.addAll(p.getJobActions((P)this));

        for (TransientProjectActionFactory tpaf : TransientProjectActionFactory.all())
            ta.addAll(Util.fixNull(tpaf.createFor(this))); // be defensive against null

        transientActions = ta;
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
            upstream = new HashSet<AbstractProject>(Items.fromNameList(req.getParameter("upstreamProjects"),AbstractProject.class));
        }

        // dependency setting might have been changed by the user, so rebuild.
        Hudson.getInstance().rebuildDependencyGraph();

        // reflect the submission of the pseudo 'upstream build trriger'.
        // this needs to be done after we release the lock on 'this',
        // or otherwise we could dead-lock

        for (AbstractProject<?,?> p : Hudson.getInstance().getAllItems(AbstractProject.class)) {
            // Don't consider child projects such as MatrixConfiguration:
            if (!p.isConfigurable()) continue;
            boolean isUpstream = upstream.contains(p);
            synchronized(p) {
                // does 'p' include us in its BuildTrigger? 
                DescribableList<Publisher,Descriptor<Publisher>> pl = p.getPublishersList();
                BuildTrigger trigger = pl.get(BuildTrigger.class);
                List<AbstractProject> newChildProjects = trigger == null ? new ArrayList<AbstractProject>():trigger.getChildProjects();
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
                    // into the list.
                    // for us not to lose the data, we need to merge them all.
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
                            combinedChildren.addAll(bt.getChildProjects());
                        existing = new BuildTrigger(new ArrayList<AbstractProject>(combinedChildren),existingList.get(0).getThreshold());
                        pl.add(existing);
                        break;
                    }

                    if(existing!=null && existing.hasSame(newChildProjects))
                        continue;   // no need to touch
                    pl.replace(new BuildTrigger(newChildProjects,
                        existing==null?Result.SUCCESS:existing.getThreshold()));
                }
            }
        }

        // notify the queue as the project might be now tied to different node
        Hudson.getInstance().getQueue().scheduleMaintenance();

        // this is to reflect the upstream build adjustments done above
        Hudson.getInstance().rebuildDependencyGraph();
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
     *      true if the project is actually added to the queue.
     *      false if the queue contained it and therefore the add()
     *      was noop
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
    public Future<R> scheduleBuild2(int quietPeriod, Cause c, Action... actions) {
        if (!isBuildable())
            return null;

        List<Action> queueActions = new ArrayList<Action>(Arrays.asList(actions));
        if (isParameterized() && Util.filter(queueActions, ParametersAction.class).isEmpty()) {
            queueActions.add(new ParametersAction(getDefaultParametersValues()));
        }

        if (c != null) {
            queueActions.add(new CauseAction(c));
        }

        WaitingItem i = Hudson.getInstance().getQueue().schedule(this, quietPeriod, queueActions);
        if(i!=null)
            return (Future)i.getFuture();
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
    public Future<R> scheduleBuild2(int quietPeriod) {
        return scheduleBuild2(quietPeriod, new LegacyCodeCause());
    }
    
    /**
     * Schedules a build of this project, and returns a {@link Future} object
     * to wait for the completion of the build.
     */
    public Future<R> scheduleBuild2(int quietPeriod, Cause c) {
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
        return Hudson.getInstance().getQueue().contains(this);
    }

    @Override
    public Queue.Item getQueueItem() {
        return Hudson.getInstance().getQueue().getItem(this);
    }

    /**
     * Gets the JDK that this project is configured with, or null.
     */
    public JDK getJDK() {
        return Hudson.getInstance().getJDK(jdk);
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

    public SortedMap<Integer, ? extends R> _getRuns() {
        return builds.getView();
    }

    public void removeRun(R run) {
        this.builds.remove(run);
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
    public synchronized List<Action> getActions() {
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
     * Because the upstream build is in progress, and we are configured to wait for that.
     */
    public static class BecauseOfUpstreamBuildInProgress extends CauseOfBlockage {
        public final AbstractProject<?,?> up;

        public BecauseOfUpstreamBuildInProgress(AbstractProject<?,?> up) {
            this.up = up;
        }

        public String getShortDescription() {
            return Messages.AbstractProject_UpstreamBuildInProgress(up.getName());
        }
    }

    public CauseOfBlockage getCauseOfBlockage() {
        if (isBuilding() && !isConcurrentBuild())
            return new BecauseOfBuildInProgress(getLastBuild());
        if (blockBuildWhenUpstreamBuilding()) {
            AbstractProject<?,?> bup = getBuildingUpstream();
            if (bup!=null)
                return new BecauseOfUpstreamBuildInProgress(bup);
        }
        return null;
    }

    /**
     * Returns the project if any of the upstream project (or itself) is either
     * building or is in the queue.
     * <p>
     * This means eventually there will be an automatic triggering of
     * the given project (provided that all builds went smoothly.)
     */
    protected AbstractProject getBuildingUpstream() {
    	DependencyGraph graph = Hudson.getInstance().getDependencyGraph();
        Set<AbstractProject> tups = graph.getTransitiveUpstream(this);
        tups.add(this);
        for (AbstractProject tup : tups) {
            if(tup!=this && (tup.isBuilding() || tup.isInQueue()))
                return tup;
        }
        return null;
    }

    public final long getEstimatedDuration() {
        AbstractBuild b = getLastSuccessfulBuild();
        if(b==null)     return -1;

        long duration = b.getDuration();
        if(duration==0) return -1;

        return duration;
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
        workspace.mkdirs();
        
        boolean r = scm.checkout(build, launcher, workspace, listener, changelogFile);
        calcPollingBaseline(build, launcher, listener);
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
     * For reasons I don't understand, if I inline this method, AbstractMethodError escapes try/catch block.
     */
    private SCMRevisionState safeCalcRevisionsFromBuild(AbstractBuild build, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        return getScm()._calcRevisionsFromBuild(build, launcher, listener);
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
        if(scm==null) {
            listener.getLogger().println(Messages.AbstractProject_NoSCM());
            return NO_CHANGES;
        }
        if(isDisabled()) {
            listener.getLogger().println(Messages.AbstractProject_Disabled());
            return NO_CHANGES;
        }

        R lb = getLastBuild();
        if (lb==null) {
            listener.getLogger().println(Messages.AbstractProject_NoBuilds());
            return BUILD_NOW;
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
            if(scm.requiresWorkspaceForPolling()) {
                // lock the workspace of the last build
                FilePath ws=lb.getWorkspace();

                if (ws==null || !ws.exists()) {
                    // workspace offline. build now, or nothing will ever be built
                    Label label = getAssignedLabel();
                    if (label != null && label.isSelfLabel()) {
                        // if the build is fixed on a node, then attempting a build will do us
                        // no good. We should just wait for the slave to come back.
                        listener.getLogger().println(Messages.AbstractProject_NoWorkspace());
                        return NO_CHANGES;
                    }
                    listener.getLogger().println( ws==null
                        ? Messages.AbstractProject_WorkspaceOffline()
                        : Messages.AbstractProject_NoWorkspace());
                    listener.getLogger().println(Messages.AbstractProject_NewBuildForWorkspace());
                    return BUILD_NOW;
                } else {
                    WorkspaceList l = lb.getBuiltOn().toComputer().getWorkspaceList();
                    // if doing non-concurrent build, acquire a workspace in a way that causes builds to block for this workspace.
                    // this prevents multiple workspaces of the same job --- the behavior of Hudson < 1.319.
                    //
                    // OTOH, if a concurrent build is chosen, the user is willing to create a multiple workspace,
                    // so better throughput is achieved over time (modulo the initial cost of creating that many workspaces)
                    // by having multiple workspaces
                    WorkspaceList.Lease lease = l.acquire(ws, !concurrentBuild);
                    Launcher launcher = ws.createLauncher(listener);
                    try {
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
            } else {
                // polling without workspace
                LOGGER.fine("Polling SCM changes of " + getName());

                if (pollingBaseline==null) // see NOTE-NO-BASELINE above
                    calcPollingBaseline(lb,null,listener);
                PollingResult r = scm.poll(this, null, null, listener, pollingBaseline);
                pollingBaseline = r.remote;
                return r;
            }
        } catch (AbortException e) {
            listener.fatalError(Messages.AbstractProject_Aborted());
            LOGGER.log(Level.FINE, "Polling "+this+" aborted",e);
            return NO_CHANGES;
        } catch (IOException e) {
            e.printStackTrace(listener.fatalError(e.getMessage()));
            return NO_CHANGES;
        } catch (InterruptedException e) {
            e.printStackTrace(listener.fatalError(Messages.AbstractProject_PollingABorted()));
            return NO_CHANGES;
        }
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
        addToList(trigger,triggers);
    }

    public void removeTrigger(TriggerDescriptor trigger) throws IOException {
        removeFromList(trigger,triggers);
    }

    protected final synchronized <T extends Describable<T>>
    void addToList( T item, List<T> collection ) throws IOException {
        for( int i=0; i<collection.size(); i++ ) {
            if(collection.get(i).getDescriptor()==item.getDescriptor()) {
                // replace
                collection.set(i,item);
                save();
                return;
            }
        }
        // add
        collection.add(item);
        save();
        updateTransientActions();
    }

    protected final synchronized <T extends Describable<T>>
    void removeFromList(Descriptor<T> item, List<T> collection) throws IOException {
        for( int i=0; i< collection.size(); i++ ) {
            if(collection.get(i).getDescriptor()==item) {
                // found it
                collection.remove(i);
                save();
                updateTransientActions();
                return;
            }
        }
    }

    public synchronized Map<TriggerDescriptor,Trigger> getTriggers() {
        return (Map)Descriptor.toMap(triggers);
    }

    /**
     * Gets the specific trigger, or null if the propert is not configured for this job.
     */
    public <T extends Trigger> T getTrigger(Class<T> clazz) {
        for (Trigger p : triggers) {
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
        return Hudson.getInstance().getDependencyGraph().getDownstream(this);
    }

    @Exported
    public final List<AbstractProject> getUpstreamProjects() {
        return Hudson.getInstance().getDependencyGraph().getUpstream(this);
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
                if (buildTrigger.getChildProjects().contains(this))
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
        return Hudson.getInstance().getDependencyGraph().getTransitiveUpstream(this);
    }

    /**
     * Gets all the downstream projects including transitive downstream projects.
     *
     * @since 1.138
     */
    public final Set<AbstractProject> getTransitiveDownstreamProjects() {
        return Hudson.getInstance().getDependencyGraph().getTransitiveDownstream(this);
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
        if(isBuildable() && hasPermission(Hudson.ADMINISTER))
            sib.add("build","build");
        return sib;
    }

    @Override
    protected HistoryWidget createHistoryWidget() {
        return new BuildHistoryWidget<R>(this,getBuilds(),HISTORY_ADAPTER);
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
    public void doBuild( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        BuildAuthorizationToken.checkPermission(this, authToken, req, rsp);

        // if a build is parameterized, let that take over
        ParametersDefinitionProperty pp = getProperty(ParametersDefinitionProperty.class);
        if (pp != null) {
            pp._doBuild(req,rsp);
            return;
        }

        Cause cause;
        if (authToken != null && authToken.getToken() != null && req.getParameter("token") != null) {
            // Optional additional cause text when starting via token
            String causeText = req.getParameter("cause");
            cause = new RemoteCause(req.getRemoteAddr(), causeText);
        } else {
            cause = new UserCause();
        }

        if (!isBuildable())
            throw HttpResponses.error(SC_INTERNAL_SERVER_ERROR,new IOException(getFullName()+" is not buildable"));

        Hudson.getInstance().getQueue().schedule(this, getDelay(req), new CauseAction(cause));
        rsp.forwardToPreviousPage(req);
    }

    /**
     * Computes the delay by taking the default value and the override in the request parameter into the account.
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
    public void doBuildWithParameters(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        BuildAuthorizationToken.checkPermission(this, authToken, req, rsp);

        ParametersDefinitionProperty pp = getProperty(ParametersDefinitionProperty.class);
        if (pp != null) {
            pp.buildWithParameters(req,rsp);
        } else {
        	throw new IllegalStateException("This build is not parameterized!");
        }
    	
    }

    /**
     * Schedules a new SCM polling command.
     */
    public void doPolling( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        BuildAuthorizationToken.checkPermission(this, authToken, req, rsp);
        schedulePolling();
        rsp.forwardToPreviousPage(req);
    }

    /**
     * Cancels a scheduled build.
     */
    public void doCancelQueue( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        checkPermission(BUILD);

        Hudson.getInstance().getQueue().cancel(this);
        rsp.forwardToPreviousPage(req);
    }

    @Override
    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, FormException {
        super.submit(req,rsp);

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
        blockBuildWhenUpstreamBuilding = req.getParameter("blockBuildWhenUpstreamBuilding")!=null;

        if(req.getParameter("hasSlaveAffinity")!=null) {
            canRoam = false;
            assignedNode = req.getParameter("slave");
            if(assignedNode !=null) {
                if(Hudson.getInstance().getLabel(assignedNode).isEmpty())
                    assignedNode = null;   // no such label
            }
        } else {
            canRoam = true;
            assignedNode = null;
        }

        concurrentBuild = req.getSubmittedForm().has("concurrentBuild");

        authToken = BuildAuthorizationToken.create(req);

        setScm(SCMS.parseSCM(req,this));

        for (Trigger t : triggers)
            t.stop();
        triggers = buildDescribable(req, Trigger.for_(this));
        for (Trigger t : triggers)
            t.start(this,true);
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
            return new DirectoryBrowserSupport(this, ws, getDisplayName()+" workspace", "folder.gif", true);
        }
    }

    /**
     * Wipes out the workspace.
     */
    public HttpResponse doDoWipeOutWorkspace() throws IOException, ServletException, InterruptedException {
        checkPermission(BUILD);
        R b = getSomeBuildWithWorkspace();
        FilePath ws = b!=null ? b.getWorkspace() : null;
        if (ws!=null && getScm().processWorkspaceBeforeDeletion(this, ws, b.getBuiltOn())) {
            ws.deleteRecursive();
            return new HttpRedirect(".");
        } else {
            // If we get here, that means the SCM blocked the workspace deletion.
            return new ForwardToView(this,"wipeOutWorkspaceBlocked.jelly");
        }
    }

    public HttpResponse doDisable() throws IOException, ServletException {
        requirePOST();
        checkPermission(CONFIGURE);
        makeDisabled(true);
        return new HttpRedirect(".");
    }

    public HttpResponse doEnable() throws IOException, ServletException {
        requirePOST();
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
                    return Mailer.descriptor().getAdminAddress();
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
    }

    /**
     * Finds a {@link AbstractProject} that has the name closest to the given name.
     */
    public static AbstractProject findNearest(String name) {
        List<AbstractProject> projects = Hudson.getInstance().getItems(AbstractProject.class);
        String[] names = new String[projects.size()];
        for( int i=0; i<projects.size(); i++ )
            names[i] = projects.get(i).getName();

        String nearest = EditDistance.findNearest(name, names);
        return (AbstractProject)Hudson.getInstance().getItem(nearest);
    }

    private static final Comparator<Integer> REVERSE_INTEGER_COMPARATOR = new Comparator<Integer>() {
        public int compare(Integer o1, Integer o2) {
            return o2-o1;
        }
    };

    private static final Logger LOGGER = Logger.getLogger(AbstractProject.class.getName());

    /**
     * Permission to abort a build. For now, let's make it the same as {@link #BUILD}
     */
    public static final Permission ABORT = BUILD;

    /**
     * Used for CLI binding.
     */
    @CLIResolver
    public static AbstractProject resolveForCLI(
            @Argument(required=true,metaVar="NAME",usage="Job name") String name) throws CmdLineException {
        AbstractProject item = Hudson.getInstance().getItemByFullName(name, AbstractProject.class);
        if (item==null)
            throw new CmdLineException(null,Messages.AbstractItem_NoSuchJobExists(name,AbstractProject.findNearest(name).getFullName()));
        return item;
    }
}
