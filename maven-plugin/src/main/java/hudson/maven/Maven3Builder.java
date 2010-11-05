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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;

import org.apache.maven.execution.AbstractExecutionListener;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.PluginConfigurationException;
import org.apache.maven.plugin.PluginContainerException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.jvnet.hudson.maven3.agent.Maven3Main;
import org.jvnet.hudson.maven3.launcher.Maven3Launcher;
import org.jvnet.hudson.maven3.listeners.HudsonMavenBuildHelper;
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
    private final Map<ModuleName,List<MavenReporter>> reporters = new HashMap<ModuleName,List<MavenReporter>>();
    private final Map<ModuleName,List<ExecutedMojo>> executedMojos = new HashMap<ModuleName,List<ExecutedMojo>>();
    private long mojoStartTime;
    private Collection<MavenModule> modules;
    private MavenBuildProxy2 lastProxy;    
    
    protected Maven3Builder(BuildListener listener,Map<ModuleName,ProxyImpl2> proxies, Collection<MavenModule> modules, List<String> goals, Map<String, String> systemProps) {
        super( listener, goals, systemProps );
        this.proxies = new HashMap<ModuleName, MavenBuildProxy2>(proxies);
        for (Entry<ModuleName,MavenBuildProxy2> e : this.proxies.entrySet())
            e.setValue(new FilterImpl(e.getValue()));

        for (MavenModule m : modules)
        {
            reporters.put(m.getModuleName(),m.createReporters());
        }
        this.modules = modules;
    }    
    
    public Result call()
        throws IOException
    {

        try {
            futures = new ArrayList<Future<?>>();
            MavenExecutionListener mavenExecutionListener = new MavenExecutionListener( this );
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
            
            PrintStream logger = listener.getLogger();
            logger.println("Maven3Builder classLoaderDebug");
            logger.println("getClass().getClassLoader(): " + getClass().getClassLoader());
            
            
            if(r==0) {
                logger.print( "r==0" );
                markAsSuccess = true;
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
        
        private ExpressionEvaluator expressionEvaluator;
        
        private MavenSession mavenSession;
        
        private MavenPluginManager mavenPluginManager;

        /**
         * Number of total nanoseconds {@link Maven3Builder} spent.
         */
        long overheadTime;
        
       
        private final Map<ModuleName,MavenBuildProxy2> proxies;
        
        private final Map<ModuleName,List<MavenReporter>> reporters = new HashMap<ModuleName,List<MavenReporter>>();

        public MavenExecutionListener(Maven3Builder maven3Builder) {
            this.maven3Builder = maven3Builder;
            expressionEvaluator = HudsonMavenBuildHelper.getEvaluator();
            mavenPluginManager = HudsonMavenBuildHelper.getMavenPluginManager();
            this.proxies = new HashMap<ModuleName, MavenBuildProxy2>(maven3Builder.proxies);
            for (Entry<ModuleName,MavenBuildProxy2> e : this.proxies.entrySet())
            {
                e.setValue(maven3Builder.new FilterImpl(e.getValue()));
            }
            for (MavenModule m : maven3Builder.modules)
            {
                reporters.put(m.getModuleName(),m.createReporters());
            }
        }
        
        private MavenBuildProxy2 getMavenBuildProxy2(MavenProject mavenProject)
        {
            for (Entry<ModuleName,MavenBuildProxy2> entry : proxies.entrySet())
            {   
               if (entry.getKey().compareTo( new ModuleName( mavenProject ) ) == 0)
               {
                   return entry.getValue();
               }
            }
            return null;
        }
        
        private Mojo getMojo(MojoExecution mojoExecution)
        {
            try
            {
                return mavenPluginManager.getConfiguredMojo( Mojo.class, mavenSession, mojoExecution );
            }
            catch ( PluginContainerException e )
            {
                throw new RuntimeException( e.getMessage(), e );
            }
            catch ( PluginConfigurationException e )
            {
                throw new RuntimeException( e.getMessage(), e );
            }
        }
        
        /**
         * @see org.apache.maven.execution.ExecutionListener#projectDiscoveryStarted(org.apache.maven.execution.ExecutionEvent)
         */
        public void projectDiscoveryStarted( ExecutionEvent event ) { }

        /**
         * @see org.apache.maven.execution.ExecutionListener#sessionStarted(org.apache.maven.execution.ExecutionEvent)
         */
        public void sessionStarted( ExecutionEvent event )
        {
            this.mavenSession = event.getSession();
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#sessionEnded(org.apache.maven.execution.ExecutionEvent)
         */
        public void sessionEnded( ExecutionEvent event ) { }

        /**
         * @see org.apache.maven.execution.ExecutionListener#projectSkipped(org.apache.maven.execution.ExecutionEvent)
         */
        public void projectSkipped( ExecutionEvent event )
        {

        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#projectStarted(org.apache.maven.execution.ExecutionEvent)
         */
        public void projectStarted( ExecutionEvent event )
        {
            maven3Builder.listener.getLogger().println( "projectStarted in MavenExecutionListener "
                                                            + event.getProject().getGroupId() + ":"
                                                            + event.getProject().getArtifactId() );
            
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#projectSucceeded(org.apache.maven.execution.ExecutionEvent)
         */
        public void projectSucceeded( ExecutionEvent event )
        {
            maven3Builder.listener.getLogger().println("projectSucceeded in adapter" );
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#projectFailed(org.apache.maven.execution.ExecutionEvent)
         */
        public void projectFailed( ExecutionEvent event )
        {
            maven3Builder.listener.getLogger().println("projectFailed" );
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#mojoSkipped(org.apache.maven.execution.ExecutionEvent)
         */
        public void mojoSkipped( ExecutionEvent event )
        {

        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#mojoStarted(org.apache.maven.execution.ExecutionEvent)
         */
        public void mojoStarted( ExecutionEvent event )
        {
            maven3Builder.listener.getLogger().println("mojoStarted " + event.getMojoExecution().getArtifactId());
            MavenProject mavenProject = event.getProject();
            XmlPlexusConfiguration xmlPlexusConfiguration = new XmlPlexusConfiguration( event.getMojoExecution().getConfiguration() );

            Mojo mojo = getMojo( event.getMojoExecution() );
            
            MojoInfo mojoInfo = new MojoInfo( event.getMojoExecution(), null, xmlPlexusConfiguration, expressionEvaluator );
            
            List<MavenReporter> mavenReporters = reporters.get( mavenProject.getArtifactId() );
            if (mavenReporters != null)
            {
                for (MavenReporter mavenReporter : mavenReporters)
                {
                    try
                    {
                        mavenReporter.preExecute( getMavenBuildProxy2( mavenProject ), mavenProject, mojoInfo, maven3Builder.listener);
                    }
                    catch ( InterruptedException e )
                    {
                        e.printStackTrace();
                    }
                    catch ( IOException e )
                    {
                        e.printStackTrace();
                    }
                }
            }            
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#mojoSucceeded(org.apache.maven.execution.ExecutionEvent)
         */
        public void mojoSucceeded( ExecutionEvent event )
        {
            maven3Builder.listener.getLogger().println("mojoSucceeded " + event.getMojoExecution().getArtifactId());
            
            //maven3Builder.proxies
            
            MavenProject mavenProject = event.getProject();
            
            List<MavenReporter> mavenReporters = maven3Builder.reporters.get( mavenProject.getArtifactId() );
            if (mavenReporters != null)
            {
                for (MavenReporter mavenReporter : mavenReporters)
                {
                    //mavenReporter.end( build, launcher, listener )
                }
            }
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#mojoFailed(org.apache.maven.execution.ExecutionEvent)
         */
        public void mojoFailed( ExecutionEvent event )
        {
            maven3Builder.listener.getLogger().println("mojoFailed " + event.getMojoExecution().getArtifactId());
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#forkStarted(org.apache.maven.execution.ExecutionEvent)
         */
        public void forkStarted( ExecutionEvent event )
        {

        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#forkSucceeded(org.apache.maven.execution.ExecutionEvent)
         */
        public void forkSucceeded( ExecutionEvent event )
        {

        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#forkFailed(org.apache.maven.execution.ExecutionEvent)
         */
        public void forkFailed( ExecutionEvent event )
        {

        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#forkedProjectStarted(org.apache.maven.execution.ExecutionEvent)
         */
        public void forkedProjectStarted( ExecutionEvent event )
        {

        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#forkedProjectSucceeded(org.apache.maven.execution.ExecutionEvent)
         */
        public void forkedProjectSucceeded( ExecutionEvent event )
        {

        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#forkedProjectFailed(org.apache.maven.execution.ExecutionEvent)
         */
        public void forkedProjectFailed( ExecutionEvent event )
        {

        }
        
        public ExpressionEvaluator getExpressionEvaluator()
        {
            return expressionEvaluator;
        }
        public void setExpressionEvaluator( ExpressionEvaluator expressionEvaluator )
        {
            this.expressionEvaluator = expressionEvaluator;
        }
        
        
    }    
    
    public static boolean markAsSuccess;

    private static final long serialVersionUID = 1L;    
    
}
