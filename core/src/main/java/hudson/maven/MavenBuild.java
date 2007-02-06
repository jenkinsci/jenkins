package hudson.maven;

import hudson.FilePath;
import hudson.Util;
import hudson.FilePath.FileCallable;
import hudson.maven.PluginManagerInterceptor.AbortException;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.DependencyGraph;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.model.Run;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.util.ArgumentListBuilder;
import org.apache.maven.BuildFailureException;
import org.apache.maven.embedder.MavenEmbedderException;
import org.apache.maven.embedder.PlexusLoggerAdapter;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.monitor.event.DefaultEventMonitor;
import org.apache.maven.monitor.event.EventMonitor;
import org.apache.maven.plugin.PluginManager;
import org.apache.maven.project.DuplicateProjectException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.dag.CycleDetectedException;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
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
     */
    public MavenModuleSetBuild getParentBuild() {
        return getParent().getParent().getBuildByNumber(getNumber());
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

    @Override
    public AbstractTestResultAction getTestResultAction() {
        return getAction(AbstractTestResultAction.class);
    }

    public void registerAsProjectAction(MavenReporter reporter) {
        if(projectActionReporters==null)
            projectActionReporters = new ArrayList<MavenReporter>();
        projectActionReporters.add(reporter);
    }
    
    @Override
    public void run() {
        run(new RunnerImpl());
        getProject().updateTransientActions();
    }

    /**
     * Runs Maven and builds the project.
     *
     * This code is executed on the remote machine.
     */
    private static final class Builder implements FileCallable<Result> {
        private final BuildListener listener;
        private final MavenBuildProxy buildProxy;
        private final MavenReporter[] reporters;
        private final List<String> goals;

        public Builder(BuildListener listener,MavenBuildProxy buildProxy,MavenReporter[] reporters, List<String> goals) {
            this.listener = listener;
            this.buildProxy = buildProxy;
            this.reporters = reporters;
            this.goals = goals;
        }

        public Result invoke(File moduleRoot, VirtualChannel channel) throws IOException {
            MavenProject p=null;
            try {
                MavenEmbedder embedder = MavenUtil.createEmbedder(listener);
                File pom = new File(moduleRoot,"pom.xml").getAbsoluteFile(); // MavenEmbedder only works if it's absolute
                if(!pom.exists()) {
                    listener.error("No POM: "+pom);
                    return Result.FAILURE;
                }

                // event monitor is mostly useless. It only provides a few strings
                EventMonitor eventMonitor = new DefaultEventMonitor( new PlexusLoggerAdapter( new EmbedderLoggerImpl(listener) ) );

                p = embedder.readProject(pom);
                PluginManagerInterceptor interceptor;

                try {
                    interceptor = (PluginManagerInterceptor)embedder.getContainer().lookup(PluginManager.class.getName());
                    interceptor.setBuilder(buildProxy,reporters,listener);
                } catch (ComponentLookupException e) {
                    throw new Error(e); // impossible
                }

                for (MavenReporter r : reporters)
                    r.preBuild(buildProxy,p,listener);

                embedder.execute(p, goals, eventMonitor,
                    new TransferListenerImpl(listener),
                    null, // TODO: allow additional properties to be specified
                    pom.getParentFile());

                interceptor.fireLeaveModule();

                return null;
            } catch (MavenEmbedderException e) {
                buildProxy.setResult(Result.FAILURE);
                e.printStackTrace(listener.error(e.getMessage()));
            } catch (ProjectBuildingException e) {
                buildProxy.setResult(Result.FAILURE);
                e.printStackTrace(listener.error(e.getMessage()));
            } catch (CycleDetectedException e) {
                buildProxy.setResult(Result.FAILURE);
                e.printStackTrace(listener.error(e.getMessage()));
            } catch (LifecycleExecutionException e) {
                buildProxy.setResult(Result.FAILURE);
                e.printStackTrace(listener.error(e.getMessage()));
            } catch (BuildFailureException e) {
                buildProxy.setResult(Result.FAILURE);
                e.printStackTrace(listener.error(e.getMessage()));
            } catch (DuplicateProjectException e) {
                buildProxy.setResult(Result.FAILURE);
                e.printStackTrace(listener.error(e.getMessage()));
            } catch (AbortException e) {
                listener.error("build aborted");
            } catch (InterruptedException e) {
                listener.error("build aborted");
            } finally {
                // this should happen after a build is marked as a failure
                try {
                    if(p!=null)
                        for (MavenReporter r : reporters)
                            r.postBuild(buildProxy,p,listener);
                } catch (InterruptedException e) {
                    buildProxy.setResult(Result.FAILURE);
                }
            }
            return Result.FAILURE;
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

        private Object writeReplace() {
            return Channel.current().export(MavenBuildProxy.class, new ProxyImpl());
        }
    }

    private class RunnerImpl extends AbstractRunner {
        protected Result doRun(BuildListener listener) throws Exception {
            // pick up a list of reporters to run
            List<MavenReporter> reporters = new ArrayList<MavenReporter>();
            getProject().getReporters().addAllTo(reporters);
            for (MavenReporterDescriptor d : MavenReporters.LIST) {
                if(getProject().getReporters().contains(d))
                    continue;   // already configured
                MavenReporter auto = d.newAutoInstance(getProject());
                if(auto!=null)
                    reporters.add(auto);
            }

            ArgumentListBuilder args = new ArgumentListBuilder();
            args.addTokenized(getProject().getGoals());

            return getProject().getModuleRoot().act(new Builder(
                listener,new ProxyImpl(),
                reporters.toArray(new MavenReporter[0]), args.toList()));
        }

        public void post(BuildListener listener) {
            if(!getResult().isWorseThan(Result.UNSTABLE)) {
                // trigger dependency builds
                DependencyGraph graph = Hudson.getInstance().getDependencyGraph();
                for( AbstractProject<?,?> down : getParent().getDownstreamProjects()) {
                    if(graph.hasIndirectDependencies(getParent(),down))
                        // if there's a longer dependency path to this project,
                        // then scheduling the build now is going to be a waste,
                        // so don't do that.
                        // let the longer path eventually trigger this build
                        continue;

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
                            if(isUpstreamBuilding(graph,up)) {
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
         * Returns true if any of the upstream project (or itself) is either
         * building or is in the queue.
         * <p>
         * This means eventually there will be an automatic triggering of
         * the given project (provided that all builds went smoothly.)
         */
        private boolean isUpstreamBuilding(DependencyGraph graph, AbstractProject project) {
            Set<AbstractProject> tups = graph.getTransitiveUpstream(project);
            tups.add(project);
            for (AbstractProject tup : tups) {
                if(tup.isBuilding() || tup.isInQueue())
                    return true;
            }
            return false;
        }
    }
}
