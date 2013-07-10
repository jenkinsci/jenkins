/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts
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
package hudson.matrix;

import hudson.EnvVars;
import hudson.Util;
import hudson.model.Action;
import hudson.model.Executor;
import hudson.model.InvisibleAction;
import hudson.model.Node;
import hudson.model.Queue.QueueAction;
import hudson.model.TaskListener;
import hudson.util.AlternativeUiTextProvider;
import hudson.util.DescribableList;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.DependencyGraph;
import hudson.model.Descriptor;
import jenkins.model.BuildDiscarder;
import jenkins.model.Jenkins;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.JDK;
import hudson.model.Label;
import hudson.model.ParametersAction;
import hudson.model.Project;
import hudson.model.SCMedItem;
import hudson.model.Queue.NonBlockingTask;
import hudson.model.Cause.LegacyCodeCause;
import hudson.scm.SCM;
import jenkins.scm.SCMCheckoutStrategy;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Builder;
import hudson.tasks.LogRotator;
import hudson.tasks.Publisher;

import java.io.IOException;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One configuration of {@link MatrixProject}.
 *
 * @author Kohsuke Kawaguchi
 */
public class MatrixConfiguration extends Project<MatrixConfiguration,MatrixRun> implements SCMedItem, NonBlockingTask {
    /**
     * The actual value combination.
     */
    private transient /*final*/ Combination combination;

    /**
     * Hash value of {@link #combination}. Cached for efficiency.
     */
    private transient String digestName;

    public MatrixConfiguration(MatrixProject parent, Combination c) {
        super(parent,c.toString());
        setCombination(c);
    }

    @Override
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        // directory name is not a name for us --- it's taken from the combination name
        super.onLoad(parent, combination.toString());
    }

    @Override
    public EnvVars getEnvironment(Node node, TaskListener listener) throws IOException, InterruptedException {
        EnvVars env =  super.getEnvironment(node, listener);

        AxisList axes = getParent().getAxes();
        for (Map.Entry<String,String> e : getCombination().entrySet()) {
            Axis a = axes.find(e.getKey());
            if (a!=null)
                a.addBuildVariable(e.getValue(),env); // TODO: hijacking addBuildVariable but perhaps we need addEnvVar?
            else
                env.put(e.getKey(), e.getValue());
        }

        return env;
    }

    @Override
    protected void updateTransientActions(){
        // This method is exactly the same as in {@link #AbstractProject}. 
        // Enabling to call this method from MatrixProject is the only reason for overriding.
        super.updateTransientActions();
    }

    @Override
    public boolean isConcurrentBuild() {
        return getParent().isConcurrentBuild();
    }

    @Override
    public void setConcurrentBuild(boolean b) throws IOException {
        throw new UnsupportedOperationException("The setting can be only changed at MatrixProject");
    }

    /**
     * Used during loading to set the combination back.
     */
    /*package*/ void setCombination(Combination c) {
        this.combination = c;
        this.digestName = c.digest().substring(0,8);
    }

    /**
     * Build numbers are always synchronized with the parent.
     *
     * <p>
     * Computing this is bit tricky. Several considerations:
     *
     * <ol>
     * <li>A new configuration build #N is started while the parent build #N is building,
     *     and when that happens we want to return N.
     * <li>But the configuration build #N is done before the parent build #N finishes,
     *     and when that happens we want to return N+1 because that's going to be the next one.
     * <li>Configuration builds might skip some numbers if the parent build is aborted
     *     before this configuration is built.
     * <li>If nothing is building right now and the last build of the parent is #N,
     *     then we want to return N+1.
     * </ol>
     */
    @Override
    public int getNextBuildNumber() {
        AbstractBuild<?,?> lb = getParent().getLastBuild();

        while (lb!=null && lb.isBuilding()) {
            lb = lb.getPreviousBuild();
        }
        if(lb==null)    return 0;

        int n=lb.getNumber()+1;

        lb = getLastBuild();
        if(lb!=null)
            n = Math.max(n,lb.getNumber()+1);

        return n;
    }

    @Override
    public int assignBuildNumber() throws IOException {
        int nb = getNextBuildNumber();
        MatrixRun r = getLastBuild();
        if(r!=null && r.getNumber()>=nb) // make sure we don't schedule the same build twice
            throw new IllegalStateException("Build #"+nb+" is already completed");
        return nb;
    }

    @Override
    public String getDisplayName() {
        return combination.toCompactString(getParent().getAxes());
    }

    @Override
    public MatrixProject getParent() {
        return (MatrixProject)super.getParent();
    }

    /**
     * Get the actual combination of the axes values for this {@link MatrixConfiguration}
     */
    public Combination getCombination() {
        return combination;
    }

    /**
     * Since {@link MatrixConfiguration} is always invoked from {@link MatrixRun}
     * once and just once, there's no point in having a quiet period.
     */
    @Override
    public int getQuietPeriod() {
        return 0;
    }

    /**
     * Inherit the value from the parent.
     */
    @Override
    public int getScmCheckoutRetryCount() {
        return getParent().getScmCheckoutRetryCount();
    }

    /**
     * Inherit the value from the parent.
     */
    @Override
    public SCMCheckoutStrategy getScmCheckoutStrategy() {
        return getParent().getScmCheckoutStrategy();
    }
 
    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    protected Class<MatrixRun> getBuildClass() {
        return MatrixRun.class;
    }

    @Override
    protected MatrixRun newBuild() throws IOException {
        List<Action> actions = Executor.currentExecutor().getCurrentWorkUnit().context.actions;
        MatrixBuild lb = getParent().getLastBuild();
        for (Action a : actions) {
            if (a instanceof ParentBuildAction) {
                MatrixBuild _lb = ((ParentBuildAction) a).parent;
                if (_lb != null) {
                    lb = _lb;
                }
            }
        }

        if (lb == null) {
            throw new IOException("cannot start a build of " + getFullName() + " since its parent has no builds at all");
        }

        // for every MatrixRun there should be a parent MatrixBuild
        MatrixRun lastBuild = new MatrixRun(this, lb.getTimestamp());

        lastBuild.number = lb.getNumber();

        builds.put(lastBuild);
        return lastBuild;
    }

    @Override
    protected void buildDependencyGraph(DependencyGraph graph) {
    }

    @Override
    public MatrixConfiguration asProject() {
        return this;
    }

    @Override
    public Label getAssignedLabel() {
        // combine all the label axes by &&.
    	String expr;
        String exprSlave = Util.join(combination.values(getParent().getAxes().subList(LabelAxis.class)), "&&");
        String exprLabel = Util.join(combination.values(getParent().getAxes().subList(LabelExpAxis.class)), "&&");
        if(!exprSlave.equals("") && !exprLabel.equals("")){
        	expr = exprSlave + "&&" + exprLabel;
        } else{
        	expr = (exprSlave.equals("")) ? exprLabel : exprSlave;
        }
        return Jenkins.getInstance().getLabel(Util.fixEmpty(expr));
    }

    @Override
    public String getPronoun() {
        return AlternativeUiTextProvider.get(PRONOUN, this, Messages.MatrixConfiguration_Pronoun());
    }

    @Override
    public JDK getJDK() {
        return Jenkins.getInstance().getJDK(combination.get("jdk"));
    }

//
// inherit build setting from the parent project
//
    @Override
    public List<Builder> getBuilders() {
        return getParent().getBuilders();
    }

    @Override
    public Map<Descriptor<Publisher>, Publisher> getPublishers() {
        return getParent().getPublishers();
    }

    @Override
    public DescribableList<Builder, Descriptor<Builder>> getBuildersList() {
        return getParent().getBuildersList();
    }

    @Override
    public DescribableList<Publisher, Descriptor<Publisher>> getPublishersList() {
        return getParent().getPublishersList();
    }

    @Override
    public Map<Descriptor<BuildWrapper>, BuildWrapper> getBuildWrappers() {
        return getParent().getBuildWrappers();
    }

    @Override
    public DescribableList<BuildWrapper, Descriptor<BuildWrapper>> getBuildWrappersList() {
        return getParent().getBuildWrappersList();
    }

    @Override
    public Publisher getPublisher(Descriptor<Publisher> descriptor) {
        return getParent().getPublisher(descriptor);
    }

    @Override
    public BuildDiscarder getBuildDiscarder() {
        // TODO: LinkedLogRotator doesn't work very well in the face of pluggable BuildDiscarder but I don't know what to do
        BuildDiscarder bd = getParent().getBuildDiscarder();
        if (bd instanceof LogRotator) {
            LogRotator lr = (LogRotator) bd;
            return new LinkedLogRotator(lr.getArtifactDaysToKeep(),lr.getArtifactNumToKeep());
        }
        return new LinkedLogRotator();
    }

    @Override
    public SCM getScm() {
        return getParent().getScm();
    }

    /*package*/ String getDigestName() {
        return digestName;
    }

    /**
     * JDK cannot be set on {@link MatrixConfiguration} because
     * it's controlled by {@link MatrixProject}.
     * @deprecated
     *      Not supported.
     */
    @Override
    public void setJDK(JDK jdk) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated
     *      Value is controlled by {@link MatrixProject}.
     */
    @Override
    public void setBuildDiscarder(BuildDiscarder logRotator) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns true if this configuration is a configuration
     * currently in use today (as opposed to the ones that are
     * there only to keep the past record.) 
     *
     * @see MatrixProject#getActiveConfigurations()
     */
    public boolean isActiveConfiguration() {
        return getParent().getActiveConfigurations().contains(this);
    }

    /**
     * On Cygwin, path names cannot be longer than 256 chars.
     * See http://cygwin.com/ml/cygwin/2005-04/msg00395.html and
     * http://www.nabble.com/Windows-Filename-too-long-errors-t3161089.html for
     * the background of this issue. Setting this flag to true would
     * cause Jenkins to use cryptic but short path name, giving more room for
     * jobs to use longer path names.
     */
    public static boolean useShortWorkspaceName = Boolean.getBoolean(MatrixConfiguration.class.getName()+".useShortWorkspaceName");

	/**
	 * @deprecated
	 *    Use {@link #scheduleBuild(ParametersAction, Cause)}.  Since 1.283
	 */
    public boolean scheduleBuild(ParametersAction parameters) {
    	return scheduleBuild(parameters, new LegacyCodeCause());
    }

    /** Starts the build with the ParametersAction that are passed in.
     *
     * @param parameters
     *      Can be null.
     * @deprecated
	 *    Use {@link #scheduleBuild(List, Cause)}.  Since 1.480
     */
    public boolean scheduleBuild(ParametersAction parameters, Cause c) {
        return scheduleBuild(Collections.singletonList(parameters), c);
    }
    /**
     * Starts the build with the actions that are passed in.
     *
     * @param actions   Can be null.
     * @param c     Reason for starting the build
     */
    public boolean scheduleBuild(List<? extends Action> actions, Cause c) {
        List<Action> allActions = new ArrayList<Action>();

        if(actions != null)
            allActions.addAll(actions);

        allActions.add(new ParentBuildAction());
        allActions.add(new CauseAction(c));

        return Jenkins.getInstance().getQueue().schedule2(this, getQuietPeriod(), allActions ).isAccepted();
    }

    /**
     *
     */
    public static class ParentBuildAction extends InvisibleAction implements QueueAction {
        public transient MatrixBuild parent = (MatrixBuild)Executor.currentExecutor().getCurrentExecutable();
        public boolean shouldSchedule(List<Action> actions) {
            return true;
        }
    }
}
