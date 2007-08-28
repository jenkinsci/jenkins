package hudson.maven;

import hudson.maven.agent.AbortException;
import hudson.maven.agent.Main;
import hudson.maven.agent.PluginManagerInterceptor;
import hudson.maven.agent.PluginManagerListener;
import hudson.maven.reporters.SurefireArchiver;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.remoting.Callable;
import hudson.remoting.DelegatingCallable;
import hudson.util.IOException2;
import org.apache.maven.BuildFailureException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ReactorManager;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.lifecycle.LifecycleExecutorInterceptor;
import org.apache.maven.lifecycle.LifecycleExecutorListener;
import org.apache.maven.monitor.event.EventDispatcher;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.classworlds.NoSuchRealmException;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

/**
 * {@link Callable} that invokes Maven CLI (in process) and drives a build.
 *
 * <p>
 * As a callable, this function returns the build result.
 *
 * <p>
 * This class defines a series of event callbacks, which are invoked during the build.
 * This allows subclass to monitor the progress of a build.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.133
 */
public abstract class MavenBuilder implements DelegatingCallable<Result,IOException> {
    /**
     * Goals to be executed in this Maven execution.
     */
    private final List<String> goals;
    /**
     * Hudson-defined system properties. These will be made available to Maven,
     * and accessible as if they are specified as -Dkey=value
     */
    private final Map<String,String> systemProps;
    /**
     * Where error messages and so on are sent.
     */
    protected final BuildListener listener;

    protected MavenBuilder(BuildListener listener, List<String> goals, Map<String, String> systemProps) {
        this.listener = listener;
        this.goals = goals;
        this.systemProps = systemProps;
    }

    /**
     * Called before the whole build.
     */
    abstract void preBuild(MavenSession session, ReactorManager rm, EventDispatcher dispatcher) throws BuildFailureException, LifecycleExecutionException, IOException, InterruptedException;

    /**
     * Called after the build has completed fully.
     */
    abstract void postBuild(MavenSession session, ReactorManager rm, EventDispatcher dispatcher) throws BuildFailureException, LifecycleExecutionException, IOException, InterruptedException;

    /**
     * Called when a build enter another module.
     */
    abstract void preModule(MavenProject project) throws InterruptedException, IOException, AbortException;

    /**
     * Called when a build leaves a module.
     */
    abstract void postModule(MavenProject project) throws InterruptedException, IOException, AbortException;

    /**
     * Called before a mojo is executed
     */
    abstract void preExecute(MavenProject project, MojoInfo mojoInfo) throws IOException, InterruptedException, AbortException;

    /**
     * Called after a mojo has finished executing.
     */
    abstract void postExecute(MavenProject project, MojoInfo mojoInfo, Exception exception) throws IOException, InterruptedException, AbortException;

    /**
     * This code is executed inside the maven jail process.
     */
    public Result call() throws IOException {
        try {
            Adapter a = new Adapter(this);
            PluginManagerInterceptor.setListener(a);
            LifecycleExecutorInterceptor.setListener(a);

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
        } finally {
            PluginManagerInterceptor.setListener(null);
            LifecycleExecutorInterceptor.setListener(null);
        }
    }

    // since reporters might be from plugins, use the uberjar to resolve them.
    public ClassLoader getClassLoader() {
        return Hudson.getInstance().getPluginManager().uberClassLoader;
    }

    /**
     * Receives {@link PluginManagerListener} and {@link LifecycleExecutorListener}
     * and converts them to {@link MavenBuilder}.
     */
    private static final class Adapter implements PluginManagerListener, LifecycleExecutorListener {
        /**
         * Used to detect when to fire {@link MavenReporter#enterModule}
         */
        private MavenProject lastModule;

        private final MavenBuilder listener;

        public Adapter(MavenBuilder listener) {
            this.listener = listener;
        }

        public void preBuild(MavenSession session, ReactorManager rm, EventDispatcher dispatcher) throws BuildFailureException, LifecycleExecutionException, IOException, InterruptedException {
            listener.preBuild(session, rm, dispatcher);
        }


        public void postBuild(MavenSession session, ReactorManager rm, EventDispatcher dispatcher) throws BuildFailureException, LifecycleExecutionException, IOException, InterruptedException {
            fireLeaveModule();
            listener.postBuild(session, rm, dispatcher);
        }

        public void endModule() throws InterruptedException, IOException {
            fireLeaveModule();
        }

        public void preExecute(MavenProject project, MojoExecution exec, PlexusConfiguration mergedConfig, ExpressionEvaluator eval) throws IOException, InterruptedException {
            if(lastModule!=project) {
                // module change
                fireLeaveModule();
                fireEnterModule(project);
            }

            listener.preExecute(project, new MojoInfo(exec, mergedConfig, eval));
        }

        public void postExecute(MavenProject project, MojoExecution exec, PlexusConfiguration mergedConfig, ExpressionEvaluator eval, Exception exception) throws IOException, InterruptedException {
            listener.preExecute(project, new MojoInfo(exec, mergedConfig, eval));
        }

        private void fireEnterModule(MavenProject project) throws InterruptedException, IOException {
            lastModule = project;
            listener.preModule(project);
        }

        private void fireLeaveModule() throws InterruptedException, IOException {
            if(lastModule!=null) {
                listener.postModule(lastModule);
                lastModule = null;
            }
        }
    }

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

    private static final long serialVersionUID = 1L;
}
