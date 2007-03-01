package hudson.maven.agent;

import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;

import java.io.IOException;

/**
 * Receives notification from {@link PluginManagerInterceptor}.
 * 
 * @author Kohsuke Kawaguchi
 */
public interface PluginManagerListener {
    void preExecute(MavenProject project,MojoExecution exec, PlexusConfiguration mergedConfig, ExpressionEvaluator eval) throws IOException, InterruptedException, AbortException;
    void postExecute(MavenProject project,MojoExecution exec, PlexusConfiguration mergedConfig, ExpressionEvaluator eval) throws IOException, InterruptedException, AbortException;
}
