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
import hudson.model.Executor;
import hudson.model.Result;
import hudson.remoting.Channel;
import hudson.remoting.DelegatingCallable;
import hudson.remoting.Future;

import java.io.IOException;
import java.io.Serializable;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;

import jenkins.model.Jenkins;

/**
 * @author Olivier Lamy
 * @author Christoph Kutzinski
 *
 */
public abstract class AbstractMavenBuilder implements DelegatingCallable<Result,IOException> {
    
    private static final long serialVersionUID = -2687215937784908860L;
    /**
     * Goals to be executed in this Maven execution.
     */
    protected final List<String> goals;
    /**
     * Hudson-defined system properties. These will be made available to Maven,
     * and accessible as if they are specified as -Dkey=value
     */
    protected final Map<String,String> systemProps;
    /**
     * Where error messages and so on are sent.
     */
    protected final BuildListener listener;
    
    protected Map<ModuleName,FilterImpl> proxies;
    
    /**
     * Kept so that we can finalize them in the end method.
     */
    protected final transient Map<ModuleName,ProxyImpl2> sourceProxies = new HashMap<ModuleName, MavenBuild.ProxyImpl2>();

    protected final Map<ModuleName,List<MavenReporter>> reporters = new HashMap<ModuleName,List<MavenReporter>>();
    
    /**
     * Record all asynchronous executions as they are scheduled,
     * to make sure they are all completed before we finish.
     */
    protected transient /*final*/ List<Future<?>> futures;
    
    protected AbstractMavenBuilder(BuildListener listener, Collection<MavenModule> modules, List<String> goals, Map<String, String> systemProps) {
        this.listener = listener;
        this.goals = goals;
        this.systemProps = systemProps;
        
        for (MavenModule m : modules) {
            reporters.put(m.getModuleName(),m.createReporters());
        }
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
    
    protected String formatArgs(List<String> args) {
        StringBuilder buf = new StringBuilder("Executing Maven: ");
        for (String arg : args) {
            final String argPassword = "-Dpassword=" ;
            String filteredArg = arg ;
            // check if current arg is password arg. Then replace password by ***** 
            if (arg.startsWith(argPassword)) {
                filteredArg=argPassword+"*********";
            }
            buf.append(' ').append(filteredArg);
        }
        return buf.toString();
    }

    /**
     * Add all the {@link #systemProps jenkins environment variables} into the {@link System#getProperties() system properties}
     * Ignores {@link #systemProps jenkins environment variables} with empty keys.
     * @throws IllegalArgumentException if a {@link #systemProps jenkins environment variable} has null value
     *      as it blows up Maven.
     * @see http://jenkins.361315.n4.nabble.com/Upgrade-to-1-424-broke-our-Maven-builds-due-to-empty-system-property-key-td3726460.html
     */
    protected void registerSystemProperties() {
        for (Map.Entry<String,String> e : systemProps.entrySet()) {
            if ("".equals(e.getKey()))
                continue;
            if (e.getValue()==null)
                throw new IllegalArgumentException("Global Environment Variable "+e.getKey()+" has a null value");
            System.getProperties().put(e.getKey(), e.getValue());
        }
    }

    protected String format(NumberFormat n, long nanoTime) {
        return n.format(nanoTime/1000000);
    }

    // since reporters might be from plugins, use the uberjar to resolve them.
    public ClassLoader getClassLoader() {
        return Jenkins.getInstance().getPluginManager().uberClassLoader;
    }
    
    /**
     * Initialize the collection of the asynchronous executions.
     * The method must be called in the Maven jail process i.e. inside the call method!
     */
    protected void initializeAsynchronousExecutions() {
        futures = new CopyOnWriteArrayList<Future<?>>();
        if (this.proxies != null) {
            for(FilterImpl proxy : this.proxies.values()) {
                proxy.setFutures(futures);
            }
        }
    }
    
    /**
     * Records a new asynchronous exection.
     */
    protected void recordAsynchronousExecution(Future<?> future) {
        futures.add(future);
    }
    
    /**
     * Waits until all asynchronous executions are finished.
     * 
     * @return null in success case; returns an ABORT result if we were interrupted while waiting
     */
    protected Result waitForAsynchronousExecutions() {
        try {
            boolean messageReported = false;
            
            for (Future<?> f : futures) {
                try {
                    if(!messageReported && !f.isDone()) {
                        messageReported = true;
                        listener.getLogger().println(Messages.MavenBuilder_Waiting());
                    }
                    f.get();
                } catch (InterruptedException e) {
                    // attempt to cancel all asynchronous tasks
                    for (Future<?> g : futures)
                        g.cancel(true);
                    listener.getLogger().println(Messages.MavenBuilder_Aborted());
                    return Executor.currentExecutor().abortResult();
                } catch (ExecutionException e) {
                    e.printStackTrace(listener.error(Messages.MavenBuilder_AsyncFailed()));
                }
            }
            return null;
        } finally {
            futures.clear();
        }
    }
    
    protected boolean isDebug() {
        for(String goal : goals) {
            if (goal.equals("-X") || goal.equals("--debug"))  return true;
        }
        return false;
    }
    
    protected boolean isQuiet() {
        for(String goal : goals) {
            if (goal.equals("-q") || goal.equals("--quiet"))  return true;
        }
        return false;
    }

    protected static class FilterImpl extends MavenBuildProxy2.Filter<MavenBuildProxy2> implements Serializable {
        
        private MavenBuildInformation mavenBuildInformation;

        /**
         * Maven can internally use multiple threads to call {@link #executeAsync(BuildCallable)},
         * making it impossible to rely on {@code Channel#current()} at the point of call, so
         * instead we capture it when we get deserialized into Maven JVM.
         * In other cases, we create FilterImpl inside Maven JVM, so we take it as a constructor.
         * See JENKINS-11458
         */
        private transient Channel channel;

        private transient List<Future<?>> futures;

        public FilterImpl(MavenBuildProxy2 core, MavenBuildInformation mavenBuildInformation) {
            super(core);
            this.mavenBuildInformation = mavenBuildInformation;
        }

        public FilterImpl(MavenBuildProxy2 core, MavenBuildInformation mavenBuildInformation, Channel channel) {
            super(core);
            this.mavenBuildInformation = mavenBuildInformation;
            if (channel == null) {
                throw new NullPointerException("channel must not be null!");
            }
            this.channel = channel;
        }

        @Override
        public void executeAsync(final BuildCallable<?,?> program) throws IOException {
            futures.add(
                    channel.callAsync(
                            new AsyncInvoker(core,program)));
        }

        public MavenBuildInformation getMavenBuildInformation() {
            return mavenBuildInformation;
        }
        
        public void setFutures(List<Future<?>> futures) {
            this.futures = futures;
        }

        public Object readResolve() {
            channel = Channel.current();
            return this;
        }

        private static final long serialVersionUID = 1L;
    }
    
}
