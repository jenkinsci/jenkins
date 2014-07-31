/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Olivier Lamy
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
package hudson.maven;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.maven.reporters.MavenArtifactRecord;
import hudson.maven.reporters.SurefireArchiver;
import hudson.maven.reporters.TestFailureDetector;
import hudson.slaves.WorkspaceList;
import hudson.slaves.WorkspaceList.Lease;
import hudson.maven.agent.AbortException;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Environment;
import hudson.model.Executor;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Maven.MavenInstallation;
import hudson.tasks.Publisher;
import hudson.util.ArgumentListBuilder;
import hudson.util.DescribableList;
import org.apache.maven.BuildFailureException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ReactorManager;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.monitor.event.EventDispatcher;
import org.apache.maven.project.MavenProject;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import jenkins.model.ArtifactManager;

import jenkins.mvn.SettingsProvider;

/**
 * {@link Run} for {@link MavenModule}.
 * 
 * @author Kohsuke Kawaguchi
 */
@SuppressWarnings("deprecation") // as we're restricted to Maven 2.x API here, but compile against Maven 3.x we cannot avoid deprecations
public class MavenBuild extends AbstractMavenBuild<MavenModule,MavenBuild> {
    /**
     * {@link MavenReporter}s that will contribute project actions.
     * Can be null if there's none.
     */
    /*package*/ List<MavenProjectActionBuilder> projectActionReporters;

    /**
     * {@link ExecutedMojo}s that record what was run.
     * Null until some time before the build completes,
     * or if this build is performed in earlier versions of Hudson.
     * @since 1.98.
     */
    private List<ExecutedMojo> executedMojos;
    
    public MavenBuild(MavenModule job) throws IOException {
        super(job);
    }

    public MavenBuild(MavenModule job, Calendar timestamp) {
        super(job, timestamp);
    }

    public MavenBuild(MavenModule project, File buildDir) throws IOException {
        super(project, buildDir);
        SurefireArchiver.fixUp(projectActionReporters);
    }

    @Override
    public String getUpUrl() {
        StaplerRequest req = Stapler.getCurrentRequest();
        if(req!=null) {
            List<Ancestor> ancs = req.getAncestors();
            for( int i=1; i<ancs.size(); i++) {
                if(ancs.get(i).getObject()==this) {
                    if(ancs.get(i-1).getObject() instanceof MavenModuleSetBuild) {
                        // if under MavenModuleSetBuild, "up" means MMSB
                        return ancs.get(i-1).getUrl()+'/';
                    }
                }
            }
        }
        return super.getUpUrl();
    }

    @Override
    public String getDisplayName() {
        StaplerRequest req = Stapler.getCurrentRequest();
        if(req!=null) {
            List<Ancestor> ancs = req.getAncestors();
            for( int i=1; i<ancs.size(); i++) {
                if(ancs.get(i).getObject()==this) {
                    if(ancs.get(i-1).getObject() instanceof MavenModuleSetBuild) {
                        // if under MavenModuleSetBuild, display the module name
                        return getParent().getDisplayName();
                    }
                }
            }
        }
        return super.getDisplayName();
    }

    /**
     * Gets the {@link MavenModuleSetBuild} that has the same build number.
     *
     * @return
     *      null if no such build exists, which happens when the module build
     *      is manually triggered.
     * @see #getModuleSetBuild()
     */
    public @CheckForNull MavenModuleSetBuild getParentBuild() {
        return getParent().getParent().getBuildByNumber(getNumber());
    }

    /**
     * The same as {@link #getParentBuild()}.
     */
    @Override
    public AbstractBuild<?, ?> getRootBuild() {
        return getParentBuild();
    }

    /**
     * Gets the "governing" {@link MavenModuleSet} that has set
     * the workspace for this build.
     *
     * @return
     *      null if no such build exists, which happens if the build
     *      is manually removed.
     * @see #getParentBuild()
     */
    public @CheckForNull MavenModuleSetBuild getModuleSetBuild() {
        return getParent().getParent().getNearestOldBuild(getNumber());
    }

    @Override
    public ChangeLogSet<? extends Entry> getChangeSet() {
        return new FilteredChangeLogSet(this);
    }

    /**
     * We always get the changeset from {@link MavenModuleSetBuild}.
     */
    @Override
    public boolean hasChangeSetComputed() {
        return true;
    }

    /**
     * Information about artifacts produced by Maven.
     */
    @Exported
    public MavenArtifactRecord getMavenArtifacts() {
        return getAction(MavenArtifactRecord.class);
    }

    /**
     * Exposes {@code MAVEN_OPTS} to forked processes.
     *
     * <p>
     * See {@link MavenModuleSetBuild#getEnvironment(TaskListener)}  for discussion.
     */
    @Override
    public EnvVars getEnvironment(TaskListener log) throws IOException, InterruptedException {
        EnvVars envs = super.getEnvironment(log);

        // We need to add M2_HOME and the mvn binary to the PATH so if Maven
        // needs to run Maven it will pick the correct one.
        // This can happen if maven calls ANT which itself calls Maven
        // or if Maven calls itself e.g. maven-release-plugin
        MavenInstallation mvn = project.getParent().getMaven();
        if (mvn == null)
            throw new hudson.AbortException(Messages.MavenModuleSetBuild_NoMavenConfigured());
        mvn = mvn.forEnvironment(envs).forNode(Computer.currentComputer().getNode(), log);
        mvn.buildEnvVars(envs);
        return envs;
    }

    public void registerAsProjectAction(MavenReporter reporter) {
        if(projectActionReporters==null)
            projectActionReporters = new ArrayList<MavenProjectActionBuilder>();
        projectActionReporters.add(reporter);
    }

    public void registerAsProjectAction(MavenProjectActionBuilder builder) {
        if(projectActionReporters==null)
            projectActionReporters = new ArrayList<MavenProjectActionBuilder>();
        projectActionReporters.add(builder);
    }

    public List<MavenProjectActionBuilder> getProjectActionBuilders() {
        if(projectActionReporters==null)
            return Collections.emptyList();
        return Collections.unmodifiableList(projectActionReporters);
    }

    public List<ExecutedMojo> getExecutedMojos() {
        if(executedMojos==null)
            return Collections.emptyList();
        else
            return Collections.unmodifiableList(executedMojos);
    }
    
    @Override
    public void run() {
        execute(new MavenBuildExecution());

        getProject().updateTransientActions();

        MavenModuleSetBuild parentBuild = getModuleSetBuild();
        if(parentBuild!=null)
            parentBuild.notifyModuleBuild(this);
    }

    /**
     * If the parent {@link MavenModuleSetBuild} is kept, keep this record, too.
     */
    @Override
    public String getWhyKeepLog() {
        MavenModuleSetBuild pb = getParentBuild();
        if(pb!=null && pb.getWhyKeepLog()!=null)
            return Messages.MavenBuild_KeptBecauseOfParent(pb);
        return super.getWhyKeepLog();
    }


    // used by executedMojos.jelly
    public static ExecutedMojo.Cache createExecutedMojoCache() {
        return new ExecutedMojo.Cache();
    }

    /**
     * Backdoor for {@link MavenModuleSetBuild} to assign workspaces for modules.
     */
    @Override
    protected void setWorkspace(FilePath path) {
        super.setWorkspace(path);
    }
    
    @Override
    public MavenModule getParent() {// don't know why, but javac wants this
        return super.getParent();
    }

    // allow MavenModuleSetBuild to set this.
    @Override
    protected void setBuiltOnStr(String builtOn) {
        super.setBuiltOnStr(builtOn);
    }

    /**
     * Runs Maven and builds the project.
     */
    private static final class Builder extends MavenBuilder {
        private final MavenBuildProxy buildProxy;
        private final AtomicBoolean hasTestFailures = new AtomicBoolean();

        /**
         * Records of what was executed.
         */
        private final List<ExecutedMojo> executedMojos = new ArrayList<ExecutedMojo>();
        private final ModuleName moduleName;

        private long startTime;

        public Builder(BuildListener listener,MavenBuildProxy buildProxy,MavenModule module, List<String> goals, Map<String,String> systemProps) {
            super(listener,Collections.singleton(module),goals,systemProps);
            this.buildProxy = new FilterImpl(buildProxy);
            this.moduleName = module.getModuleName();
        }

        private class FilterImpl extends MavenBuildProxy.Filter<MavenBuildProxy> implements Serializable {
            public FilterImpl(MavenBuildProxy buildProxy) {
                super(buildProxy);
            }

            @Override
            public void executeAsync(final BuildCallable<?,?> program) throws IOException {
                recordAsynchronousExecution(Channel.current().callAsync(new AsyncInvoker(core,program)));
            }

            private static final long serialVersionUID = 1L;
        }

        @Override
        void preBuild(MavenSession session, ReactorManager rm, EventDispatcher dispatcher) throws BuildFailureException, LifecycleExecutionException, IOException, InterruptedException {
            for (MavenReporter r : reporters.get(moduleName))
                r.preBuild(buildProxy,rm.getTopLevelProject(),listener);
        }

        @Override
        void postBuild(MavenSession session, ReactorManager rm, EventDispatcher dispatcher) throws BuildFailureException, LifecycleExecutionException, IOException, InterruptedException {
            buildProxy.setExecutedMojos(executedMojos);
            for (MavenReporter r : reporters.get(moduleName))
                r.postBuild(buildProxy,rm.getTopLevelProject(),listener);
        }

        @Override
        void preExecute(MavenProject project, MojoInfo info) throws IOException, InterruptedException, AbortException {
            for (MavenReporter r : reporters.get(moduleName))
                if(!r.preExecute(buildProxy,project,info,listener))
                    throw new AbortException(r+" failed");

            startTime = System.currentTimeMillis();
        }

        @Override
        void postExecute(MavenProject project, MojoInfo info, Exception exception) throws IOException, InterruptedException, AbortException {
            executedMojos.add(new ExecutedMojo(info,System.currentTimeMillis()-startTime));

            for (MavenReporter r : reporters.get(moduleName)){
                if(!r.postExecute(buildProxy,project,info,listener,exception)) {
                    throw new AbortException(r+" failed");
                } else if (r instanceof TestFailureDetector) {
                    if(((TestFailureDetector) r).hasTestFailures()) {
                        hasTestFailures.compareAndSet(false, true);
                    }
                }
            }
        }

        @Override
        void onReportGenerated(MavenProject project, MavenReportInfo report) throws IOException, InterruptedException, AbortException {
            for (MavenReporter r : reporters.get(moduleName))
                if(!r.reportGenerated(buildProxy,project,report,listener))
                    throw new AbortException(r+" failed");
        }

        @Override
        void preModule(MavenProject project) throws InterruptedException, IOException, AbortException {
            for (MavenReporter r : reporters.get(moduleName))
                if(!r.enterModule(buildProxy,project,listener))
                    throw new AbortException(r+" failed");
        }

        @Override
        void postModule(MavenProject project) throws InterruptedException, IOException, AbortException {
            for (MavenReporter r : reporters.get(moduleName))
                if(!r.leaveModule(buildProxy,project,listener))
                    throw new AbortException(r+" failed");
        }

        @Override
        public boolean hasBuildFailures() {
            return hasTestFailures.get();
        }
        
        private static final long serialVersionUID = 1L;
    }

    /**
     * {@link MavenBuildProxy} implementation.
     */
    class ProxyImpl implements MavenBuildProxy, Serializable {
        private static final long serialVersionUID = 8865133776526671879L;

        private final Map<String,String> artifacts = new LinkedHashMap<String,String>();

        public <V, T extends Throwable> V execute(BuildCallable<V, T> program) throws T, IOException, InterruptedException {
            return program.call(MavenBuild.this);
        }

        /**
         * This method is implemented by the remote proxy before the invocation
         * gets to this. So correct code shouldn't be invoking this method on the master ever.
         *
         * @deprecated
         *      This helps IDE find coding mistakes when someone tries to call this method.
         */
        public final void executeAsync(BuildCallable<?,?> program) throws IOException {
            throw new AssertionError();
        }

        public FilePath getRootDir() {
            return new FilePath(MavenBuild.this.getRootDir());
        }

        public FilePath getProjectRootDir() {
            return new FilePath(MavenBuild.this.getParent().getRootDir());
        }

        public FilePath getModuleSetRootDir() {
            return new FilePath(MavenBuild.this.getParent().getParent().getRootDir());
        }

        /**
         * @deprecated Does not work with {@link ArtifactManager}.
         */
        @Deprecated
        public FilePath getArtifactsDir() {
            return new FilePath(MavenBuild.this.getArtifactsDir());
        }

        @Override public void queueArchiving(String artifactPath, String artifact) {
            artifacts.put(artifactPath, artifact);
        }

        void performArchiving(Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
            for (Map.Entry<String,String> e : artifacts.entrySet()) {
                listener.getLogger().println("[JENKINS] Archiving " + e.getValue() + " to " + e.getKey());
            }
            ArtifactManager am = pickArtifactManager();
            FilePath ws = getWorkspace();
            Map<String,String> artifactsInsideWorkspace = new LinkedHashMap<String,String>();
            String prefix = ws.act(new CanonicalPath()) + '/'; // try to relativize paths to workspace
            Iterator<Map.Entry<String,String>> it = artifacts.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String,String> e = it.next();
                String p = new FilePath(ws, e.getValue()).act(new CanonicalPath());
                if (!p.startsWith(prefix)) {
                    listener.getLogger().println(p + " is not inside " + prefix + "; will archive in a separate pass");
                    continue;
                }
                artifactsInsideWorkspace.put(e.getKey(), p.substring(prefix.length()));
                it.remove();
            }
            if (!artifactsInsideWorkspace.isEmpty()) {
                am.archive(ws, launcher, listener, artifactsInsideWorkspace);
            }
            // Now handle other files outside the workspace, if any.
            for (Map.Entry<String,String> e : artifacts.entrySet()) {
                FilePath f = new FilePath(ws, e.getValue());
                am.archive(f.getParent(), launcher, listener, Collections.singletonMap(e.getKey(), f.getName()));
            }
        }

        public void setResult(Result result) {
            MavenBuild.this.setResult(result);
        }

        public Calendar getTimestamp() {
            return MavenBuild.this.getTimestamp();
        }

        public long getMilliSecsSinceBuildStart() {
            return System.currentTimeMillis()-getTimestamp().getTimeInMillis();
        }

        public boolean isArchivingDisabled() {
            return MavenBuild.this.getParent().getParent().isArchivingDisabled();
        }
        
        public void registerAsProjectAction(MavenReporter reporter) {
            MavenBuild.this.registerAsProjectAction(reporter);
        }

        public void registerAsProjectAction(MavenProjectActionBuilder builder) {
            MavenBuild.this.registerAsProjectAction(builder);
        }

        public void registerAsAggregatedProjectAction(MavenReporter reporter) {
            MavenModuleSetBuild pb = getParentBuild();
            if(pb!=null)
                pb.registerAsProjectAction(reporter);
        }

        public void setExecutedMojos(List<ExecutedMojo> executedMojos) {
            MavenBuild.this.executedMojos = executedMojos;
        }

        private Object writeReplace() {
            return Channel.current().export(MavenBuildProxy.class,this);
        }

        public MavenBuildInformation getMavenBuildInformation() {
            return new MavenBuildInformation( MavenBuild.this.getModuleSetBuild().getMavenVersionUsed());
        }
    }

    private static final class CanonicalPath implements FilePath.FileCallable<String> {
        private static final long serialVersionUID = 1;
        @Override public String invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            return f.getCanonicalPath().replace(File.separatorChar, '/');
        }
    }

    public class ProxyImpl2 extends ProxyImpl implements MavenBuildProxy2 {
        private static final long serialVersionUID = -3377221864644014218L;
        
        private final SplittableBuildListener listener;
        long startTime;
        private final OutputStream log;
        private final MavenModuleSetBuild parentBuild;

        ProxyImpl2(MavenModuleSetBuild parentBuild,SplittableBuildListener listener) throws FileNotFoundException {
            this.parentBuild = parentBuild;
            this.listener = listener;
            log = new FileOutputStream(getLogFile()); // no buffering so that AJAX clients can see the log live
        }

        public void start() {
            onStartBuilding();
            startTime = System.currentTimeMillis();
            try {
                sync();
                listener.setSideOutputStream(log);
            } catch (IOException e) {
                e.printStackTrace();
            }
            RunListener.fireStarted(MavenBuild.this, listener);
        }

        public void end() {
            if(result==null)
                setResult(Result.SUCCESS);
            onEndBuilding();
            duration += System.currentTimeMillis()- startTime;
            parentBuild.notifyModuleBuild(MavenBuild.this);
            try {
                sync();
                listener.setSideOutputStream(null);
                save();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                updateSymlinks(listener);
            } catch (InterruptedException x) {
                Logger.getLogger(MavenBuild.class.getName()).log(Level.WARNING, null, x);
            }
            RunListener.fireCompleted(MavenBuild.this, listener);
        }

        /**
         * Sends the accumulated log in {@link SplittableBuildListener} to the log of this build.
         */
        public void appendLastLog() {
            try {
                sync();
                listener.setSideOutputStream(log);
                listener.setSideOutputStream(null);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * Before we touch I/O streams, we need to make sure all the remote I/O operations are locally completed,
         * or else we end up switching the log traffic at unaligned moments.
         */
        private void sync() throws IOException {
            try {
                Channel ch = Channel.current();
                if (ch!=null)
                    listener.synchronizeOnMark(ch);
            } catch (InterruptedException e) {
                // our signature doesn't allow us to throw InterruptedException, so we process it later
                Thread.currentThread().interrupt();
            }
        }

        /**
         * Performs final clean up. Invoked after the entire aggregator build is completed.
         */
        protected void close() {
            try {
                sync();
                log.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if(hasntStartedYet()) {
                // Mark the build as not_built. This method is used when the aggregated build
                // failed before it didn't even get to this module
                // OR if the aggregated build is an incremental one and this
                // module needn't be build.
                MavenBuild.this.execute(new RunExecution() {
                    public Result run(BuildListener listener) {
                        listener.getLogger().println(Messages.MavenBuild_FailedEarlier());
                        return Result.NOT_BUILT;
                    }

                    public void post(BuildListener listener) {
                    }

                    public void cleanUp(BuildListener listener) {
                    }
                });
            }
            
            rememberModulesToBuildAgainNextTime();
        }

        private void rememberModulesToBuildAgainNextTime() {
            MavenModuleSetBuild moduleSetBuild = getModuleSetBuild();
            
            if (moduleSetBuild == null) {
                // ModuleSetBuild is gone, for whatever reason JENKINS-9822
                return;
            }
            
            if(hasntStartedYet()) {
                // record modules which have not been build though they should have - i.e. because they
                // have SCM changes.
                // see JENKINS-5764
                if (moduleSetBuild.getParent().isIncrementalBuild()
                    && moduleSetBuild.getResult() != Result.SUCCESS
                    && moduleSetBuild.getResult() != Result.UNSTABLE) {
                    UnbuiltModuleAction action = moduleSetBuild.getAction(UnbuiltModuleAction.class);
                    if (action == null) {
                        action = new UnbuiltModuleAction();
                        moduleSetBuild.getActions().add(action);
                    }
                    action.addUnbuiltModule(getParent().getModuleName());
                }
            } else {
                // mark that this module has been built now, if it has previously been remembered as unbuilt
                // JENKINS-5764
                MavenModuleSetBuild previousParentBuild = moduleSetBuild.getPreviousBuild();
                if (previousParentBuild != null) {
                    UnbuiltModuleAction unbuiltModuleAction = previousParentBuild.getAction(UnbuiltModuleAction.class);
                    if (unbuiltModuleAction != null) {
                        unbuiltModuleAction.removeUnbuildModule(getParent().getModuleName());
                        try {
                            previousParentBuild.save();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                
                if (moduleSetBuild.getParent().isIncrementalBuild() &&
                        (moduleSetBuild.getResult() != Result.SUCCESS)) {
                
                    // JENKINS-5121: maybe module needs to be deployed on next build over the deployment threshold
                    MavenModuleSet mavenModuleSet = moduleSetBuild.getParent();
                    boolean isDeploying = false;
                    Result deploymentThreshold = Result.SUCCESS;
                    DescribableList<Publisher,Descriptor<Publisher>> publishers = mavenModuleSet.getPublishersList();
                    for (Publisher publisher : publishers) {
                        if (publisher instanceof RedeployPublisher) {
                            isDeploying = true;
                            deploymentThreshold = ((RedeployPublisher)publisher).getTreshold();
                            break;
                        }
                    }
                    
                    if (isDeploying && moduleSetBuild.getResult().isWorseThan(deploymentThreshold)) {
                        UnbuiltModuleAction action = moduleSetBuild.getAction(UnbuiltModuleAction.class);
                        if (action == null) {
                            action = new UnbuiltModuleAction();
                            moduleSetBuild.getActions().add(action);
                        }
                        action.addUnbuiltModule(getParent().getModuleName());
                    }
                }
            }
        }

        /**
         * Gets the build for which this proxy is created.
         */
        public MavenBuild owner() {
            return MavenBuild.this;
        }

        private Object writeReplace() {
            // when called from remote, methods need to be executed in the proper Executor's context.
            return Channel.current().export(MavenBuildProxy2.class,
                Executor.currentExecutor().newImpersonatingProxy(MavenBuildProxy2.class,this));
        }
    }
    
    

    private class MavenBuildExecution extends AbstractBuildExecution {
        private List<MavenReporter> reporters;

        @Override
        public MavenBuild getBuild() {
            return (MavenBuild)super.getBuild();
        }

        @Override
        public MavenModule getProject() {
            return (MavenModule)super.getProject();
        }

        @Override
        protected Lease decideWorkspace(Node n, WorkspaceList wsl) throws InterruptedException, IOException {
            return wsl.allocate(getModuleSetBuild().getModuleRoot().child(getProject().getRelativePath()));
        }

        protected Result doRun(BuildListener listener) throws Exception {
            // pick up a list of reporters to run
            reporters = getProject().createReporters();
            MavenModuleSet mms = getProject().getParent();
            if(debug)
                listener.getLogger().println("Reporters="+reporters);

            for (BuildWrapper w : mms.getBuildWrappersList()) {
                Environment e = w.setUp(MavenBuild.this, launcher, listener);
                if (e == null) {
                    return Result.FAILURE;
                }
                buildEnvironments.add(e);
            }

            EnvVars envVars = getEnvironment(listener); // buildEnvironments should be set up first
            
            MavenInstallation mvn = getProject().getParent().getMaven();
            
            mvn = mvn.forEnvironment(envVars).forNode(Computer.currentComputer().getNode(), listener);
            
            MavenInformation mavenInformation = getModuleRoot().act( new MavenVersionCallable( mvn.getHome() ));
            
            String mavenVersion = mavenInformation.getVersion();

            LOGGER.fine(getFullDisplayName()+" is building with mavenVersion " + mavenVersion + " from file " + mavenInformation.getVersionResourcePath());
            

            MavenUtil.MavenVersion mavenVersionType = MavenUtil.getMavenVersion( mavenVersion );

            final ProcessCache.Factory factory;

            switch ( mavenVersionType ){
                case MAVEN_2:
                    LOGGER.fine( "using maven 2 " + mavenVersion );
                    factory = new MavenProcessFactory( getParent().getParent(), MavenBuild.this, launcher, envVars, getMavenOpts(listener, envVars), null );
                    break;
                case MAVEN_3_0_X:
                    LOGGER.fine( "using maven 3 " + mavenVersion );
                    factory = new Maven3ProcessFactory( getParent().getParent(), MavenBuild.this, launcher, envVars, getMavenOpts(listener, envVars), null );
                    break;
                default:
                    LOGGER.fine( "using maven 3 " + mavenVersion );
                    factory = new Maven31ProcessFactory( getParent().getParent(), MavenBuild.this, launcher, envVars, getMavenOpts(listener, envVars), null );

            }

            ProcessCache.MavenProcess process = MavenBuild.mavenProcessCache.get( launcher.getChannel(), listener, factory);

            ArgumentListBuilder margs = new ArgumentListBuilder("-N","-B");
            FilePath localRepo = mms.getLocalRepository().locate(MavenBuild.this);
            if(localRepo!=null)
                // the workspace must be on this node, so getRemote() is safe.
                margs.add("-Dmaven.repo.local="+localRepo.getRemote());
            
            String settingsPath = SettingsProvider.getSettingsRemotePath(mms.getSettings(), MavenBuild.this, listener);
            if (settingsPath != null) {
                margs.add("-s").add(settingsPath);
            }

            margs.add("-f",getModuleRoot().child("pom.xml").getRemote());
            margs.addTokenized(getProject().getGoals());

            Map<String,String> systemProps = new HashMap<String, String>(envVars);
            // backward compatibility
            systemProps.put("hudson.build.number",String.valueOf(getNumber()));

            if (mavenVersionType == MavenUtil.MavenVersion.MAVEN_3_0_X || mavenVersionType == MavenUtil.MavenVersion.MAVEN_3_1)
            { 
                // FIXME here for maven 3 builds
                listener.getLogger().println("Building single Maven modules is not implemented for Maven 3, yet!");
                return Result.ABORTED;
            }
            else
            {
                boolean normalExit = false;
                try {
                    ProxyImpl proxy = new ProxyImpl();
                    Result r = process.call(new Builder(
                        listener, proxy,
                        getProject(), margs.toList(), systemProps));
                    proxy.performArchiving(launcher, listener);
                    normalExit = true;
                    return r;
                } finally {
                    if(normalExit)  process.recycle();
                    else            process.discard();
    
                    // tear down in reverse order
                    boolean failed=false;
                    for( int i=buildEnvironments.size()-1; i>=0; i-- ) {
                        if (!buildEnvironments.get(i).tearDown(MavenBuild.this,listener)) {
                            failed=true;
                        }                    
                    }
                    // WARNING The return in the finally clause will trump any return before
                    if (failed) return Result.FAILURE;
                }
            }
        }

        public void post2(BuildListener listener) throws Exception {
            if (reporters != null) {
                for (MavenReporter reporter : reporters) {
                    reporter.end(MavenBuild.this,launcher,listener);
                }
            }
        }

    }

    public String getMavenOpts(TaskListener listener, EnvVars envVars) {
        return envVars.expand(expandTokens(listener, getProject().getParent().getMavenOpts()));
    }

    private static final int MAX_PROCESS_CACHE = 5;

    protected static final ProcessCache mavenProcessCache = new ProcessCache(MAX_PROCESS_CACHE);

    /**
     * Set true to produce debug output.
     */
    public static boolean debug = false;

    private static final Logger LOGGER = Logger.getLogger(MavenBuild.class.getName());
}
