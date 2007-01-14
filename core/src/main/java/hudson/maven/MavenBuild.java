package hudson.maven;

import hudson.FilePath.FileCallable;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.Run;
import hudson.remoting.VirtualChannel;
import hudson.remoting.Channel;
import hudson.util.IOException2;
import hudson.FilePath;
import org.apache.maven.BuildFailureException;
import org.apache.maven.embedder.MavenEmbedder;
import org.apache.maven.embedder.MavenEmbedderException;
import org.apache.maven.embedder.PlexusLoggerAdapter;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.monitor.event.DefaultEventMonitor;
import org.apache.maven.monitor.event.EventMonitor;
import org.apache.maven.project.DuplicateProjectException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.codehaus.plexus.util.dag.CycleDetectedException;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Calendar;

/**
 * {@link Run} for {@link MavenJob}.
 * 
 * @author Kohsuke Kawaguchi
 */
public class MavenBuild extends AbstractBuild<MavenJob,MavenBuild> {
    public MavenBuild(MavenJob job) throws IOException {
        super(job);
    }

    public MavenBuild(MavenJob job, Calendar timestamp) {
        super(job, timestamp);
    }

    public MavenBuild(MavenJob project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    @Override
    public void run() {
        run(new RunnerImpl());
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

                for (MavenReporter r : reporters)
                    r.preBuild(buildProxy,p,listener);

                try {
                    embedder.execute(p, Arrays.asList("install"),
                        eventMonitor,
                        new TransferListenerImpl(listener),
                        null, // TODO: allow additional properties to be specified
                        pom.getParentFile());
                } finally {
                    for (MavenReporter r : reporters)
                        r.postBuild(buildProxy,p,listener);
                }

                return null;
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

        //private boolean build(BuildListener listener, Map<?, Builder> steps) throws IOException, InterruptedException {
        //    for( Builder bs : steps.values() )
        //        if(!bs.perform(Build.this, launcher, listener))
        //            return false;
        //    return true;
        //}

        //private boolean preBuild(BuildListener listener,Map<?,? extends BuildStep> steps) {
        //    for( BuildStep bs : steps.values() )
        //        if(!bs.preBuild(Build.this,listener))
        //            return false;
        //    return true;
        //}
    }
}
