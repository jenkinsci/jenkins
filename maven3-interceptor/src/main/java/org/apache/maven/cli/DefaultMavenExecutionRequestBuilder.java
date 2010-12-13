package org.apache.maven.cli;

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
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulationException;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.lifecycle.internal.LifecycleWeaveBuilder;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.properties.internal.EnvironmentUtils;
import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingResult;
import org.apache.maven.settings.building.SettingsProblem;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.codehaus.plexus.util.StringUtils;
import org.sonatype.aether.impl.internal.EnhancedLocalRepositoryManager;
import org.sonatype.aether.transfer.TransferListener;
import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecUtil;
import org.sonatype.plexus.components.sec.dispatcher.model.SettingsSecurity;

/**
 * Most of code is coming from asf svn repo waiting before having available
 * @author Olivier Lamy
 */
@Component( role = MavenExecutionRequestBuilder.class)
public class DefaultMavenExecutionRequestBuilder
    implements MavenExecutionRequestBuilder, Initializable
{

    @Requirement
    private SettingsBuilder settingsBuilder;

    @Requirement
    private MavenExecutionRequestPopulator executionRequestPopulator;

    @Requirement
    private Logger plexusLogger;

    @Requirement
    private ModelProcessor modelProcessor;

    @Requirement
    private PlexusContainer plexusContainer;

    private DefaultSecDispatcher dispatcher;

    public void initialize()
        throws InitializationException
    {
        try
        {
            dispatcher = (DefaultSecDispatcher) plexusContainer.lookup( SecDispatcher.class, "maven" );
        }
        catch ( ComponentLookupException e )
        {
            throw new InitializationException( e.getMessage(), e );
        }
    }

    /**
     * @throws MavenExecutionRequestPopulationException 
     * @see org.jvnet.hudson.maven3.MavenExecutionRequestBuilder.MavenExecutionRequestsBuilder#getMavenExecutionRequest(java.lang.String[])
     */
    public MavenExecutionRequest getMavenExecutionRequest( String[] args, PrintStream printStream )
        throws MavenExecutionRequestPopulationException, SettingsBuildingException,
        MavenExecutionRequestsBuilderException
    {
        try
        {
            CliRequest cliRequest = new CliRequest( args, null );
            initialize( cliRequest, printStream );
            // Need to process cli options first to get possible logging options
            cli( cliRequest );
            logging( cliRequest );
            commands( cliRequest );
            properties( cliRequest );
            // we are in a container so no need
            //container( cliRequest );
            settings( cliRequest );
            populateRequest( cliRequest );
            encryption( cliRequest );

            MavenExecutionRequest request = executionRequestPopulator.populateDefaults( cliRequest.request );
            
            // TODO move this in ASF sources ?
            
            if (request.getProjectBuildingRequest().getRepositorySession()== null)
            {
                MavenRepositorySystemSession session = new MavenRepositorySystemSession();
                if (session.getLocalRepositoryManager() == null)
                {
                    session.setLocalRepositoryManager( new EnhancedLocalRepositoryManager( request.getLocalRepositoryPath() ) );
                }
                request.getProjectBuildingRequest().setRepositorySession( session );
            }
                        
            return request;
        }
        catch ( Exception e )
        {
            throw new MavenExecutionRequestsBuilderException( e.getMessage(), e );
        }

    }

    static File resolveFile( File file, String workingDirectory )
    {
        if ( file == null )
        {
            return null;
        }
        else if ( file.isAbsolute() )
        {
            return file;
        }
        else if ( file.getPath().startsWith( File.separator ) )
        {
            // drive-relative Windows path
            return file.getAbsoluteFile();
        }
        else
        {
            return new File( workingDirectory, file.getPath() ).getAbsoluteFile();
        }
    }

    private void initialize( CliRequest cliRequest, PrintStream printStream )
    {
        if ( cliRequest.stdout == null )
        {
            cliRequest.stdout = System.out;
        }
        if ( cliRequest.stderr == null )
        {
            cliRequest.stderr = System.err;
        }

        if ( cliRequest.logger == null )
        {
            cliRequest.logger = new PrintStreamLogger( cliRequest.stdout );
        }
        else
        {
            cliRequest.logger.setStream( cliRequest.stdout );
        }

        if ( cliRequest.workingDirectory == null )
        {
            cliRequest.workingDirectory = System.getProperty( "user.dir" );
        }

        //
        // Make sure the Maven home directory is an absolute path to save us from confusion with say drive-relative
        // Windows paths.
        //
        String mavenHome = System.getProperty( "maven.home" );

        if ( mavenHome != null )
        {
            System.setProperty( "maven.home", new File( mavenHome ).getAbsolutePath() );
        }
    }

    private void cli( CliRequest cliRequest )
        throws Exception
    {
        CLIManager cliManager = new CLIManager();

        try
        {
            cliRequest.commandLine = cliManager.parse( cliRequest.args );
        }
        catch ( ParseException e )
        {
            cliRequest.stderr.println( "Unable to parse command line options: " + e.getMessage() );
            cliManager.displayHelp( cliRequest.stdout );
            throw e;
        }

        // TODO: these should be moved out of here. Wrong place.
        //
        if ( cliRequest.commandLine.hasOption( CLIManager.HELP ) )
        {
            cliManager.displayHelp( cliRequest.stdout );
            throw new MavenCli.ExitException( 0 );
        }

        if ( cliRequest.commandLine.hasOption( CLIManager.VERSION ) )
        {
            CLIReportingUtils.showVersion( cliRequest.stdout );
            throw new MavenCli.ExitException( 0 );
        }
    }

    private void logging( CliRequest cliRequest )
    {
        cliRequest.debug = cliRequest.commandLine.hasOption( CLIManager.DEBUG );
        cliRequest.quiet = !cliRequest.debug && cliRequest.commandLine.hasOption( CLIManager.QUIET );
        cliRequest.showErrors = cliRequest.debug || cliRequest.commandLine.hasOption( CLIManager.ERRORS );

        if ( cliRequest.debug )
        {
            cliRequest.request.setLoggingLevel( MavenExecutionRequest.LOGGING_LEVEL_DEBUG );
        }
        else if ( cliRequest.quiet )
        {
            // TODO: we need to do some more work here. Some plugins use sys out or log errors at info level.
            // Ideally, we could use Warn across the board
            cliRequest.request.setLoggingLevel( MavenExecutionRequest.LOGGING_LEVEL_ERROR );
            // TODO:Additionally, we can't change the mojo level because the component key includes the version and
            // it isn't known ahead of time. This seems worth changing.
        }
        else
        {
            cliRequest.request.setLoggingLevel( MavenExecutionRequest.LOGGING_LEVEL_INFO );
        }

        plexusLogger.setThreshold( cliRequest.request.getLoggingLevel() );

        if ( cliRequest.commandLine.hasOption( CLIManager.LOG_FILE ) )
        {
            File logFile = new File( cliRequest.commandLine.getOptionValue( CLIManager.LOG_FILE ) );
            logFile = resolveFile( logFile, cliRequest.workingDirectory );

            try
            {
                cliRequest.fileStream = new PrintStream( logFile );
                cliRequest.logger.setStream( cliRequest.fileStream );
            }
            catch ( FileNotFoundException e )
            {
                cliRequest.stderr.println( e );
                cliRequest.logger.setStream( cliRequest.stdout );
            }
        }
        else
        {
            cliRequest.logger.setStream( cliRequest.stdout );
        }

        cliRequest.request.setExecutionListener( new ExecutionEventLogger( cliRequest.logger ) );
    }

    private void commands( CliRequest cliRequest )
    {
        if ( cliRequest.debug || cliRequest.commandLine.hasOption( CLIManager.SHOW_VERSION ) )
        {
            CLIReportingUtils.showVersion( cliRequest.stdout );
        }

        if ( cliRequest.showErrors )
        {
            cliRequest.logger.info( "Error stacktraces are turned on." );
        }

        //
        // TODO: move checksum policies to
        //
        if ( MavenExecutionRequest.CHECKSUM_POLICY_WARN.equals( cliRequest.request.getGlobalChecksumPolicy() ) )
        {
            cliRequest.logger.info( "Disabling strict checksum verification on all artifact downloads." );
        }
        else if ( MavenExecutionRequest.CHECKSUM_POLICY_FAIL.equals( cliRequest.request.getGlobalChecksumPolicy() ) )
        {
            cliRequest.logger.info( "Enabling strict checksum verification on all artifact downloads." );
        }
    }

    private void properties( CliRequest cliRequest )
    {
        populateProperties( cliRequest.commandLine, cliRequest.systemProperties, cliRequest.userProperties );
    }

    // ----------------------------------------------------------------------
    // System properties handling
    // ----------------------------------------------------------------------

    static void populateProperties( CommandLine commandLine, Properties systemProperties, Properties userProperties )
    {
        EnvironmentUtils.addEnvVars( systemProperties );

        // ----------------------------------------------------------------------
        // Options that are set on the command line become system properties
        // and therefore are set in the session properties. System properties
        // are most dominant.
        // ----------------------------------------------------------------------

        if ( commandLine.hasOption( CLIManager.SET_SYSTEM_PROPERTY ) )
        {
            String[] defStrs = commandLine.getOptionValues( CLIManager.SET_SYSTEM_PROPERTY );

            if ( defStrs != null )
            {
                for ( int i = 0; i < defStrs.length; ++i )
                {
                    setCliProperty( defStrs[i], userProperties );
                }
            }
        }

        systemProperties.putAll( System.getProperties() );
    }

    private static void setCliProperty( String property, Properties properties )
    {
        String name;

        String value;

        int i = property.indexOf( "=" );

        if ( i <= 0 )
        {
            name = property.trim();

            value = "true";
        }
        else
        {
            name = property.substring( 0, i ).trim();

            value = property.substring( i + 1 );
        }

        properties.setProperty( name, value );

        // ----------------------------------------------------------------------
        // I'm leaving the setting of system properties here as not to break
        // the SystemPropertyProfileActivator. This won't harm embedding. jvz.
        // ----------------------------------------------------------------------

        System.setProperty( name, value );
    }

    private void settings( CliRequest cliRequest )
        throws Exception
    {
        File userSettingsFile;

        if ( cliRequest.commandLine.hasOption( CLIManager.ALTERNATE_USER_SETTINGS ) )
        {
            userSettingsFile = new File( cliRequest.commandLine.getOptionValue( CLIManager.ALTERNATE_USER_SETTINGS ) );
            userSettingsFile = resolveFile( userSettingsFile, cliRequest.workingDirectory );

            if ( !userSettingsFile.isFile() )
            {
                throw new FileNotFoundException( "The specified user settings file does not exist: " + userSettingsFile );
            }
        }
        else
        {
            userSettingsFile = MavenCli.DEFAULT_USER_SETTINGS_FILE;
        }

        cliRequest.logger.debug( "Reading user settings from " + userSettingsFile );

        File globalSettingsFile;

        if ( cliRequest.commandLine.hasOption( CLIManager.ALTERNATE_GLOBAL_SETTINGS ) )
        {
            globalSettingsFile = new File( cliRequest.commandLine.getOptionValue( CLIManager.ALTERNATE_GLOBAL_SETTINGS ) );
            globalSettingsFile = resolveFile( globalSettingsFile, cliRequest.workingDirectory );

            if ( !globalSettingsFile.isFile() )
            {
                throw new FileNotFoundException( "The specified global settings file does not exist: "
                    + globalSettingsFile );
            }
        }
        else
        {
            globalSettingsFile = MavenCli.DEFAULT_GLOBAL_SETTINGS_FILE;
        }

        cliRequest.logger.debug( "Reading global settings from " + globalSettingsFile );

        cliRequest.request.setGlobalSettingsFile( globalSettingsFile );
        cliRequest.request.setUserSettingsFile( userSettingsFile );

        SettingsBuildingRequest settingsRequest = new DefaultSettingsBuildingRequest();
        settingsRequest.setGlobalSettingsFile( globalSettingsFile );
        settingsRequest.setUserSettingsFile( userSettingsFile );
        settingsRequest.setSystemProperties( cliRequest.systemProperties );
        settingsRequest.setUserProperties( cliRequest.userProperties );

        SettingsBuildingResult settingsResult = settingsBuilder.build( settingsRequest );

        executionRequestPopulator.populateFromSettings( cliRequest.request, settingsResult.getEffectiveSettings() );

        if ( !settingsResult.getProblems().isEmpty() && cliRequest.logger.isWarnEnabled() )
        {
            cliRequest.logger.warn( "" );
            cliRequest.logger.warn( "Some problems were encountered while building the effective settings" );

            for ( SettingsProblem problem : settingsResult.getProblems() )
            {
                cliRequest.logger.warn( problem.getMessage() + " @ " + problem.getLocation() );
            }

            cliRequest.logger.warn( "" );
        }
    }

    private MavenExecutionRequest populateRequest( CliRequest cliRequest )
    {
        MavenExecutionRequest request = cliRequest.request;
        CommandLine commandLine = cliRequest.commandLine;
        String workingDirectory = cliRequest.workingDirectory;
        boolean debug = cliRequest.debug;
        boolean quiet = cliRequest.quiet;
        boolean showErrors = cliRequest.showErrors;

        String[] deprecatedOptions = { "up", "npu", "cpu", "npr" };
        for ( String deprecatedOption : deprecatedOptions )
        {
            if ( commandLine.hasOption( deprecatedOption ) )
            {
                cliRequest.stdout.println( "[WARNING] Command line option -" + deprecatedOption
                    + " is deprecated and will be removed in future Maven versions." );
            }
        }

        // ----------------------------------------------------------------------
        // Now that we have everything that we need we will fire up plexus and
        // bring the maven component to life for use.
        // ----------------------------------------------------------------------

        if ( commandLine.hasOption( CLIManager.BATCH_MODE ) )
        {
            request.setInteractiveMode( false );
        }

        boolean noSnapshotUpdates = false;
        if ( commandLine.hasOption( CLIManager.SUPRESS_SNAPSHOT_UPDATES ) )
        {
            noSnapshotUpdates = true;
        }

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        @SuppressWarnings("unchecked")
        List<String> goals = commandLine.getArgList();

        boolean recursive = true;

        // this is the default behavior.
        String reactorFailureBehaviour = MavenExecutionRequest.REACTOR_FAIL_FAST;

        if ( commandLine.hasOption( CLIManager.NON_RECURSIVE ) )
        {
            recursive = false;
        }

        if ( commandLine.hasOption( CLIManager.FAIL_FAST ) )
        {
            reactorFailureBehaviour = MavenExecutionRequest.REACTOR_FAIL_FAST;
        }
        else if ( commandLine.hasOption( CLIManager.FAIL_AT_END ) )
        {
            reactorFailureBehaviour = MavenExecutionRequest.REACTOR_FAIL_AT_END;
        }
        else if ( commandLine.hasOption( CLIManager.FAIL_NEVER ) )
        {
            reactorFailureBehaviour = MavenExecutionRequest.REACTOR_FAIL_NEVER;
        }

        if ( commandLine.hasOption( CLIManager.OFFLINE ) )
        {
            request.setOffline( true );
        }

        boolean updateSnapshots = false;

        if ( commandLine.hasOption( CLIManager.UPDATE_SNAPSHOTS ) )
        {
            updateSnapshots = true;
        }

        String globalChecksumPolicy = null;

        if ( commandLine.hasOption( CLIManager.CHECKSUM_FAILURE_POLICY ) )
        {
            globalChecksumPolicy = MavenExecutionRequest.CHECKSUM_POLICY_FAIL;
        }
        else if ( commandLine.hasOption( CLIManager.CHECKSUM_WARNING_POLICY ) )
        {
            globalChecksumPolicy = MavenExecutionRequest.CHECKSUM_POLICY_WARN;
        }

        File baseDirectory = new File( workingDirectory, "" ).getAbsoluteFile();

        // ----------------------------------------------------------------------
        // Profile Activation
        // ----------------------------------------------------------------------

        List<String> activeProfiles = new ArrayList<String>();

        List<String> inactiveProfiles = new ArrayList<String>();

        if ( commandLine.hasOption( CLIManager.ACTIVATE_PROFILES ) )
        {
            String[] profileOptionValues = commandLine.getOptionValues( CLIManager.ACTIVATE_PROFILES );
            if ( profileOptionValues != null )
            {
                for ( int i = 0; i < profileOptionValues.length; ++i )
                {
                    StringTokenizer profileTokens = new StringTokenizer( profileOptionValues[i], "," );

                    while ( profileTokens.hasMoreTokens() )
                    {
                        String profileAction = profileTokens.nextToken().trim();

                        if ( profileAction.startsWith( "-" ) || profileAction.startsWith( "!" ) )
                        {
                            inactiveProfiles.add( profileAction.substring( 1 ) );
                        }
                        else if ( profileAction.startsWith( "+" ) )
                        {
                            activeProfiles.add( profileAction.substring( 1 ) );
                        }
                        else
                        {
                            activeProfiles.add( profileAction );
                        }
                    }
                }
            }
        }

        TransferListener transferListener;

        if ( quiet )
        {
            transferListener = new QuietMavenTransferListener( );
        }
        else if ( request.isInteractiveMode() )
        {
            transferListener = new ConsoleMavenTransferListener( cliRequest.stdout );
        }
        else
        {
            transferListener = new BatchModeMavenTransferListener( cliRequest.stdout );
        }

        //transferListener. .setShowChecksumEvents( false );

        String alternatePomFile = null;
        if ( commandLine.hasOption( CLIManager.ALTERNATE_POM_FILE ) )
        {
            alternatePomFile = commandLine.getOptionValue( CLIManager.ALTERNATE_POM_FILE );
        }

        int loggingLevel;

        if ( debug )
        {
            loggingLevel = MavenExecutionRequest.LOGGING_LEVEL_DEBUG;
        }
        else if ( quiet )
        {
            // TODO: we need to do some more work here. Some plugins use sys out or log errors at info level.
            // Ideally, we could use Warn across the board
            loggingLevel = MavenExecutionRequest.LOGGING_LEVEL_ERROR;
            // TODO:Additionally, we can't change the mojo level because the component key includes the version and
            // it isn't known ahead of time. This seems worth changing.
        }
        else
        {
            loggingLevel = MavenExecutionRequest.LOGGING_LEVEL_INFO;
        }

        File userToolchainsFile;
        if ( commandLine.hasOption( CLIManager.ALTERNATE_USER_TOOLCHAINS ) )
        {
            userToolchainsFile = new File( commandLine.getOptionValue( CLIManager.ALTERNATE_USER_TOOLCHAINS ) );
            userToolchainsFile = resolveFile( userToolchainsFile, workingDirectory );
        }
        else
        {
            userToolchainsFile = MavenCli.DEFAULT_USER_TOOLCHAINS_FILE;
        }

        request.setBaseDirectory( baseDirectory ).setGoals( goals ).setSystemProperties( cliRequest.systemProperties )
            .setUserProperties( cliRequest.userProperties ).setReactorFailureBehavior( reactorFailureBehaviour ) // default: fail fast
            .setRecursive( recursive ) // default: true
            .setShowErrors( showErrors ) // default: false
            .addActiveProfiles( activeProfiles ) // optional
            .addInactiveProfiles( inactiveProfiles ) // optional
            .setLoggingLevel( loggingLevel ) // default: info
            .setTransferListener( transferListener ) // default: batch mode which goes along with interactive
            .setUpdateSnapshots( updateSnapshots ) // default: false
            .setNoSnapshotUpdates( noSnapshotUpdates ) // default: false
            .setGlobalChecksumPolicy( globalChecksumPolicy ) // default: warn
            .setUserToolchainsFile( userToolchainsFile );

        if ( alternatePomFile != null )
        {
            File pom = resolveFile( new File( alternatePomFile ), workingDirectory );

            request.setPom( pom );
        }
        else
        {
            File pom = modelProcessor.locatePom( baseDirectory );

            if ( pom.isFile() )
            {
                request.setPom( pom );
            }
        }

        if ( ( request.getPom() != null ) && ( request.getPom().getParentFile() != null ) )
        {
            request.setBaseDirectory( request.getPom().getParentFile() );
        }

        if ( commandLine.hasOption( CLIManager.RESUME_FROM ) )
        {
            request.setResumeFrom( commandLine.getOptionValue( CLIManager.RESUME_FROM ) );
        }

        if ( commandLine.hasOption( CLIManager.PROJECT_LIST ) )
        {
            String projectList = commandLine.getOptionValue( CLIManager.PROJECT_LIST );
            String[] projects = StringUtils.split( projectList, "," );
            request.setSelectedProjects( Arrays.asList( projects ) );
        }

        if ( commandLine.hasOption( CLIManager.ALSO_MAKE ) && !commandLine.hasOption( CLIManager.ALSO_MAKE_DEPENDENTS ) )
        {
            request.setMakeBehavior( MavenExecutionRequest.REACTOR_MAKE_UPSTREAM );
        }
        else if ( !commandLine.hasOption( CLIManager.ALSO_MAKE )
            && commandLine.hasOption( CLIManager.ALSO_MAKE_DEPENDENTS ) )
        {
            request.setMakeBehavior( MavenExecutionRequest.REACTOR_MAKE_DOWNSTREAM );
        }
        else if ( commandLine.hasOption( CLIManager.ALSO_MAKE )
            && commandLine.hasOption( CLIManager.ALSO_MAKE_DEPENDENTS ) )
        {
            request.setMakeBehavior( MavenExecutionRequest.REACTOR_MAKE_BOTH );
        }

        String localRepoProperty = request.getUserProperties().getProperty( MavenCli.LOCAL_REPO_PROPERTY );

        if ( localRepoProperty == null )
        {
            localRepoProperty = request.getSystemProperties().getProperty( MavenCli.LOCAL_REPO_PROPERTY );
        }

        if ( localRepoProperty != null )
        {
            request.setLocalRepositoryPath( localRepoProperty );
        }

        final String threadConfiguration = commandLine.hasOption( CLIManager.THREADS ) ? commandLine
            .getOptionValue( CLIManager.THREADS ) : request.getSystemProperties()
            .getProperty( MavenCli.THREADS_DEPRECATED ); // TODO: Remove this setting. Note that the int-tests use it

        if ( threadConfiguration != null )
        {
            request.setPerCoreThreadCount( threadConfiguration.contains( "C" ) );
            if ( threadConfiguration.contains( "W" ) )
            {
                LifecycleWeaveBuilder.setWeaveMode( request.getUserProperties() );
            }
            request.setThreadCount( threadConfiguration.replace( "C", "" ).replace( "W", "" ).replace( "auto", "" ) );
        }

        return request;
    }

    private void encryption( CliRequest cliRequest )
        throws Exception
    {
        if ( cliRequest.commandLine.hasOption( CLIManager.ENCRYPT_MASTER_PASSWORD ) )
        {
            String passwd = cliRequest.commandLine.getOptionValue( CLIManager.ENCRYPT_MASTER_PASSWORD );

            DefaultPlexusCipher cipher = new DefaultPlexusCipher();

            cliRequest.stdout.println( cipher.encryptAndDecorate( passwd,
                                                                  DefaultSecDispatcher.SYSTEM_PROPERTY_SEC_LOCATION ) );

            throw new MavenCli.ExitException( 0 );
        }
        else if ( cliRequest.commandLine.hasOption( CLIManager.ENCRYPT_PASSWORD ) )
        {
            String passwd = cliRequest.commandLine.getOptionValue( CLIManager.ENCRYPT_PASSWORD );

            String configurationFile = dispatcher.getConfigurationFile();

            if ( configurationFile.startsWith( "~" ) )
            {
                configurationFile = System.getProperty( "user.home" ) + configurationFile.substring( 1 );
            }

            String file = System.getProperty( DefaultSecDispatcher.SYSTEM_PROPERTY_SEC_LOCATION, configurationFile );

            String master = null;

            SettingsSecurity sec = SecUtil.read( file, true );
            if ( sec != null )
            {
                master = sec.getMaster();
            }

            if ( master == null )
            {
                throw new IllegalStateException( "Master password is not set in the setting security file: " + file );
            }

            DefaultPlexusCipher cipher = new DefaultPlexusCipher();
            String masterPasswd = cipher.decryptDecorated( master, DefaultSecDispatcher.SYSTEM_PROPERTY_SEC_LOCATION );
            cliRequest.stdout.println( cipher.encryptAndDecorate( passwd, masterPasswd ) );

            throw new MavenCli.ExitException( 0 );
        }
    }

    static class CliRequest
    {
        String[] args;
        CommandLine commandLine;
        PrintStream stdout;
        PrintStream stderr;
        ClassWorld classWorld;
        String workingDirectory;
        boolean debug;
        boolean quiet;
        boolean showErrors = true;
        PrintStream fileStream;
        Properties userProperties = new Properties();
        Properties systemProperties = new Properties();
        MavenExecutionRequest request;
        PrintStreamLogger logger;

        CliRequest( String[] args, ClassWorld classWorld )
        {
            this.args = args;
            this.classWorld = classWorld;
            this.request = new DefaultMavenExecutionRequest();
        }
    }    
    
}
