/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Olivier Lamy, CloudBees, Inc.
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
import hudson.maven.reporters.TestFailureDetector;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.remoting.Channel;
import hudson.remoting.DelegatingCallable;
import hudson.util.IOException2;
import org.apache.maven.cli.event.ExecutionEventLogger;
import org.apache.maven.eventspy.EventSpy;
import org.apache.maven.execution.AbstractExecutionListener;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.jvnet.hudson.maven3.listeners.HudsonMavenExecutionResult;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static hudson.Util.fixNull;

/**
 * @author Olivier Lamy
 * @author Kohsuke Kawaguchi
 */
public class Maven3Builder extends AbstractMavenBuilder implements DelegatingCallable<Result,IOException> {

    /**
     * Flag needs to be set at the constructor, so that this reflects
     * the setting at master.
     */
    private final boolean profile = MavenProcessFactory.profile;
    
    HudsonMavenExecutionResult mavenExecutionResult;

    Class<?> maven3MainClass;
    Class<?> maven3LauncherClass;
    boolean supportEventSpy = false;

    protected Maven3Builder(Maven3BuilderRequest maven3BuilderRequest) {
        super( maven3BuilderRequest.listener, maven3BuilderRequest.modules, maven3BuilderRequest.goals, maven3BuilderRequest.systemProps );
        this.sourceProxies.putAll(maven3BuilderRequest.proxies);
        this.proxies = new HashMap<ModuleName, FilterImpl>();
        for (Entry<ModuleName,ProxyImpl2> e : this.sourceProxies.entrySet()) {
            this.proxies.put(e.getKey(), new FilterImpl(e.getValue(), maven3BuilderRequest.mavenBuildInformation));
        }
        this.maven3LauncherClass = maven3BuilderRequest.maven3LauncherClass;
        this.maven3MainClass = maven3BuilderRequest.maven3MainClass;
        this.supportEventSpy = maven3BuilderRequest.supportEventSpy;
    }

    protected static class Maven3BuilderRequest {
        BuildListener listener;
        Map<ModuleName,ProxyImpl2> proxies;
        Collection<MavenModule> modules;
        List<String> goals;
        Map<String, String> systemProps;
        MavenBuildInformation mavenBuildInformation;
        Class<?> maven3MainClass;
        Class<?> maven3LauncherClass;
        boolean supportEventSpy = false;
    }
    
    public Result call() throws IOException {

        try {
            initializeAsynchronousExecutions();

            MavenExecutionListener mavenExecutionListener = supportEventSpy ? new JenkinsEventSpy(this) : new MavenExecutionListener( this );
            if (supportEventSpy)
            {
                Method setEventSpiesMethod = maven3LauncherClass.getMethod( "setEventSpies", List.class );
                setEventSpiesMethod.invoke( null, Collections.singletonList(mavenExecutionListener) );

            } else {
                Method setMavenExecutionListenerMethod = maven3LauncherClass.getMethod( "setMavenExecutionListener", ExecutionListener.class );

                setMavenExecutionListenerMethod.invoke( null, mavenExecutionListener );
            }
            markAsSuccess = false;

            registerSystemProperties();

            PrintStream logger = listener.getLogger();
            logger.println(formatArgs(goals));

            Method launchMethod = maven3MainClass.getMethod( "launch", String[].class );

            Integer res = (Integer) launchMethod.invoke(null, new Object[] {goals.toArray(new String[goals.size()])} );

            //int r = Maven3Main.launch( goals.toArray(new String[goals.size()]));

            int r = res.intValue();

            // now check the completion status of async ops
            long startTime = System.nanoTime();
            
            Result waitForAsyncExecutionsResult = waitForAsynchronousExecutions();
            if (waitForAsyncExecutionsResult != null) {
                return waitForAsyncExecutionsResult;
            }
            
            mavenExecutionListener.overheadTime += System.nanoTime()-startTime;

            if(profile) {
                NumberFormat n = NumberFormat.getInstance();
                logger.println("Total overhead was "+format(n,mavenExecutionListener.overheadTime)+"ms");
                Channel ch = Channel.current();
                logger.println("Class loading "   +format(n,ch.classLoadingTime.get())   +"ms, "+ch.classLoadingCount+" classes");
                logger.println("Resource loading "+format(n,ch.resourceLoadingTime.get())+"ms, "+ch.resourceLoadingCount+" times");                
            }

            Method mavenExecutionResultGetMethod = maven3LauncherClass.getMethod( "getMavenExecutionResult", null );

            mavenExecutionResult = (HudsonMavenExecutionResult) mavenExecutionResultGetMethod.invoke( null, null );

            //mavenExecutionResult = Maven3Launcher.getMavenExecutionResult();
            
            if(r==0 && mavenExecutionResult.getThrowables().isEmpty()) {
                if(mavenExecutionListener.hasTestFailures()){
                    return Result.UNSTABLE;    
                }
                return Result.SUCCESS;
            }

			// manage of Maven error are moved to ExecutionEventLogger, they are
			// threaded as in MavenCli

            if(markAsSuccess) {
                logger.println(Messages.MavenBuilder_Failed());
                if(mavenExecutionListener.hasTestFailures()){
                    return Result.UNSTABLE;    
                }
                return Result.SUCCESS;
            }
            return Result.FAILURE;
        } catch (NoSuchMethodException e) {
            throw new IOException2(e);
        } catch (IllegalAccessException e) {
            throw new IOException2(e);
        } catch (InvocationTargetException e) {
            throw new IOException2(e);
        //} catch (ClassNotFoundException e) {
        //    throw new IOException2(e);
        } catch (Exception e) {
            throw new IOException2(e);
        } finally {
            if (DUMP_PERFORMANCE_COUNTERS)
                Channel.current().dumpPerformanceCounters(listener.error("Remoting stats"));
        }
    }

    private static final class JenkinsEventSpy extends MavenExecutionListener implements EventSpy,Serializable{
        private static final long serialVersionUID = 4942789836756366117L;

        public JenkinsEventSpy(AbstractMavenBuilder maven3Builder) {
           super(maven3Builder);
            // avoid log event output duplication for maven 3.1 build which use eventSpy
            // there is a delagation which duplicate log event.
            this.eventLogger = new ExecutionEventLogger(  ){
                @Override
                public void projectDiscoveryStarted( ExecutionEvent event ) {  }

                @Override
                public void sessionStarted( ExecutionEvent event ){  }

                @Override
                public void sessionEnded( ExecutionEvent event ){  }

                @Override
                public void projectSkipped( ExecutionEvent event ){  }

                @Override
                public void projectStarted( ExecutionEvent event ){  }

                @Override
                public void mojoSkipped( ExecutionEvent event ){  }

                @Override
                public void mojoStarted( ExecutionEvent event ){  }

                @Override
                public void forkStarted( ExecutionEvent event ){  }

                @Override
                public void forkSucceeded( ExecutionEvent event ){  }

                @Override
                public void forkedProjectStarted( ExecutionEvent event ){  }

                @Override
                public void projectSucceeded( ExecutionEvent event ){  }

                @Override
                public void projectFailed( ExecutionEvent event ){  }

                @Override
                public void forkFailed( ExecutionEvent event ){  }

                @Override
                public void mojoSucceeded( ExecutionEvent event ){  }

                @Override
                public void mojoFailed( ExecutionEvent event ){  }

                @Override
                public void forkedProjectSucceeded( ExecutionEvent event ){  }

                @Override
                public void forkedProjectFailed( ExecutionEvent event ){  }

            };
        }

        @Override
        public void init( Context context )
            throws Exception
        {
            //no op
        }

        @Override
        public void onEvent( Object event )
            throws Exception
        {
            if (event instanceof ExecutionEvent){
                ExecutionEvent.Type eventType = ( (ExecutionEvent) event ).getType();

                switch ( eventType )
                {
                    case ProjectDiscoveryStarted:
                        super.projectDiscoveryStarted( (ExecutionEvent) event );
                        break;
                    case SessionStarted:
                        super.sessionStarted( (ExecutionEvent) event );
                        break;
                    case SessionEnded:
                        super.sessionEnded( (ExecutionEvent) event );
                        break;
                    case ProjectSkipped:
                        super.projectSkipped( (ExecutionEvent) event );
                        break;
                    case ProjectStarted:
                        super.projectStarted( (ExecutionEvent) event );
                        break;
                    case ProjectSucceeded:
                        super.projectSucceeded( (ExecutionEvent) event );
                        break;
                    case ProjectFailed:
                        super.projectFailed( (ExecutionEvent) event );
                        break;
                    case MojoSkipped:
                        super.mojoSkipped( (ExecutionEvent) event );
                        break;
                    case MojoStarted:
                        super.mojoStarted( (ExecutionEvent) event );
                        break;
                    case MojoSucceeded:
                        super.mojoSucceeded( (ExecutionEvent) event );
                        break;
                    case MojoFailed:
                        super.mojoFailed( (ExecutionEvent) event );
                        break;
                    case ForkStarted:
                        super.forkedProjectStarted( (ExecutionEvent) event );
                        break;
                    case ForkSucceeded:
                        super.forkSucceeded( (ExecutionEvent) event );
                        break;
                    case ForkFailed:
                        super.forkFailed( (ExecutionEvent) event );
                        break;
                    case ForkedProjectStarted:
                        super.forkedProjectStarted( (ExecutionEvent) event );
                        break;
                    case ForkedProjectSucceeded:
                        super.forkedProjectSucceeded( (ExecutionEvent) event );
                        break;
                    case ForkedProjectFailed:
                        super.forkFailed( (ExecutionEvent) event );
                        break;
                    default:
                        LOGGER.fine( "event not managed" );
                }

            }
        }

        @Override
        public void close()
            throws Exception
        {
            //no op
        }
    }

    private static class MavenExecutionListener extends AbstractExecutionListener implements Serializable, ExecutionListener {

        private static final long serialVersionUID = 4942789836756366116L;

        private final AbstractMavenBuilder maven3Builder;
        
        private AtomicBoolean hasTestFailures = new AtomicBoolean();

        private org.slf4j.Logger logger = LoggerFactory.getLogger( MavenExecutionListener.class );

        /**
         * Number of total nanoseconds {@link Maven3Builder} spent.
         */
        long overheadTime;
        
       
        private final Map<ModuleName,FilterImpl> proxies;
        
        private final Map<ModuleName,List<ExecutedMojo>> executedMojosPerModule = new ConcurrentHashMap<ModuleName, List<ExecutedMojo>>();
        
        private final Map<ModuleName,List<MavenReporter>> reporters;
        
        private final Map<ModuleName, Long> currentMojoStartPerModuleName = new ConcurrentHashMap<ModuleName, Long>();
        
        protected ExecutionEventLogger eventLogger;

        public MavenExecutionListener(AbstractMavenBuilder maven3Builder) {
            this.maven3Builder = maven3Builder;
            this.proxies = new ConcurrentHashMap<ModuleName, FilterImpl>(maven3Builder.proxies);
            for (ModuleName name : this.proxies.keySet()) {
                executedMojosPerModule.put( name, new CopyOnWriteArrayList<ExecutedMojo>() );
            }
            this.reporters = new ConcurrentHashMap<ModuleName, List<MavenReporter>>(maven3Builder.reporters);
            

            // E.g. there's also the option to redirect logging to a file which is handled there, but not here.
            this.eventLogger = new ExecutionEventLogger( logger );
        }



        /**
         * Whether there where test failures detected during the build.
         * @since 1.496
         */
        public boolean hasTestFailures(){
            return hasTestFailures.get();
        }
        
        private MavenBuildProxy2 getMavenBuildProxy2(MavenProject mavenProject) {
            for (Entry<ModuleName,FilterImpl> entry : proxies.entrySet()) {   
               if (entry.getKey().compareTo( new ModuleName( mavenProject ) ) == 0) {
                   return entry.getValue();
               }
            }
            return null;
        }
        
        private List<MavenReporter> getMavenReporters(MavenProject mavenProject) {
            return reporters.get( new ModuleName( mavenProject ) );
        }        
        
        private long initMojoStartTime( MavenProject mavenProject) {
            long mojoStartTime = System.currentTimeMillis();
            this.currentMojoStartPerModuleName.put( new ModuleName( mavenProject), mojoStartTime);
            return mojoStartTime;
        }
        
        private Long getMojoStartTime(MavenProject mavenProject) {
            return currentMojoStartPerModuleName.get( new ModuleName(mavenProject) );
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
            this.eventLogger.sessionStarted(event);
            
            Map<ModuleName, MavenProject> buildingProjects = getSessionProjects(event);
            
            for (Entry<ModuleName,FilterImpl> e : this.proxies.entrySet()) {
                MavenProject project = buildingProjects.get(e.getKey());
                if (project!=null) {
                    for (MavenReporter mavenReporter : fixNull(reporters.get(e.getKey()))) {
                        try {
                            mavenReporter.preBuild( e.getValue() ,project, maven3Builder.listener);
                        } catch ( InterruptedException x ) {
                            x.printStackTrace();
                        } catch ( IOException x ) {
                            x.printStackTrace();
                        }
                    }
                } else {
                    // set all modules which are not actually being build (in incremental builds) to NOT_BUILD (JENKINS-9072)
                    LOGGER.fine("Project " + e.getKey() + " needs not be build");

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
            debug( "sessionEnded" );
            this.eventLogger.sessionEnded( event );

            Map<ModuleName, MavenProject> buildingProjects = getSessionProjects(event);

            for (Entry<ModuleName,FilterImpl> e : fixNull(this.proxies.entrySet())) {
                MavenProject project = buildingProjects.get(e.getKey());
                if (project!=null) {
                    for (MavenReporter mavenReporter : reporters.get(e.getKey())) {
                        try {
                            mavenReporter.postBuild( e.getValue() ,project, maven3Builder.listener);
                        } catch ( InterruptedException x ) {
                            x.printStackTrace();
                        } catch ( IOException x ) {
                            x.printStackTrace();
                        }
                    }
                }
            }
        }

        /**
         * All {@link MavenProject}s in the current session, keyed by their names.
         */
        private Map<ModuleName, MavenProject> getSessionProjects(ExecutionEvent event) {
            List<MavenProject> projects = event.getSession().getProjects();
            debug("Projects to build: " + projects);
            Map<ModuleName,MavenProject> buildingProjects = new HashMap<ModuleName,MavenProject>();
            for (MavenProject p : projects) {
                buildingProjects.put(new ModuleName(p), p);
            }
            return buildingProjects;
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#projectSkipped(org.apache.maven.execution.ExecutionEvent)
         */
        public void projectSkipped( ExecutionEvent event ) {
            debug("projectSkipped " + gav(event.getProject()));
            this.eventLogger.projectSkipped( event );
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#projectStarted(org.apache.maven.execution.ExecutionEvent)
         */
        public void projectStarted( ExecutionEvent event ) {
            debug( "projectStarted " + gav(event.getProject()));
            recordProjectStarted(event);
            this.eventLogger.projectStarted( event );
            
        }
        
        private void recordProjectStarted(ExecutionEvent event) {
            MavenProject mavenProject = event.getProject();
            List<MavenReporter> mavenReporters = getMavenReporters( mavenProject );                
            
            MavenBuildProxy2 mavenBuildProxy2 = getMavenBuildProxy2( mavenProject );
            mavenBuildProxy2.start();
            
            
            for (MavenReporter mavenReporter : fixNull(mavenReporters)) {
                try {
                    mavenReporter.enterModule( mavenBuildProxy2 ,mavenProject, maven3Builder.listener);
                } catch ( InterruptedException e ) {
                    e.printStackTrace();
                } catch ( IOException e ) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#projectSucceeded(org.apache.maven.execution.ExecutionEvent)
         */
        public void projectSucceeded( ExecutionEvent event ) {
            debug( "projectSucceeded "+gav(event.getProject()));
            recordProjectEnded(event,Result.SUCCESS);
            this.eventLogger.projectSucceeded( event );
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#projectFailed(org.apache.maven.execution.ExecutionEvent)
         */
        public void projectFailed( ExecutionEvent event ) {
            debug("projectFailed " + gav(event.getProject()));
            recordProjectEnded(event,Result.FAILURE);
            this.eventLogger.projectFailed(event);
        }

        private void recordProjectEnded(ExecutionEvent event, Result result) {
            MavenBuildProxy2 mavenBuildProxy2 = getMavenBuildProxy2( event.getProject() );
            mavenBuildProxy2.setResult(result);

            List<MavenReporter> mavenReporters = getMavenReporters( event.getProject() );

            for ( MavenReporter mavenReporter : fixNull(mavenReporters)) {
                try {
                    mavenReporter.leaveModule( mavenBuildProxy2, event.getProject(), maven3Builder.listener);
                } catch ( InterruptedException e ) {
                    e.printStackTrace();
                } catch ( IOException e ) {
                    e.printStackTrace();
                }
            }

            mavenBuildProxy2.end();
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#mojoSkipped(org.apache.maven.execution.ExecutionEvent)
         */
        public void mojoSkipped( ExecutionEvent event ) {
            debug("mojoSkipped " + mojoExec(event));
            this.eventLogger.mojoSkipped( event );
        }
        
        /**
         * @see org.apache.maven.execution.ExecutionListener#mojoStarted(org.apache.maven.execution.ExecutionEvent)
         */
        public void mojoStarted( ExecutionEvent event ) {
            debug( "mojoStarted " + mojoExec( event ) );
            recordMojoStarted(event);
            this.eventLogger.mojoStarted( event );
        }
        
        private void recordMojoStarted(ExecutionEvent event) {
            long startTime = initMojoStartTime( event.getProject() );
            
            MavenProject mavenProject = event.getProject();
            MojoInfo mojoInfo = new MojoInfo(event,startTime);

            List<MavenReporter> mavenReporters = getMavenReporters( mavenProject );                
            
            MavenBuildProxy2 mavenBuildProxy2 = getMavenBuildProxy2( mavenProject );
            
            for (MavenReporter mavenReporter : fixNull(mavenReporters)) {
                try {
                    mavenReporter.preExecute( mavenBuildProxy2, mavenProject, mojoInfo, maven3Builder.listener);
                } catch ( InterruptedException e ) {
                    e.printStackTrace();
                } catch ( IOException e ) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#mojoSucceeded(org.apache.maven.execution.ExecutionEvent)
         */
        public void mojoSucceeded( ExecutionEvent event ) {
            debug("mojoSucceeded " + mojoExec(event));
            recordMojoEnded(event,null);
            this.eventLogger.mojoSucceeded( event );
        }
        
        private void recordMojoEnded(ExecutionEvent event, Exception problem) {
            MavenProject mavenProject = event.getProject();
            MojoInfo mojoInfo = new MojoInfo(event,getMojoStartTime(event.getProject()));

            recordExecutionTime(event,mojoInfo);

            List<MavenReporter> mavenReporters = getMavenReporters( mavenProject );                
            
            MavenBuildProxy2 mavenBuildProxy2 = getMavenBuildProxy2( mavenProject );
            
            mavenBuildProxy2.setExecutedMojos( this.executedMojosPerModule.get( new ModuleName(event) ) );
            
            for (MavenReporter mavenReporter : fixNull(mavenReporters)) {
                try {
                    mavenReporter.postExecute( mavenBuildProxy2, mavenProject, mojoInfo, maven3Builder.listener, problem);
                    if (mavenReporter instanceof TestFailureDetector) {
                        if(((TestFailureDetector) mavenReporter).hasTestFailures()) {
                            hasTestFailures.compareAndSet(false, true);
                        }
                    }
                } catch ( InterruptedException e ) {
                    e.printStackTrace();
                } catch ( IOException e ) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * Record how long it took to run this mojo.
         */
        private void recordExecutionTime(ExecutionEvent event, MojoInfo mojoInfo) {
            MavenProject p = event.getProject();
            List<ExecutedMojo> m = executedMojosPerModule.get(new ModuleName(p));
            if (m==null)    // defensive check
                executedMojosPerModule.put(new ModuleName(p), m=new CopyOnWriteArrayList<ExecutedMojo>());

            Long startTime = getMojoStartTime( event.getProject() );
            m.add(new ExecutedMojo( mojoInfo, startTime == null ? 0 : System.currentTimeMillis() - startTime ));
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#mojoFailed(org.apache.maven.execution.ExecutionEvent)
         */
        public void mojoFailed( ExecutionEvent event ) {
            debug("mojoFailed " + mojoExec(event));
            recordMojoEnded(event, getExecutionException(event));
            this.eventLogger.mojoFailed( event );
        }

        private void debug(String msg) {
            LOGGER.fine(msg);
        }
        

        private Exception getExecutionException(ExecutionEvent event) {
            // http://issues.jenkins-ci.org/browse/JENKINS-8493
            // with maven 3.0.2 see http://jira.codehaus.org/browse/MNG-4922
            // catch NoSuchMethodError if folks not using 3.0.2+
            try {
                return event.getException();
            } catch (NoSuchMethodError e) {
                return new MojoExecutionException(event.getMojoExecution()+" failed");
            }
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#forkStarted(org.apache.maven.execution.ExecutionEvent)
         */
        public void forkStarted( ExecutionEvent event ) {
            LOGGER.fine("mojo forkStarted " + mojoExec(event));
            recordMojoStarted(event);
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#forkSucceeded(org.apache.maven.execution.ExecutionEvent)
         */
        public void forkSucceeded( ExecutionEvent event ) {
            LOGGER.fine("mojo forkSucceeded " + mojoExec(event));
            recordMojoEnded(event,null);
        }

        /**
         * @see org.apache.maven.execution.ExecutionListener#forkFailed(org.apache.maven.execution.ExecutionEvent)
         */
        public void forkFailed( ExecutionEvent event ) {
            LOGGER.fine("mojo forkFailed " + mojoExec(event));
            recordMojoEnded(event, getExecutionException(event));
        }

        /*
            Forked life cycle handling
            --------------------------

            As discussed in MavenBuildProxy2, Jenkins has a simplistic view of Maven build sequence,
            and in particular it doesn't recognize the forked life cycle as a first-class citizen.
            So to map the reality with the Jenkins' simplified model, we don't report forked project as
            a separate module start/end.

            Doing so would require that we remember the nesting, and when the forking is over, we need to
            tell MavenBuildProxy2 of the right module that its build has resumed.
         */

        public void forkedProjectStarted( ExecutionEvent event ) {
            debug("forkedProjectStarted " + gav(event.getProject()));
//            recordProjectStarted(event);
            this.eventLogger.forkedProjectStarted( event );
        }

        public void forkedProjectSucceeded( ExecutionEvent event ) {
            debug("forkedProjectSucceeded " +gav(event.getProject()));
//            recordProjectEnded(event,Result.SUCCESS);
            this.eventLogger.forkedProjectSucceeded(event);
        }

        public void forkedProjectFailed( ExecutionEvent event ) {
            debug("forkedProjectFailed " +gav(event.getProject()));
//            recordProjectEnded(event,Result.FAILURE);
        }

        private String gav(MavenProject p) {
            return String.format("%s:%s:%s", p.getGroupId(), p.getArtifactId(), p.getVersion());
        }

        private String mojoExec(ExecutionEvent event) {
            MojoExecution me = event.getMojoExecution();
            return String.format("%s:%s:%s(%s)", me.getGroupId(), me.getArtifactId(), me.getVersion(), me.getExecutionId());
        }
    }

    public static boolean markAsSuccess;

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(Maven3Builder.class.getName());

    public static boolean DUMP_PERFORMANCE_COUNTERS = Boolean.getBoolean(Maven3Builder.class.getName()+".dumpPerformanceCounters");
}
