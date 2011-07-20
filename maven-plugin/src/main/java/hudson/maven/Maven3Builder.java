/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Olivier Lamy
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

import hudson.Launcher;
import hudson.maven.MavenBuild.ProxyImpl2;
import hudson.maven.util.ExecutionEventLogger;
import hudson.model.BuildListener;
import hudson.model.Executor;
import jenkins.model.Jenkins;
import hudson.model.Result;
import hudson.remoting.Channel;
import hudson.remoting.DelegatingCallable;
import hudson.remoting.Future;
import hudson.util.IOException2;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.text.NumberFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;

import org.apache.maven.cli.PrintStreamLogger;
import org.apache.maven.execution.AbstractExecutionListener;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.jvnet.hudson.maven3.agent.Maven3Main;
import org.jvnet.hudson.maven3.launcher.Maven3Launcher;
import org.jvnet.hudson.maven3.listeners.HudsonMavenExecutionResult;

/**
 * @author Olivier Lamy
 *
 */
public class Maven3Builder extends AbstractMavenBuilder implements DelegatingCallable<Result,IOException> {

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
    
    HudsonMavenExecutionResult mavenExecutionResult;    
    
    private final Map<ModuleName,MavenBuildProxy2> proxies;
    private final Map<ModuleName,ProxyImpl2> sourceProxies;
    private final Map<ModuleName,List<MavenReporter>> reporters = new HashMap<ModuleName,List<MavenReporter>>();
    
    private final MavenBuildInformation mavenBuildInformation;
    
    protected Maven3Builder(BuildListener listener,Map<ModuleName,ProxyImpl2> proxies, Map<ModuleName,List<MavenReporter>> reporters, List<String> goals, Map<String, String> systemProps, MavenBuildInformation mavenBuildInformation) {
        super( listener, goals, systemProps );
        this.mavenBuildInformation = mavenBuildInformation;
        sourceProxies = new HashMap<ModuleName, ProxyImpl2>(proxies);
        this.proxies = new HashMap<ModuleName, MavenBuildProxy2>(proxies);
        for (Entry<ModuleName,MavenBuildProxy2> e : this.proxies.entrySet())
            e.setValue(new FilterImpl(e.getValue(), this.mavenBuildInformation,Channel.current()));

        this.reporters.putAll( reporters );
    }    
    
    public Result call() throws IOException {

        MavenExecutionListener mavenExecutionListener = new MavenExecutionListener( this );
        try {
            futures = new CopyOnWriteArrayList<Future<?>>(  );
            
            Maven3Launcher.setMavenExecutionListener( mavenExecutionListener );
            
            markAsSuccess = false;

            registerSystemProperties();

            listener.getLogger().println(formatArgs(goals));
            
            
            int r = Maven3Main.launch( goals.toArray(new String[goals.size()]));

            // now check the completion status of async ops
            boolean messageReported = false;
            long startTime = System.nanoTime();
            for (Future<?> f : futures) {
                try {
                    if(!f.isDone() && !messageReported) {
                        messageReported = true;
                        // FIXME messages
                        listener.getLogger().println("maven builder waiting");
                    }
                    f.get();
                } catch (InterruptedException e) {
                    // attempt to cancel all asynchronous tasks
                    for (Future<?> g : futures)
                        g.cancel(true);
                    // FIXME messages
                    listener.getLogger().println("build aborted");
                    return Executor.currentExecutor().abortResult();
                } catch (ExecutionException e) {
                    // FIXME messages
                    e.printStackTrace(listener.error("async build failed"));
                }
            }
            mavenExecutionListener.overheadTime += System.nanoTime()-startTime;
            futures.clear();

            if(profile) {
                NumberFormat n = NumberFormat.getInstance();
                PrintStream logger = listener.getLogger();
                logger.println("Total overhead was "+format(n,mavenExecutionListener.overheadTime)+"ms");
                Channel ch = Channel.current();
                logger.println("Class loading "   +format(n,ch.classLoadingTime.get())   +"ms, "+ch.classLoadingCount+" classes");
                logger.println("Resource loading "+format(n,ch.resourceLoadingTime.get())+"ms, "+ch.resourceLoadingCount+" times");                
            }

            mavenExecutionResult = Maven3Launcher.getMavenExecutionResult();
            
            PrintStream logger = listener.getLogger();
            
            if(r==0 && mavenExecutionResult.getThrowables().isEmpty()) return Result.SUCCESS;
            
            if (!mavenExecutionResult.getThrowables().isEmpty()) {
                logger.println( "mavenExecutionResult exceptions not empty");
                for(Throwable throwable : mavenExecutionResult.getThrowables()) {
                    logger.println("message : " + throwable.getMessage());
                    if (throwable.getCause()!=null) {
                        logger.println("cause : " + throwable.getCause().getMessage());
                    }
                    logger.println("Stack trace : ");
                    throwable.printStackTrace( logger );
                }
                
            }

            if(markAsSuccess) {
                listener.getLogger().println(Messages.MavenBuilder_Failed());
                return Result.SUCCESS;
            }
            return Result.FAILURE;
        } catch (NoSuchMethodException e) {
            throw new IOException2(e);
        } catch (IllegalAccessException e) {
            throw new IOException2(e);
        } catch (InvocationTargetException e) {
            throw new IOException2(e);
        } catch (ClassNotFoundException e) {
            throw new IOException2(e);
        } catch (Exception e) {
            throw new IOException2(e);
        }
    }

    // since reporters might be from plugins, use the uberjar to resolve them.
    public ClassLoader getClassLoader() {
        return Jenkins.getInstance().getPluginManager().uberClassLoader;
    }
    
    
    /**
     * Invoked after the maven has finished running, and in the master, not in the maven process.
     */
    void end(Launcher launcher) throws IOException, InterruptedException {
        for (Map.Entry<ModuleName,ProxyImpl2> e : sourceProxies.entrySet()) {
            ProxyImpl2 p = e.getValue();
            for (MavenReporter r : reporters.get(e.getKey())) {
                // we'd love to do this when the module build ends, but doing so requires
                // we know how many task segments are in the current build.
                r.end(p.owner(),launcher,listener);
                p.appendLastLog();
            }
            p.close();
        }
    }      

    private class FilterImpl extends MavenBuildProxy2.Filter<MavenBuildProxy2> implements Serializable {
        
        private MavenBuildInformation mavenBuildInformation;

        private Channel channel;
        
        public FilterImpl(MavenBuildProxy2 core, MavenBuildInformation mavenBuildInformation, Channel channel) {
            super(core);
            this.mavenBuildInformation = mavenBuildInformation;
            this.channel = channel;
        }

        @Override
        public void executeAsync(final BuildCallable<?,?> program) throws IOException {
            futures.add(channel.callAsync(new AsyncInvoker(core,program)));
        }

        private static final long serialVersionUID = 1L;

        public MavenBuildInformation getMavenBuildInformation()
        {
            return mavenBuildInformation;
        }
    }    
    
    
    private static final class MavenExecutionListener extends AbstractExecutionListener implements Serializable, ExecutionListener {

        private final Maven3Builder maven3Builder;
       
        /**
         * Number of total nanoseconds {@link Maven3Builder} spent.
         */
        long overheadTime;
        
       
        private final Map<ModuleName,MavenBuildProxy2> proxies;
        
        private final Map<ModuleName,List<ExecutedMojo>> executedMojosPerModule = new ConcurrentHashMap<ModuleName, List<ExecutedMojo>>();
        
        private final Map<ModuleName,List<MavenReporter>> reporters = new ConcurrentHashMap<ModuleName,List<MavenReporter>>();
        
        private final Map<ModuleName, Long> currentMojoStartPerModuleName = new ConcurrentHashMap<ModuleName, Long>();
        
        private ExecutionEventLogger eventLogger;

        public MavenExecutionListener(Maven3Builder maven3Builder) {
            this.maven3Builder = maven3Builder;
            this.proxies = new ConcurrentHashMap<ModuleName, MavenBuildProxy2>(maven3Builder.proxies);
            for (Entry<ModuleName,MavenBuildProxy2> e : this.proxies.entrySet())
            {
                e.setValue(maven3Builder.new FilterImpl(e.getValue(), maven3Builder.mavenBuildInformation, Channel.current()));
                executedMojosPerModule.put( e.getKey(), new CopyOnWriteArrayList<ExecutedMojo>() );
            }
            this.reporters.putAll( new ConcurrentHashMap<ModuleName, List<MavenReporter>>(maven3Builder.reporters) );
            this.eventLogger = new ExecutionEventLogger( new PrintStreamLogger( maven3Builder.listener.getLogger() ) );
        }
        
        private MavenBuildProxy2 getMavenBuildProxy2(MavenProject mavenProject) {
            for (Entry<ModuleName,MavenBuildProxy2> entry : proxies.entrySet()) {   
               if (entry.getKey().compareTo( new ModuleName( mavenProject ) ) == 0) {
                   return entry.getValue();
               }
            }
            return null;
        }
        
        // FIME really used somewhere ???
        // FIXME MojoInfo need the real mojo ?? 
        // so tricky to do. need to use MavenPluginManager on the current Maven Build
        private Mojo getMojo(MojoExecution mojoExecution, MavenSession mavenSession) {
            return null;
        }
        
        private ExpressionEvaluator getExpressionEvaluator(MavenSession session, MojoExecution mojoExecution) {
            return new PluginParameterExpressionEvaluator( session, mojoExecution );
        }

        private List<MavenReporter> getMavenReporters(MavenProject mavenProject) {
            return reporters.get( new ModuleName( mavenProject ) );
        }        
        
        private void initMojoStartTime( MavenProject mavenProject) {
            this.currentMojoStartPerModuleName.put( new ModuleName( mavenProject.getGroupId(),
                                                                    mavenProject.getArtifactId() ),
                                                    Long.valueOf( new Date().getTime() ) );
        }
        
        private Long getMojoStartTime(MavenProject mavenProject) {
            return currentMojoStartPerModuleName.get( new ModuleName( mavenProject.getGroupId(),
                                                                      mavenProject.getArtifactId() ) );
        }
        
        /**
         * @see org.apache.maven.execution.ExecutionListener#projectDiscoveryStarted(org.apache.maven.execution.ExecutionEvent)
         */
        public void projectDiscoveryStarted( ExecutionEvent event ) {
            this.eventLogger.projectDiscoveryStarted( event );
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#sessionStarted(org.apache.maven.execution.ExecutionEvent)
         */
        public void sessionStarted( ExecutionEvent event ) {
            this.eventLogger.sessionStarted( event );
            
            // set all modules which are not actually being build (in incremental builds) to NOT_BUILD
            // JENKINS-9072
            List<MavenProject> projects = event.getSession().getProjects();
            //maven3Builder.listener.getLogger().println("Projects to build: " + projects);
            Set<ModuleName> buildingProjects = new HashSet<ModuleName>();
            for (MavenProject p : projects) {
                buildingProjects.add(new ModuleName(p));
            }
            
            for (Entry<ModuleName,MavenBuildProxy2> e : this.proxies.entrySet()) {
                if (! buildingProjects.contains(e.getKey())) {
                    //maven3Builder.listener.getLogger().println("Project " + e.getKey() + " needs not be build");
                    MavenBuildProxy2 proxy = e.getValue();
                    proxy.start();
                    proxy.setResult(Result.NOT_BUILT);
                    proxy.end();
                }
            }
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#sessionEnded(org.apache.maven.execution.ExecutionEvent)
         */
        public void sessionEnded( ExecutionEvent event )  {
            maven3Builder.listener.getLogger().println( "sessionEnded" );
            this.eventLogger.sessionEnded( event );
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#projectSkipped(org.apache.maven.execution.ExecutionEvent)
         */
        public void projectSkipped( ExecutionEvent event ) {
            maven3Builder.listener.getLogger().println("projectSkipped " + event.getProject().getGroupId() 
                                                       + ":"  + event.getProject().getArtifactId()
                                                       + ":" + event.getProject().getVersion());    
            this.eventLogger.projectSkipped( event );
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#projectStarted(org.apache.maven.execution.ExecutionEvent)
         */
        public void projectStarted( ExecutionEvent event ) {
            //maven3Builder.listener.getLogger().println( "projectStarted " + event.getProject().getGroupId() + ":"
            //                                                + event.getProject().getArtifactId() + ":" + event.getProject().getVersion() );
            reccordProjectStarted( event );        
            this.eventLogger.projectStarted( event );
            
        }
        
        public void reccordProjectStarted( ExecutionEvent event ) {
            MavenProject mavenProject = event.getProject();
            List<MavenReporter> mavenReporters = getMavenReporters( mavenProject );                
            
            MavenBuildProxy2 mavenBuildProxy2 = getMavenBuildProxy2( mavenProject );
            mavenBuildProxy2.start();
            
            
            if (mavenReporters != null) {
                for (MavenReporter mavenReporter : mavenReporters) {
                    try {
                        mavenReporter.enterModule( mavenBuildProxy2 ,mavenProject, maven3Builder.listener);
                    } catch ( InterruptedException e ) {
                        e.printStackTrace();
                    } catch ( IOException e ) {
                        e.printStackTrace();
                    }
                }
            }            
            
            if (mavenReporters != null) {
                for (MavenReporter mavenReporter : mavenReporters) {
                    try {
                        mavenReporter.preBuild( mavenBuildProxy2 ,mavenProject, maven3Builder.listener);
                    } catch ( InterruptedException e ) {
                        e.printStackTrace();
                    } catch ( IOException e ) {
                        e.printStackTrace();
                    }
                }
            }             
            
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#projectSucceeded(org.apache.maven.execution.ExecutionEvent)
         */
        public void projectSucceeded( ExecutionEvent event ) {
            maven3Builder.listener.getLogger().println( "projectSucceeded "
                                                            + event.getProject().getGroupId() + ":"
                                                            + event.getProject().getArtifactId() + ":"
                                                            + event.getProject().getVersion());
            reccordProjectSucceeded( event );
            this.eventLogger.projectSucceeded( event );
        }
        
        public void reccordProjectSucceeded( ExecutionEvent event ) {
            MavenBuildProxy2 mavenBuildProxy2 = getMavenBuildProxy2( event.getProject() );
            mavenBuildProxy2.setResult( Result.SUCCESS );
            
            
            List<MavenReporter> mavenReporters = getMavenReporters( event.getProject() );
            
            if ( mavenReporters != null ) {
                for ( MavenReporter mavenReporter : mavenReporters ) {
                    try {
                        mavenReporter.leaveModule( mavenBuildProxy2, event.getProject(), maven3Builder.listener);
                    } catch ( InterruptedException e ) {
                        e.printStackTrace();
                    } catch ( IOException e ) {
                        e.printStackTrace();
                    }
                }
            }             
            
            if ( mavenReporters != null ) {
                for ( MavenReporter mavenReporter : mavenReporters ) {
                    try {
                        mavenReporter.postBuild( mavenBuildProxy2, event.getProject(), maven3Builder.listener);
                    } catch ( InterruptedException e ) {
                        e.printStackTrace();
                    } catch ( IOException e ) {
                        e.printStackTrace();
                    }
                }
            }
           
            mavenBuildProxy2.end();
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#projectFailed(org.apache.maven.execution.ExecutionEvent)
         */
        public void projectFailed( ExecutionEvent event ) {
            maven3Builder.listener.getLogger().println("projectFailed " + event.getProject().getGroupId() 
                                                                        + ":"  + event.getProject().getArtifactId()
                                                                        + ":" + event.getProject().getVersion());
            reccordProjectFailed( event );
            this.eventLogger.projectFailed( event );
        }
        
        public void reccordProjectFailed( ExecutionEvent event ) {
            MavenBuildProxy2 mavenBuildProxy2 = getMavenBuildProxy2( event.getProject() );
            mavenBuildProxy2.setResult( Result.FAILURE );
            MavenProject mavenProject = event.getProject();
            List<MavenReporter> mavenReporters = getMavenReporters( mavenProject );
            
            if ( mavenReporters != null ) {
                for ( MavenReporter mavenReporter : mavenReporters ) {
                    try {
                        mavenReporter.leaveModule( mavenBuildProxy2, mavenProject, maven3Builder.listener);
                    } catch ( InterruptedException e ) {
                        e.printStackTrace();
                    } catch ( IOException e ) {
                        e.printStackTrace();
                    }
                }
            }             
            
            if ( mavenReporters != null ) {
                for ( MavenReporter mavenReporter : mavenReporters ) {
                    try {
                        mavenReporter.postBuild( mavenBuildProxy2, mavenProject, maven3Builder.listener);
                    } catch ( InterruptedException e ) {
                        e.printStackTrace();
                    } catch ( IOException e ) {
                        e.printStackTrace();
                    }
                }
            }

            mavenBuildProxy2.end();
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#mojoSkipped(org.apache.maven.execution.ExecutionEvent)
         */
        public void mojoSkipped( ExecutionEvent event ) {
            maven3Builder.listener.getLogger().println("mojoSkipped " + event.getMojoExecution().getGroupId() + ":"
                                                       + event.getMojoExecution().getArtifactId() + ":"
                                                       + event.getMojoExecution().getVersion()
                                                       + "(" + event.getMojoExecution().getExecutionId() + ")");
            this.eventLogger.mojoSkipped( event );
        }
        
        /**
         * @see org.apache.maven.execution.ExecutionListener#mojoStarted(org.apache.maven.execution.ExecutionEvent)
         */
        public void mojoStarted( ExecutionEvent event ) {
            //maven3Builder.listener.getLogger().println("mojoStarted " + event.getMojoExecution().getGroupId() + ":"
            //                                                          + event.getMojoExecution().getArtifactId() + ":"
            //                                                          + event.getMojoExecution().getVersion()
            //                                                          + "(" + event.getMojoExecution().getExecutionId() + ")");
            reccordMojoStarted( event );
            this.eventLogger.mojoStarted( event );
        }
        
        public void reccordMojoStarted( ExecutionEvent event ) {
            initMojoStartTime( event.getProject() );
            
            MavenProject mavenProject = event.getProject();
            XmlPlexusConfiguration xmlPlexusConfiguration = new XmlPlexusConfiguration( event.getMojoExecution().getConfiguration() );

            Mojo mojo = null;//getMojo( event.getMojoExecution(), event.getSession() );
            
            MojoInfo mojoInfo =
                new MojoInfo( event.getMojoExecution(), mojo, xmlPlexusConfiguration,
                              getExpressionEvaluator( event.getSession(), event.getMojoExecution() ) );

            List<MavenReporter> mavenReporters = getMavenReporters( mavenProject );                
            
            MavenBuildProxy2 mavenBuildProxy2 = getMavenBuildProxy2( mavenProject );
            
            if (mavenReporters != null) {
                for (MavenReporter mavenReporter : mavenReporters) {
                    try {
                        mavenReporter.preExecute( mavenBuildProxy2, mavenProject, mojoInfo, maven3Builder.listener);
                    } catch ( InterruptedException e ) {
                        e.printStackTrace();
                    } catch ( IOException e ) {
                        e.printStackTrace();
                    }
                }
            }            
        }        

        /**
         * @see org.apache.maven.execution.ExecutionListener#mojoSucceeded(org.apache.maven.execution.ExecutionEvent)
         */
        public void mojoSucceeded( ExecutionEvent event ) {
            //maven3Builder.listener.getLogger().println("mojoSucceeded " + event.getMojoExecution().getGroupId() + ":"
            //                                           + event.getMojoExecution().getArtifactId() + ":"
            //                                           + event.getMojoExecution().getVersion()
            //                                           + "(" + event.getMojoExecution().getExecutionId() + ")");
            reccordMojoSucceeded( event );
            this.eventLogger.mojoSucceeded( event );
        }
        
        public void reccordMojoSucceeded( ExecutionEvent event ) {
            Long startTime = getMojoStartTime( event.getProject() );
            Date endTime = new Date();
            MavenProject mavenProject = event.getProject();
            XmlPlexusConfiguration xmlPlexusConfiguration = new XmlPlexusConfiguration( event.getMojoExecution().getConfiguration() );

            Mojo mojo = null;//getMojo( event.getMojoExecution(), event.getSession() );

            MojoInfo mojoInfo =
                new MojoInfo( event.getMojoExecution(), mojo, xmlPlexusConfiguration,
                              getExpressionEvaluator( event.getSession(), event.getMojoExecution() ) );

            try {
                ExecutedMojo executedMojo =
                    new ExecutedMojo( mojoInfo, startTime == null ? 0 : endTime.getTime() - startTime.longValue() );
                this.executedMojosPerModule.get( new ModuleName( mavenProject.getGroupId(),
                                                                 mavenProject.getArtifactId() ) ).add( executedMojo );
                
            } catch ( Exception e ) {
                // ignoring this
                maven3Builder.listener.getLogger().println( "ignoring exception during new ExecutedMojo "
                                                                + e.getMessage() );
            }
            
            List<MavenReporter> mavenReporters = getMavenReporters( mavenProject );                
            
            MavenBuildProxy2 mavenBuildProxy2 = getMavenBuildProxy2( mavenProject );
            
            mavenBuildProxy2.setExecutedMojos( this.executedMojosPerModule.get( new ModuleName( event.getProject() ) ) );
            
            if (mavenReporters != null) {
                for (MavenReporter mavenReporter : mavenReporters) {
                    try {
                        mavenReporter.postExecute( mavenBuildProxy2, mavenProject, mojoInfo, maven3Builder.listener, null);
                    } catch ( InterruptedException e ) {
                        e.printStackTrace();
                    }
                    catch ( IOException e ) {
                        e.printStackTrace();
                    }
                }
            }
        }        

        /**
         * @see org.apache.maven.execution.ExecutionListener#mojoFailed(org.apache.maven.execution.ExecutionEvent)
         */
        public void mojoFailed( ExecutionEvent event ) {
            maven3Builder.listener.getLogger().println("mojoFailed " + event.getMojoExecution().getGroupId() + ":"
                                                       + event.getMojoExecution().getArtifactId() + ":"
                                                       + event.getMojoExecution().getVersion()
                                                       + "(" + event.getMojoExecution().getExecutionId() + ")");
            reccordMojoFailed( event );
            this.eventLogger.mojoFailed( event );
        }
        
        public void reccordMojoFailed( ExecutionEvent event ) {
            Long startTime = getMojoStartTime( event.getProject() );
            Date endTime = new Date();
            MavenProject mavenProject = event.getProject();
            XmlPlexusConfiguration xmlPlexusConfiguration = new XmlPlexusConfiguration( event.getMojoExecution().getConfiguration() );

            Mojo mojo = null;//getMojo( event.getMojoExecution(), event.getSession() );

            MojoInfo mojoInfo =
                new MojoInfo( event.getMojoExecution(), mojo, xmlPlexusConfiguration,
                              getExpressionEvaluator( event.getSession(), event.getMojoExecution() ) );

            try {
                ExecutedMojo executedMojo =
                    new ExecutedMojo( mojoInfo, startTime == null ? 0 : endTime.getTime() - startTime.longValue() );
                this.executedMojosPerModule.get( new ModuleName( mavenProject.getGroupId(),
                                                                 mavenProject.getArtifactId() ) ).add( executedMojo );
            } catch ( Exception e ) {
                // ignoring this
                maven3Builder.listener.getLogger().println( "ignoring exception during new ExecutedMojo "
                                                                + e.getMessage() );
            }
            
            List<MavenReporter> mavenReporters = getMavenReporters( mavenProject );                
            
            MavenBuildProxy2 mavenBuildProxy2 = getMavenBuildProxy2( mavenProject );
            
            mavenBuildProxy2.setExecutedMojos( this.executedMojosPerModule.get( new ModuleName( event.getProject() ) ) );
            
            if (mavenReporters != null) {
                for (MavenReporter mavenReporter : mavenReporters) {
                    try {
                        // http://issues.jenkins-ci.org/browse/HUDSON-8493
                        // with maven 3.0.2 see http://jira.codehaus.org/browse/MNG-4922
                        // catch NoSuchMethodError if folks not using 3.0.2+
                        try {
                            mavenReporter.postExecute( mavenBuildProxy2, mavenProject, mojoInfo, maven3Builder.listener, event.getException() );
                        } catch (NoSuchMethodError e) {
                            mavenReporter.postExecute( mavenBuildProxy2, mavenProject, mojoInfo, maven3Builder.listener, null );
                        }
                    } catch ( InterruptedException e ) {
                        e.printStackTrace();
                    } catch ( IOException e ) {
                        e.printStackTrace();
                    }
                }
            }            
        }        

        /**
         * @see org.apache.maven.execution.ExecutionListener#forkStarted(org.apache.maven.execution.ExecutionEvent)
         */
        public void forkStarted( ExecutionEvent event )
        {
            maven3Builder.listener.getLogger().println("mojo forkStarted " + event.getMojoExecution().getGroupId() + ":"
                                                       + event.getMojoExecution().getArtifactId() + ":"
                                                       + event.getMojoExecution().getVersion()
                                                       + "(" + event.getMojoExecution().getExecutionId() + ")");
            reccordMojoStarted( event );
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#forkSucceeded(org.apache.maven.execution.ExecutionEvent)
         */
        public void forkSucceeded( ExecutionEvent event ) {
            maven3Builder.listener.getLogger().println("mojo forkSucceeded " + event.getMojoExecution().getGroupId() + ":"
                                                       + event.getMojoExecution().getArtifactId() + ":"
                                                       + event.getMojoExecution().getVersion()
                                                       + "(" + event.getMojoExecution().getExecutionId() + ")");
            reccordMojoSucceeded( event );
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#forkFailed(org.apache.maven.execution.ExecutionEvent)
         */
        public void forkFailed( ExecutionEvent event ) {
            maven3Builder.listener.getLogger().println("mojo forkFailed " + event.getMojoExecution().getGroupId() + ":"
                                                       + event.getMojoExecution().getArtifactId() + ":"
                                                       + event.getMojoExecution().getVersion()
                                                       + "(" + event.getMojoExecution().getExecutionId() + ")");  
            reccordMojoFailed( event );
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#forkedProjectStarted(org.apache.maven.execution.ExecutionEvent)
         */
        public void forkedProjectStarted( ExecutionEvent event ) {
            maven3Builder.listener.getLogger().println( "forkedProjectStarted " + event.getProject().getGroupId() + ":"
                                                        + event.getProject().getArtifactId() + event.getProject().getVersion() );
            reccordProjectStarted( event ); 
            this.eventLogger.forkedProjectStarted( event );
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#forkedProjectSucceeded(org.apache.maven.execution.ExecutionEvent)
         */
        public void forkedProjectSucceeded( ExecutionEvent event ) {
            maven3Builder.listener.getLogger().println( "forkedProjectSucceeded "
                                                        + event.getProject().getGroupId() + ":"
                                                        + event.getProject().getArtifactId()
                                                        + event.getProject().getVersion());
            reccordProjectSucceeded( event );
            this.eventLogger.forkedProjectSucceeded( event );
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#forkedProjectFailed(org.apache.maven.execution.ExecutionEvent)
         */
        public void forkedProjectFailed( ExecutionEvent event ) {
            maven3Builder.listener.getLogger().println("forkedProjectFailed " + event.getProject().getGroupId() 
                                                       + ":"  + event.getProject().getArtifactId()
                                                       + ":" + event.getProject().getVersion());
            reccordProjectFailed( event );
        }        
        
    }    
    
    public static boolean markAsSuccess;

    private static final long serialVersionUID = 1L;    
    
}
