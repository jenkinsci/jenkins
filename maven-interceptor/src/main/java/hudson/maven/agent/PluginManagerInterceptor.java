package hudson.maven.agent;

import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.DefaultPluginManager;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.PluginConfigurationException;
import org.apache.maven.plugin.PluginManagerException;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Description in META-INF/plexus/components.xml makes it possible to use this instead of the default
 * plugin manager.
 *
 * @author Kohsuke Kawaguchi
 */
public class PluginManagerInterceptor extends DefaultPluginManager {

    private final Method mergeMojoConfiguration;

    private PluginManagerListener listener;

    public PluginManagerInterceptor() {
        try {
            this.mergeMojoConfiguration = DefaultPluginManager.class.getDeclaredMethod(
                "mergeMojoConfiguration", XmlPlexusConfiguration.class, MojoDescriptor.class);
            mergeMojoConfiguration.setAccessible(true);
        } catch (NoSuchMethodException e) {
            NoSuchMethodError x = new NoSuchMethodError("Unable to find DefaultPluginManager.mergeMojoConfiguration()");
            x.initCause(e);
            throw x;
        }
    }

    public void setListener(PluginManagerListener listener) {
        this.listener = listener;
    }

    public void executeMojo(MavenProject project, MojoExecution mojoExecution, MavenSession session) throws ArtifactResolutionException, MojoExecutionException, MojoFailureException, ArtifactNotFoundException, InvalidDependencyVersionException, PluginManagerException, PluginConfigurationException {
        Xpp3Dom dom = getConfigDom(mojoExecution, project);

        XmlPlexusConfiguration pomConfiguration;
        if ( dom == null )
        {
            pomConfiguration = new XmlPlexusConfiguration( "configuration" );
        }
        else
        {
            pomConfiguration = new XmlPlexusConfiguration( dom );
        }

        MojoDescriptor mojoDescriptor = mojoExecution.getMojoDescriptor();
        PlexusConfiguration mergedConfiguration;

        try {
            mergedConfiguration = (PlexusConfiguration) mergeMojoConfiguration.invoke(this, pomConfiguration, mojoDescriptor );
        } catch (IllegalAccessException e) {
            IllegalAccessError x = new IllegalAccessError();
            x.initCause(e);
            throw x;
        } catch (InvocationTargetException e) {
            throw new MojoExecutionException("Failed to check configuration",e);
        }
        // this just seems like an error check
        //PlexusConfiguration extractedMojoConfiguration =
        //    extractMojoConfiguration( mergedConfiguration, mojoDescriptor ); // what does this do?

        ExpressionEvaluator eval = new PluginParameterExpressionEvaluator( session, mojoExecution,
                                                                                          pathTranslator, getLogger(),
                                                                                          project,
                                                                                          session.getExecutionProperties() );

        try {
            if(listener!=null)
                listener.preExecute(project,mojoExecution,mergedConfiguration,eval);
            super.executeMojo(project, mojoExecution, session);
            if(listener!=null)
                listener.postExecute(project,mojoExecution,mergedConfiguration,eval);
        } catch (InterruptedException e) {
            // orderly abort
            throw new AbortException("Execution aborted",e);
        } catch (IOException e) {
            throw new PluginManagerException(e.getMessage(),e);
        }
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

    /**
     * Thrown when {@link PluginManagerListener} returned false to orderly
     * abort the execution. The caller shouldn't dump the stack trace for
     * this exception.
     */
    public final class AbortException extends PluginManagerException {
        public AbortException(String message) {
            super(message);
        }
        public AbortException(String message, Exception e) {
            super(message, e);
        }
    }
}
