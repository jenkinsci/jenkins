/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Red Hat, Inc., Victor Glushenkov, Alan Harder, Olivier Lamy, Dominik Bartholdi
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

import static hudson.model.Result.FAILURE;
import static org.apache.maven.model.building.ModelBuildingRequest.VALIDATION_LEVEL_MAVEN_3_0;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Util;
import hudson.maven.MavenBuild.ProxyImpl2;
import hudson.maven.reporters.MavenAggregatedArtifactRecord;
import hudson.maven.reporters.MavenFingerprinter;
import hudson.maven.reporters.MavenMailer;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Cause.UpstreamCause;
import hudson.model.Computer;
import hudson.model.Environment;
import hudson.model.Executor;
import hudson.model.Fingerprint;
import hudson.model.Node;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.StringParameterDefinition;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.scm.ChangeLogSet;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildWrapper;
import hudson.tasks.MailSender;
import hudson.tasks.Maven.MavenInstallation;
import hudson.util.ArgumentListBuilder;
import hudson.util.IOUtils;
import hudson.util.StreamTaskListener;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import jenkins.mvn.GlobalSettingsProvider;
import jenkins.mvn.SettingsProvider;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.codehaus.plexus.util.PathTool;
import jenkins.maven3.agent.Maven31Main;
import org.jvnet.hudson.maven3.agent.Maven3Main;
import org.jvnet.hudson.maven3.launcher.Maven31Launcher;
import org.jvnet.hudson.maven3.launcher.Maven3Launcher;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;

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

    private String mavenVersionUsed;

    private transient Object notifyModuleBuildLock = new Object();
    private transient Result effectiveResult;

    public MavenModuleSetBuild(MavenModuleSet job) throws IOException {
        super(job);
    }

    public MavenModuleSetBuild(MavenModuleSet project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    @Override
    protected void onLoad() {
        super.onLoad();
        notifyModuleBuildLock = new Object();
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

        // We need to add M2_HOME and the mvn binary to the PATH so if Maven
        // needs to run Maven it will pick the correct one.
        // This can happen if maven calls ANT which itself calls Maven
        // or if Maven calls itself e.g. maven-release-plugin
        MavenInstallation mvn = project.getMaven();
        if (mvn == null)
            throw new AbortException(Messages.MavenModuleSetBuild_NoMavenConfigured());

        
        mvn = mvn.forEnvironment(envs);
        
        Computer computer = Computer.currentComputer();
        if (computer != null) { // just in case were not in a build
            Node node = computer.getNode(); // TODO should this rather be getBuiltOn()? Cf. JENKINS-18898
            if (node != null) {
                mvn = mvn.forNode(node, log);
                mvn.buildEnvVars(envs);
            }
        }
        
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
        if (isBuilding()) {
            return computeResult();
        }
        synchronized (notifyModuleBuildLock) {
            if (effectiveResult == null) {
                effectiveResult = computeResult();
            }
            return effectiveResult;
        }
    }

    private Result computeResult() {
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
            private static final long serialVersionUID = 5572368347535713298L;
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
                    if (FilenameUtils.separatorsToUnix(path).startsWith(normalizePath(sub.getRelativePath())))
                        return true;
                return false;
            }

            /**
             * Does this change happen somewhere in the given module or its descendants?
             */
            private boolean isDescendantOf(ChangeLogSet.Entry e, MavenModule mod) {
                for (String path : e.getAffectedPaths()) {
                    if (FilenameUtils.separatorsToUnix(path).startsWith(normalizePath(mod.getRelativePath())))
                        return true;
                }
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
    
    /**
     * Returns the estimated duration for this builds.
     * Takes only the modules into account which are actually being build in
     * case of incremental builds.
     * 
     * @return the estimated duration in milliseconds
     * @since 1.383
     */
    @Override
    public long getEstimatedDuration() {
        
        if (!project.isIncrementalBuild()) {
            return super.getEstimatedDuration();
        }

        long result = 0;
        
        Map<MavenModule, List<MavenBuild>> moduleBuilds = getModuleBuilds();
        
        boolean noModuleBuildsYet = true; 
        
        for (List<MavenBuild> builds : moduleBuilds.values()) {
            if (!builds.isEmpty()) {
                noModuleBuildsYet = false;
                MavenBuild build = builds.get(0);
                if (build.getResult() != Result.NOT_BUILT && build.getEstimatedDuration() != -1) {
                    result += build.getEstimatedDuration();
                }
            }
        }
        
        if (noModuleBuildsYet) {
            // modules not determined, yet, i.e. POM not parsed.
            // Use best estimation we have:
            return super.getEstimatedDuration();
        }
        
        result += estimateModuleSetBuildDurationOverhead(3);
        
        return result != 0 ? result : -1;
    }

    /**
     * Estimates the duration overhead the {@link MavenModuleSetBuild} itself adds
     * to the sum of durations of the module builds.
     */
    private long estimateModuleSetBuildDurationOverhead(int numberOfBuilds) {
        List<MavenModuleSetBuild> moduleSetBuilds = getParent().getEstimatedDurationCandidates();
        
        if (moduleSetBuilds.isEmpty()) {
            return 0;
        }
        
        long overhead = 0;
        for(MavenModuleSetBuild moduleSetBuild : moduleSetBuilds) {
            long sumOfModuleBuilds = 0;
            for (List<MavenBuild> builds : moduleSetBuild.getModuleBuilds().values()) {
                if (!builds.isEmpty()) {
                    MavenBuild moduleBuild = builds.get(0);
                    sumOfModuleBuilds += moduleBuild.getDuration();
                }
            }
            
            overhead += Math.max(0, moduleSetBuild.getDuration() - sumOfModuleBuilds);
        }
        
        return Math.round((double)overhead / moduleSetBuilds.size());
    }

    private static String normalizePath(String relPath) {
        relPath = StringUtils.trimToEmpty( relPath );
        if (StringUtils.isEmpty( relPath )) {
            LOGGER.config("No need to normalize an empty path.");
        } else {
            if(FilenameUtils.indexOfLastSeparator( relPath ) == -1) {
                LOGGER.config("No need to normalize "+relPath);
            } else {
                String tmp = FilenameUtils.normalize( relPath );
                if(tmp == null) {
                    LOGGER.config("Path " + relPath + " can not be normalized (parent dir is unknown). Keeping as is.");
                } else {
                    LOGGER.config("Normalized path " + relPath + " to "+tmp);
                    relPath = tmp;
                }
                relPath = FilenameUtils.separatorsToUnix( relPath );
            }
        }
        LOGGER.fine("Returning path " + relPath);
        return relPath;
    }

    /**
     * Gets the version of Maven used for build.
     *
     * @return
     *      null if this build is done by earlier version of Jenkins that didn't record this information
     *      (this means the build was done by Maven2.x)
     */
    @Exported
    public String getMavenVersionUsed() {
        return mavenVersionUsed;
    }

    public void setMavenVersionUsed( String mavenVersionUsed ) throws IOException {
        this.mavenVersionUsed = Util.intern(mavenVersionUsed);
        save();
    }

    @Override
    public synchronized void delete() throws IOException {
        super.delete();
        // Delete all contained module builds too
        for (List<MavenBuild> list : getModuleBuilds().values())
            for (MavenBuild build : list)
                build.delete();
    }

    @Override
    public synchronized void deleteArtifacts() throws IOException {
    	super.deleteArtifacts();
    	for (List<MavenBuild> list : getModuleBuilds().values())
            for (MavenBuild build : list)
                build.deleteArtifacts();
    }
    
    @Override
    public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        // map corresponding module build under this object
        if(token.indexOf('$')>0) {
            MavenModule m = getProject().getModule(token);
            if(m!=null) return m.getBuildByNumber(getNumber());
        }
        return super.getDynamic(token,req,rsp);
    }

    /**
     * Information about artifacts produced by Maven.
     */
    @Exported
    public MavenAggregatedArtifactRecord getMavenArtifacts() {
        return getAction(MavenAggregatedArtifactRecord.class);
    }

    /**
     * Computes the latest module builds that correspond to this build.
     * (when individual modules are built, a new ModuleSetBuild is not created,
     *  but rather the new module build falls under the previous ModuleSetBuild)
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
        execute(new MavenModuleSetBuildExecution());
        getProject().updateTransientActions();
    }

    @Override
    public Fingerprint.RangeSet getDownstreamRelationship(@SuppressWarnings("rawtypes") AbstractProject that) {
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
            // use a separate lock object since this synchronized block calls into plugins,
            // which in turn can access other MavenModuleSetBuild instances, which will result in a dead lock.
            synchronized(notifyModuleBuildLock) {
                effectiveResult = null;
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
                        addAction(mar);
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
                    moduleFsName, StreamTaskListener.NULL);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,"Failed to update "+this,e);
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING,"Failed to update "+this,e);
        }
    }

    public String getMavenOpts(TaskListener listener, EnvVars envVars) {
        return envVars.expand(expandTokens(listener, project.getMavenOpts()));
    }

    /**
     * The sole job of the {@link MavenModuleSet} build is to update SCM
     * and triggers module builds.
     */
    private class MavenModuleSetBuildExecution extends AbstractBuildExecution {
        private Map<ModuleName,MavenBuild.ProxyImpl2> proxies;

        protected Result doRun(final BuildListener listener) throws Exception {
            
        	Result r = null;
        	PrintStream logger = listener.getLogger();

            try {
            	
                EnvVars envVars = getEnvironment(listener);
                MavenInstallation mvn = project.getMaven();
                if(mvn==null)
                    throw new AbortException(Messages.MavenModuleSetBuild_NoMavenConfigured());

                mvn = mvn.forEnvironment(envVars).forNode(Computer.currentComputer().getNode(), listener);
                
                MavenInformation mavenInformation = getModuleRoot().act( new MavenVersionCallable( mvn.getHome() ));
                
                String mavenVersion = mavenInformation.getVersion();
                
                MavenBuildInformation mavenBuildInformation = new MavenBuildInformation( mavenVersion );
                
                setMavenVersionUsed( mavenVersion );

                LOGGER.fine(getFullDisplayName()+" is building with mavenVersion " + mavenVersion + " from file " + mavenInformation.getVersionResourcePath());
                
                if(!project.isAggregatorStyleBuild()) {
                    parsePoms(listener, logger, envVars, mvn, mavenVersion, mavenBuildInformation);
                    // start module builds
                    logger.println("Triggering "+project.getRootModule().getModuleName());
                    project.getRootModule().scheduleBuild(new UpstreamCause((Run<?,?>)MavenModuleSetBuild.this));
                } else {
                    // do builds here
                    try {
                        List<BuildWrapper> wrappers = new ArrayList<BuildWrapper>();
                        for (BuildWrapper w : project.getBuildWrappersList())
                            wrappers.add(w);
                        ParametersAction parameters = getAction(ParametersAction.class);
                        if (parameters != null)
                            parameters.createBuildWrappers(MavenModuleSetBuild.this,wrappers);

                        for( BuildWrapper w : wrappers) {
                            Environment e = w.setUp(MavenModuleSetBuild.this, launcher, listener);
                            if(e==null){
                                setResult(r = Result.FAILURE);
                                return r;
                            }
                            buildEnvironments.add(e);
                            e.buildEnvVars(envVars); // #3502: too late for getEnvironment to do this
                        }
                        
                    	// run pre build steps
                    	if(!preBuild(listener,project.getPrebuilders())
                        || !preBuild(listener,project.getPostbuilders())
                        || !preBuild(listener,project.getPublishers())){
                    		setResult(r = FAILURE);
                            return r;
                    	}

                    	if(!build(listener,project.getPrebuilders().toList())){
                    		setResult(r = FAILURE);
                            return r;
            			}

                        parsePoms(listener, logger, envVars, mvn, mavenVersion, mavenBuildInformation); // #5428 : do pre-build *before* parsing pom
                        SplittableBuildListener slistener = new SplittableBuildListener(listener);
                        proxies = new HashMap<ModuleName, ProxyImpl2>();
                        List<ModuleName> changedModules = new ArrayList<ModuleName>();
                        
                        if (project.isIncrementalBuild() && !getChangeSet().isEmptySet()) {
                            changedModules.addAll(getUnbuildModulesSinceLastSuccessfulBuild());
                        }

                        for (MavenModule m : project.sortedActiveModules) {
                            MavenBuild mb = m.newBuild();
                            // JENKINS-8418
                            mb.setBuiltOnStr( getBuiltOnStr() );
                            // Check if incrementalBuild is selected and that there are changes -
                            // we act as if incrementalBuild is not set if there are no changes.
                            if (!MavenModuleSetBuild.this.getChangeSet().isEmptySet()
                                && project.isIncrementalBuild()) {
                                //If there are changes for this module, add it.
                                // Also add it if we've never seen this module before,
                                // or if the previous build of this module failed or was unstable.
                                if ((mb.getPreviousBuiltBuild() == null) ||
                                    (!getChangeSetFor(m).isEmpty()) 
                                    || (mb.getPreviousBuiltBuild().getResult().isWorseThan(Result.SUCCESS))) {
                                    changedModules.add(m.getModuleName());
                                }
                            }

                            mb.setWorkspace(getModuleRoot().child(m.getRelativePath()));
                            proxies.put(m.getModuleName(), mb.new ProxyImpl2(MavenModuleSetBuild.this,slistener));
                        }

                        // run the complete build here

                        // figure out the root POM location.
                        // choice of module root ('ws' in this method) is somewhat arbitrary
                        // when multiple CVS/SVN modules are checked out, so also check
                        // the path against the workspace root if that seems like what the user meant (see issue #1293)
                        String rootPOM = project.getRootPOM(envVars); // JENKINS-13822
                        FilePath pom = getModuleRoot().child(rootPOM);
                        FilePath parentLoc = getWorkspace().child(rootPOM);
                        if(!pom.exists() && parentLoc.exists())
                            pom = parentLoc;

                        
                        final ProcessCache.MavenProcess process;
                        
                        boolean maven3orLater = mavenBuildInformation.isMaven3OrLater();

                        MavenUtil.MavenVersion mavenVersionType = MavenUtil.getMavenVersion( mavenVersion );

                        final ProcessCache.Factory factory;

                        Class<?> maven3MainClass = null;

                        Class<?> maven3LauncherClass = null;

                        switch ( mavenVersionType ){
                            case MAVEN_2:
                                LOGGER.fine( "using maven 2 " + mavenVersion );
                                factory = new MavenProcessFactory( project, MavenModuleSetBuild.this, launcher, envVars,getMavenOpts(listener, envVars),
                                                                  pom.getParent() );
                                break;
                            case MAVEN_3_0_X:
                                LOGGER.fine( "using maven 3 " + mavenVersion );
                                factory = new Maven3ProcessFactory( project, MavenModuleSetBuild.this, launcher, envVars, getMavenOpts(listener, envVars),
                                                                    pom.getParent() );
                                maven3MainClass = Maven3Main.class;
                                maven3LauncherClass = Maven3Launcher.class;
                                break;
                            default:
                                LOGGER.fine( "using maven 3 " + mavenVersion );
                                factory = new Maven31ProcessFactory( project, MavenModuleSetBuild.this, launcher, envVars, getMavenOpts(listener, envVars),
                                                                    pom.getParent() );
                                maven3MainClass = Maven31Main.class;
                                maven3LauncherClass = Maven31Launcher.class;
                        }

                        process = MavenBuild.mavenProcessCache.get( launcher.getChannel(), slistener, factory);


                        ArgumentListBuilder margs = new ArgumentListBuilder().add("-B").add("-f", pom.getRemote());
                        FilePath localRepo = project.getLocalRepository().locate(MavenModuleSetBuild.this);
                        if(localRepo!=null)
                            margs.add("-Dmaven.repo.local="+localRepo.getRemote());

                        FilePath remoteSettings = SettingsProvider.getSettingsFilePath(project.getSettings(), MavenModuleSetBuild.this, listener);
                        if (remoteSettings != null)
                            margs.add("-s" , remoteSettings.getRemote());

                        FilePath remoteGlobalSettings = GlobalSettingsProvider.getSettingsFilePath(project.getGlobalSettings(), MavenModuleSetBuild.this, listener);
                        if (remoteGlobalSettings != null)
                            margs.add("-gs" , remoteGlobalSettings.getRemote());
                        
                        // If incrementalBuild is set
                        // and the previous build didn't specify that we need a full build
                        // and we're on Maven 2.1 or later
                        // and there's at least one module listed in changedModules,
                        // then do the Maven incremental build commands.
                        // If there are no changed modules, we're building everything anyway.
                        boolean maven2_1orLater = new ComparableVersion (mavenVersion).compareTo( new ComparableVersion ("2.1") ) >= 0;
                        boolean needsFullBuild = getPreviousCompletedBuild() != null &&
                            getPreviousCompletedBuild().getAction(NeedsFullBuildAction.class) != null;
                        if (project.isIncrementalBuild()) {
                            if (!needsFullBuild && maven2_1orLater && !changedModules.isEmpty()) {
                                margs.add("-amd");
                                margs.add("-pl", Util.join(changedModules, ","));
                            } else {
                                if (LOGGER.isLoggable(Level.FINE)) {
                                    LOGGER.fine(String.format("Skipping incremental build: needsFullBuild=%s, maven2.1orLater=%s, changedModulesEmpty?=%s",
                                            needsFullBuild, maven2_1orLater, changedModules.isEmpty()));
                                }
                            }
                        }


                        
                        final List<MavenArgumentInterceptorAction> argInterceptors = this.getBuild().getActions(MavenArgumentInterceptorAction.class);
                        
						// find the correct maven goals and options, there might by an action overruling the defaults
                        String goals = project.getGoals(); // default
                        for (MavenArgumentInterceptorAction mavenArgInterceptor : argInterceptors) {
                        	final String goalsAndOptions = mavenArgInterceptor.getGoalsAndOptions((MavenModuleSetBuild)this.getBuild());
							if(StringUtils.isNotBlank(goalsAndOptions)){
                        		goals = goalsAndOptions;
                                // only one interceptor is allowed to overwrite the whole "goals and options" string
                        		break;
                        	}
						}
						margs.addTokenized(envVars.expand(goals));

						// enable the interceptors to change the whole command argument list
						// all available interceptors are allowed to modify the argument list
						for (MavenArgumentInterceptorAction mavenArgInterceptor : argInterceptors) {
							final ArgumentListBuilder newMargs = mavenArgInterceptor.intercept(margs, (MavenModuleSetBuild)this.getBuild());
							if (newMargs != null) {
								margs = newMargs;
							}
						}
                        
                        final AbstractMavenBuilder builder;
                        if (maven3orLater) {
                            Maven3Builder.Maven3BuilderRequest maven3BuilderRequest = new Maven3Builder.Maven3BuilderRequest();
                            maven3BuilderRequest.listener=slistener;
                            maven3BuilderRequest.proxies=proxies;
                            maven3BuilderRequest.modules=project.sortedActiveModules;
                            maven3BuilderRequest.goals=margs.toList();
                            maven3BuilderRequest.systemProps=envVars;
                            maven3BuilderRequest.mavenBuildInformation=mavenBuildInformation;
                            maven3BuilderRequest.maven3MainClass=maven3MainClass;
                            maven3BuilderRequest.maven3LauncherClass=maven3LauncherClass;
                            maven3BuilderRequest.supportEventSpy = MavenUtil.supportEventSpy( mavenVersion );
                            builder = new Maven3Builder(maven3BuilderRequest);
                        } else {
                            builder = 
                                new Maven2Builder(slistener, proxies, project.sortedActiveModules, margs.toList(), envVars, mavenBuildInformation);
                        }
                        
                        MavenProbeAction mpa=null;
                        try {
                            mpa = new MavenProbeAction(project,process.channel);
                            addAction(mpa);
                            r = process.call(builder);
                            for (ProxyImpl2 proxy : proxies.values()) {
                                proxy.performArchiving(launcher, listener);
                            }
                            return r;
                        } finally {
                            builder.end(launcher);
                            getActions().remove(mpa);
                            process.discard();
                        }                            
                        
                    } catch (InterruptedException e) {
                        r = Executor.currentExecutor().abortResult();
                        throw e;
                    } finally {
            			// only run post build steps if requested...
                        if (r==null || r.isBetterOrEqualTo(project.getRunPostStepsIfResult())) {
                            if(!build(listener,project.getPostbuilders().toList())){
                                r = FAILURE;
            				}
            			}
            			
                        if (r != null) {
                            setResult(r);
                        }

                        // tear down in reverse order
                        boolean failed=false;
                        for( int i=buildEnvironments.size()-1; i>=0; i-- ) {
                            if (!buildEnvironments.get(i).tearDown(MavenModuleSetBuild.this,listener)) {
                                failed=true;
                            }                    
                        }
                        // WARNING The return in the finally clause will trump any return before
                        if (failed) return Result.FAILURE;
                    }
                }
                
                
                return r;
            } catch (AbortException e) {
                if(e.getMessage()!=null)
                    listener.error(e.getMessage());
                return Result.FAILURE;
            } catch (InterruptedIOException e) {
                e.printStackTrace(listener.error("Aborted Maven execution for InterruptedIOException"));
                return Executor.currentExecutor().abortResult();
            } catch (IOException e) {
                e.printStackTrace(listener.error(Messages.MavenModuleSetBuild_FailedToParsePom()));
                return Result.FAILURE;
            } catch (RunnerAbortedException e) {
                return Result.FAILURE;
            } catch (RuntimeException e) {
                // bug in the code.
                e.printStackTrace(listener.error("Processing failed due to a bug in the code. Please report this to jenkinsci-users@googlegroups.com"));
                logger.println("project="+project);
                logger.println("project.getModules()="+project.getModules());
                logger.println("project.getRootModule()="+project.getRootModule());
                throw e;
            } finally {
            }
        }

        
        private boolean build(BuildListener listener, Collection<hudson.tasks.Builder> steps) throws IOException, InterruptedException {
            for( BuildStep bs : steps ){
                if(!perform(bs,listener)) {
                	LOGGER.fine(MessageFormat.format("{1} failed", bs));
                    return false;
                }
            }
            return true;
        }

        /**
         * Returns the modules which have not been build since the last successful aggregator build
         * though they should be because they had SCM changes.
         * This can happen when the aggregator build fails before it reaches the module.
         * 
         * See JENKINS-5764
         */
        private Collection<ModuleName> getUnbuildModulesSinceLastSuccessfulBuild() {
            Collection<ModuleName> unbuiltModules = new ArrayList<ModuleName>();
            MavenModuleSetBuild previousSuccessfulBuild = getPreviousSuccessfulBuild();
            if (previousSuccessfulBuild == null) {
                // no successful build, yet. Just take the 1st build
                previousSuccessfulBuild = getParent().getFirstBuild();
            }
            
            if (previousSuccessfulBuild != null) {
                MavenModuleSetBuild previousBuild = previousSuccessfulBuild;
                do {
                    UnbuiltModuleAction unbuiltModuleAction = previousBuild.getAction(UnbuiltModuleAction.class);
                    if (unbuiltModuleAction != null) {
                        for (ModuleName name : unbuiltModuleAction.getUnbuildModules()) {
                            unbuiltModules.add(name);
                        }
                    }
                    
                    previousBuild = previousBuild.getNextBuild();
                } while (previousBuild != null && previousBuild != MavenModuleSetBuild.this);
            }
            return unbuiltModules;
        }

        private void parsePoms(BuildListener listener, PrintStream logger, EnvVars envVars, MavenInstallation mvn, String mavenVersion, MavenBuildInformation mavenBuildInformation) throws IOException, InterruptedException {
            logger.println("Parsing POMs");

            List<PomInfo> poms;
            try {
                PomParser.Result result = getModuleRoot().act(new PomParser(listener, mvn, mavenVersion, envVars, MavenModuleSetBuild.this));
                poms = result.infos;
                mavenBuildInformation.modelParents.putAll(result.modelParents);
            } catch (IOException e) {
                if (project.isIncrementalBuild()) {
                    // If POM parsing failed we should do a full build next time.
                    // Otherwise only the modules which have a SCM change for the next build might
                    // be build next time.
                    getActions().add(new NeedsFullBuildAction());
                }
                
                if (e.getCause() instanceof AbortException)
                    throw (AbortException) e.getCause();
                throw e;
            } catch (MavenExecutionException e) {
                // Maven failed to parse POM
                e.getCause().printStackTrace(listener.error(Messages.MavenModuleSetBuild_FailedToParsePom()));
                if (project.isIncrementalBuild()) {
                    getActions().add(new NeedsFullBuildAction());
                }
                throw new AbortException();
            }
            
            boolean needsDependencyGraphRecalculation = false;

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
                        if (!mm.isSameModule(pom)) {
                            needsDependencyGraphRecalculation = true;
                        }
                        mm.reconfigure(pom);
                        modules.put(pom.name,mm);
                    } else {// this looks like a new module
                        logger.println(Messages.MavenModuleSetBuild_DiscoveredModule(pom.name,pom.displayName));
                        mm = new MavenModule(project,pom,getNumber());
                        mm.onCreatedFromScratch();
                        modules.put(mm.getModuleName(),mm);
                        needsDependencyGraphRecalculation = true;
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
                    needsDependencyGraphRecalculation = true;
                }
                modules.putAll(old);
            }

            // we might have added new modules
            if (needsDependencyGraphRecalculation) {
                logger.println("Modules changed, recalculating dependency graph");
                Jenkins.getInstance().rebuildDependencyGraph();
            }

            // module builds must start with this build's number
            for (MavenModule m : modules.values())
                m.updateNextBuildNumber(getNumber());
        }

        protected void post2(BuildListener listener) throws Exception {
            // asynchronous executions from the build might have left some unsaved state,
            // so just to be safe, save them all.
            for (MavenBuild b : getModuleLastBuilds().values())
                b.save();

            // at this point the result is all set, so ignore the return value
            if (!performAllBuildSteps(listener, project.getPublishers(), true))
                setResult(FAILURE);
            if (!performAllBuildSteps(listener, project.getProperties(), true))
                setResult(FAILURE);

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
            MavenMailer mailer = project.getReporters().get(MavenMailer.class);
            if (mailer != null) {
                new MailSender(mailer.recipients,
                        mailer.dontNotifyEveryUnstableBuild,
                        mailer.sendToIndividuals).execute(MavenModuleSetBuild.this, listener);
            }

            // too late to set the build result at this point. so ignore failures.
            performAllBuildSteps(listener, project.getPublishers(), false);
            performAllBuildSteps(listener, project.getProperties(), false);
            super.cleanUp(listener);
        }

    }

    /**
     * Used to tunnel exception from Maven through remoting.
     */
    private static final class MavenExecutionException extends RuntimeException {
        private MavenExecutionException(Exception cause) {
            super(cause);
        }

        @Override
        public Exception getCause() {
            return (Exception)super.getCause();
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Executed on the slave to parse POM and extract information into {@link PomInfo},
     * which will be then brought back to the master.
     */
    private static final class PomParser implements FileCallable<PomParser.Result> {
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
        private final String globalSettings;
        private final boolean nonRecursive;
        // We're called against the module root, not the workspace, which can cause a lot of confusion.
        private final String workspaceProper;
        private final String mavenVersion;
        
        private final String moduleRootPath;
        
        private boolean resolveDependencies = false;
  
        private boolean processPlugins = false;
        
        private int mavenValidationLevel = -1;
        
        private boolean updateSnapshots = false;
        
        String rootPOMRelPrefix;

        private final PlexusModuleContributor plexusContributors;
        
        PomParser(BuildListener listener, MavenInstallation mavenHome, String mavenVersion, EnvVars envVars, MavenModuleSetBuild build) throws IOException, InterruptedException {
            // project cannot be shipped to the remote JVM, so all the relevant properties need to be captured now.
            MavenModuleSet project = build.getProject();
            this.listener = listener;
            this.mavenHome = mavenHome;
            this.rootPOM = project.getRootPOM(envVars); // JENKINS-13822
            this.profiles = project.getProfiles();
            this.properties = project.getMavenProperties();
            this.updateSnapshots = isUpdateSnapshots(project.getGoals());
            ParametersDefinitionProperty parametersDefinitionProperty = project.getProperty( ParametersDefinitionProperty.class );
            if (parametersDefinitionProperty != null && parametersDefinitionProperty.getParameterDefinitions() != null) {
                for (ParameterDefinition parameterDefinition : parametersDefinitionProperty.getParameterDefinitions()) {
                    // those must used as env var
                    if (parameterDefinition instanceof StringParameterDefinition) {
                        this.properties.put( "env." + parameterDefinition.getName(), ((StringParameterDefinition)parameterDefinition).getDefaultValue() );
                    }
                }
            }
            if (envVars != null && !envVars.isEmpty()) {
                for (Entry<String,String> entry : envVars.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        this.properties.put( "env." + entry.getKey(), entry.getValue() );
                    }
                }
            }
            
            this.nonRecursive = project.isNonRecursive();

            this.workspaceProper = build.getWorkspace().getRemote();
            LOGGER.fine("Workspace is " + workspaceProper);
            FilePath localRepo = project.getLocalRepository().locate(build);
            if (localRepo!=null) {
                this.privateRepository = localRepo.getRemote();
            } else {
                this.privateRepository = null;
            }
            
            this.alternateSettings = SettingsProvider.getSettingsRemotePath(project.getSettings(), build, listener);
            this.globalSettings = GlobalSettingsProvider.getSettingsRemotePath(project.getGlobalSettings(), build, listener);
            
            this.mavenVersion = mavenVersion;
            this.resolveDependencies = project.isResolveDependencies();
            this.processPlugins = project.isProcessPlugins();
            
            this.moduleRootPath = 
                project.getScm().getModuleRoot( build.getWorkspace(), project.getLastBuild() ).getRemote();
            
            this.mavenValidationLevel = project.getMavenValidationLevel();
            plexusContributors = PlexusModuleContributorFactory.aggregate(build);
        }

        private boolean isUpdateSnapshots(String goals) {
          return StringUtils.contains(goals, "-U") || StringUtils.contains(goals, "--update-snapshots");
        }

        public static final class Result implements Serializable {
            public final List<PomInfo> infos;
            public final Map<String,String> modelParents;
            public Result(List<PomInfo> infos, Map<String,String> modelParents) {
                this.infos = infos;
                this.modelParents = modelParents;
            }
        }

        public Result invoke(File ws, VirtualChannel channel) throws IOException {
            File pom;
            
            PrintStream logger = listener.getLogger();

            if (IOUtils.isAbsolute(rootPOM)) {
                pom = new File(rootPOM);
            } else {
                // choice of module root ('ws' in this method) is somewhat arbitrary
                // when multiple CVS/SVN modules are checked out, so also check
                // the path against the workspace root if that seems like what the user meant (see issue #1293)
                pom = new File(ws, rootPOM);
                File parentLoc = new File(ws.getParentFile(),rootPOM);
                if(!pom.exists() && parentLoc.exists())
                    pom = parentLoc;
            }

            if(!pom.exists())
                throw new AbortException(Messages.MavenModuleSetBuild_NoSuchPOMFile(pom));

            if (rootPOM.startsWith("../") || rootPOM.startsWith("..\\")) {
                File wsp = new File(workspaceProper);
                               
                if (!ws.equals(wsp)) {
                    rootPOMRelPrefix = ws.getCanonicalPath().substring(wsp.getCanonicalPath().length()+1)+"/";
                } else {
                    rootPOMRelPrefix = wsp.getName() + "/";
                }
            } else {
                rootPOMRelPrefix = "";
            }            
            
            if(verbose)
                logger.println("Parsing "
			       + (nonRecursive ? "non-recursively " : "recursively ")
			       + pom);
	    
            File settingsLoc;

            if (alternateSettings == null) {
                settingsLoc = null;
            } else if (IOUtils.isAbsolute(alternateSettings)) {
                settingsLoc = new File(alternateSettings);
            } else {
                // Check for settings.xml first in the workspace proper, and then in the current directory,
                // which is getModuleRoot().
                // This is backwards from the order the root POM logic uses, but it's to be consistent with the Maven execution logic.
                settingsLoc = new File(workspaceProper, alternateSettings);
                File mrSettingsLoc = new File(workspaceProper, alternateSettings);
                if (!settingsLoc.exists() && mrSettingsLoc.exists())
                    settingsLoc = mrSettingsLoc;
            }
            if (debug)
            {
                logger.println(Messages.MavenModuleSetBuild_SettinsgXmlAndPrivateRepository(settingsLoc,privateRepository));
            }
            if ((settingsLoc != null) && (!settingsLoc.exists())) {
                throw new AbortException(Messages.MavenModuleSetBuild_NoSuchAlternateSettings(settingsLoc.getAbsolutePath()));
            }

            try {
                MavenEmbedderRequest mer = new MavenEmbedderRequest( listener, mavenHome.getHomeDir(),
                                                                                      profiles, properties,
                                                                                      privateRepository, settingsLoc );
                mer.setTransferListener(new SimpleTransferListener(listener));
                mer.setUpdateSnapshots(this.updateSnapshots);
                
                mer.setProcessPlugins(this.processPlugins);
                mer.setResolveDependencies(this.resolveDependencies);
                if (globalSettings != null) {
                    mer.setGlobalSettings(new File(globalSettings));
                }
                
                // FIXME handle 3.1 level when version will be here : no rush :-)
                // or made something configurable tru the ui ?
                ReactorReader reactorReader = null;
                boolean maven3OrLater = MavenUtil.maven3orLater(mavenVersion);
                if (maven3OrLater) {
                    mer.setValidationLevel(VALIDATION_LEVEL_MAVEN_3_0);
                } else {
                    reactorReader = new ReactorReader( new HashMap<String, MavenProject>(), new File(workspaceProper) );
                    mer.setWorkspaceReader(reactorReader);
                }

                {// create a classloader that loads extensions
                    List<URL> urls = plexusContributors.getPlexusComponentJars();
                    if (!urls.isEmpty()) {
                        mer.setClassLoader(
                                new URLClassLoader(urls.toArray(new URL[urls.size()]),
                                        mer.getClassLoader()));
                    }
                }
                
                if (this.mavenValidationLevel >= 0) {
                    mer.setValidationLevel(this.mavenValidationLevel);
                }
                
                //mavenEmbedderRequest.setClassLoader( MavenEmbedderUtils.buildClassRealm( mavenHome.getHomeDir(), null, null ) );
                
                MavenEmbedder embedder = MavenUtil.createEmbedder( mer );
                
                MavenProject rootProject = null;
                
                List<MavenProject> mps = new ArrayList<MavenProject>(0);
                if (maven3OrLater) {
                    mps = embedder.readProjects( pom,!this.nonRecursive );

                } else {
                    // http://issues.jenkins-ci.org/browse/HUDSON-8390
                    // we cannot read maven projects in one time for backward compatibility
                    // but we have to use a ReactorReader to get some pom with bad inheritence configured
                    MavenProject mavenProject = embedder.readProject( pom );
                    rootProject = mavenProject;
                    mps.add( mavenProject );
                    reactorReader.addProject( mavenProject );
                    if (!this.nonRecursive) {
                        readChilds( mavenProject, embedder, mps, reactorReader );
                    }
                }
                Map<String,MavenProject> canonicalPaths = new HashMap<String, MavenProject>( mps.size() );
                Map<String,String> modelParents = new HashMap<String,String>();
                for(MavenProject mp : mps) {
                    // Projects are indexed by POM path and not module path because
                    // Maven allows to have several POMs with different names in the same directory
                    canonicalPaths.put( mp.getFile().getCanonicalPath(), mp );
                    while (true) {
                        String k = mp.getId();
                        if (modelParents.containsKey(k)) {
                            break;
                        }
                        MavenProject mpp = mp.getParent();
                        if (mpp == null) {
                            break;
                        }
                        modelParents.put(k, mpp.getId());
                        mp = mpp;
                    }
                }                
                //MavenUtil.resolveModules(embedder,mp,getRootPath(rootPOMRelPrefix),relPath,listener,nonRecursive);

                if(verbose) {
                    for (Entry<String,MavenProject> e : canonicalPaths.entrySet())
                        logger.printf("Discovered %s at %s\n",e.getValue().getId(),e.getKey());
                }

                Set<PomInfo> infos = new LinkedHashSet<PomInfo>();
                
                if (maven3OrLater) {
                    for (MavenProject mp : mps) {
                        if (mp.isExecutionRoot()) {
                            rootProject = mp;
                            continue;
                        }
                    }
                }
                // if rootProject is null but no reason :-) use the first one
                if (rootProject == null) {
                    rootProject = mps.get( 0 );
                }
                toPomInfo(rootProject,null,canonicalPaths,infos);

                for (PomInfo pi : infos)
                    pi.cutCycle();

                return new Result(new ArrayList<PomInfo>(infos), modelParents);
            } catch (MavenEmbedderException e) {
                throw new MavenExecutionException(e);
            } catch (ProjectBuildingException e) {
                throw new MavenExecutionException(e);
            }
        }

        /**
         * @see PomInfo#relativePath to understand relPath calculation
         */
        private void toPomInfo(MavenProject mp, PomInfo parent, Map<String,MavenProject> abslPath, Set<PomInfo> infos) throws IOException {
            
            String relPath = PathTool.getRelativeFilePath( this.moduleRootPath, mp.getBasedir().getPath() );
            relPath = normalizePath(relPath);

            if (parent == null ) {
                relPath = getRootPath(rootPOMRelPrefix);
            }
            
            relPath = StringUtils.removeStart( relPath, "/" );
            
            PomInfo pi = new PomInfo(mp, parent, relPath);
            infos.add(pi);
            if(!this.nonRecursive) {
                for (String modulePath : mp.getModules())
                {
                    if (StringUtils.isBlank( modulePath )) {
                        continue;
                    }
                    File path = new File(mp.getBasedir(), modulePath);
                    // HUDSON-8391 : Modules are indexed by POM path thus
                    // by default we have to add the default pom.xml file
                    if(path.isDirectory())
                      path = new File(mp.getBasedir(), modulePath+"/pom.xml");
                    MavenProject child = abslPath.get( path.getCanonicalPath());
                    if (child == null) {
                        listener.getLogger().printf(Messages.MavenModuleSetBuild_FoundModuleWithoutProject(modulePath));
                        continue;
                    }
                    toPomInfo(child,pi,abslPath,infos);
                }
            }
        }
        
        private void readChilds(MavenProject mp, MavenEmbedder mavenEmbedder, List<MavenProject> mavenProjects, ReactorReader reactorReader) 
            throws ProjectBuildingException, MavenEmbedderException {
            if (mp.getModules() == null || mp.getModules().isEmpty()) {
                return;
            }
            for (String module : mp.getModules()) {
                if ( Util.fixEmptyAndTrim( module ) != null ) {
                    File pomFile = new File(mp.getFile().getParent(), module);
                    MavenProject mavenProject2 = null;
                    // take care of HUDSON-8445
                    if (pomFile.isFile())
                        mavenProject2 = mavenEmbedder.readProject( pomFile );
                    else
                        mavenProject2 = mavenEmbedder.readProject( new File(mp.getFile().getParent(), module + "/pom.xml") );
                    mavenProjects.add( mavenProject2 );
                    reactorReader.addProject( mavenProject2 );
                    readChilds( mavenProject2, mavenEmbedder, mavenProjects, reactorReader );
                }
            }
        }
        
        /**
         * Computes the path of {@link #rootPOM}.
         *
         * Returns "abc" if rootPOM="abc/pom.xml"
         * If rootPOM="pom.xml", this method returns "".
         */
        private String getRootPath(String prefix) {
            int idx = Math.max(rootPOM.lastIndexOf('/'), rootPOM.lastIndexOf('\\'));
            if(idx==-1) return "";
            return prefix + rootPOM.substring(0,idx);
        }
        

        private static final long serialVersionUID = 1L;
    }
        
    private static final Logger LOGGER = Logger.getLogger(MavenModuleSetBuild.class.getName());

    /**
     * Extra verbose debug switch.
     */
    public static boolean debug = Boolean.getBoolean( "hudson.maven.debug" );

    @Override
    public MavenModuleSet getParent() {// don't know why, but javac wants this
        return super.getParent();
    }
    
    /**
     * will log in the {@link TaskListener} when transferFailed and transferSucceeded
     * @author Olivier Lamy
     * @since 
     */
    public static class SimpleTransferListener implements TransferListener
    {
        private TaskListener taskListener;
        public SimpleTransferListener(TaskListener taskListener)
        {
            this.taskListener = taskListener;
        }

        public void transferCorrupted( TransferEvent arg0 )
            throws TransferCancelledException
        {
            // no op
        }

        public void transferFailed( TransferEvent transferEvent )
        {
            taskListener.getLogger().println(Messages.MavenModuleSetBuild_FailedToTransfer(transferEvent.getException().getMessage()));
        }

        public void transferInitiated( TransferEvent arg0 )
            throws TransferCancelledException
        {
            // no op
        }

        public void transferProgressed( TransferEvent arg0 )
            throws TransferCancelledException
        {
            // no op            
        }

        public void transferStarted( TransferEvent arg0 )
            throws TransferCancelledException
        {
            // no op
        }

        public void transferSucceeded( TransferEvent transferEvent )
        {
            taskListener.getLogger().println( Messages.MavenModuleSetBuild_DownloadedArtifact(
                    transferEvent.getResource().getRepositoryUrl(),
                    transferEvent.getResource().getResourceName()) );
        }
        
    }
}
