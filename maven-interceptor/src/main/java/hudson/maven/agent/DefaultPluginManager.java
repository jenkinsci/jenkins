
package hudson.maven.agent;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.MavenArtifactFilterManager;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataRetrievalException;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.metadata.ResolutionGroup;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.MultipleArtifactsNotFoundException;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.RuntimeInformation;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.monitor.event.EventDispatcher;
import org.apache.maven.monitor.event.MavenEvents;
import org.apache.maven.monitor.logging.DefaultLog;
import org.apache.maven.plugin.ContextEnabled;
import org.apache.maven.plugin.DebugConfigurationListener;
import org.apache.maven.plugin.InvalidPluginException;
import org.apache.maven.plugin.MavenPluginCollector;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.PluginConfigurationException;
import org.apache.maven.plugin.PluginManager;
import org.apache.maven.plugin.PluginManagerException;
import org.apache.maven.plugin.PluginMappingManager;
import org.apache.maven.plugin.PluginNotFoundException;
import org.apache.maven.plugin.PluginParameterException;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.Parameter;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptorBuilder;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.version.PluginVersionManager;
import org.apache.maven.plugin.version.PluginVersionNotFoundException;
import org.apache.maven.plugin.version.PluginVersionResolutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.apache.maven.project.artifact.MavenMetadataSource;
import org.apache.maven.project.path.PathTranslator;
import org.apache.maven.reporting.MavenReport;
import org.apache.maven.settings.Settings;
import org.codehaus.classworlds.ClassRealm;
import org.codehaus.classworlds.NoSuchRealmException;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.ComponentConfigurator;
import org.codehaus.plexus.component.configurator.ConfigurationListener;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.component.repository.exception.ComponentLifecycleException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * olamy : code removed in maven 3 so imported sources 
 * from maven 2.0.9 tag : /maven-core/src/main/java/org/apache/maven/plugin/DefaultPluginManager.java
 * really hackhish !
 * @author olamy
 * 
 */
public class DefaultPluginManager
    extends AbstractLogEnabled
    implements PluginManager, Initializable, Contextualizable
{
    protected PlexusContainer container;

    protected PluginDescriptorBuilder pluginDescriptorBuilder;

    protected ArtifactFilter artifactFilter;

    private Log mojoLogger;

    private Map resolvedCoreArtifactFiles = new HashMap();

    // component requirements
    protected PathTranslator pathTranslator;

    protected MavenPluginCollector pluginCollector;

    protected PluginVersionManager pluginVersionManager;

    protected ArtifactFactory artifactFactory;

    protected ArtifactResolver artifactResolver;

    protected ArtifactMetadataSource artifactMetadataSource;

    protected RuntimeInformation runtimeInformation;

    protected MavenProjectBuilder mavenProjectBuilder;

    protected PluginMappingManager pluginMappingManager;

    // END component requirements

    public DefaultPluginManager()
    {
        pluginDescriptorBuilder = new PluginDescriptorBuilder();
    }

    // ----------------------------------------------------------------------
    //
    // ----------------------------------------------------------------------

    public PluginDescriptor getPluginDescriptorForPrefix( String prefix )
    {
        return pluginCollector.getPluginDescriptorForPrefix( prefix );
    }

    public Plugin getPluginDefinitionForPrefix( String prefix,
                                                MavenSession session,
                                                MavenProject project )
    {
        // TODO: since this is only used in the lifecycle executor, maybe it should be moved there? There is no other
        // use for the mapping manager in here
        return pluginMappingManager.getByPrefix( prefix, session.getSettings().getPluginGroups(),
                                                 project.getPluginArtifactRepositories(),
                                                 session.getLocalRepository() );
    }

    public PluginDescriptor verifyPlugin( Plugin plugin,
                                          MavenProject project,
                                          Settings settings,
                                          ArtifactRepository localRepository )
        throws ArtifactResolutionException, PluginVersionResolutionException, ArtifactNotFoundException,
        InvalidVersionSpecificationException, InvalidPluginException, PluginManagerException, PluginNotFoundException,
        PluginVersionNotFoundException
    {
        // TODO: this should be possibly outside
        // All version-resolution logic has been moved to DefaultPluginVersionManager.
        if ( plugin.getVersion() == null )
        {
            String version = pluginVersionManager.resolvePluginVersion( plugin.getGroupId(), plugin.getArtifactId(),
                                                                        project, settings, localRepository );
            plugin.setVersion( version );
        }

        return verifyVersionedPlugin( plugin, project, localRepository );
    }

    private PluginDescriptor verifyVersionedPlugin( Plugin plugin,
                                                    MavenProject project,
                                                    ArtifactRepository localRepository )
        throws PluginVersionResolutionException, ArtifactNotFoundException, ArtifactResolutionException,
        InvalidVersionSpecificationException, InvalidPluginException, PluginManagerException, PluginNotFoundException
    {
        // TODO: this might result in an artifact "RELEASE" being resolved continuously
        // FIXME: need to find out how a plugin gets marked as 'installed'
        // and no ChildContainer exists. The check for that below fixes
        // the 'Can't find plexus container for plugin: xxx' error.
        try
        {
            VersionRange versionRange = VersionRange.createFromVersionSpec( plugin.getVersion() );

            List remoteRepositories = new ArrayList();
            remoteRepositories.addAll( project.getPluginArtifactRepositories() );
            remoteRepositories.addAll( project.getRemoteArtifactRepositories() );

            checkRequiredMavenVersion( plugin, localRepository, remoteRepositories );

            Artifact pluginArtifact =
                artifactFactory.createPluginArtifact( plugin.getGroupId(), plugin.getArtifactId(), versionRange );

            pluginArtifact = project.replaceWithActiveArtifact( pluginArtifact );

            artifactResolver.resolve( pluginArtifact, project.getPluginArtifactRepositories(), localRepository );

            PlexusContainer pluginContainer = container.getChildContainer( plugin.getKey() );

            File pluginFile = pluginArtifact.getFile();

            if ( !pluginCollector.isPluginInstalled( plugin ) || ( pluginContainer == null ) )
            {
                addPlugin( plugin, pluginArtifact, project, localRepository );
            }
            else if ( pluginFile.lastModified() > pluginContainer.getCreationDate().getTime() )
            {
                getLogger().info(
                    "Reloading plugin container for: " + plugin.getKey() + ". The plugin artifact has changed." );

                pluginContainer.dispose();

                pluginCollector.flushPluginDescriptor( plugin );

                addPlugin( plugin, pluginArtifact, project, localRepository );
            }

            project.addPlugin( plugin );
        }
        catch ( ArtifactNotFoundException e )
        {
            String groupId = plugin.getGroupId();
            String artifactId = plugin.getArtifactId();
            String version = plugin.getVersion();

            if ( ( groupId == null ) || ( artifactId == null ) || ( version == null ) )
            {
                throw new PluginNotFoundException( e );
            }
            else if ( groupId.equals( e.getGroupId() ) && artifactId.equals( e.getArtifactId() ) &&
                version.equals( e.getVersion() ) && "maven-plugin".equals( e.getType() ) )
            {
                throw new PluginNotFoundException( e );
            }
            else
            {
                throw e;
            }
        }

        return pluginCollector.getPluginDescriptor( plugin );
    }

    /**
     * @todo would be better to store this in the plugin descriptor, but then it won't be available to the version
     * manager which executes before the plugin is instantiated
     */
    private void checkRequiredMavenVersion( Plugin plugin,
                                            ArtifactRepository localRepository,
                                            List remoteRepositories )
        throws PluginVersionResolutionException, InvalidPluginException
    {
        try
        {
            Artifact artifact = artifactFactory.createProjectArtifact( plugin.getGroupId(), plugin.getArtifactId(),
                                                                       plugin.getVersion() );
            MavenProject project =
                mavenProjectBuilder.buildFromRepository( artifact, remoteRepositories, localRepository, false );
            // if we don't have the required Maven version, then ignore an update
            if ( ( project.getPrerequisites() != null ) && ( project.getPrerequisites().getMaven() != null ) )
            {
                DefaultArtifactVersion requiredVersion =
                    new DefaultArtifactVersion( project.getPrerequisites().getMaven() );
                if ( runtimeInformation.getApplicationVersion().compareTo( requiredVersion ) < 0 )
                {
                    throw new PluginVersionResolutionException( plugin.getGroupId(), plugin.getArtifactId(),
                                                                "Plugin requires Maven version " + requiredVersion );
                }
            }
        }
        catch ( ProjectBuildingException e )
        {
            throw new InvalidPluginException(
                "Unable to build project for plugin '" + plugin.getKey() + "': " + e.getMessage(), e );
        }
    }

    protected void addPlugin( Plugin plugin,
                              Artifact pluginArtifact,
                              MavenProject project,
                              ArtifactRepository localRepository )
        throws PluginManagerException, InvalidPluginException
    {
        PlexusContainer child;

        try
        {
            child = container.createChildContainer( plugin.getKey(),
                                                    Collections.singletonList( pluginArtifact.getFile() ),
                                                    Collections.EMPTY_MAP,
                                                    Collections.singletonList( pluginCollector ) );
            try
            {
                child.getContainerRealm().importFrom( "plexus.core", "org.codehaus.plexus.util.xml.Xpp3Dom" );
                child.getContainerRealm().importFrom( "plexus.core", "org.codehaus.plexus.util.xml.pull" );

                // MNG-2878
                child.getContainerRealm().importFrom( "plexus.core", "/default-report.xml" );
            }
            catch ( NoSuchRealmException e )
            {
                // won't happen
            }
        }
        catch ( PlexusContainerException e )
        {
            throw new PluginManagerException(
                "Failed to create plugin container for plugin '" + plugin + "': " + e.getMessage(), e );
        }

        // this plugin's descriptor should have been discovered in the child creation, so we should be able to
        // circle around and set the artifacts and class realm
        PluginDescriptor addedPlugin = pluginCollector.getPluginDescriptor( plugin );

        if ( addedPlugin == null )
        {
            throw new IllegalStateException( "The PluginDescriptor for the plugin " + plugin + " was not found." );
        }

        addedPlugin.setClassRealm( child.getContainerRealm() );

        // we're only setting the plugin's artifact itself as the artifact list, to allow it to be retrieved
        // later when the plugin is first invoked. Retrieving this artifact will in turn allow us to
        // transitively resolve its dependencies, and add them to the plugin container...
        addedPlugin.setArtifacts( Collections.singletonList( pluginArtifact ) );

        try
        {
            // the only Plugin instance which will have dependencies is the one specified in the project.
            // We need to look for a Plugin instance there, in case the instance we're using didn't come from
            // the project.
            Plugin projectPlugin = (Plugin) project.getBuild().getPluginsAsMap().get( plugin.getKey() );

            if ( projectPlugin == null )
            {
                projectPlugin = plugin;
            }

            Set artifacts = MavenMetadataSource.createArtifacts( artifactFactory, projectPlugin.getDependencies(), null,
                                                                 null, project );

//            Set artifacts =
//                MavenMetadataSource.createArtifacts( artifactFactory, plugin.getDependencies(), null, null, project );

            addedPlugin.setIntroducedDependencyArtifacts( artifacts );
        }
        catch ( InvalidDependencyVersionException e )
        {
            throw new InvalidPluginException( "Plugin '" + plugin + "' is invalid: " + e.getMessage(), e );
        }
    }

    // ----------------------------------------------------------------------
    // Mojo execution
    // ----------------------------------------------------------------------

    public void executeMojo( MavenProject project,
                             MojoExecution mojoExecution,
                             MavenSession session )
        throws ArtifactResolutionException, MojoExecutionException, MojoFailureException, ArtifactNotFoundException,
        InvalidDependencyVersionException, PluginManagerException, PluginConfigurationException
    {
        MojoDescriptor mojoDescriptor = mojoExecution.getMojoDescriptor();

        // NOTE: I'm putting these checks in here, since this is the central point of access for
        // anything that wants to execute a mojo.
        if ( mojoDescriptor.isProjectRequired() && !session.isUsingPOMsFromFilesystem() )
        {
            throw new MojoExecutionException( "Cannot execute mojo: " + mojoDescriptor.getGoal() +
                ". It requires a project with an existing pom.xml, but the build is not using one." );
        }

        if ( mojoDescriptor.isOnlineRequired() && session.getSettings().isOffline() )
        {
            // TODO: Should we error out, or simply warn and skip??
            throw new MojoExecutionException( "Mojo: " + mojoDescriptor.getGoal() +
                " requires online mode for execution. Maven is currently offline." );
        }

        if ( mojoDescriptor.isDependencyResolutionRequired() != null )
        {
            Collection projects;

            if ( mojoDescriptor.isAggregator() )
            {
                projects = session.getSortedProjects();
            }
            else
            {
                projects = Collections.singleton( project );
            }

            for ( Iterator i = projects.iterator(); i.hasNext(); )
            {
                MavenProject p = (MavenProject) i.next();

                resolveTransitiveDependencies( session, artifactResolver,
                                               mojoDescriptor.isDependencyResolutionRequired(), artifactFactory, p, mojoDescriptor.isAggregator() );
            }

            downloadDependencies( project, session, artifactResolver );
        }

        String goalName = mojoDescriptor.getFullGoalName();

        Mojo plugin;

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

        plugin = getConfiguredMojo( session, dom, project, false, mojoExecution );

        // Event monitoring.
        String event = MavenEvents.MOJO_EXECUTION;
        EventDispatcher dispatcher = session.getEventDispatcher();

        String goalExecId = goalName;

        if ( mojoExecution.getExecutionId() != null )
        {
            goalExecId += " {execution: " + mojoExecution.getExecutionId() + "}";
        }

        dispatcher.dispatchStart( event, goalExecId );

        ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();

        try
        {
            Thread.currentThread().setContextClassLoader(
                mojoDescriptor.getPluginDescriptor().getClassRealm().getClassLoader() );

            plugin.execute();

            dispatcher.dispatchEnd( event, goalExecId );
        }
        catch ( MojoExecutionException e )
        {
            session.getEventDispatcher().dispatchError( event, goalExecId, e );

            throw e;
        }
        catch ( MojoFailureException e )
        {
            session.getEventDispatcher().dispatchError( event, goalExecId, e );

            throw e;
        }
        catch ( LinkageError e )
        {
            if ( getLogger().isFatalErrorEnabled() )
            {
                getLogger().fatalError(
                                        plugin.getClass().getName() + "#execute() caused a linkage error ("
                                            + e.getClass().getName() + ") and may be out-of-date. Check the realms:" );

                ClassRealm pluginRealm = mojoDescriptor.getPluginDescriptor().getClassRealm();
                StringBuffer sb = new StringBuffer();
                sb.append( "Plugin realm = " + pluginRealm.getId() ).append( '\n' );
                for ( int i = 0; i < pluginRealm.getConstituents().length; i++ )
                {
                    sb.append( "urls[" + i + "] = " + pluginRealm.getConstituents()[i] );
                    if ( i != ( pluginRealm.getConstituents().length - 1 ) )
                    {
                        sb.append( '\n' );
                    }
                }
                getLogger().fatalError( sb.toString() );

                ClassRealm containerRealm = container.getContainerRealm();
                sb = new StringBuffer();
                sb.append( "Container realm = " + containerRealm.getId() ).append( '\n' );
                for ( int i = 0; i < containerRealm.getConstituents().length; i++ )
                {
                    sb.append( "urls[" + i + "] = " + containerRealm.getConstituents()[i] );
                    if ( i != ( containerRealm.getConstituents().length - 1 ) )
                    {
                        sb.append( '\n' );
                    }
                }
                getLogger().fatalError( sb.toString() );
            }

            session.getEventDispatcher().dispatchError( event, goalExecId, e );

            throw e;
        }
        finally
        {

            Thread.currentThread().setContextClassLoader( oldClassLoader );

            try
            {
                PlexusContainer pluginContainer = getPluginContainer( mojoDescriptor.getPluginDescriptor() );

                pluginContainer.release( plugin );
            }
            catch ( ComponentLifecycleException e )
            {
                if ( getLogger().isErrorEnabled() )
                {
                    getLogger().error( "Error releasing plugin - ignoring.", e );
                }
            }
        }
    }

    public MavenReport getReport( MavenProject project,
                                  MojoExecution mojoExecution,
                                  MavenSession session )
        throws ArtifactNotFoundException, PluginConfigurationException, PluginManagerException,
        ArtifactResolutionException
    {
        MojoDescriptor mojoDescriptor = mojoExecution.getMojoDescriptor();
        PluginDescriptor descriptor = mojoDescriptor.getPluginDescriptor();
        Xpp3Dom dom = project.getReportConfiguration( descriptor.getGroupId(), descriptor.getArtifactId(),
                                                      mojoExecution.getExecutionId() );
        if ( mojoExecution.getConfiguration() != null )
        {
            dom = Xpp3Dom.mergeXpp3Dom( dom, mojoExecution.getConfiguration() );
        }

        return (MavenReport) getConfiguredMojo( session, dom, project, true, mojoExecution );
    }

    public PluginDescriptor verifyReportPlugin( ReportPlugin reportPlugin,
                                                MavenProject project,
                                                MavenSession session )
        throws PluginVersionResolutionException, ArtifactResolutionException, ArtifactNotFoundException,
        InvalidVersionSpecificationException, InvalidPluginException, PluginManagerException, PluginNotFoundException,
        PluginVersionNotFoundException
    {
        String version = reportPlugin.getVersion();

        if ( version == null )
        {
            version = pluginVersionManager.resolveReportPluginVersion( reportPlugin.getGroupId(),
                                                                       reportPlugin.getArtifactId(), project,
                                                                       session.getSettings(),
                                                                       session.getLocalRepository() );
            reportPlugin.setVersion( version );
        }

        Plugin forLookup = new Plugin();

        forLookup.setGroupId( reportPlugin.getGroupId() );
        forLookup.setArtifactId( reportPlugin.getArtifactId() );
        forLookup.setVersion( version );

        return verifyVersionedPlugin( forLookup, project, session.getLocalRepository() );
    }

    private PlexusContainer getPluginContainer( PluginDescriptor pluginDescriptor )
        throws PluginManagerException
    {
        String pluginKey = pluginDescriptor.getPluginLookupKey();

        PlexusContainer pluginContainer = container.getChildContainer( pluginKey );

        if ( pluginContainer == null )
        {
            throw new PluginManagerException( "Cannot find Plexus container for plugin: " + pluginKey );
        }

        return pluginContainer;
    }

    private Mojo getConfiguredMojo( MavenSession session,
                                    Xpp3Dom dom,
                                    MavenProject project,
                                    boolean report,
                                    MojoExecution mojoExecution )
        throws PluginConfigurationException, ArtifactNotFoundException, PluginManagerException,
        ArtifactResolutionException
    {
        MojoDescriptor mojoDescriptor = mojoExecution.getMojoDescriptor();

        PluginDescriptor pluginDescriptor = mojoDescriptor.getPluginDescriptor();

        PlexusContainer pluginContainer = getPluginContainer( pluginDescriptor );

        // if this is the first time this plugin has been used, the plugin's container will only
        // contain the plugin's artifact in isolation; we need to finish resolving the plugin's
        // dependencies, and add them to the container.
        ensurePluginContainerIsComplete( pluginDescriptor, pluginContainer, project, session );

        Mojo plugin;
        try
        {
            plugin = (Mojo) pluginContainer.lookup( Mojo.ROLE, mojoDescriptor.getRoleHint() );
            if ( report && !( plugin instanceof MavenReport ) )
            {
                // TODO: the mojoDescriptor should actually capture this information so we don't get this far
                return null;
            }
        }
        catch ( ComponentLookupException e )
        {
            throw new PluginManagerException( "Unable to find the mojo '" + mojoDescriptor.getRoleHint() +
                "' in the plugin '" + pluginDescriptor.getPluginLookupKey() + "'", e );
        }

        if ( plugin instanceof ContextEnabled )
        {
            Map pluginContext = session.getPluginContext( pluginDescriptor, project );

            ( (ContextEnabled) plugin ).setPluginContext( pluginContext );
        }

        plugin.setLog( mojoLogger );

        XmlPlexusConfiguration pomConfiguration;
        if ( dom == null )
        {
            pomConfiguration = new XmlPlexusConfiguration( "configuration" );
        }
        else
        {
            pomConfiguration = new XmlPlexusConfiguration( dom );
        }

        // Validate against non-editable (@readonly) parameters, to make sure users aren't trying to
        // override in the POM.
        validatePomConfiguration( mojoDescriptor, pomConfiguration );

        PlexusConfiguration mergedConfiguration = mergeMojoConfiguration( pomConfiguration, mojoDescriptor );

        // TODO: plexus changes to make this more like the component descriptor so this can be used instead
        //            PlexusConfiguration mergedConfiguration = mergeConfiguration( pomConfiguration,
        //                                                                          mojoDescriptor.getConfiguration() );

        ExpressionEvaluator expressionEvaluator = new PluginParameterExpressionEvaluator( session, mojoExecution,
                                                                                          pathTranslator, getLogger(),
                                                                                          project,
                                                                                          session.getExecutionProperties() );

        PlexusConfiguration extractedMojoConfiguration =
            extractMojoConfiguration( mergedConfiguration, mojoDescriptor );

        checkRequiredParameters( mojoDescriptor, extractedMojoConfiguration, expressionEvaluator );

        populatePluginFields( plugin, mojoDescriptor, extractedMojoConfiguration, pluginContainer,
                              expressionEvaluator );
        return plugin;
    }

    private void ensurePluginContainerIsComplete( PluginDescriptor pluginDescriptor,
                                                  PlexusContainer pluginContainer,
                                                  MavenProject project,
                                                  MavenSession session )
        throws ArtifactNotFoundException, PluginManagerException, ArtifactResolutionException
    {
        // if the plugin's already been used once, don't re-do this step...
        // otherwise, we have to finish resolving the plugin's classpath and start the container.
        if ( ( pluginDescriptor.getArtifacts() != null ) && ( pluginDescriptor.getArtifacts().size() == 1 ) )
        {
            Artifact pluginArtifact = (Artifact) pluginDescriptor.getArtifacts().get( 0 );

            ArtifactRepository localRepository = session.getLocalRepository();

            ResolutionGroup resolutionGroup;
            try
            {
                resolutionGroup = artifactMetadataSource.retrieve( pluginArtifact, localRepository,
                                                                   project.getPluginArtifactRepositories() );
            }
            catch ( ArtifactMetadataRetrievalException e )
            {
                throw new ArtifactResolutionException( "Unable to download metadata from repository for plugin '" +
                    pluginArtifact.getId() + "': " + e.getMessage(), pluginArtifact, e );
            }

            checkPlexusUtils( resolutionGroup, artifactFactory );

            // [jdcasey; 20-March-2008]:
            // This is meant to eliminate the introduction of duplicated artifacts.
            // Since much of the reasoning for reversing the order of introduction of
            // plugin dependencies rests on the notion that we need to be able to
            // introduce upgraded versions of plugin dependencies on a case-by-case
            // basis, we need to remove the original version prior to artifact
            // resolution. This is consistent with recent thinking on duplicated
            // dependency specifications within a POM, where that case should
            // throw a model validation exception.
            //
            // Here, we just want to remove any chance that the ArtifactCollector
            // could make a bad choice, and use the old version in spite of our
            // explicit preference otherwise.

            // First, we're going to accumulate plugin dependencies in an ordered map,
            // keyed by dependencyConflictId (the ordered map is meant to preserve relative
            // ordering of the dependencies that do make the cut).
            Map dependencyMap = new LinkedHashMap();

            // Next, we need to accumulate all dependencies in a List, to make it
            // simpler to iterate through them all and add them to the map.
            List all = new ArrayList();

            // plugin-level dependencies from the consuming POM override dependencies
            // from the plugin's own POM.
            all.addAll( pluginDescriptor.getIntroducedDependencyArtifacts() );

            // add in the deps from the plugin POM now.
            all.addAll( resolutionGroup.getArtifacts() );

            for ( Iterator it = all.iterator(); it.hasNext(); )
            {
                Artifact artifact = (Artifact) it.next();
                String conflictId = artifact.getDependencyConflictId();

                // if the map already contains this dependencyConflictId, it constitutes an
                // overridden dependency. Don't use the old one (we know it's old from the
                // order in which dependencies were added to this list).
                if ( !dependencyMap.containsKey( conflictId ) )
                {
                    dependencyMap.put( conflictId, artifact );
                }
            }

            // Create an ordered set of dependencies from the ordered map we used above, to feed into the resolver.
            Set dependencies = new LinkedHashSet( dependencyMap.values() );

            if ( getLogger().isDebugEnabled() )
            {
                // list all dependencies to be used by this plugin (first-level deps, not transitive ones).
                getLogger().debug( "Plugin dependencies for:\n\n" + pluginDescriptor.getId()
                                   + "\n\nare:\n\n"
                                   + StringUtils.join( dependencies.iterator(), "\n" ) + "\n\n" );
            }

            List repositories = new ArrayList();
            repositories.addAll( resolutionGroup.getResolutionRepositories() );
            repositories.addAll( project.getRemoteArtifactRepositories() );

            /* get plugin managed versions */
            Map pluginManagedDependencies = new HashMap();
            try
            {
                MavenProject pluginProject =
                    mavenProjectBuilder.buildFromRepository( pluginArtifact, project.getRemoteArtifactRepositories(),
                                                             localRepository );
                if ( pluginProject != null )
                {
                    pluginManagedDependencies = pluginProject.getManagedVersionMap();
                }
            }
            catch ( ProjectBuildingException e )
            {
                // this can't happen, it would have blowed up at artifactMetadataSource.retrieve()
            }

            ArtifactResolutionResult result = artifactResolver.resolveTransitively( dependencies, pluginArtifact,
                                                                                    pluginManagedDependencies,
                                                                                    localRepository, repositories,
                                                                                    artifactMetadataSource,
                                                                                    artifactFilter );

            Set resolved = result.getArtifacts();

            for ( Iterator it = resolved.iterator(); it.hasNext(); )
            {
                Artifact artifact = (Artifact) it.next();

                if ( !artifact.equals( pluginArtifact ) )
                {
                    artifact = project.replaceWithActiveArtifact( artifact );

                    try
                    {
                        pluginContainer.addJarResource( artifact.getFile() );
                    }
                    catch ( PlexusContainerException e )
                    {
                        throw new PluginManagerException( "Error adding plugin dependency '" +
                            artifact.getDependencyConflictId() + "' into plugin manager: " + e.getMessage(), e );
                    }
                }
            }

            pluginDescriptor.setClassRealm( pluginContainer.getContainerRealm() );

            List unresolved = new ArrayList( dependencies );

            unresolved.removeAll( resolved );

            if ( getLogger().isDebugEnabled() )
            {
                // list all artifacts that were filtered out during the resolution process.
                // these are already present in the core container.
                getLogger().debug( " The following artifacts were filtered out for plugin: "
                                   + pluginDescriptor.getId()
                                   + " because they're already in the core of Maven:\n\n"
                                   + StringUtils.join( unresolved.iterator(), "\n" )
                                   + "\n\nThese will use the artifact files already in the core ClassRealm instead, to allow them to be included in PluginDescriptor.getArtifacts().\n\n" );
            }

            // Grab a file for all filtered artifacts, even if it means resolving them. This
            // is necessary in order to present a full complement of a plugin's transitive
            // dependencies to anyone who calls PluginDescriptor.getArtifacts().
            resolveCoreArtifacts( unresolved, localRepository, resolutionGroup.getResolutionRepositories() );

            // Re-join resolved and filtered-but-now-resolved artifacts.
            // NOTE: The process of filtering then re-adding some artifacts will
            // result in different ordering within the PluginDescriptor.getArtifacts()
            // List than should have happened if none had been filtered. All filtered
            // artifacts will be listed last...
            List allResolved = new ArrayList( resolved.size() + unresolved.size() );

            allResolved.addAll( resolved );
            allResolved.addAll( unresolved );

            pluginDescriptor.setArtifacts( allResolved );
        }
    }

    public static void checkPlexusUtils( ResolutionGroup resolutionGroup, ArtifactFactory artifactFactory )
    {
        // ----------------------------------------------------------------------------
        // If the plugin already declares a dependency on plexus-utils then we're all
        // set as the plugin author is aware of its use. If we don't have a dependency
        // on plexus-utils then we must protect users from stupid plugin authors who
        // did not declare a direct dependency on plexus-utils because the version
        // Maven uses is hidden from downstream use. We will also bump up any
        // anything below 1.1 to 1.1 as this mimics the behaviour in 2.0.5 where
        // plexus-utils 1.1 was being forced into use.
        // ----------------------------------------------------------------------------

        VersionRange vr = null;

        try
        {
            vr = VersionRange.createFromVersionSpec( "[1.1,)" );
        }
        catch ( InvalidVersionSpecificationException e )
        {
            // Won't happen
        }

        boolean plexusUtilsPresent = false;

        for ( Iterator i = resolutionGroup.getArtifacts().iterator(); i.hasNext(); )
        {
            Artifact a = (Artifact) i.next();

            if ( a.getArtifactId().equals( "plexus-utils" ) &&
                vr.containsVersion( new DefaultArtifactVersion( a.getVersion() ) ) )
            {
                plexusUtilsPresent = true;

                break;
            }
        }

        if ( !plexusUtilsPresent )
        {
            // We will add plexus-utils as every plugin was getting this anyway from Maven itself. We will set the
            // version to the latest version we know that works as of the 2.0.6 release. We set the scope to runtime
            // as this is what's implicitly happening in 2.0.6.

            resolutionGroup.getArtifacts().add( artifactFactory.createArtifact( "org.codehaus.plexus",
                                                                                "plexus-utils", "1.1",
                                                                                Artifact.SCOPE_RUNTIME, "jar" ) );
        }
    }

    private void resolveCoreArtifacts( List unresolved,
                                       ArtifactRepository localRepository,
                                       List resolutionRepositories )
        throws ArtifactResolutionException, ArtifactNotFoundException
    {
        for ( Iterator it = unresolved.iterator(); it.hasNext(); )
        {
            Artifact artifact = (Artifact) it.next();

            File artifactFile = (File) resolvedCoreArtifactFiles.get( artifact.getId() );

            if ( artifactFile == null )
            {
                String resource =
                    "/META-INF/maven/" + artifact.getGroupId() + "/" + artifact.getArtifactId() + "/pom.xml";

                URL resourceUrl = container.getContainerRealm().getResource( resource );

                if ( resourceUrl == null )
                {
                    artifactResolver.resolve( artifact, resolutionRepositories, localRepository );

                    artifactFile = artifact.getFile();
                }
                else
                {
                    String artifactPath = resourceUrl.getPath();

                    if ( artifactPath.startsWith( "file:" ) )
                    {
                        artifactPath = artifactPath.substring( "file:".length() );
                    }

                    artifactPath = artifactPath.substring( 0, artifactPath.length() - resource.length() );

                    if ( artifactPath.endsWith( "/" ) )
                    {
                        artifactPath = artifactPath.substring( 0, artifactPath.length() - 1 );
                    }

                    if ( artifactPath.endsWith( "!" ) )
                    {
                        artifactPath = artifactPath.substring( 0, artifactPath.length() - 1 );
                    }

                    artifactFile = new File( artifactPath ).getAbsoluteFile();
                }

                resolvedCoreArtifactFiles.put( artifact.getId(), artifactFile );
            }

            artifact.setFile( artifactFile );
        }
    }

    private PlexusConfiguration extractMojoConfiguration( PlexusConfiguration mergedConfiguration,
                                                          MojoDescriptor mojoDescriptor )
    {
        Map parameterMap = mojoDescriptor.getParameterMap();

        PlexusConfiguration[] mergedChildren = mergedConfiguration.getChildren();

        XmlPlexusConfiguration extractedConfiguration = new XmlPlexusConfiguration( "configuration" );

        for ( int i = 0; i < mergedChildren.length; i++ )
        {
            PlexusConfiguration child = mergedChildren[i];

            if ( parameterMap.containsKey( child.getName() ) )
            {
                extractedConfiguration.addChild( copyConfiguration( child ) );
            }
            else
            {
                // TODO: I defy anyone to find these messages in the '-X' output! Do we need a new log level?
                // ideally, this would be elevated above the true debug output, but below the default INFO level...
                // [BP] (2004-07-18): need to understand the context more but would prefer this could be either WARN or
                // removed - shouldn't need DEBUG to diagnose a problem most of the time.
                getLogger().debug( "*** WARNING: Configuration \'" + child.getName() + "\' is not used in goal \'" +
                    mojoDescriptor.getFullGoalName() + "; this may indicate a typo... ***" );
            }
        }

        return extractedConfiguration;
    }

    private void checkRequiredParameters( MojoDescriptor goal,
                                          PlexusConfiguration configuration,
                                          ExpressionEvaluator expressionEvaluator )
        throws PluginConfigurationException
    {
        // TODO: this should be built in to the configurator, as we presently double process the expressions

        List parameters = goal.getParameters();

        if ( parameters == null )
        {
            return;
        }

        List invalidParameters = new ArrayList();

        for ( int i = 0; i < parameters.size(); i++ )
        {
            Parameter parameter = (Parameter) parameters.get( i );

            if ( parameter.isRequired() )
            {
                // the key for the configuration map we're building.
                String key = parameter.getName();

                Object fieldValue = null;
                String expression = null;
                PlexusConfiguration value = configuration.getChild( key, false );
                try
                {
                    if ( value != null )
                    {
                        expression = value.getValue( null );

                        fieldValue = expressionEvaluator.evaluate( expression );

                        if ( fieldValue == null )
                        {
                            fieldValue = value.getAttribute( "default-value", null );
                        }
                    }

                    if ( ( fieldValue == null ) && StringUtils.isNotEmpty( parameter.getAlias() ) )
                    {
                        value = configuration.getChild( parameter.getAlias(), false );
                        if ( value != null )
                        {
                            expression = value.getValue( null );
                            fieldValue = expressionEvaluator.evaluate( expression );
                            if ( fieldValue == null )
                            {
                                fieldValue = value.getAttribute( "default-value", null );
                            }
                        }
                    }
                }
                catch ( ExpressionEvaluationException e )
                {
                    throw new PluginConfigurationException( goal.getPluginDescriptor(), e.getMessage(), e );
                }

                // only mark as invalid if there are no child nodes
                if ( ( fieldValue == null ) && ( ( value == null ) || ( value.getChildCount() == 0 ) ) )
                {
                    parameter.setExpression( expression );
                    invalidParameters.add( parameter );
                }
            }
        }

        if ( !invalidParameters.isEmpty() )
        {
            throw new PluginParameterException( goal, invalidParameters );
        }
    }

    private void validatePomConfiguration( MojoDescriptor goal,
                                           PlexusConfiguration pomConfiguration )
        throws PluginConfigurationException
    {
        List parameters = goal.getParameters();

        if ( parameters == null )
        {
            return;
        }

        for ( int i = 0; i < parameters.size(); i++ )
        {
            Parameter parameter = (Parameter) parameters.get( i );

            // the key for the configuration map we're building.
            String key = parameter.getName();

            PlexusConfiguration value = pomConfiguration.getChild( key, false );

            if ( ( value == null ) && StringUtils.isNotEmpty( parameter.getAlias() ) )
            {
                key = parameter.getAlias();
                value = pomConfiguration.getChild( key, false );
            }

            if ( value != null )
            {
                // Make sure the parameter is either editable/configurable, or else is NOT specified in the POM
                if ( !parameter.isEditable() )
                {
                    StringBuffer errorMessage = new StringBuffer()
                        .append( "ERROR: Cannot override read-only parameter: " );
                    errorMessage.append( key );
                    errorMessage.append( " in goal: " ).append( goal.getFullGoalName() );

                    throw new PluginConfigurationException( goal.getPluginDescriptor(), errorMessage.toString() );
                }

                String deprecated = parameter.getDeprecated();
                if ( StringUtils.isNotEmpty( deprecated ) )
                {
                    getLogger().warn( "DEPRECATED [" + parameter.getName() + "]: " + deprecated );
                }
            }
        }
    }

    private PlexusConfiguration mergeMojoConfiguration( XmlPlexusConfiguration fromPom,
                                                        MojoDescriptor mojoDescriptor )
    {
        XmlPlexusConfiguration result = new XmlPlexusConfiguration( fromPom.getName() );
        result.setValue( fromPom.getValue( null ) );

        if ( mojoDescriptor.getParameters() != null )
        {
            PlexusConfiguration fromMojo = mojoDescriptor.getMojoConfiguration();

            for ( Iterator it = mojoDescriptor.getParameters().iterator(); it.hasNext(); )
            {
                Parameter parameter = (Parameter) it.next();

                String paramName = parameter.getName();
                String alias = parameter.getAlias();
                String implementation = parameter.getImplementation();

                PlexusConfiguration pomConfig = fromPom.getChild( paramName );
                PlexusConfiguration aliased = null;

                if ( alias != null )
                {
                    aliased = fromPom.getChild( alias );
                }

                PlexusConfiguration mojoConfig = fromMojo.getChild( paramName, false );

                // first we'll merge configurations from the aliased and real params.
                // TODO: Is this the right thing to do?
                if ( aliased != null )
                {
                    if ( pomConfig == null )
                    {
                        pomConfig = new XmlPlexusConfiguration( paramName );
                    }

                    pomConfig = buildTopDownMergedConfiguration( pomConfig, aliased );
                }

                PlexusConfiguration toAdd = null;

                if ( pomConfig != null )
                {
                    pomConfig = buildTopDownMergedConfiguration( pomConfig, mojoConfig );

                    if ( StringUtils.isNotEmpty( pomConfig.getValue( null ) ) || ( pomConfig.getChildCount() > 0 ) )
                    {
                        toAdd = pomConfig;
                    }
                }

                if ( ( toAdd == null ) && ( mojoConfig != null ) )
                {
                    toAdd = copyConfiguration( mojoConfig );
                }

                if ( toAdd != null )
                {
                    if ( ( implementation != null ) && ( toAdd.getAttribute( "implementation", null ) == null ) )
                    {

                        XmlPlexusConfiguration implementationConf = new XmlPlexusConfiguration( paramName );

                        implementationConf.setAttribute( "implementation", parameter.getImplementation() );

                        toAdd = buildTopDownMergedConfiguration( toAdd, implementationConf );
                    }

                    result.addChild( toAdd );
                }
            }
        }
        return result;
    }

    private XmlPlexusConfiguration buildTopDownMergedConfiguration( PlexusConfiguration dominant,
                                                                    PlexusConfiguration recessive )
    {
        XmlPlexusConfiguration result = new XmlPlexusConfiguration( dominant.getName() );

        String value = dominant.getValue( null );

        if ( StringUtils.isEmpty( value ) && ( recessive != null ) )
        {
            value = recessive.getValue( null );
        }

        if ( StringUtils.isNotEmpty( value ) )
        {
            result.setValue( value );
        }

        String[] attributeNames = dominant.getAttributeNames();

        for ( int i = 0; i < attributeNames.length; i++ )
        {
            String attributeValue = dominant.getAttribute( attributeNames[i], null );

            result.setAttribute( attributeNames[i], attributeValue );
        }

        if ( recessive != null )
        {
            attributeNames = recessive.getAttributeNames();

            for ( int i = 0; i < attributeNames.length; i++ )
            {
                String attributeValue = recessive.getAttribute( attributeNames[i], null );
                // TODO: recessive seems to be dominant here?
                result.setAttribute( attributeNames[i], attributeValue );
            }
        }

        PlexusConfiguration[] children = dominant.getChildren();

        for ( int i = 0; i < children.length; i++ )
        {
            PlexusConfiguration childDom = children[i];
            PlexusConfiguration childRec = recessive == null ? null : recessive.getChild( childDom.getName(), false );

            if ( childRec != null )
            {
                result.addChild( buildTopDownMergedConfiguration( childDom, childRec ) );
            }
            else
            {   // FIXME: copy, or use reference?
                result.addChild( copyConfiguration( childDom ) );
            }
        }

        return result;
    }

    public static PlexusConfiguration copyConfiguration( PlexusConfiguration src )
    {
        // TODO: shouldn't be necessary
        XmlPlexusConfiguration dom = new XmlPlexusConfiguration( src.getName() );
        dom.setValue( src.getValue( null ) );

        String[] attributeNames = src.getAttributeNames();
        for ( int i = 0; i < attributeNames.length; i++ )
        {
            String attributeName = attributeNames[i];
            dom.setAttribute( attributeName, src.getAttribute( attributeName, null ) );
        }

        PlexusConfiguration[] children = src.getChildren();
        for ( int i = 0; i < children.length; i++ )
        {
            dom.addChild( copyConfiguration( children[i] ) );
        }

        return dom;
    }

    // ----------------------------------------------------------------------
    // Mojo Parameter Handling
    // ----------------------------------------------------------------------

    private void populatePluginFields( Mojo plugin,
                                       MojoDescriptor mojoDescriptor,
                                       PlexusConfiguration configuration,
                                       PlexusContainer pluginContainer,
                                       ExpressionEvaluator expressionEvaluator )
        throws PluginConfigurationException
    {
        ComponentConfigurator configurator = null;

        try
        {
            String configuratorId = mojoDescriptor.getComponentConfigurator();

            // TODO: could the configuration be passed to lookup and the configurator known to plexus via the descriptor
            // so that this meethod could entirely be handled by a plexus lookup?
            if ( StringUtils.isNotEmpty( configuratorId ) )
            {
                configurator =
                    (ComponentConfigurator) pluginContainer.lookup( ComponentConfigurator.ROLE, configuratorId );
            }
            else
            {
                configurator = (ComponentConfigurator) pluginContainer.lookup( ComponentConfigurator.ROLE );
            }

            ConfigurationListener listener = new DebugConfigurationListener( getLogger() );

            getLogger().debug( "Configuring mojo '" + mojoDescriptor.getId() + "' -->" );
            configurator.configureComponent( plugin, configuration, expressionEvaluator,
                                             pluginContainer.getContainerRealm(), listener );
            getLogger().debug( "-- end configuration --" );
        }
        catch ( ComponentConfigurationException e )
        {
            throw new PluginConfigurationException( mojoDescriptor.getPluginDescriptor(),
                                                    "Unable to parse the created DOM for plugin configuration", e );
        }
        catch ( ComponentLookupException e )
        {
            throw new PluginConfigurationException( mojoDescriptor.getPluginDescriptor(),
                                                    "Unable to retrieve component configurator for plugin configuration",
                                                    e );
        }
        catch ( LinkageError e )
        {
            if ( getLogger().isFatalErrorEnabled() )
            {
                getLogger().fatalError(
                                        configurator.getClass().getName() + "#configureComponent(...) caused a linkage error ("
                                            + e.getClass().getName() + ") and may be out-of-date. Check the realms:" );

                ClassRealm pluginRealm = mojoDescriptor.getPluginDescriptor().getClassRealm();
                StringBuffer sb = new StringBuffer();
                sb.append( "Plugin realm = " + pluginRealm.getId() ).append( '\n' );
                for ( int i = 0; i < pluginRealm.getConstituents().length; i++ )
                {
                    sb.append( "urls[" + i + "] = " + pluginRealm.getConstituents()[i] );
                    if ( i != ( pluginRealm.getConstituents().length - 1 ) )
                    {
                        sb.append( '\n' );
                    }
                }
                getLogger().fatalError( sb.toString() );

                ClassRealm containerRealm = container.getContainerRealm();
                sb = new StringBuffer();
                sb.append( "Container realm = " + containerRealm.getId() ).append( '\n' );
                for ( int i = 0; i < containerRealm.getConstituents().length; i++ )
                {
                    sb.append( "urls[" + i + "] = " + containerRealm.getConstituents()[i] );
                    if ( i != ( containerRealm.getConstituents().length - 1 ) )
                    {
                        sb.append( '\n' );
                    }
                }
                getLogger().fatalError( sb.toString() );
            }

            throw new PluginConfigurationException( mojoDescriptor.getPluginDescriptor(),
                                                    e.getClass().getName() + ": " + e.getMessage(), e );
        }
        finally
        {
            if ( configurator != null )
            {
                try
                {
                    pluginContainer.release( configurator );
                }
                catch ( ComponentLifecycleException e )
                {
                    getLogger().debug( "Failed to release plugin container - ignoring." );
                }
            }
        }
    }

    public static String createPluginParameterRequiredMessage( MojoDescriptor mojo,
                                                               Parameter parameter,
                                                               String expression )
    {
        StringBuffer message = new StringBuffer();

        message.append( "The '" );
        message.append( parameter.getName() );
        message.append( "' parameter is required for the execution of the " );
        message.append( mojo.getFullGoalName() );
        message.append( " mojo and cannot be null." );
        if ( expression != null )
        {
            message.append( " The retrieval expression was: " ).append( expression );
        }

        return message.toString();
    }

    // ----------------------------------------------------------------------
    // Lifecycle
    // ----------------------------------------------------------------------

    public void contextualize( Context context )
        throws ContextException
    {
        container = (PlexusContainer) context.get( PlexusConstants.PLEXUS_KEY );

        mojoLogger = new DefaultLog( container.getLoggerManager().getLoggerForComponent( Mojo.ROLE ) );
    }

    public void initialize()
    {
        artifactFilter = MavenArtifactFilterManager.createStandardFilter();
    }

    // ----------------------------------------------------------------------
    // Artifact resolution
    // ----------------------------------------------------------------------

    private void resolveTransitiveDependencies( MavenSession context,
                                                ArtifactResolver artifactResolver,
                                                String scope,
                                                ArtifactFactory artifactFactory,
                                                MavenProject project, boolean isAggregator )
        throws ArtifactResolutionException, ArtifactNotFoundException, InvalidDependencyVersionException
    {
        ArtifactFilter filter = new ScopeArtifactFilter( scope );

        // TODO: such a call in MavenMetadataSource too - packaging not really the intention of type
        Artifact artifact = artifactFactory.createBuildArtifact( project.getGroupId(), project.getArtifactId(),
                                                                 project.getVersion(), project.getPackaging() );

        // TODO: we don't need to resolve over and over again, as long as we are sure that the parameters are the same
        // check this with yourkit as a hot spot.
        // Don't recreate if already created - for effeciency, and because clover plugin adds to it
        if ( project.getDependencyArtifacts() == null )
        {
            project.setDependencyArtifacts( project.createArtifacts( artifactFactory, null, null ) );
        }
        
        Set resolvedArtifacts;
        try
        {
            ArtifactResolutionResult result = artifactResolver.resolveTransitively( project.getDependencyArtifacts(),
                                                                                artifact,
                                                                                project.getManagedVersionMap(),
                                                                                context.getLocalRepository(),
                                                                                project.getRemoteArtifactRepositories(),
                                                                                artifactMetadataSource, filter );
            resolvedArtifacts = result.getArtifacts();
        }
        catch (MultipleArtifactsNotFoundException me)
        {
            /*only do this if we are an aggregating plugin: MNG-2277
            if the dependency doesn't yet exist but is in the reactor, then 
            all we can do is warn and skip it. A better fix can be inserted into 2.1*/
            if (isAggregator && checkMissingArtifactsInReactor( context.getSortedProjects(), me.getMissingArtifacts() ))
            {
                resolvedArtifacts = new HashSet(me.getResolvedArtifacts());
            }
            else
            {
                //we can't find all the artifacts in the reactor so bubble the exception up.
                throw me;
            }
        }
        project.setArtifacts( resolvedArtifacts );
    }

    /**
     * This method is checking to see if the artifacts that can't be resolved are all
     * part of this reactor. This is done to prevent a chicken or egg scenario with
     * fresh projects that have a plugin that is an aggregator and requires dependencies. See
     * MNG-2277 for more info.
     * @param projects the sibling projects in the reactor
     * @param missing the artifacts that can't be found
     * @return true if ALL missing artifacts are found in the reactor.
     */
    private boolean checkMissingArtifactsInReactor(Collection projects, Collection missing)
    {
        Collection foundInReactor = new HashSet();
        Iterator iter = missing.iterator();
        while (iter.hasNext())
        {
            Artifact mArtifact = (Artifact) iter.next();
            Iterator pIter = projects.iterator();
            while (pIter.hasNext())
            {
                MavenProject p = (MavenProject) pIter.next();
                if (p.getArtifactId().equals( mArtifact.getArtifactId()) &&
                    p.getGroupId().equals( mArtifact.getGroupId()) &&
                    p.getVersion().equals( mArtifact.getVersion()))
                {
                    //TODO: the packaging could be different, but the exception doesn't contain that info
                    //most likely it would be produced by the project we just found in the reactor since all
                    //the other info matches. Assume it's ok.
                    getLogger().warn( "The dependency: "+ p.getId()+" can't be resolved but has been found in the reactor.\nThis dependency has been excluded from the plugin execution. You should rerun this mojo after executing mvn install.\n" );
                    
                    //found it, move on.
                    foundInReactor.add( p );
                    break;
                }   
            }
        }
        
        //if all of them have been found, we can continue.
        return foundInReactor.size() == missing.size();
    }
    
    
    // ----------------------------------------------------------------------
    // Artifact downloading
    // ----------------------------------------------------------------------

    private void downloadDependencies( MavenProject project,
                                       MavenSession context,
                                       ArtifactResolver artifactResolver )
        throws ArtifactResolutionException, ArtifactNotFoundException
    {
        ArtifactRepository localRepository = context.getLocalRepository();
        List remoteArtifactRepositories = project.getRemoteArtifactRepositories();

        for ( Iterator it = project.getArtifacts().iterator(); it.hasNext(); )
        {
            Artifact artifact = (Artifact) it.next();

            artifactResolver.resolve( artifact, remoteArtifactRepositories, localRepository );
        }
    }

    public Object getPluginComponent( Plugin plugin,
                                      String role,
                                      String roleHint )
        throws PluginManagerException, ComponentLookupException
    {
        PluginDescriptor pluginDescriptor = pluginCollector.getPluginDescriptor( plugin );

        PlexusContainer pluginContainer = getPluginContainer( pluginDescriptor );

        return pluginContainer.lookup( role, roleHint );
    }

    public Map getPluginComponents( Plugin plugin,
                                    String role )
        throws ComponentLookupException, PluginManagerException
    {
        PluginDescriptor pluginDescriptor = pluginCollector.getPluginDescriptor( plugin );

        PlexusContainer pluginContainer = getPluginContainer( pluginDescriptor );

        return pluginContainer.lookupMap( role );
    }
}