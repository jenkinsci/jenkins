package hudson.maven;

import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ReactorManager;
import org.apache.maven.monitor.event.EventDispatcher;
import org.apache.maven.BuildFailureException;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.lifecycle.LifecycleExecutorListener;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

import hudson.model.BuildListener;
import hudson.maven.agent.PluginManagerListener;
import hudson.maven.agent.AbortException;

/**
 * Description in META-INF/plexus/components.xml makes it possible to use this instead of the default
 * plugin manager.
 *
 * @author Kohsuke Kawaguchi
 */
public class PluginManagerInterceptor implements PluginManagerListener, LifecycleExecutorListener {

    private final MavenBuildProxy buildProxy;
    private final MavenReporter[] reporters;
    private final BuildListener listener;

    /**
     * Used to detect when to fire {@link MavenReporter#enterModule}
     */
    private MavenProject lastModule;

    /**
     * Records of what was executed.
     */
    private final List<ExecutedMojo> executedMojos = new ArrayList<ExecutedMojo>();

    private long startTime;

    /**
     * Called by {@link MavenBuild} to connect this object to the rest of Hudson objects,
     * namely {@link MavenBuild}.
     *
     * <p>
     * We can't do this in the constructor because this object is created by
     * Plexus along with the rest of Maven objects.
     */
    public PluginManagerInterceptor(MavenBuildProxy buildProxy, MavenReporter[] reporters, BuildListener listener) {
        this.buildProxy = buildProxy;
        this.reporters = reporters;
        this.listener = listener;
    }

    /**
     * Called before the whole build.
     */
    public void preBuild(MavenSession session, ReactorManager rm, EventDispatcher dispatcher) throws BuildFailureException, LifecycleExecutionException {
        try {
            for (MavenReporter r : reporters)
                r.preBuild(buildProxy,rm.getTopLevelProject(),listener);
        } catch (InterruptedException e) {
            throw new BuildFailureException("aborted",e);
        } catch (IOException e) {
            throw new BuildFailureException(e.getMessage(),e);
        }
    }


    public void postBuild(MavenSession session, ReactorManager rm, EventDispatcher dispatcher) throws BuildFailureException, LifecycleExecutionException {
        try {
            fireLeaveModule();
            buildProxy.setExecutedMojos(executedMojos);
            for (MavenReporter r : reporters)
                r.postBuild(buildProxy,rm.getTopLevelProject(),listener);
        } catch (InterruptedException e) {
            throw new BuildFailureException("aborted",e);
        } catch (IOException e) {
            throw new BuildFailureException(e.getMessage(),e);
        } catch (AbortException e) {
            throw new BuildFailureException("aborted",e);
        }
    }

    public void preExecute(MavenProject project, MojoExecution exec, PlexusConfiguration mergedConfig, ExpressionEvaluator eval) throws IOException, InterruptedException, AbortException {
        if(lastModule!=project) {
            // module change
            fireLeaveModule();
            fireEnterModule(project);
        }

        MojoInfo info = new MojoInfo(exec, mergedConfig, eval);
        for (MavenReporter r : reporters)
            if(!r.preExecute(buildProxy,project,info,listener))
                throw new AbortException(r+" failed");

        startTime = System.currentTimeMillis();
    }

    public void postExecute(MavenProject project, MojoExecution exec, PlexusConfiguration mergedConfig, ExpressionEvaluator eval, Exception exception) throws IOException, InterruptedException, AbortException {
        MojoInfo info = new MojoInfo(exec, mergedConfig, eval);

        executedMojos.add(new ExecutedMojo(info,System.currentTimeMillis()-startTime));

        for (MavenReporter r : reporters)
            if(!r.postExecute(buildProxy,project,info,listener,exception))
                throw new AbortException(r+" failed");
    }
    
    private void fireEnterModule(MavenProject project) throws InterruptedException, IOException, AbortException {
        lastModule = project;
        for (MavenReporter r : reporters)
            if(!r.enterModule(buildProxy,project,listener))
                throw new AbortException(r+" failed");
    }

    private void fireLeaveModule() throws InterruptedException, IOException, AbortException {
        if(lastModule!=null) {
            for (MavenReporter r : reporters)
                if(!r.leaveModule(buildProxy,lastModule,listener))
                    throw new AbortException(r+" failed");
        }
    }
}
