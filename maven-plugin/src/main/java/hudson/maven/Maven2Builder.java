package hudson.maven;

import hudson.Launcher;
import hudson.maven.MavenBuild.ProxyImpl2;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.remoting.Channel;
import hudson.util.MaskingClassLoader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.maven.BuildFailureException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ReactorManager;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.monitor.event.EventDispatcher;
import org.apache.maven.project.MavenProject;

/**
 * Runs Maven and builds the project.
 *
 * This is only used for
 * {@link MavenModuleSet#isAggregatorStyleBuild() the aggregator style build}.
 */
final class Maven2Builder extends MavenBuilder {
    private final Map<ModuleName,MavenBuildProxy2> proxies;
    private final Map<ModuleName,List<MavenReporter>> reporters = new HashMap<ModuleName,List<MavenReporter>>();
    private final Map<ModuleName,List<ExecutedMojo>> executedMojos = new HashMap<ModuleName,List<ExecutedMojo>>();
    private long mojoStartTime;

    private MavenBuildProxy2 lastProxy;

    /**
     * Kept so that we can finalize them in the end method.
     */
    private final transient Map<ModuleName,ProxyImpl2> sourceProxies;

    public Maven2Builder(BuildListener listener,Map<ModuleName,ProxyImpl2> proxies, Collection<MavenModule> modules, List<String> goals, Map<String,String> systemProps,  MavenBuildInformation mavenBuildInformation) {
        super(listener,goals,systemProps);
        this.sourceProxies = proxies;
        this.proxies = new HashMap<ModuleName, MavenBuildProxy2>(proxies);
        for (Entry<ModuleName,MavenBuildProxy2> e : this.proxies.entrySet())
            e.setValue(new FilterImpl(e.getValue(), mavenBuildInformation, Channel.current()));

        for (MavenModule m : modules)
            reporters.put(m.getModuleName(),m.createReporters());
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

    @Override
    public Result call() throws IOException {
        try {
            if (MavenModuleSetBuild.debug) {
                listener.getLogger().println("Builder extends MavenBuilder in call " + Thread.currentThread().getContextClassLoader());
            }
            return super.call();
        } finally {
            if(lastProxy!=null)
                lastProxy.appendLastLog();
        }
    }


    @Override
    void preBuild(MavenSession session, ReactorManager rm, EventDispatcher dispatcher) throws BuildFailureException, LifecycleExecutionException, IOException, InterruptedException {
        // set all modules which are not actually being build (in incremental builds) to NOT_BUILD
        
        List<MavenProject> projects = rm.getSortedProjects();
        Set<ModuleName> buildingProjects = new HashSet<ModuleName>();
        for (MavenProject p : projects) {
            buildingProjects.add(new ModuleName(p));
        }
        
        for (Entry<ModuleName,MavenBuildProxy2> e : this.proxies.entrySet()) {
            if (! buildingProjects.contains(e.getKey())) {
                MavenBuildProxy2 proxy = e.getValue();
                proxy.start();
                proxy.setResult(Result.NOT_BUILT);
                proxy.end();
            }
        }
    }

    void postBuild(MavenSession session, ReactorManager rm, EventDispatcher dispatcher) throws BuildFailureException, LifecycleExecutionException, IOException, InterruptedException {
        // TODO
    }

    void preModule(MavenProject project) throws InterruptedException, IOException, hudson.maven.agent.AbortException {
        ModuleName name = new ModuleName(project);
        MavenBuildProxy2 proxy = proxies.get(name);
        listener.getLogger().flush();   // make sure the data until here are all written
        proxy.start();
        for (MavenReporter r : reporters.get(name))
            if(!r.preBuild(proxy,project,listener))
                throw new hudson.maven.agent.AbortException(r+" failed");
    }

    void postModule(MavenProject project) throws InterruptedException, IOException, hudson.maven.agent.AbortException {
        ModuleName name = new ModuleName(project);
        MavenBuildProxy2 proxy = proxies.get(name);
        List<MavenReporter> rs = reporters.get(name);
        if(rs==null) { // probe for issue #906
            throw new AssertionError("reporters.get("+name+")==null. reporters="+reporters+" proxies="+proxies);
        }
        for (MavenReporter r : rs)
            if(!r.postBuild(proxy,project,listener))
                throw new hudson.maven.agent.AbortException(r+" failed");
        proxy.setExecutedMojos(executedMojos.get(name));
        listener.getLogger().flush();   // make sure the data until here are all written
        proxy.end();
        lastProxy = proxy;
    }

    void preExecute(MavenProject project, MojoInfo mojoInfo) throws IOException, InterruptedException, hudson.maven.agent.AbortException {
        ModuleName name = new ModuleName(project);
        MavenBuildProxy proxy = proxies.get(name);
        for (MavenReporter r : reporters.get(name))
            if(!r.preExecute(proxy,project,mojoInfo,listener))
                throw new hudson.maven.agent.AbortException(r+" failed");

        mojoStartTime = System.currentTimeMillis();
    }

    void postExecute(MavenProject project, MojoInfo mojoInfo, Exception exception) throws IOException, InterruptedException, hudson.maven.agent.AbortException {
        ModuleName name = new ModuleName(project);

        List<ExecutedMojo> mojoList = executedMojos.get(name);
        if(mojoList==null)
            executedMojos.put(name,mojoList=new ArrayList<ExecutedMojo>());
        mojoList.add(new ExecutedMojo(mojoInfo,System.currentTimeMillis()-mojoStartTime));

        MavenBuildProxy2 proxy = proxies.get(name);
        for (MavenReporter r : reporters.get(name))
            if(!r.postExecute(proxy,project,mojoInfo,listener,exception))
                throw new hudson.maven.agent.AbortException(r+" failed");
        if(exception!=null)
            proxy.setResult(Result.FAILURE);
    }

    void onReportGenerated(MavenProject project, MavenReportInfo report) throws IOException, InterruptedException, hudson.maven.agent.AbortException {
        ModuleName name = new ModuleName(project);
        MavenBuildProxy proxy = proxies.get(name);
        for (MavenReporter r : reporters.get(name))
            if(!r.reportGenerated(proxy,project,report,listener))
                throw new hudson.maven.agent.AbortException(r+" failed");
    }
    
    

    private static final long serialVersionUID = 1L;

    @Override
    public ClassLoader getClassLoader()
    {
        return new MaskingClassLoader( super.getClassLoader() );
    }
}