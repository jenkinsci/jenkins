package hudson.maven;

import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;

import java.io.IOException;

import hudson.model.BuildListener;
import hudson.maven.agent.PluginManagerListener;
import hudson.maven.agent.AbortException;

/**
 * Description in META-INF/plexus/components.xml makes it possible to use this instead of the default
 * plugin manager.
 *
 * @author Kohsuke Kawaguchi
 */
public class PluginManagerInterceptor implements PluginManagerListener {

    private MavenBuildProxy buildProxy;
    private MavenReporter[] reporters;
    private BuildListener listener;

    /**
     * Used to detect when to fire {@link MavenReporter#enterModule}
     */
    private MavenProject lastModule;

    /**
     * Called by {@link MavenBuild} to connect this object to the rest of Hudson objects,
     * namely {@link MavenBuild}.
     *
     * <p>
     * We can't do this in the constructor because this object is created by
     * Plexus along with the rest of Maven objects.
     */
    /*package*/ void setBuilder(MavenBuildProxy buildProxy, MavenReporter[] reporters, BuildListener listener) {
        this.buildProxy = buildProxy;
        this.reporters = reporters;
        this.listener = listener;
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
    }

    public void postExecute(MavenProject project, MojoExecution exec, PlexusConfiguration mergedConfig, ExpressionEvaluator eval) throws IOException, InterruptedException, AbortException {
        MojoInfo info = new MojoInfo(exec, mergedConfig, eval);

        for (MavenReporter r : reporters)
            if(!r.postExecute(buildProxy,project, info,listener))
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
