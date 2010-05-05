/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
import hudson.remoting.Channel;
import hudson.remoting.Future;
import hudson.util.IOException2;
import org.apache.maven.BuildFailureException;
import org.apache.maven.reporting.MavenReport;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ReactorManager;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.lifecycle.LifecycleExecutorInterceptor;
import org.apache.maven.lifecycle.LifecycleExecutorListener;
import org.apache.maven.monitor.event.EventDispatcher;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.project.MavenProject;
import org.codehaus.classworlds.NoSuchRealmException;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.text.NumberFormat;

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

    /**
     * Flag needs to be set at the constructor, so that this reflects
     * the setting at master.
     */
    private final boolean profile = MavenProcessFactory.profile;

    /**
     * Record all asynchronous executions as they are scheduled,
     * to make sure they are all completed before we finish.
     */
    protected transient /*final*/ List<Future<?>> futures;

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
     * Called after a {@link MavenReport} is successfully generated.
     */
    abstract void onReportGenerated(MavenProject project, MavenReportInfo report) throws IOException, InterruptedException, AbortException;

    /**
     * This code is executed inside the maven jail process.
     */
    public Result call() throws IOException {
        try {
            futures = new ArrayList<Future<?>>();
            Adapter a = new Adapter(this);
            PluginManagerInterceptor.setListener(a);
            LifecycleExecutorInterceptor.setListener(a);

            markAsSuccess = false;

            // working around NPE when someone puts a null value into systemProps.
            for (Map.Entry<String,String> e : systemProps.entrySet()) {
                if (e.getValue()==null)
                    throw new IllegalArgumentException("System property "+e.getKey()+" has a null value");
                System.getProperties().put(e.getKey(), e.getValue());
            }

            listener.getLogger().println(formatArgs(goals));
            int r = Main.launch(goals.toArray(new String[goals.size()]));

            // now check the completion status of async ops
            boolean messageReported = false;
            long startTime = System.nanoTime();
            for (Future<?> f : futures) {
                try {
                    if(!f.isDone() && !messageReported) {
                        messageReported = true;
                        listener.getLogger().println(Messages.MavenBuilder_Waiting());
                    }
                    f.get();
                } catch (InterruptedException e) {
                    // attempt to cancel all asynchronous tasks
                    for (Future<?> g : futures)
                        g.cancel(true);
                    listener.getLogger().println(Messages.MavenBuilder_Aborted());
                    return Result.ABORTED;
                } catch (ExecutionException e) {
                    e.printStackTrace(listener.error(Messages.MavenBuilder_AsyncFailed()));
                }
            }
            a.overheadTime += System.nanoTime()-startTime;
            futures.clear();

            if(profile) {
                NumberFormat n = NumberFormat.getInstance();
                PrintStream logger = listener.getLogger();
                logger.println("Total overhead was "+format(n,a.overheadTime)+"ms");
                Channel ch = Channel.current();
                logger.println("Class loading "   +format(n,ch.classLoadingTime.get())   +"ms, "+ch.classLoadingCount+" classes");
                logger.println("Resource loading "+format(n,ch.resourceLoadingTime.get())+"ms, "+ch.resourceLoadingCount+" times");                
            }

            if(r==0)    return Result.SUCCESS;

            if(markAsSuccess) {
                listener.getLogger().println(Messages.MavenBuilder_Failed());
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

    private String formatArgs(List<String> args) {
        StringBuilder buf = new StringBuilder("Executing Maven: ");
        for (String arg : args)
            buf.append(' ').append(arg);
        return buf.toString();
    }

    private String format(NumberFormat n, long nanoTime) {
        return n.format(nanoTime/1000000);
    }

    // since reporters might be from plugins, use the uberjar to resolve them.
    public ClassLoader getClassLoader() {
        return Hudson.getInstance().getPluginManager().uberClassLoader;
    }

    /**
     * Receives {@link PluginManagerListener} and {@link LifecycleExecutorListener} events
     * and converts them to {@link MavenBuilder} events.
     */
    private static final class Adapter implements PluginManagerListener, LifecycleExecutorListener {
        /**
         * Used to detect when to fire {@link MavenReporter#enterModule}
         */
        private MavenProject lastModule;

        private final MavenBuilder listener;

        /**
         * Number of total nanoseconds {@link MavenBuilder} spent.
         */
        long overheadTime;

        public Adapter(MavenBuilder listener) {
            this.listener = listener;
        }

        public void preBuild(MavenSession session, ReactorManager rm, EventDispatcher dispatcher) throws BuildFailureException, LifecycleExecutionException, IOException, InterruptedException {
            long startTime = System.nanoTime();
            listener.preBuild(session, rm, dispatcher);
            overheadTime += System.nanoTime()-startTime;
        }

        public void postBuild(MavenSession session, ReactorManager rm, EventDispatcher dispatcher) throws BuildFailureException, LifecycleExecutionException, IOException, InterruptedException {
            long startTime = System.nanoTime();
            fireLeaveModule();
            listener.postBuild(session, rm, dispatcher);
            overheadTime += System.nanoTime()-startTime;
        }

        public void endModule() throws InterruptedException, IOException {
            long startTime = System.nanoTime();
            fireLeaveModule();
            overheadTime += System.nanoTime()-startTime;
        }

        public void preExecute(MavenProject project, MojoExecution exec, Mojo mojo, PlexusConfiguration mergedConfig, ExpressionEvaluator eval) throws IOException, InterruptedException {
            long startTime = System.nanoTime();
            if(lastModule!=project) {
                // module change
                fireLeaveModule();
                fireEnterModule(project);
            }

            listener.preExecute(project, new MojoInfo(exec, mojo, mergedConfig, eval));
            overheadTime += System.nanoTime()-startTime;
        }

        public void postExecute(MavenProject project, MojoExecution exec, Mojo mojo, PlexusConfiguration mergedConfig, ExpressionEvaluator eval, Exception exception) throws IOException, InterruptedException {
            long startTime = System.nanoTime();
            listener.postExecute(project, new MojoInfo(exec, mojo, mergedConfig, eval),exception);
            overheadTime += System.nanoTime()-startTime;
        }

        public void onReportGenerated(MavenReport report, MojoExecution mojoExecution, PlexusConfiguration mergedConfig, ExpressionEvaluator eval) throws IOException, InterruptedException {
            long startTime = System.nanoTime();
            listener.onReportGenerated(lastModule,new MavenReportInfo(mojoExecution,report,mergedConfig,eval));
            overheadTime += System.nanoTime()-startTime;
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
