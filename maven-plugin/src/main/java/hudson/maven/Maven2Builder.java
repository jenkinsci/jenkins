/*
 * The MIT License
 * 
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Red Hat, Inc., Victor Glushenkov, Alan Harder, Olivier Lamy, Christoph Kutzinski
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;

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
@SuppressWarnings("deprecation") // as we're restricted to Maven 2.x API here, but compile against Maven 3.x, we cannot avoid deprecations
final class Maven2Builder extends MavenBuilder {
    private final Map<ModuleName,List<ExecutedMojo>> executedMojos = new HashMap<ModuleName,List<ExecutedMojo>>();
    private long mojoStartTime;

    private MavenBuildProxy2 lastProxy;
    private final AtomicBoolean hasTestFailures = new AtomicBoolean();
    

    public Maven2Builder(BuildListener listener,Map<ModuleName,ProxyImpl2> proxies, Collection<MavenModule> modules, List<String> goals, Map<String,String> systemProps,  MavenBuildInformation mavenBuildInformation) {
        super(listener,modules,goals,systemProps);
        this.sourceProxies.putAll(proxies);
        this.proxies = new HashMap<ModuleName, FilterImpl>();
        for (Entry<ModuleName,ProxyImpl2> e : this.sourceProxies.entrySet()) {
            this.proxies.put(e.getKey(), new FilterImpl(e.getValue(), mavenBuildInformation));
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
        
        for (Entry<ModuleName,FilterImpl> e : this.proxies.entrySet()) {
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
        for (MavenReporter r : reporters.get(name)){
            if(!r.postExecute(proxy,project,mojoInfo,listener,exception)) {
                throw new hudson.maven.agent.AbortException(r+" failed");
            } else if (r instanceof TestFailureDetector) {
                if(((TestFailureDetector) r).hasTestFailures()) {
                    hasTestFailures.compareAndSet(false, true);
                }
            }
        }
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
    
    @Override
    public boolean hasBuildFailures() {
        return hasTestFailures.get();
    }    

    private static final long serialVersionUID = 1L;
}