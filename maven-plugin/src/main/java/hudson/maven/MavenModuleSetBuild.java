/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Red Hat, Inc., Victor Glushenkov
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

import hudson.AbortException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.EnvVars;
import hudson.scm.ChangeLogSet;
import hudson.FilePath.FileCallable;
import hudson.maven.MavenBuild.ProxyImpl2;
import hudson.maven.reporters.MavenFingerprinter;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Environment;
import hudson.model.Fingerprint;
import hudson.model.Hudson;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.model.Cause.UpstreamCause;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.remoting.RequestAbortedException;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Maven.MavenInstallation;
import hudson.util.ArgumentListBuilder;
import hudson.util.StreamTaskListener;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.maven.BuildFailureException;
import org.apache.maven.embedder.MavenEmbedderException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ReactorManager;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.monitor.event.EventDispatcher;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

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
public class MavenModuleSetBuild extends AbstractMavenBuild<MavenModuleSet,MavenModuleSetBuild> {
    /**
     * {@link MavenReporter}s that will contribute project actions.
     * Can be null if there's none.
     */
    /*package*/ List<MavenReporter> projectActionReporters;

    public MavenModuleSetBuild(MavenModuleSet job) throws IOException {
        super(job);
    }

    public MavenModuleSetBuild(MavenModuleSet project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    /**
     * Exposes {@code MAVEN_OPTS} to forked processes.
     *
     * When we fork Maven, we do so directly by executing Java, thus this environment variable
     * is pointless (we have to tweak JVM launch option correctly instead, which can be seen in
     * {@link MavenProcessFactory}), but setting the environment variable explicitly is still
     * useful in case this Maven forks other Maven processes via normal way. See HUDSON-3644.
     */
    @Override
    public EnvVars getEnvironment(TaskListener log) throws IOException, InterruptedException {
        EnvVars envs = super.getEnvironment(log);
        String opts = project.getMavenOpts();
        if(opts!=null)
            envs.put("MAVEN_OPTS", opts);
        return envs;
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
            if(br==Result.NOT_BUILT)
                continue;   // UGLY: when computing combined status, ignore the modules that were not built
            else
            if(br!=null)
                r = r.combine(br);
        }

        return r;
    }

    /**
     * Returns the filtered changeset entries that match the given module.
     */
    /*package*/ List<ChangeLogSet.Entry> getChangeSetFor(final MavenModule mod) {
        return new ArrayList<ChangeLogSet.Entry>() {
            {
                // modules that are under 'mod'. lazily computed
                List<MavenModule> subsidiaries = null;

                for (ChangeLogSet.Entry e : getChangeSet()) {
                    if(isDescendantOf(e, mod)) {
                        if(subsidiaries==null)
                            subsidiaries = mod.getSubsidiaries();

                        // make sure at least one change belongs to this module proper,
                        // and not its subsidiary module
                        if (notInSubsidiary(subsidiaries, e))
                            add(e);
                    }
                }
            }

            private boolean notInSubsidiary(List<MavenModule> subsidiaries, ChangeLogSet.Entry e) {
                for (String path : e.getAffectedPaths())
                    if(!belongsToSubsidiary(subsidiaries, path))
                        return true;
                return false;
            }

            private boolean belongsToSubsidiary(List<MavenModule> subsidiaries, String path) {
                for (MavenModule sub : subsidiaries)
                    if(path.startsWith(sub.getRelativePath()))
                        return true;
                return false;
            }

            /**
             * Does this change happen somewhere in the given module or its descendants?
             */
            private boolean isDescendantOf(ChangeLogSet.Entry e, MavenModule mod) {
                for (String path : e.getAffectedPaths())
                    if(path.startsWith(mod.getRelativePath()))
                        return true;
                return false;
            }
        };
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

    public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        // map corresponding module build under this object
        if(token.indexOf('$')>0) {
            MavenModule m = getProject().getModule(token);
            if(m!=null) return m.getBuildByNumber(getNumber());
        }
        return super.getDynamic(token,req,rsp);
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

    public void registerAsProjectAction(MavenReporter reporter) {
        if(projectActionReporters==null)
            projectActionReporters = new ArrayList<MavenReporter>();
        projectActionReporters.add(reporter);
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

    @Override
    public Fingerprint.RangeSet getDownstreamRelationship(AbstractProject that) {
        Fingerprint.RangeSet rs = super.getDownstreamRelationship(that);
        for(List<MavenBuild> builds : getModuleBuilds().values())
            for (MavenBuild b : builds)
                rs.add(b.getDownstreamRelationship(that));
        return rs;
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
                for (AggregatableAction aa : newBuild.getActions(AggregatableAction.class)) {
                    if(individuals.add(aa.getClass())) {
                        // new AggregatableAction
                        MavenAggregatedReport mar = aa.createAggregatedAction(this, moduleBuilds);
                        mar.update(moduleBuilds,newBuild);
                        actions.add(mar);
                        modified = true;
                    }
                }

                if(modified) {
                    save();
                    getProject().updateTransientActions();
                }
            }

            // symlink to this module build
            String moduleFsName = newBuild.getProject().getModuleName().toFileSystemName();
            Util.createSymlink(getRootDir(),
                    "../../modules/"+ moduleFsName +"/builds/"+newBuild.getId() /*ugly!*/,
                    moduleFsName, new StreamTaskListener());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,"Failed to update "+this,e);
        } catch (InterruptedException e) {
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
                EnvVars envVars = getEnvironment(listener);
                MavenInstallation mvn = project.getMaven();
                if(mvn==null)
                    throw new AbortException("A Maven installation needs to be available for this project to be built.\n"+
                                             "Either your server has no Maven installations defined, or the requested Maven version does not exist.");
                
                mvn = mvn.forEnvironment(envVars).forNode(Computer.currentComputer().getNode(), listener);
                parsePoms(listener, logger, envVars, mvn);

                if(!project.isAggregatorStyleBuild()) {
                    // start module builds
                    logger.println("Triggering "+project.getRootModule().getModuleName());
                    project.getRootModule().scheduleBuild(new UpstreamCause(MavenModuleSetBuild.this));
                } else {
                    // do builds here
                    try {
                        List<BuildWrapper> wrappers = new ArrayList<BuildWrapper>();
                        for (BuildWrapper w : project.getBuildWrappers())
                            wrappers.add(w);
                        ParametersAction parameters = getAction(ParametersAction.class);
                        if (parameters != null)
                            parameters.createBuildWrappers(MavenModuleSetBuild.this,wrappers);

                        buildEnvironments = new ArrayList<Environment>();
                        for( BuildWrapper w : wrappers) {
                            BuildWrapper.Environment e = w.setUp(MavenModuleSetBuild.this, launcher, listener);
                            if(e==null)
                                return Result.FAILURE;
                            buildEnvironments.add(e);
                            e.buildEnvVars(envVars); // #3502: too late for getEnvironment to do this
                        }

                        if(!preBuild(listener, project.getPublishers()))
                            return Result.FAILURE;

                        SplittableBuildListener slistener = new SplittableBuildListener(listener);
                        proxies = new HashMap<ModuleName, ProxyImpl2>();
                        List<String> changedModules = new ArrayList<String>();

                        for (MavenModule m : project.sortedActiveModules) {
                            
                            // Check if incrementalBuild is selected and that there are changes -
                            // we act as if incrementalBuild is not set if there are no changes.
                            if (!MavenModuleSetBuild.this.getChangeSet().isEmptySet()
                                && project.isIncrementalBuild()) {
                                if (!getChangeSetFor(m).isEmpty()) {
                                    changedModules.add(m.getModuleName().toString());
                                }
                            }
                            
                            proxies.put(m.getModuleName(),m.newBuild().new ProxyImpl2(MavenModuleSetBuild.this,slistener));
                        }

                        // run the complete build here

                        // figure out the root POM location.
                        // choice of module root ('ws' in this method) is somewhat arbitrary
                        // when multiple CVS/SVN modules are checked out, so also check
                        // the path against the workspace root if that seems like what the user meant (see issue #1293)
                        String rootPOM = project.getRootPOM();
                        FilePath pom = project.getModuleRoot().child(rootPOM);
                        FilePath parentLoc = project.getWorkspace().child(rootPOM);
                        if(!pom.exists() && parentLoc.exists())
                            pom = parentLoc;

                        ProcessCache.MavenProcess process = MavenBuild.mavenProcessCache.get(launcher.getChannel(), slistener,
                            new MavenProcessFactory(project,launcher,envVars,pom.getParent()));

                        ArgumentListBuilder margs = new ArgumentListBuilder().add("-B").add("-f", pom.getRemote());
                        if(project.usesPrivateRepository())
                            margs.add("-Dmaven.repo.local="+project.getWorkspace().child(".repository"));
                        // If incrementalBuild is set, and we're on Maven 2.1 or later, *and* there's at least one module
                        // listed in changedModules, do the Maven incremental build commands - if there are no changed modules,
                        // We're building everything anyway.
                        if (project.isIncrementalBuild() && mvn.isMaven2_1(launcher) && !changedModules.isEmpty()) {
                            margs.add("-amd");
                            margs.add("-pl", Util.join(changedModules, ","));
                        }

                        if (project.getAlternateSettings() != null) {
                            margs.add("-s").add(project.getWorkspace().child(project.getAlternateSettings()));
                        }

                        margs.addTokenized(envVars.expand(project.getGoals()));

                        Builder builder = new Builder(slistener, proxies, project.sortedActiveModules, margs.toList(), envVars);
                        MavenProbeAction mpa=null;
                        try {
                            mpa = new MavenProbeAction(project,process.channel);
                            addAction(mpa);
                            return process.channel.call(builder);
                        } catch (RequestAbortedException e) {
                            // this is normally triggered by the unexpected Maven JVM termination.
                            // check if the process is still alive, after giving it a bit of time to die
                            Thread.sleep(1000);
                            if(process.proc.isAlive())
                                throw e; // it's still alive. treat this as a bug in the code
                            else {
                                String msg = "Maven JVM terminated unexpectedly with exit code " + process.proc.join();
                                LOGGER.log(Level.FINE,msg,e);
                                throw new AbortException(msg);
                            }
                        } finally {
                            builder.end(launcher);
                            getActions().remove(mpa);
                            process.discard();
                        }
                    } finally {
                        // tear down in reverse order
                        boolean failed=false;
                        for( int i=buildEnvironments.size()-1; i>=0; i-- ) {
                            if (!buildEnvironments.get(i).tearDown(MavenModuleSetBuild.this,listener)) {
                                failed=true;
                            }                    
                        }
                        buildEnvironments = null;
                        // WARNING The return in the finally clause will trump any return before
                        if (failed) return Result.FAILURE;
                    }
                }
                
                return null;
            } catch (AbortException e) {
                if(e.getMessage()!=null)
                    listener.error(e.getMessage());
                return Result.FAILURE;
            } catch (InterruptedIOException e) {
                listener.error("Aborted");
                return Result.ABORTED;
            } catch (InterruptedException e) {
                listener.error("Aborted");
                return Result.ABORTED;
            } catch (IOException e) {
                e.printStackTrace(listener.error(Messages.MavenModuleSetBuild_FailedToParsePom()));
                return Result.FAILURE;
            } catch (RunnerAbortedException e) {
                return Result.FAILURE;
            } catch (RuntimeException e) {
                // bug in the code.
                e.printStackTrace(listener.error("Processing failed due to a bug in the code. Please report this to users@hudson.dev.java.net"));
                logger.println("project="+project);
                logger.println("project.getModules()="+project.getModules());
                logger.println("project.getRootModule()="+project.getRootModule());
                throw e;
            }
        }

        private void parsePoms(BuildListener listener, PrintStream logger, EnvVars envVars, MavenInstallation mvn) throws IOException, InterruptedException {
            logger.println("Parsing POMs");

            List<PomInfo> poms;
            try {
                poms = project.getModuleRoot().act(new PomParser(listener, mvn, project));
            } catch (IOException e) {
                if (e.getCause() instanceof AbortException)
                    throw (AbortException) e.getCause();
                throw e;
            } catch (MavenExecutionException e) {
                // Maven failed to parse POM
                e.getCause().printStackTrace(listener.error(Messages.MavenModuleSetBuild_FailedToParsePom()));
                throw new AbortException();
            }

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
                        logger.println(Messages.MavenModuleSetBuild_DiscoveredModule(pom.name,pom.displayName));
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
                    om.makeDisabled(true);
                }
                modules.putAll(old);
            }

            // we might have added new modules
            Hudson.getInstance().rebuildDependencyGraph();

            // module builds must start with this build's number
            for (MavenModule m : modules.values())
                m.updateNextBuildNumber(getNumber());
        }

        protected void post2(BuildListener listener) throws Exception {
            // asynchronous executions from the build might have left some unsaved state,
            // so just to be safe, save them all.
            for (MavenBuild b : getModuleLastBuilds().values())
                b.save();

            performAllBuildStep(listener, project.getPublishers(), true);
            performAllBuildStep(listener, project.getProperties(), true);

            // aggregate all module fingerprints to us,
            // so that dependencies between module builds can be understood as
            // dependencies between module set builds.
            // TODO: we really want to implement this as a publisher,
            // but we don't want to ask for a user configuration, nor should it
            // show up in the persisted record.
            MavenFingerprinter.aggregate(MavenModuleSetBuild.this);
        }

        @Override
        public void cleanUp(BuildListener listener) throws Exception {
            if(project.isAggregatorStyleBuild()) {
                // schedule downstream builds. for non aggregator style builds,
                // this is done by each module
                if(getResult().isBetterOrEqualTo(Result.SUCCESS)) {
                	scheduleDownstreamBuilds(listener);
                }
            }

            performAllBuildStep(listener, project.getPublishers(),false);
            performAllBuildStep(listener, project.getProperties(),false);
        }
    }

    /**
     * Runs Maven and builds the project.
     *
     * This is only used for
     * {@link MavenModuleSet#isAggregatorStyleBuild() the aggregator style build}.
     */
    private static final class Builder extends MavenBuilder {
        private final Map<ModuleName,MavenBuildProxy2> proxies;
        private final Map<ModuleName,List<MavenReporter>> reporters = new HashMap<ModuleName,List<MavenReporter>>();
        private final Map<ModuleName,List<ExecutedMojo>> executedMojos = new HashMap<ModuleName,List<ExecutedMojo>>();
        private long mojoStartTime;

        private MavenBuildProxy2 lastProxy;

        /**
         * Kept so that we can finalize them in the end method.
         */
        private final transient Map<ModuleName,ProxyImpl2> sourceProxies;

        public Builder(BuildListener listener,Map<ModuleName,ProxyImpl2> proxies, Collection<MavenModule> modules, List<String> goals, Map<String,String> systemProps) {
            super(listener,goals,systemProps);
            this.sourceProxies = proxies;
            this.proxies = new HashMap<ModuleName, MavenBuildProxy2>(proxies);
            for (Entry<ModuleName,MavenBuildProxy2> e : this.proxies.entrySet())
                e.setValue(new FilterImpl(e.getValue()));

            for (MavenModule m : modules)
                reporters.put(m.getModuleName(),m.createReporters());
        }

        private class FilterImpl extends MavenBuildProxy2.Filter<MavenBuildProxy2> implements Serializable {
            public FilterImpl(MavenBuildProxy2 core) {
                super(core);
            }

            public void executeAsync(final BuildCallable<?,?> program) throws IOException {
                futures.add(Channel.current().callAsync(new AsyncInvoker(core,program)));
            }

            private static final long serialVersionUID = 1L;
        }

        /**
         * Invoked after the maven has finished running, and in the master, not in the maven process.
         */
        void end(Launcher launcher) throws IOException, InterruptedException {
            for (Map.Entry<ModuleName,ProxyImpl2> e : sourceProxies.entrySet()) {
                ProxyImpl2 p = e.getValue();
                for (MavenReporter r : reporters.get(e.getKey())) {
                    // we'd love to do this when the module build ends, but doing so requires
                    // we know how many task segments are in the current build.
                    r.end(p.owner(),launcher,listener);
                    p.appendLastLog();
                }
                p.close();
            }
        }

        public Result call() throws IOException {
            try {
                return super.call();
            } finally {
                if(lastProxy!=null)
                    lastProxy.appendLastLog();
            }
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
            List<MavenReporter> rs = reporters.get(name);
            if(rs==null) { // probe for issue #906
                throw new AssertionError("reporters.get("+name+")==null. reporters="+reporters+" proxies="+proxies);
            }
            for (MavenReporter r : rs)
                if(!r.postBuild(proxy,project,listener))
                    throw new hudson.maven.agent.AbortException(r+" failed");
            proxy.setExecutedMojos(executedMojos.get(name));
            listener.getLogger().flush();   // make sure the data until here are all written
            proxy.end();
            lastProxy = proxy;
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

        void onReportGenerated(MavenProject project, MavenReportInfo report) throws IOException, InterruptedException, hudson.maven.agent.AbortException {
            ModuleName name = new ModuleName(project);
            MavenBuildProxy proxy = proxies.get(name);
            for (MavenReporter r : reporters.get(name))
                if(!r.reportGenerated(proxy,project,report,listener))
                    throw new hudson.maven.agent.AbortException(r+" failed");
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Used to tunnel exception from Maven through remoting.
     */
    private static final class MavenExecutionException extends RuntimeException {
        private MavenExecutionException(Exception cause) {
            super(cause);
        }

        public Exception getCause() {
            return (Exception)super.getCause();
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
        private final boolean verbose = debug;
        private final MavenInstallation mavenHome;
        private final String profiles;
        private final Properties properties;
        private final String privateRepository;
        private final String alternateSettings;

        public PomParser(BuildListener listener, MavenInstallation mavenHome, MavenModuleSet project) {
            // project cannot be shipped to the remote JVM, so all the relevant properties need to be captured now.
            this.listener = listener;
            this.mavenHome = mavenHome;
            this.rootPOM = project.getRootPOM();
            this.profiles = project.getProfiles();
            this.properties = project.getMavenProperties();
            if (project.usesPrivateRepository()) {
                this.privateRepository = project.getWorkspace().child(".repository").getRemote();
            }
            else {
                this.privateRepository = null;
            }
            this.alternateSettings = project.getAlternateSettings();
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

            // choice of module root ('ws' in this method) is somewhat arbitrary
            // when multiple CVS/SVN modules are checked out, so also check
            // the path against the workspace root if that seems like what the user meant (see issue #1293)
            File parentLoc = new File(ws.getParentFile(),rootPOM);
            if(!pom.exists() && parentLoc.exists())
                pom = parentLoc;

            if(!pom.exists())
                throw new AbortException(Messages.MavenModuleSetBuild_NoSuchPOMFile(pom));

            if(verbose)
                logger.println("Parsing "+pom);

            File settingsLoc = (alternateSettings == null) ? null 
                : new File(ws, alternateSettings);

            if ((settingsLoc != null) && (!settingsLoc.exists())) {
                throw new AbortException(Messages.MavenModuleSetBuild_NoSuchAlternateSettings(settingsLoc.getAbsolutePath()));
            }

            try {
                MavenEmbedder embedder = MavenUtil.
                        createEmbedder(listener, mavenHome.getHomeDir(), profiles,
                                       properties, privateRepository, settingsLoc);
                MavenProject mp = embedder.readProject(pom);
                Map<MavenProject,String> relPath = new HashMap<MavenProject,String>();
                MavenUtil.resolveModules(embedder,mp,getRootPath(),relPath,listener);

                if(verbose) {
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
                throw new MavenExecutionException(e);
            } catch (ProjectBuildingException e) {
                throw new MavenExecutionException(e);
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
     * Extra verbose debug switch.
     */
    public static boolean debug = false;
    
    @Override
    public MavenModuleSet getParent() {// don't know why, but javac wants this
        return super.getParent();
    }
}
