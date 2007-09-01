package hudson.maven;

import hudson.AbortException;
import hudson.maven.MavenBuild.ProxyImpl2;
import hudson.FilePath.FileCallable;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.model.AbstractProject;
import hudson.remoting.VirtualChannel;
import hudson.util.ArgumentListBuilder;
import hudson.util.IOException2;
import org.apache.maven.BuildFailureException;
import org.apache.maven.embedder.MavenEmbedderException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ReactorManager;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.monitor.event.EventDispatcher;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link Build} for {@link MavenModuleSet}.
 *
 * <p>
 * A "build" of {@link MavenModuleSet} consists of:
 *
 * <ol>
 * <li>Update the workspace.
 * <li>Parse POMs
 * <li>Trigger module builds.
 * </ol>
 *
 * This object remembers the changelog and what {@link MavenBuild}s are done
 * on this.
 *  
 * @author Kohsuke Kawaguchi
 */
public final class MavenModuleSetBuild extends AbstractBuild<MavenModuleSet,MavenModuleSetBuild> {
    public MavenModuleSetBuild(MavenModuleSet job) throws IOException {
        super(job);
    }

    public MavenModuleSetBuild(MavenModuleSet project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    /**
     * Displays the combined status of all modules.
     * <p>
     * More precisely, this picks up the status of this build itself,
     * plus all the latest builds of the modules that belongs to this build. 
     */
    @Override
    public Result getResult() {
        Result r = super.getResult();

        for (MavenBuild b : getModuleLastBuilds().values()) {
            Result br = b.getResult();
            if(r==null)
                r = br;
            else
            if(br!=null)
                r = r.combine(br);
        }

        return r;
    }

    /**
     * Computes the module builds that correspond to this build.
     * <p>
     * A module may be built multiple times (by the user action),
     * so the value is a list.
     */
    public Map<MavenModule,List<MavenBuild>> getModuleBuilds() {
        Collection<MavenModule> mods = getParent().getModules();

        // identify the build number range. [start,end)
        MavenModuleSetBuild nb = getNextBuild();
        int end = nb!=null ? nb.getNumber() : Integer.MAX_VALUE;

        // preserve the order by using LinkedHashMap
        Map<MavenModule,List<MavenBuild>> r = new LinkedHashMap<MavenModule,List<MavenBuild>>(mods.size());

        for (MavenModule m : mods) {
            List<MavenBuild> builds = new ArrayList<MavenBuild>();
            MavenBuild b = m.getNearestBuild(number);
            while(b!=null && b.getNumber()<end) {
                builds.add(b);
                b = b.getNextBuild();
            }
            r.put(m,builds);
        }

        return r;
    }

    /**
     * Computes the latest module builds that correspond to this build.
     */
    public Map<MavenModule,MavenBuild> getModuleLastBuilds() {
        Collection<MavenModule> mods = getParent().getModules();

        // identify the build number range. [start,end)
        MavenModuleSetBuild nb = getNextBuild();
        int end = nb!=null ? nb.getNumber() : Integer.MAX_VALUE;

        // preserve the order by using LinkedHashMap
        Map<MavenModule,MavenBuild> r = new LinkedHashMap<MavenModule,MavenBuild>(mods.size());

        for (MavenModule m : mods) {
            MavenBuild b = m.getNearestOldBuild(end - 1);
            if(b!=null && b.getNumber()>=getNumber())
                r.put(m,b);
        }

        return r;
    }

    /**
     * Finds {@link Action}s from all the module builds that belong to this
     * {@link MavenModuleSetBuild}. One action per one {@link MavenModule},
     * and newer ones take precedence over older ones.
     */
    public <T extends Action> List<T> findModuleBuildActions(Class<T> action) {
        Collection<MavenModule> mods = getParent().getModules();
        List<T> r = new ArrayList<T>(mods.size());

        // identify the build number range. [start,end)
        MavenModuleSetBuild nb = getNextBuild();
        int end = nb!=null ? nb.getNumber()-1 : Integer.MAX_VALUE;

        for (MavenModule m : mods) {
            MavenBuild b = m.getNearestOldBuild(end);
            while(b!=null && b.getNumber()>=number) {
                T a = b.getAction(action);
                if(a!=null) {
                    r.add(a);
                    break;
                }
                b = b.getPreviousBuild();
            }
        }

        return r;
    }

    public void run() {
        run(new RunnerImpl());
        getProject().updateTransientActions();
    }

    /**
     * Called when a module build that corresponds to this module set build
     * has completed.
     */
    /*package*/ void notifyModuleBuild(MavenBuild newBuild) {
        try {
            // update module set build number
            getParent().updateNextBuildNumber();

            // update actions
            Map<MavenModule, List<MavenBuild>> moduleBuilds = getModuleBuilds();

            // actions need to be replaced atomically especially
            // given that two builds might complete simultaneously.
            synchronized(this) {
                boolean modified = false;

                List<Action> actions = getActions();
                Set<Class<? extends AggregatableAction>> individuals = new HashSet<Class<? extends AggregatableAction>>();
                for (Action a : actions) {
                    if(a instanceof MavenAggregatedReport) {
                        MavenAggregatedReport mar = (MavenAggregatedReport) a;
                        mar.update(moduleBuilds,newBuild);
                        individuals.add(mar.getIndividualActionType());
                        modified = true;
                    }
                }

                // see if the new build has any new aggregatable action that we haven't seen.
                for (Action a : newBuild.getActions()) {
                    if (a instanceof AggregatableAction) {
                        AggregatableAction aa = (AggregatableAction) a;
                        if(individuals.add(aa.getClass())) {
                            // new AggregatableAction
                            MavenAggregatedReport mar = aa.createAggregatedAction(this, moduleBuilds);
                            mar.update(moduleBuilds,newBuild);
                            actions.add(mar);
                            modified = true;
                        }
                    }
                }

                if(modified) {
                    save();
                    getProject().updateTransientActions();
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,"Failed to update "+this,e);
        }
    }

    /**
     * The sole job of the {@link MavenModuleSet} build is to update SCM
     * and triggers module builds.
     */
    private class RunnerImpl extends AbstractRunner {
        private Map<ModuleName,MavenBuild.ProxyImpl2> proxies;

        protected Result doRun(final BuildListener listener) throws Exception {
            PrintStream logger = listener.getLogger();
            try {
                logger.println("Parsing POMs");
                List<PomInfo> poms = project.getModuleRoot().act(new PomParser(listener,project.getRootPOM()));

                // update the module list
                Map<ModuleName,MavenModule> modules = project.modules;
                synchronized(modules) {
                    Map<ModuleName,MavenModule> old = new HashMap<ModuleName, MavenModule>(modules);
                    List<MavenModule> sortedModules = new ArrayList<MavenModule>();

                    modules.clear();
                    if(debug)
                        logger.println("Root POM is "+poms.get(0).name);
                    project.reconfigure(poms.get(0));
                    for (PomInfo pom : poms) {
                        MavenModule mm = old.get(pom.name);
                        if(mm!=null) {// found an existing matching module
                            if(debug)
                                logger.println("Reconfiguring "+mm);
                            mm.reconfigure(pom);
                            modules.put(pom.name,mm);
                        } else {// this looks like a new module
                            logger.println("Discovered a new module "+pom.name+" "+pom.displayName);
                            mm = new MavenModule(project,pom,getNumber());
                            modules.put(mm.getModuleName(),mm);
                        }
                        sortedModules.add(mm);
                        mm.save();
                    }
                    // at this point the list contains all the live modules
                    project.sortedActiveModules = sortedModules;

                    // remaining modules are no longer active.
                    old.keySet().removeAll(modules.keySet());
                    for (MavenModule om : old.values()) {
                        if(debug)
                            logger.println("Disabling "+om);
                        om.disable();
                    }
                    modules.putAll(old);
                }

                // we might have added new modules
                Hudson.getInstance().rebuildDependencyGraph();

                // module builds must start with this build's number
                for (MavenModule m : modules.values())
                    m.updateNextBuildNumber(getNumber());

                if(!project.isAggregatorStyleBuild()) {
                    // start module builds
                    logger.println("Triggering "+project.getRootModule().getModuleName());
                    project.getRootModule().scheduleBuild();
                } else {
                    // do builds here
                    SplittableBuildListener slistener = new SplittableBuildListener(listener);
                    proxies = new HashMap<ModuleName, ProxyImpl2>();
                    for (MavenModule m : modules.values())
                        proxies.put(m.getModuleName(),m.newBuild().new ProxyImpl2(MavenModuleSetBuild.this,slistener));

                    // run the complete build here
                    Map<String,String> envVars = getEnvVars();

                    ProcessCache.MavenProcess process = MavenBuild.mavenProcessCache.get(launcher.getChannel(), slistener,
                        new MavenProcessFactory(project,launcher,envVars));

                    ArgumentListBuilder margs = new ArgumentListBuilder();
                    margs.add("-B").add("-f",project.getModuleRoot().child(project.getRootPOM()).getRemote());
                    margs.addTokenized(project.getGoals());

                    try {
                        return process.channel.call(new Builder(
                            slistener,proxies,modules.values(),margs.toList(),envVars));
                    } finally {
                        for (ProxyImpl2 p : proxies.values())
                            p.abortIfNotStarted();
                        process.discard();
                    }
                }
                
                return null;
            } catch (AbortException e) {
                // error should have been already reported.
                return Result.FAILURE;
            } catch (IOException e) {
                e.printStackTrace(listener.error("Failed to parse POMs"));
                return Result.FAILURE;
            } catch (InterruptedException e) {
                e.printStackTrace(listener.error("Aborted"));
                return Result.FAILURE;
            } catch (RuntimeException e) {
                // bug in the code.
                e.printStackTrace(listener.error("Processing failed due to a bug in the code. Please report thus to users@hudson.dev.java.net"));
                logger.println("project="+project);
                logger.println("project.getModules()="+project.getModules());
                logger.println("project.getRootModule()="+project.getRootModule());
                throw e;
            }
        }

        public void post(BuildListener listener) {
            if(project.isAggregatorStyleBuild()) {
                // schedule downstream builds. for non aggregator style builds,
                // this is done by each module
                if(getResult().isBetterOrEqualTo(Result.SUCCESS)) {
                    HashSet<AbstractProject> downstreams = new HashSet<AbstractProject>(project.modules.values());
                    downstreams.add(project);
                    for (ProxyImpl2 p : proxies.values())
                        p.owner().scheduleDownstreamBuilds(listener,downstreams);
                }
            }
        }
    }

    /**
     * Runs Maven and builds the project.
     */
    private static final class Builder extends MavenBuilder {
        private final Map<ModuleName,? extends MavenBuildProxy2> proxies;
        private final Map<ModuleName,List<MavenReporter>> reporters = new HashMap<ModuleName,List<MavenReporter>>();
        private final Map<ModuleName,List<ExecutedMojo>> executedMojos = new HashMap<ModuleName,List<ExecutedMojo>>();
        private long mojoStartTime;


        public Builder(BuildListener listener,Map<ModuleName,? extends MavenBuildProxy2> proxies, Collection<MavenModule> modules, List<String> goals, Map<String,String> systemProps) {
            super(listener,goals,systemProps);
            this.proxies = proxies;

            for (MavenModule m : modules)
                reporters.put(m.getModuleName(),m.createReporters());
        }

        void preBuild(MavenSession session, ReactorManager rm, EventDispatcher dispatcher) throws BuildFailureException, LifecycleExecutionException, IOException, InterruptedException {
            // TODO
        }

        void postBuild(MavenSession session, ReactorManager rm, EventDispatcher dispatcher) throws BuildFailureException, LifecycleExecutionException, IOException, InterruptedException {
            // TODO
        }

        void preModule(MavenProject project) throws InterruptedException, IOException, hudson.maven.agent.AbortException {
            ModuleName name = new ModuleName(project);
            MavenBuildProxy2 proxy = proxies.get(name);
            listener.getLogger().flush();   // make sure the data until here are all written
            proxy.start();
            for (MavenReporter r : reporters.get(name))
                if(!r.preBuild(proxy,project,listener))
                    throw new hudson.maven.agent.AbortException(r+" failed");
        }

        void postModule(MavenProject project) throws InterruptedException, IOException, hudson.maven.agent.AbortException {
            ModuleName name = new ModuleName(project);
            MavenBuildProxy2 proxy = proxies.get(name);
            for (MavenReporter r : reporters.get(name))
                if(!r.postBuild(proxy,project,listener))
                    throw new hudson.maven.agent.AbortException(r+" failed");
            proxy.setExecutedMojos(executedMojos.get(name));
            listener.getLogger().flush();   // make sure the data until here are all written
            proxy.end();
        }

        void preExecute(MavenProject project, MojoInfo mojoInfo) throws IOException, InterruptedException, hudson.maven.agent.AbortException {
            ModuleName name = new ModuleName(project);
            MavenBuildProxy proxy = proxies.get(name);
            for (MavenReporter r : reporters.get(name))
                if(!r.preExecute(proxy,project,mojoInfo,listener))
                    throw new hudson.maven.agent.AbortException(r+" failed");

            mojoStartTime = System.currentTimeMillis();
        }

        void postExecute(MavenProject project, MojoInfo mojoInfo, Exception exception) throws IOException, InterruptedException, hudson.maven.agent.AbortException {
            ModuleName name = new ModuleName(project);

            List<ExecutedMojo> mojoList = executedMojos.get(name);
            if(mojoList==null)
                executedMojos.put(name,mojoList=new ArrayList<ExecutedMojo>());
            mojoList.add(new ExecutedMojo(mojoInfo,System.currentTimeMillis()-mojoStartTime));

            MavenBuildProxy2 proxy = proxies.get(name);
            for (MavenReporter r : reporters.get(name))
                if(!r.postExecute(proxy,project,mojoInfo,listener,exception))
                    throw new hudson.maven.agent.AbortException(r+" failed");
            if(exception!=null)
                proxy.setResult(Result.FAILURE);
        }

        private static final long serialVersionUID = 1L;
    }
    
    /**
     * Executed on the slave to parse POM and extract information into {@link PomInfo},
     * which will be then brought back to the master.
     */
    private static final class PomParser implements FileCallable<List<PomInfo>> {
        private final BuildListener listener;
        private final String rootPOM;
        /**
         * Capture the value of the static field so that the debug flag
         * takes an effect even when {@link PomParser} runs in a slave.
         */
        private final boolean versbose = debug;

        public PomParser(BuildListener listener, String rootPOM) {
            this.listener = listener;
            this.rootPOM = rootPOM;
        }

        /**
         * Computes the path of {@link #rootPOM}.
         *
         * Returns "abc" if rootPOM="abc/pom.xml"
         * If rootPOM="pom.xml", this method returns "".
         */
        private String getRootPath() {
            int idx = Math.max(rootPOM.lastIndexOf('/'), rootPOM.lastIndexOf('\\'));
            if(idx==-1) return "";
            return rootPOM.substring(0,idx);
        }

        public List<PomInfo> invoke(File ws, VirtualChannel channel) throws IOException {
            File pom = new File(ws,rootPOM);

            PrintStream logger = listener.getLogger();

            if(!pom.exists()) {
                logger.println("No such file "+pom);
                logger.println("Perhaps you need to specify the correct POM file path in the project configuration?");
                throw new AbortException();
            }

            if(versbose)
                logger.println("Parsing "+pom);

            try {
                MavenEmbedder embedder = MavenUtil.createEmbedder(listener);
                MavenProject mp = embedder.readProject(pom);
                Map<MavenProject,String> relPath = new HashMap<MavenProject,String>();
                MavenUtil.resolveModules(embedder,mp,getRootPath(),relPath,listener);

                if(versbose) {
                    for (Entry<MavenProject, String> e : relPath.entrySet())
                        logger.printf("Discovered %s at %s\n",e.getKey().getId(),e.getValue());
                }

                List<PomInfo> infos = new ArrayList<PomInfo>();
                toPomInfo(mp,null,relPath,infos);

                for (PomInfo pi : infos)
                    pi.cutCycle();
                
                embedder.stop();
                return infos;
            } catch (MavenEmbedderException e) {
                // TODO: better error handling needed
                throw new IOException2(e);
            } catch (ProjectBuildingException e) {
                throw new IOException2(e);
            }
        }

        private void toPomInfo(MavenProject mp, PomInfo parent, Map<MavenProject,String> relPath, List<PomInfo> infos) {
            PomInfo pi = new PomInfo(mp, parent, relPath.get(mp));
            infos.add(pi);
            for (MavenProject child : (List<MavenProject>)mp.getCollectedProjects())
                toPomInfo(child,pi,relPath,infos);
        }

        private static final long serialVersionUID = 1L;
    }

    private static final Logger LOGGER = Logger.getLogger(MavenModuleSetBuild.class.getName());

    /**
     * Extra versbose debug switch.
     */
    public static boolean debug = false;
}
