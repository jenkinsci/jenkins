package hudson.maven;

import org.apache.maven.plugin.DefaultPluginManager;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.PluginManagerException;
import org.apache.maven.plugin.PluginConfigurationException;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;

/**
 * Description in META-INF/plexus/components.xml makes it possible to use this instead of the default
 * plugin manager.
 *
 * @author Kohsuke Kawaguchi
 */
public class PluginManagetInterceptor extends DefaultPluginManager {

    public void executeMojo(MavenProject project, MojoExecution mojoExecution, MavenSession session) throws ArtifactResolutionException, MojoExecutionException, MojoFailureException, ArtifactNotFoundException, InvalidDependencyVersionException, PluginManagerException, PluginConfigurationException {
        Xpp3Dom dom = getConfigDom(mojoExecution, project);

        ExpressionEvaluator eval = new PluginParameterExpressionEvaluator( session, mojoExecution,
                                                                                          pathTranslator, getLogger(),
                                                                                          project,
                                                                                          session.getExecutionProperties() );

        System.out.println("EXECUTING "+mojoExecution.getExecutionId());
        super.executeMojo(project, mojoExecution, session);
    }

    private Xpp3Dom getConfigDom(MojoExecution mojoExecution, MavenProject project) {
        MojoDescriptor mojoDescriptor = mojoExecution.getMojoDescriptor();
        PluginDescriptor pluginDescriptor = mojoDescriptor.getPluginDescriptor();
        String goalId = mojoDescriptor.getGoal();
        String groupId = pluginDescriptor.getGroupId();
        String artifactId = pluginDescriptor.getArtifactId();
        String executionId = mojoExecution.getExecutionId();
        Xpp3Dom dom = project.getGoalConfiguration( groupId, artifactId, executionId, goalId );
        Xpp3Dom reportDom = project.getReportConfiguration( groupId, artifactId, executionId );
        dom = Xpp3Dom.mergeXpp3Dom( dom, reportDom );
        if ( mojoExecution.getConfiguration() != null )
        {
            dom = Xpp3Dom.mergeXpp3Dom( dom, mojoExecution.getConfiguration() );
        }
        return dom;
    }
}
