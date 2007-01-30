package hudson.maven;

import hudson.FilePath.FileCallable;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.DependencyGraph;
import hudson.model.Fingerprint.RangeSet;
import hudson.remoting.VirtualChannel;
import hudson.remoting.Channel;
import hudson.util.IOException2;
import hudson.FilePath;
import hudson.maven.PluginManagerInterceptor.AbortException;
import org.apache.maven.BuildFailureException;
import org.apache.maven.plugin.PluginManager;
import org.apache.maven.embedder.MavenEmbedderException;
import org.apache.maven.embedder.PlexusLoggerAdapter;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.monitor.event.DefaultEventMonitor;
import org.apache.maven.monitor.event.EventMonitor;
import org.apache.maven.project.DuplicateProjectException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.codehaus.plexus.util.dag.CycleDetectedException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Calendar;

/**
 * {@link Run} for {@link MavenModule}.
 * 
 * @author Kohsuke Kawaguchi
 */
public class MavenBuild extends AbstractBuild<MavenModule,MavenBuild> {
    public MavenBuild(MavenModule job) throws IOException {
        super(job);
    }

    public MavenBuild(MavenModule job, Calendar timestamp) {
        super(job, timestamp);
    }

    public MavenBuild(MavenModule project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    @Override
    public void run() {
        run(new RunnerImpl());
    }

    @Override
    public RangeSet getDownstreamRelationship(AbstractProject that) {
        // TODO
        throw new UnsupportedOperationException();
    }

    @Override
    public int getUpstreamRelationship(AbstractProject that) {
        // TODO
        throw new UnsupportedOperationException();
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

        public Builder(BuildListener listener,MavenBuildProxy buildProxy,MavenReporter[] reporters) {
            this.listener = listener;
            this.buildProxy = buildProxy;
            this.reporters = reporters;
        }

        public Result invoke(File moduleRoot, VirtualChannel channel) throws IOException {
            try {
                MavenEmbedder embedder = MavenUtil.createEmbedder(listener);
                File pom = new File(moduleRoot,"pom.xml").getAbsoluteFile(); // MavenEmbedder only works if it's absolute
                if(!pom.exists()) {
                    listener.error("No POM: "+pom);
                    return Result.FAILURE;
                }

                // event monitor is mostly useless. It only provides a few strings
                EventMonitor eventMonitor = new DefaultEventMonitor( new PlexusLoggerAdapter( new EmbedderLoggerImpl(listener) ) );

                MavenProject p = embedder.readProject(pom);
                PluginManagerInterceptor interceptor;

                try {
                    interceptor = (PluginManagerInterceptor)embedder.getContainer().lookup(PluginManager.class.getName());
                    interceptor.setBuilder(buildProxy,reporters,listener);
                } catch (ComponentLookupException e) {
                    throw new Error(e); // impossible
                }

                for (MavenReporter r : reporters)
                    r.preBuild(buildProxy,p,listener);

                try {
                    embedder.execute(p, Arrays.asList("install"),
                        eventMonitor,
                        new TransferListenerImpl(listener),
                        null, // TODO: allow additional properties to be specified
                        pom.getParentFile());

                    interceptor.fireLeaveModule();
                } finally {
                    for (MavenReporter r : reporters)
                        r.postBuild(buildProxy,p,listener);
                }

                return Result.SUCCESS;
            } catch (MavenEmbedderException e) {
                throw new IOException2(e);
            } catch (ProjectBuildingException e) {
                throw new IOException2(e);
            } catch (CycleDetectedException e) {
                throw new IOException2(e);
            } catch (LifecycleExecutionException e) {
                throw new IOException2(e);
            } catch (BuildFailureException e) {
                throw new IOException2(e);
            } catch (DuplicateProjectException e) {
                throw new IOException2(e);
            } catch (AbortException e) {
                listener.error("build aborted");
                return Result.FAILURE;
            } catch (InterruptedException e) {
                listener.error("build aborted");
                return Result.FAILURE;
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

        public FilePath getArtifactsDir() {
            return new FilePath(MavenBuild.this.getArtifactsDir());
        }

        private Object writeReplace() {
            return Channel.current().export(MavenBuildProxy.class, new ProxyImpl());
        }
    }

    private class RunnerImpl extends AbstractRunner {
        protected Result doRun(BuildListener listener) throws Exception {
            //if(!preBuild(listener,project.getBuilders()))
            //    return Result.FAILURE;
            //if(!preBuild(listener,project.getPublishers()))
            //    return Result.FAILURE;

            return getProject().getModuleRoot().act(new Builder(listener,new ProxyImpl(),
                getProject().getReporters().toArray(new MavenReporter[0])));
        }

        public void post(BuildListener listener) {
            if(!getResult().isWorseThan(Result.UNSTABLE)) {
                // trigger dependency builds
                DependencyGraph graph = Hudson.getInstance().getDependencyGraph();
                for( AbstractProject down : getParent().getDownstreamProjects()) {
                    if(!graph.hasIndirectDependencies(getParent(),down)) {
                        // if there's a longer dependency path to this project,
                        // then scheduling the build now is going to be a waste,
                        // so don't do that.
                        listener.getLogger().println("Triggering a new build of "+down.getName());
                        down.scheduleBuild();
                    }
                }
            }
            //// run all of them even if one of them failed
            //try {
            //    for( Publisher bs : project.getPublishers().values() )
            //        bs.perform(Build.this, launcher, listener);
            //} catch (InterruptedException e) {
            //    e.printStackTrace(listener.fatalError("aborted"));
            //    setResult(Result.FAILURE);
            //} catch (IOException e) {
            //    e.printStackTrace(listener.fatalError("failed"));
            //    setResult(Result.FAILURE);
            //}
        }
    }
}
