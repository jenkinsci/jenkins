package hudson.maven;

import hudson.FilePath;
import hudson.Util;
import hudson.maven.agent.AbortException;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.DependencyGraph;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.model.Run;
import hudson.remoting.Channel;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.util.ArgumentListBuilder;
import org.apache.maven.BuildFailureException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ReactorManager;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.monitor.event.EventDispatcher;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link Run} for {@link MavenModule}.
 * 
 * @author Kohsuke Kawaguchi
 */
public class MavenBuild extends AbstractBuild<MavenModule,MavenBuild> {
    /**
     * {@link MavenReporter}s that will contribute project actions.
     * Can be null if there's none.
     */
    /*package*/ List<MavenReporter> projectActionReporters;

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
    }

    /**
     * Gets the {@link MavenModuleSetBuild} that has the same build number.
     *
     * @return
     *      null if no such build exists, which happens when the module build
     *      is manually triggered.
     * @see #getModuleSetBuild()
     */
    public MavenModuleSetBuild getParentBuild() {
        return getParent().getParent().getBuildByNumber(getNumber());
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
    public MavenModuleSetBuild getModuleSetBuild() {
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

    public void registerAsProjectAction(MavenReporter reporter) {
        if(projectActionReporters==null)
            projectActionReporters = new ArrayList<MavenReporter>();
        projectActionReporters.add(reporter);
    }

    public List<ExecutedMojo> getExecutedMojos() {
        if(executedMojos==null)
            return Collections.emptyList();
        else
            return Collections.unmodifiableList(executedMojos);
    }
    
    @Override
    public void run() {
        run(new RunnerImpl());

        getProject().updateTransientActions();

        MavenModuleSetBuild parentBuild = getModuleSetBuild();
        if(parentBuild!=null)
            parentBuild.notifyModuleBuild(this);
    }

    /**
     * Runs Maven and builds the project.
     */
    private static final class Builder extends MavenBuilder {
        private final MavenBuildProxy buildProxy;
        private final MavenReporter[] reporters;

        /**
         * Records of what was executed.
         */
        private final List<ExecutedMojo> executedMojos = new ArrayList<ExecutedMojo>();

        private long startTime;

        public Builder(BuildListener listener,MavenBuildProxy buildProxy,MavenReporter[] reporters, List<String> goals, Map<String,String> systemProps) {
            super(listener,goals,systemProps);
            this.buildProxy = buildProxy;
            this.reporters = reporters;
        }

        @Override
        void preBuild(MavenSession session, ReactorManager rm, EventDispatcher dispatcher) throws BuildFailureException, LifecycleExecutionException, IOException, InterruptedException {
            for (MavenReporter r : reporters)
                r.preBuild(buildProxy,rm.getTopLevelProject(),listener);
        }

        @Override
        void postBuild(MavenSession session, ReactorManager rm, EventDispatcher dispatcher) throws BuildFailureException, LifecycleExecutionException, IOException, InterruptedException {
            buildProxy.setExecutedMojos(executedMojos);
            for (MavenReporter r : reporters)
                r.postBuild(buildProxy,rm.getTopLevelProject(),listener);
        }

        @Override
        void preExecute(MavenProject project, MojoInfo info) throws IOException, InterruptedException, AbortException {
            for (MavenReporter r : reporters)
                if(!r.preExecute(buildProxy,project,info,listener))
                    throw new AbortException(r+" failed");

            startTime = System.currentTimeMillis();
        }

        @Override
        void postExecute(MavenProject project, MojoInfo info, Exception exception) throws IOException, InterruptedException, AbortException {
            executedMojos.add(new ExecutedMojo(info,System.currentTimeMillis()-startTime));

            for (MavenReporter r : reporters)
                if(!r.postExecute(buildProxy,project,info,listener,exception))
                    throw new AbortException(r+" failed");
        }

        @Override
        void preModule(MavenProject project) throws InterruptedException, IOException, AbortException {
            for (MavenReporter r : reporters)
                if(!r.enterModule(buildProxy,project,listener))
                    throw new AbortException(r+" failed");
        }

        @Override
        void postModule(MavenProject project) throws InterruptedException, IOException, AbortException {
            for (MavenReporter r : reporters)
                if(!r.leaveModule(buildProxy,project,listener))
                    throw new AbortException(r+" failed");
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * {@link MavenBuildProxy} implementation.
     */
    class ProxyImpl implements MavenBuildProxy, Serializable {
        public <V, T extends Throwable> V execute(BuildCallable<V, T> program) throws T, IOException, InterruptedException {
            return program.call(MavenBuild.this);
        }

        public FilePath getRootDir() {
            return new FilePath(MavenBuild.this.getRootDir());
        }

        public FilePath getProjectRootDir() {
            return new FilePath(MavenBuild.this.getParent().getRootDir());
        }

        public FilePath getArtifactsDir() {
            return new FilePath(MavenBuild.this.getArtifactsDir());
        }

        public void setResult(Result result) {
            MavenBuild.this.setResult(result);
        }

        public void registerAsProjectAction(MavenReporter reporter) {
            MavenBuild.this.registerAsProjectAction(reporter);
        }

        public void setExecutedMojos(List<ExecutedMojo> executedMojos) {
            MavenBuild.this.executedMojos = executedMojos;
        }

        private Object writeReplace() {
            return Channel.current().export(MavenBuildProxy.class,this);
        }
    }

    class ProxyImpl2 extends ProxyImpl implements MavenBuildProxy2 {
        long startTime;
        public void start() {
            onStartBuilding();
            startTime = System.currentTimeMillis();
        }

        public void end() {
            if(result==null)
                setResult(Result.SUCCESS);
            onEndBuilding();
            duration = System.currentTimeMillis()- startTime;
            try {
                save();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * Mark the build as aborted. This method is used when the aggregated build
         * failed before it didn't even get to this module.
         */
        protected void abortIfNotStarted() {
            if(hasntStartedYet()) {
                run(new Runner() {
                    public Result run(BuildListener listener) throws Exception, RunnerAbortedException {
                        listener.getLogger().println("Build failed before it gets to this module");
                        return Result.ABORTED;
                    }

                    public void post(BuildListener listener) throws Exception {
                    }
                });
            }
        }

        private Object writeReplace() {
            return Channel.current().export(MavenBuildProxy2.class,this);
        }
    }

    private class RunnerImpl extends AbstractRunner {
        private List<MavenReporter> reporters;

        protected Result doRun(BuildListener listener) throws Exception {
            // pick up a list of reporters to run
            reporters = getProject().createReporters();
            if(debug)
                listener.getLogger().println("Reporters="+reporters);

            Map<String,String> envVars = getEnvVars();

            ProcessCache.MavenProcess process = mavenProcessCache.get(launcher.getChannel(), listener,
                new MavenProcessFactory(getParent().getParent(),launcher,envVars));

            ArgumentListBuilder margs = new ArgumentListBuilder();
            margs.add("-N").add("-B");
            margs.add("-f",getParent().getModuleRoot().child("pom.xml").getRemote());
            margs.addTokenized(getProject().getGoals());

            Map<String,String> systemProps = new HashMap<String, String>(envVars);
            // backward compatibility
            systemProps.put("hudson.build.number",String.valueOf(getNumber()));

            boolean normalExit = false;
            try {
                Result r = process.channel.call(new Builder(
                    listener,new ProxyImpl(),
                    reporters.toArray(new MavenReporter[0]), margs.toList(), systemProps));
                normalExit = true;
                return r;
            } finally {
                if(normalExit)  process.recycle();
                else            process.discard();
            }
        }

        public void post(BuildListener listener) throws IOException, InterruptedException {
            for (MavenReporter reporter : reporters)
                reporter.end(MavenBuild.this,launcher,listener);

            if(getResult().isBetterOrEqualTo(Result.SUCCESS)) {
                // trigger dependency builds
                DependencyGraph graph = Hudson.getInstance().getDependencyGraph();
                for( AbstractProject<?,?> down : getParent().getDownstreamProjects()) {
                    if(debug)
                        listener.getLogger().println("Considering whether to trigger "+down+" or not");

                    if(graph.hasIndirectDependencies(getProject(),down)) {
                        // if there's a longer dependency path to this project,
                        // then scheduling the build now is going to be a waste,
                        // so don't do that.
                        // let the longer path eventually trigger this build
                        if(debug)
                            listener.getLogger().println(" -> No, because there's a longer dependency path");
                        continue;
                    }

                    // if the downstream module depends on multiple modules,
                    // only trigger them when all the upstream dependencies are updated.
                    boolean trigger = true;
                    
                    AbstractBuild<?,?> dlb = down.getLastBuild(); // can be null.
                    for (MavenModule up : Util.filter(down.getUpstreamProjects(),MavenModule.class)) {
                        MavenBuild ulb;
                        if(up==getProject()) {
                            // the current build itself is not registered as lastSuccessfulBuild
                            // at this point, so we have to take that into account. ugly.
                            if(getResult()==null || !getResult().isWorseThan(Result.UNSTABLE))
                                ulb = MavenBuild.this;
                            else
                                ulb = up.getLastSuccessfulBuild();
                        } else
                            ulb = up.getLastSuccessfulBuild();
                        if(ulb==null) {
                            // if no usable build is available from the upstream,
                            // then we have to wait at least until this build is ready
                            if(debug)
                                listener.getLogger().println(" -> No, because another upstream "+up+" for "+down+" has no successful build");
                            trigger = false;
                            break;
                        }

                        // if no record of the relationship in the last build
                        // is available, we'll just have to assume that the condition
                        // for the new build is met, or else no build will be fired forever.
                        if(dlb==null)   continue;
                        int n = dlb.getUpstreamRelationship(up);
                        if(n==-1)   continue;

                        assert ulb.getNumber()>=n;

                        if(ulb.getNumber()==n) {
                            // there's no new build of this upstream since the last build
                            // of the downstream, and the upstream build is in progress.
                            // The new downstream build should wait until this build is started
                            AbstractProject bup = getBuildingUpstream(graph, up);
                            if(bup!=null) {
                                if(debug)
                                    listener.getLogger().println(" -> No, because another upstream "+bup+" for "+down+" is building");
                                trigger = false;
                                break;
                            }
                        }
                    }

                    if(trigger) {
                        listener.getLogger().println("Triggering a new build of "+down.getName());
                        down.scheduleBuild();
                    }
                }
            }
        }

        /**
         * Returns the project if any of the upstream project (or itself) is either
         * building or is in the queue.
         * <p>
         * This means eventually there will be an automatic triggering of
         * the given project (provided that all builds went smoothly.)
         */
        private AbstractProject getBuildingUpstream(DependencyGraph graph, AbstractProject project) {
            Set<AbstractProject> tups = graph.getTransitiveUpstream(project);
            tups.add(project);
            for (AbstractProject tup : tups) {
                if(tup!=getProject() && (tup.isBuilding() || tup.isInQueue()))
                    return tup;
            }
            return null;
        }
    }

    private static final int MAX_PROCESS_CACHE = 5;

    protected static final ProcessCache mavenProcessCache = new ProcessCache(MAX_PROCESS_CACHE);

    /**
     * Set true to produce debug output.
     */
    public static boolean debug = false;
}
