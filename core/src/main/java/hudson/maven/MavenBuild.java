package hudson.maven;

import hudson.FilePath;
import hudson.Util;
import hudson.maven.agent.Main;
import hudson.maven.reporters.SurefireArchiver;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.DependencyGraph;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.Slave;
import hudson.model.JDK;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.Launcher;
import hudson.remoting.Which;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.tasks.Maven.MavenInstallation;
import hudson.util.ArgumentListBuilder;
import hudson.util.IOException2;
import org.apache.maven.lifecycle.LifecycleExecutorInterceptor;
import org.codehaus.classworlds.NoSuchRealmException;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

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
    private static final class Builder implements Callable<Result,IOException> {
        private final BuildListener listener;
        private final MavenBuildProxy buildProxy;
        private final MavenReporter[] reporters;
        private final List<String> goals;
        /**
         * Hudson-defined system properties. These will be made available to Maven,
         * and accessible as if they are specified as -Dkey=value
         */
        private final Map<String,String> systemProps;

        public Builder(BuildListener listener,MavenBuildProxy buildProxy,MavenReporter[] reporters, List<String> goals, Map<String,String> systemProps) {
            this.listener = listener;
            this.buildProxy = buildProxy;
            this.reporters = reporters;
            this.goals = goals;
            this.systemProps = systemProps;
        }

        /**
         * This code is executed inside the maven jail process.
         */
        public Result call() throws IOException {
            try {
                PluginManagerInterceptor pmi = new PluginManagerInterceptor(buildProxy, reporters, listener);
                hudson.maven.agent.PluginManagerInterceptor.setListener(pmi);
                LifecycleExecutorInterceptor.setListener(pmi);

                markAsSuccess = false;

                System.getProperties().putAll(systemProps);

                int r = Main.launch(goals.toArray(new String[goals.size()]));

                if(r==0)    return Result.SUCCESS;

                if(markAsSuccess) {
                    listener.getLogger().println("Maven failed with error.");
                    return Result.SUCCESS;
                }

                return Result.FAILURE;
            } catch (NoSuchMethodException e) {
                throw new IOException2(e);
            } catch (IllegalAccessException e) {
                throw new IOException2(e);
            } catch (NoSuchRealmException e) {
                throw new IOException2(e);
            } catch (InvocationTargetException e) {
                throw new IOException2(e);
            } catch (ClassNotFoundException e) {
                throw new IOException2(e);
            }
        }
    }

    /**
     * {@link MavenBuildProxy} implementation.
     */
    private class ProxyImpl implements MavenBuildProxy, Serializable {
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
            return Channel.current().export(MavenBuildProxy.class, new ProxyImpl());
        }
    }

    private static final class GetJavaExe implements Callable<String,IOException> {
        public String call() throws IOException {
            return new File(new File(System.getProperty("java.home")),"bin/java").getPath();
        }
    }

    private static final class GetRemotingJar implements Callable<String,IOException> {
        public String call() throws IOException {
            return Which.jarFile(Launcher.class).getPath();
        }
    }

    private class RunnerImpl extends AbstractRunner implements ProcessCache.Factory {
        private List<MavenReporter> reporters = new ArrayList<MavenReporter>();

        protected Result doRun(BuildListener listener) throws Exception {
            // pick up a list of reporters to run
            getProject().getReporters().addAllTo(reporters);
            getProject().getParent().getReporters().addAllTo(reporters);
            
            for (MavenReporterDescriptor d : MavenReporters.LIST) {
                if(getProject().getReporters().contains(d))
                    continue;   // already configured
                MavenReporter auto = d.newAutoInstance(getProject());
                if(auto!=null)
                    reporters.add(auto);
            }

            if(debug)
                listener.getLogger().println("Reporters="+reporters);

            ProcessCache.MavenProcess process = mavenProcessCache.get(launcher.getChannel(), listener, this);

            ArgumentListBuilder margs = new ArgumentListBuilder();
            margs.add("-N");
            margs.add("-f",getParent().getModuleRoot().child("pom.xml").getRemote());
            margs.addTokenized(getProject().getGoals());

            Map<String,String> systemProps = new HashMap<String, String>();
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


        /**
         * Starts maven process.
         */
        public Channel newProcess(BuildListener listener, OutputStream out) throws IOException, InterruptedException {
            return launcher.launchChannel(buildMavenCmdLine(listener).toCommandArray(),
                out, getProject().getParent().getModuleRoot(), getEnvVars());
        }

        /**
         * Builds the command line argument list to launch the maven process.
         *
         * UGLY.
         */
        private ArgumentListBuilder buildMavenCmdLine(BuildListener listener) throws IOException, InterruptedException {
            MavenInstallation mvn = getMavenInstallation();
            if(mvn==null) {
                listener.error("Maven version is not configured for this project. Can't determine which Maven to run");
                throw new RunnerAbortedException();
            }

            // find classworlds.jar
            File bootDir = new File(mvn.getHomeDir(), "core/boot");
            File[] classworlds = bootDir.listFiles(CLASSWORLDS_FILTER);
            if(classworlds==null || classworlds.length==0) {
                // Maven 2.0.6 puts it to a different place
                bootDir = new File(mvn.getHomeDir(), "boot");
                classworlds = bootDir.listFiles(CLASSWORLDS_FILTER);
                if(classworlds==null || classworlds.length==0) {
                    listener.error("No classworlds*.jar found in "+mvn.getHomeDir()+" -- Is this a valid maven2 directory?");
                    throw new RunnerAbortedException();
                }
            }

            boolean isMaster = getCurrentNode()==Hudson.getInstance();
            FilePath slaveRoot=null;
            if(!isMaster)
                slaveRoot = ((Slave)getCurrentNode()).getFilePath();

            ArgumentListBuilder args = new ArgumentListBuilder();
            args.add("java");

            if(debugPort!=0)
                args.add("-Xrunjdwp:transport=dt_socket,server=y,address="+debugPort);

            args.addTokenized(getMavenOpts());

            args.add("-cp");
            args.add(
                (isMaster?Which.jarFile(Main.class).getAbsolutePath():slaveRoot.child("maven-agent.jar").getRemote())+
                (launcher.isUnix()?":":";")+
                classworlds[0].getAbsolutePath());
            args.add(Main.class.getName());

            // M2_HOME
            args.add(mvn.getMavenHome());

            // remoting.jar
            args.add(launcher.getChannel().call(new GetRemotingJar()));
            // interceptor.jar
            args.add(isMaster?
                Which.jarFile(hudson.maven.agent.PluginManagerInterceptor.class).getAbsolutePath():
                slaveRoot.child("maven-interceptor.jar").getRemote());
            return args;
        }

        public String getMavenOpts() {
            return getParent().getParent().getMavenOpts();
        }

        public MavenInstallation getMavenInstallation() {
            return getParent().getParent().getMaven();
        }

        public JDK getJava() {
            return getParent().getParent().getJDK();
        }

        public void post(BuildListener listener) {
            try {
                for (MavenReporter reporter : reporters)
                    reporter.end(MavenBuild.this,launcher,listener);
            } catch (InterruptedException e) {
                e.printStackTrace(listener.fatalError("aborted"));
                setResult(Result.FAILURE);
            } catch (IOException e) {
                e.printStackTrace(listener.fatalError("failed"));
                setResult(Result.FAILURE);
            }

            if(!getResult().isWorseThan(Result.UNSTABLE)) {
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

    /**
     * If not 0, launch Maven with a debugger port.
     */
    public static int debugPort;

    private static final int MAX_PROCESS_CACHE = 5;

    static {
        String port = System.getProperty(MavenBuild.class.getName() + ".debugPort");
        if(port!=null)
            debugPort = Integer.parseInt(port);
    }

    private static final ProcessCache mavenProcessCache = new ProcessCache(MAX_PROCESS_CACHE);

    /**
     * Used by selected {@link MavenReporter}s to notify the maven build agent
     * that even though Maven is going to fail, we should report the build as
     * success.
     *
     * <p>
     * This rather ugly hook is necessary to mark builds as unstable, since
     * maven considers a test failure to be a build failure, which will otherwise
     * mark the build as FAILED.
     *
     * <p>
     * It's OK for this field to be static, because the JVM where this is actually
     * used is in the Maven JVM, so only one build is going on for the whole JVM.
     *
     * <p>
     * Even though this field is public, please consider this field reserved
     * for {@link SurefireArchiver}. Subject to change without notice.
     */
    public static boolean markAsSuccess;

    private static final FilenameFilter CLASSWORLDS_FILTER = new FilenameFilter() {
        public boolean accept(File dir, String name) {
            return name.startsWith("classworlds") && name.endsWith(".jar");
        }
    };

    /**
     * Set true to produce debug output.
     */
    public static boolean debug = false;
}
