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
import hudson.model.BuildListener;
import hudson.model.Hudson;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;

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
    
    protected Maven3Builder(BuildListener listener,Map<ModuleName,ProxyImpl2> proxies, Map<ModuleName,List<MavenReporter>> reporters, List<String> goals, Map<String, String> systemProps) {
        super( listener, goals, systemProps );
        sourceProxies = new HashMap<ModuleName, ProxyImpl2>(proxies);
        this.proxies = new HashMap<ModuleName, MavenBuildProxy2>(proxies);
        for (Entry<ModuleName,MavenBuildProxy2> e : this.proxies.entrySet())
            e.setValue(new FilterImpl(e.getValue()));

        this.reporters.putAll( reporters );
    }    
    
    public Result call() throws IOException {

        MavenExecutionListener mavenExecutionListener = new MavenExecutionListener( this );
        try {
            futures = new ArrayList<Future<?>>();
            
            Maven3Launcher.setMavenExecutionListener( mavenExecutionListener );
            
            markAsSuccess = false;

            // working around NPE when someone puts a null value into systemProps.
            for (Map.Entry<String,String> e : systemProps.entrySet()) {
                if (e.getValue()==null)
                    throw new IllegalArgumentException("System property "+e.getKey()+" has a null value");
                System.getProperties().put(e.getKey(), e.getValue());
            }

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
                    return Result.ABORTED;
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
            
            
            //FIXME handle
            //mavenExecutionResult.getThrowables()
            PrintStream logger = listener.getLogger();
            logger.println("Maven3Builder classLoaderDebug");
            logger.println("getClass().getClassLoader(): " + getClass().getClassLoader());
            
            if(r==0 && mavenExecutionResult.getThrowables().isEmpty()) {
                logger.print( "r==0" );
                markAsSuccess = true;
            }
            if (!mavenExecutionResult.getThrowables().isEmpty()) {
                logger.println( "mavenExecutionResult.throwables not empty");
                for(Throwable throwable : mavenExecutionResult.getThrowables()) {
                    throwable.printStackTrace( logger );
                }
                markAsSuccess = false;
            }

            if(markAsSuccess) {
                // FIXME message
                //listener.getLogger().println(Messages.MavenBuilder_Failed());
                listener.getLogger().println("success");
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
        return Hudson.getInstance().getPluginManager().uberClassLoader;
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
        public FilterImpl(MavenBuildProxy2 core) {
            super(core);
        }

        @Override
        public void executeAsync(final BuildCallable<?,?> program) throws IOException {
            futures.add(Channel.current().callAsync(new AsyncInvoker(core,program)));
        }

        private static final long serialVersionUID = 1L;
    }    
    
    
    private static final class MavenExecutionListener extends AbstractExecutionListener implements Serializable, ExecutionListener {

        private final Maven3Builder maven3Builder;
       
        /**
         * Number of total nanoseconds {@link Maven3Builder} spent.
         */
        long overheadTime;
        
       
        private final Map<ModuleName,MavenBuildProxy2> proxies;
        
        private final Map<ModuleName,List<ExecutedMojo>> executedMojosPerModule = new ConcurrentHashMap<ModuleName, List<ExecutedMojo>>();
        
        private final Map<ModuleName,List<MavenReporter>> reporters = new HashMap<ModuleName,List<MavenReporter>>();
        
        private final Map<ModuleName, Long> currentMojoStartPerModuleName = new ConcurrentHashMap<ModuleName, Long>();

        public MavenExecutionListener(Maven3Builder maven3Builder) {
            this.maven3Builder = maven3Builder;
            this.proxies = new HashMap<ModuleName, MavenBuildProxy2>(maven3Builder.proxies);
            for (Entry<ModuleName,MavenBuildProxy2> e : this.proxies.entrySet())
            {
                e.setValue(maven3Builder.new FilterImpl(e.getValue()));
                executedMojosPerModule.put( e.getKey(), new CopyOnWriteArrayList<ExecutedMojo>() );
            }
            this.reporters.putAll( new HashMap<ModuleName, List<MavenReporter>>(maven3Builder.reporters) );
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
            // no op
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#sessionStarted(org.apache.maven.execution.ExecutionEvent)
         */
        public void sessionStarted( ExecutionEvent event ) {
            // no op
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#sessionEnded(org.apache.maven.execution.ExecutionEvent)
         */
        public void sessionEnded( ExecutionEvent event )  {
            maven3Builder.listener.getLogger().println( "sessionEnded in MavenExecutionListener " );
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#projectSkipped(org.apache.maven.execution.ExecutionEvent)
         */
        public void projectSkipped( ExecutionEvent event ) {
            MavenBuildProxy2 mavenBuildProxy2 = getMavenBuildProxy2( event.getProject() );
            maven3Builder.listener.getLogger().println("projectSkipped" );
            mavenBuildProxy2.end();
            mavenBuildProxy2.setResult( Result.ABORTED );            
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#projectStarted(org.apache.maven.execution.ExecutionEvent)
         */
        public void projectStarted( ExecutionEvent event ) {
            maven3Builder.listener.getLogger().println( "projectStarted in MavenExecutionListener "
                                                            + event.getProject().getGroupId() + ":"
                                                            + event.getProject().getArtifactId() );
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
            maven3Builder.listener.getLogger().println( "projectSucceeded in MavenExecutionListener "
                                                            + event.getProject().getGroupId() + ":"
                                                            + event.getProject().getArtifactId() );
            MavenProject mavenProject = event.getProject();
            List<MavenReporter> mavenReporters = getMavenReporters( mavenProject );

            MavenBuildProxy2 mavenBuildProxy2 = getMavenBuildProxy2( mavenProject );
            mavenBuildProxy2.end();
            mavenBuildProxy2.setResult( Result.SUCCESS );
            
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
           
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#projectFailed(org.apache.maven.execution.ExecutionEvent)
         */
        public void projectFailed( ExecutionEvent event ) {
            maven3Builder.listener.getLogger().println("projectFailed" );
            MavenBuildProxy2 mavenBuildProxy2 = getMavenBuildProxy2( event.getProject() );
            mavenBuildProxy2.end();
            mavenBuildProxy2.setResult( Result.FAILURE );
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#mojoSkipped(org.apache.maven.execution.ExecutionEvent)
         */
        public void mojoSkipped( ExecutionEvent event ) {
            // TODO ?
        }
        
        /**
         * @see org.apache.maven.execution.ExecutionListener#mojoStarted(org.apache.maven.execution.ExecutionEvent)
         */
        public void mojoStarted( ExecutionEvent event ) {
            initMojoStartTime( event.getProject() );
            maven3Builder.listener.getLogger().println("mojoStarted " + event.getMojoExecution().getArtifactId());
            
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
            Long startTime = getMojoStartTime( event.getProject() );
            Date endTime = new Date();
            maven3Builder.listener.getLogger().println("mojoSucceeded " + event.getMojoExecution().getArtifactId());
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
            maven3Builder.listener.getLogger().println("mojoFailed " + event.getMojoExecution().getArtifactId());
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
                        // FIXME get exception during mojo execution ?
                        mavenReporter.postExecute( mavenBuildProxy2, mavenProject, mojoInfo, maven3Builder.listener, null );
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
            // TODO !
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#forkSucceeded(org.apache.maven.execution.ExecutionEvent)
         */
        public void forkSucceeded( ExecutionEvent event ) {
            // TODO !
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#forkFailed(org.apache.maven.execution.ExecutionEvent)
         */
        public void forkFailed( ExecutionEvent event ) {
            // TODO !            
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#forkedProjectStarted(org.apache.maven.execution.ExecutionEvent)
         */
        public void forkedProjectStarted( ExecutionEvent event ) {
            // TODO !
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#forkedProjectSucceeded(org.apache.maven.execution.ExecutionEvent)
         */
        public void forkedProjectSucceeded( ExecutionEvent event ) {
            // TODO !
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#forkedProjectFailed(org.apache.maven.execution.ExecutionEvent)
         */
        public void forkedProjectFailed( ExecutionEvent event ) {
            // TODO !
        }        
        
    }    
    
    public static boolean markAsSuccess;

    private static final long serialVersionUID = 1L;    
    
}
